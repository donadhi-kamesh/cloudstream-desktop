package dev.csdesktop.player

import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import dev.csdesktop.extloader.AppPaths
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Locale

data class DrmPlaybackRequest(
    val manifestUrl: String,
    val licenseUrl: String?,
    val headers: Map<String, String>,
    val keyRequestParameters: Map<String, String>,
    val scheme: DrmScheme,
    val title: String,
)

object WebView2Runtime {
    const val INSTALLER_URL = "https://go.microsoft.com/fwlink/p/?LinkId=2124703"
    private val clientIds = listOf(
        "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}",
        "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}",
    )

    fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

    fun isAvailable(): Boolean {
        if (!isWindows()) return false
        val paths = listOf(
            File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Microsoft/EdgeWebView/Application"),
            File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Microsoft/EdgeWebView/Application"),
        )
        if (paths.any { it.isDirectory && it.list()?.isNotEmpty() == true }) return true
        return runCatching {
            val roots = listOf(WinReg.HKEY_LOCAL_MACHINE, WinReg.HKEY_CURRENT_USER)
            val pathsReg = listOf(
                "SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}",
                "SOFTWARE\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}",
            )
            roots.any { hive ->
                pathsReg.any { path ->
                    Advapi32Util.registryKeyExists(hive, path)
                }
            }
        }.getOrDefault(false)
    }

    fun missingMessage(): String {
        return if (!isWindows()) {
            "Widevine and PlayReady playback uses the Windows Edge WebView2 CDM. This OS has no WebView2 runtime."
        } else {
            "Microsoft Edge WebView2 runtime is not installed. CloudStream Desktop does not ship a CDM. Install the Evergreen runtime, then retry."
        }
    }
}

class ShakaPlayerHost {
    fun htmlFor(req: DrmPlaybackRequest): String {
        val scheme = when (req.scheme) {
            DrmScheme.Widevine -> "com.widevine.alpha"
            DrmScheme.PlayReady -> "com.microsoft.playready"
            else -> ""
        }
        val headerEntries = req.headers.entries.joinToString(",") {
            "\"${escape(it.key)}\":\"${escape(it.value)}\""
        }
        val extra = req.keyRequestParameters.entries.joinToString(",") {
            "\"${escape(it.key)}\":\"${escape(it.value)}\""
        }
        return SHAKA_HTML
            .replace("{{TITLE}}", escape(req.title))
            .replace("{{MANIFEST}}", escape(req.manifestUrl))
            .replace("{{LICENSE}}", escape(req.licenseUrl.orEmpty()))
            .replace("{{SCHEME}}", scheme)
            .replace("{{HEADERS}}", "{$headerEntries}")
            .replace("{{EXTRA}}", "{$extra}")
    }

    fun writeAndOpen(req: DrmPlaybackRequest): File {
        val html = htmlFor(req)
        val file = File(AppPaths.cache, "shaka-play.html")
        file.writeText(html)
        return file
    }

    fun openInBrowser(req: DrmPlaybackRequest) {
        val file = writeAndOpen(req)
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(file.toURI())
        } else {
            throw IllegalStateException("No desktop browser available to open the Shaka player page")
        }
    }

    companion object {
        fun fromLink(link: DrmExtractorLink, title: String, scheme: DrmScheme): DrmPlaybackRequest {
            return DrmPlaybackRequest(
                manifestUrl = link.url,
                licenseUrl = link.licenseUrl,
                headers = link.headers,
                keyRequestParameters = link.keyRequestParameters,
                scheme = scheme,
                title = title,
            )
        }

        private fun escape(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("<", "\\u003c")
    }
}

private val SHAKA_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <title>{{TITLE}} — CloudStream Desktop</title>
  <style>
    html,body { margin:0; background:#09090b; color:#fafafa; font-family:Segoe UI,sans-serif; height:100%; }
    video { width:100%; height:100%; background:#000; }
    #err { display:none; padding:24px; }
    a { color:#c084fc; }
  </style>
  <script src="https://cdn.jsdelivr.net/npm/shaka-player@4.11.7/dist/shaka-player.compiled.min.js"></script>
</head>
<body>
<video id="v" autoplay controls></video>
<div id="err"></div>
<script>
async function start() {
  const video = document.getElementById('v');
  const err = document.getElementById('err');
  function fail(msg) {
    video.style.display = 'none';
    err.style.display = 'block';
    err.textContent = msg;
  }
  try {
    shaka.polyfill.installAll();
    if (!shaka.Player.isBrowserSupported()) {
      fail('This browser cannot play DRM video with Shaka Player.');
      return;
    }
    const player = new shaka.Player();
    await player.attach(video);
    player.addEventListener('error', (e) => {
      const d = e.detail || {};
      fail('Shaka error ' + (d.code || '') + ': ' + (d.message || JSON.stringify(d)));
    });
    const scheme = "{{SCHEME}}";
    const license = "{{LICENSE}}";
    const headers = {{HEADERS}};
    const extra = {{EXTRA}};
    if (scheme && license) {
      player.configure({
        drm: {
          servers: { [scheme]: license },
          advanced: { [scheme]: { distinctiveIdentifierRequired: false, persistentStateRequired: false } }
        }
      });
      player.getNetworkingEngine().registerRequestFilter((type, request) => {
        Object.assign(request.headers, headers);
        if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) {
          Object.assign(request.headers, extra);
        }
      });
    } else if (scheme && !license) {
      fail('DRM scheme ' + scheme + ' requires a license server URL. None was provided by the extractor.');
      return;
    }
    await player.load("{{MANIFEST}}");
  } catch (e) {
    fail(String(e));
  }
}
document.addEventListener('DOMContentLoaded', start);
</script>
</body>
</html>
""".trimIndent()
