package dev.csdesktop.player

import com.lagradost.cloudstream3.utils.DrmExtractorLink
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny ClearKey helper: serves a JWKS key file and optionally rewrites HLS/DASH
 * manifests so mpv can decrypt ClearKey streams locally (no CDM).
 */
class ClearKeyProxy {
    private val client = OkHttpClient()
    private val keys = ConcurrentHashMap<String, KeySet>()
    private val seq = AtomicInteger(1)
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var port: Int = 0
        private set

    data class KeySet(val kid: String, val key: String, val headers: Map<String, String> = emptyMap())

    fun start() {
        if (engine != null) return
        val server = embeddedServer(CIO, port = 47832, host = "127.0.0.1") {
            routing {
                get("/ck/{id}/keys.json") {
                    val id = call.parameters["id"].orEmpty()
                    val ks = keys[id]
                    if (ks == null) {
                        call.respondText("missing", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondText(clearkeyJson(ks), ContentType.Application.Json)
                }
                get("/ck/{id}/manifest") {
                    val id = call.parameters["id"].orEmpty()
                    val url = call.request.queryParameters["u"].orEmpty()
                    val ks = keys[id]
                    if (ks == null || url.isBlank()) {
                        call.respondText("missing", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    val builder = Request.Builder().url(url)
                    ks.headers.forEach { (k, v) -> builder.header(k, v) }
                    if (ks.headers.keys.none { it.equals("user-agent", true) }) {
                        builder.header("User-Agent", StreamProxy.DEFAULT_UA)
                    }
                    val body = client.newCall(builder.build()).execute().use { it.body?.string().orEmpty() }
                    val rewritten = rewriteManifest(body, ks, url, id)
                    val type = if (url.contains(".mpd")) ContentType.Application.Xml
                    else ContentType.parse("application/vnd.apple.mpegurl")
                    call.respondText(rewritten, type)
                }
            }
        }
        engine = server.start(wait = false)
        port = 47832
    }

    fun stop() {
        engine?.stop(200, 500)
        engine = null
    }

    fun prepare(link: DrmExtractorLink): PreparedClearKey {
        start()
        val kid = normalizeHex(link.kid)
        val key = normalizeHex(link.key)
        if (kid.isNullOrBlank() || key.isNullOrBlank()) {
            throw IllegalArgumentException("ClearKey link is missing kid/key")
        }
        val id = seq.getAndIncrement().toString()
        val headers = HashMap<String, String>(link.headers)
        if (link.referer.isNotBlank()) headers.putIfAbsent("Referer", link.referer)
        keys[id] = KeySet(kid, key, headers)
        val keysUrl = "http://127.0.0.1:$port/ck/$id/keys.json"
        val manifest = "http://127.0.0.1:$port/ck/$id/manifest?u=${StreamProxy.encode(link.url)}"
        return PreparedClearKey(
            manifestUrl = manifest,
            keysUrl = keysUrl,
            mpvArgs = listOf(
                "--demuxer-lavf-o=decryption_key=$key",
            ),
        )
    }

    private fun rewriteManifest(body: String, ks: KeySet, originalUrl: String, id: String): String {
        // HLS: inject a local KEY URI. DASH: leave as-is; mpv uses --demuxer-lavf-o decryption_key.
        if (originalUrl.contains(".m3u8") || body.contains("#EXTM3U")) {
            val keyLine = "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"http://127.0.0.1:$port/ck/$id/keys.json\",KEYFORMAT=\"identity\""
            return if (body.contains("#EXT-X-KEY")) {
                body.replace(Regex("#EXT-X-KEY:[^\\n]+"), keyLine)
            } else {
                body.replace("#EXTM3U", "#EXTM3U\n$keyLine")
            }
        }
        return body
    }

    companion object {
        fun clearkeyJson(ks: KeySet): String {
            val kidB64 = hexToB64Url(ks.kid)
            val keyB64 = hexToB64Url(ks.key)
            return """{"keys":[{"kty":"oct","kid":"$kidB64","k":"$keyB64"}],"type":"temporary"}"""
        }

        fun normalizeHex(value: String?): String? {
            if (value.isNullOrBlank()) return null
            val trimmed = value.trim()
            val decoded = if (trimmed.length == 16 || trimmed.length == 24 || trimmed.contains('=') || trimmed.contains('-') || trimmed.contains('_')) {
                runCatching {
                    val padded = trimmed.replace('-', '+').replace('_', '/')
                    val withPad = padded + "=".repeat((4 - padded.length % 4) % 4)
                    Base64.getDecoder().decode(withPad).joinToString("") { "%02x".format(it) }
                }.getOrNull()
            } else null
            val hex = (decoded ?: trimmed).lowercase().replace("0x", "").replace(" ", "").replace("-", "")
            return hex.ifBlank { null }
        }

        private fun hexToB64Url(hex: String): String {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

data class PreparedClearKey(
    val manifestUrl: String,
    val keysUrl: String,
    val mpvArgs: List<String>,
)
