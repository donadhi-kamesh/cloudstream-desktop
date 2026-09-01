package dev.csdesktop.player

import com.lagradost.api.Log
import com.lagradost.cloudstream3.network.DesktopCookieJar
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local reverse proxy that re-attaches Referer / User-Agent / Cookie headers on
 * HLS/DASH playlists and their child segments. Many CDNs 403 without this.
 */
class StreamProxy {
    private val client = OkHttpClient.Builder()
        .cookieJar(DesktopCookieJar)
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val seq = AtomicInteger(1)
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var port: Int = 0
        private set

    data class Session(
        val id: String,
        val headers: Map<String, String>,
    )

    fun start() {
        if (engine != null) return
        val server = embeddedServer(CIO, port = 47831, host = "127.0.0.1") {
            routing {
                get("/s/{id}/{encoded}") {
                    val id = call.parameters["id"].orEmpty()
                    val encoded = call.parameters["encoded"].orEmpty()
                    val target = decode(encoded)
                    val session = sessions[id]
                    if (session == null) {
                        call.respondText("unknown session", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    proxy(call, session, target)
                }
            }
        }
        engine = server.start(wait = false)
        port = 47831
        Log.i("StreamProxy", "listening on 127.0.0.1:$port")
    }

    fun stop() {
        engine?.stop(200, 500)
        engine = null
    }

    fun wrap(url: String, headers: Map<String, String>): String {
        start()
        val id = seq.getAndIncrement().toString()
        val merged = HashMap<String, String>()
        headers.forEach { (k, v) -> merged[k] = v }
        DesktopCookieJar.getCookieHeader(url)?.let { merged.putIfAbsent("Cookie", it) }
        sessions[id] = Session(id, merged)
        return "http://127.0.0.1:$port/s/$id/${encode(url)}"
    }

    private suspend fun proxy(
        call: io.ktor.server.application.ApplicationCall,
        session: Session,
        url: String,
    ) {
        val builder = Request.Builder().url(url)
        session.headers.forEach { (k, v) -> builder.header(k, v) }
        if (!session.headers.keys.any { it.equals("user-agent", true) }) {
            builder.header("User-Agent", DEFAULT_UA)
        }
        if (!session.headers.keys.any { it.equals("referer", true) }) {
            originOf(url)?.let { builder.header("Referer", it) }
        }
        call.request.headers["Range"]?.let { builder.header("Range", it) }
        val resp = try {
            client.newCall(builder.build()).execute()
        } catch (t: Throwable) {
            Log.e("StreamProxy", "fetch failed $url: ${t.message}")
            call.respondText(t.message ?: "proxy error", status = HttpStatusCode.BadGateway)
            return
        }
        resp.use { response ->
            if (response.code >= 400) {
                Log.w("StreamProxy", "${response.code} $url")
            }
            val contentType = response.header("Content-Type").orEmpty()
            call.response.status(HttpStatusCode.fromValue(response.code))
            response.header("Content-Type")?.let { call.response.header(HttpHeaders.ContentType, it) }
            val body = response.body ?: run {
                call.respondBytes(ByteArray(0))
                return
            }
            val isPlaylist = contentType.contains("mpegurl", true) ||
                contentType.contains("dash+xml", true) ||
                url.substringBefore('?').endsWith(".m3u8") ||
                url.substringBefore('?').endsWith(".mpd")
            if (isPlaylist) {
                val text = rewritePlaylist(body.string(), url, session.id)
                val type = if (url.contains(".mpd")) ContentType.Application.Xml else ContentType.parse("application/vnd.apple.mpegurl")
                call.respondText(text, type)
            } else {
                val length = response.header("Content-Length")
                if (length != null) call.response.header(HttpHeaders.ContentLength, length)
                response.header("Content-Range")?.let { call.response.header(HttpHeaders.ContentRange, it) }
                call.respondOutputStream {
                    body.byteStream().copyTo(this)
                }
            }
        }
    }

    private fun rewritePlaylist(body: String, playlistUrl: String, sessionId: String): String {
        val base = playlistUrl.substringBeforeLast('/') + "/"
        val uriRe = Regex("""URI=(["'])([^"']+)\1""")
        return body.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith("#") -> uriRe.replace(line) { m ->
                    val q = m.groupValues[1]
                    "URI=$q${proxied(sessionId, toAbsolute(m.groupValues[2], base))}$q"
                }
                else -> proxied(sessionId, toAbsolute(trimmed, base))
            }
        }
    }

    private fun proxied(sessionId: String, url: String): String =
        "http://127.0.0.1:$port/s/$sessionId/${encode(url)}"

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        fun encode(url: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(StandardCharsets.UTF_8))

        fun decode(value: String): String =
            try {
                String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                URLDecoder.decode(value, StandardCharsets.UTF_8)
            }

        fun toAbsolute(url: String, base: String): String {
            if (url.startsWith("http://") || url.startsWith("https://")) return url
            if (url.startsWith("//")) return "https:$url"
            return base + url.trimStart('/')
        }

        fun originOf(url: String): String? {
            return runCatching {
                val u = java.net.URI(url)
                if (u.scheme.isNullOrBlank() || u.host.isNullOrBlank()) null
                else "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}/"
            }.getOrNull()
        }
    }
}
