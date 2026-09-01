package dev.csdesktop.extloader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Accepts the same repository inputs CloudStream does:
 * full repo.json URL, GitHub blob URLs, raw plugins.json, and shortcodes
 * (`cloudstreamrepo://`, `https://cs.repo/?`, cutt.ly / py.md codes).
 */
class RepoClient(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) {
    fun fetchRepository(rawInput: String): ParsedRepo {
        val url = resolveInput(rawInput)
            ?: throw IllegalArgumentException("Could not resolve repository URL from: $rawInput")
        val body = getText(url)
        val element = json.parseToJsonElement(body)
        if (element is JsonArray) {
            val plugins = json.decodeFromJsonElement<List<SitePlugin>>(element).map { it.absolutized(url) }
            return ParsedRepo(
                manifest = RepositoryManifest(
                    name = guessName(url),
                    pluginLists = listOf(url),
                ),
                plugins = plugins,
                resolvedUrl = url,
            )
        }
        if (element is JsonObject && element.containsKey("pluginLists")) {
            val manifest = json.decodeFromJsonElement<RepositoryManifest>(element)
            val plugins = manifest.pluginLists.flatMap { listUrl ->
                parsePluginList(normalizeGitUrl(listUrl))
            }
            return ParsedRepo(manifest, plugins, url)
        }
        if (element is JsonObject && (element.containsKey("url") || element.containsKey("internalName"))) {
            val plugin = json.decodeFromJsonElement<SitePlugin>(element).absolutized(url)
            return ParsedRepo(
                manifest = RepositoryManifest(name = plugin.name.ifBlank { guessName(url) }, pluginLists = listOf(url)),
                plugins = listOf(plugin),
                resolvedUrl = url,
            )
        }
        throw IllegalArgumentException("Not a CloudStream repo.json or plugins.json: $url")
    }

    fun parsePluginList(url: String): List<SitePlugin> {
        val body = getText(url)
        val element = json.parseToJsonElement(body)
        val plugins: List<SitePlugin> = when (element) {
            is JsonArray -> json.decodeFromJsonElement(element)
            is JsonObject -> listOf(json.decodeFromJsonElement(element))
            else -> emptyList()
        }
        return plugins.map { it.absolutized(url) }
    }

    fun absolutize(baseUrl: String, url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.contains(Regex("^https?://", RegexOption.IGNORE_CASE))) return normalizeGitUrl(trimmed)
        return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun SitePlugin.absolutized(baseUrl: String): SitePlugin = copy(
        url = absolutize(baseUrl, url),
        jarUrl = jarUrl?.let { absolutize(baseUrl, it) },
        iconUrl = iconUrl?.let { absolutize(baseUrl, it) },
    )

    fun resolveInput(raw: String): String? {
        val fixed = raw.trim()
        if (fixed.isEmpty()) return null
        if (fixed.contains(Regex("^https?://"))) {
            return normalizeGitUrl(fixed)
        }
        if (fixed.contains(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"))) {
            val stripped = fixed.replace(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"), "")
            val asHttp = if (stripped.contains(Regex("^https?://"))) stripped else "https://$stripped"
            return normalizeGitUrl(asHttp)
        }
        if (fixed.matches(Regex("^[a-zA-Z0-9!_-]+$"))) {
            return resolveShortcode(fixed)
        }
        return null
    }

    fun normalizeGitUrl(url: String): String {
        var u = url.trim()
        // GitHub blob → raw
        u = u.replace(
            Regex("^https://github.com/([^/]+)/([^/]+)/blob/"),
            "https://raw.githubusercontent.com/$1/$2/",
        )
        u = u.replace(
            Regex("^https://www.github.com/([^/]+)/([^/]+)/blob/"),
            "https://raw.githubusercontent.com/$1/$2/",
        )
        return u
    }

    private fun resolveShortcode(code: String): String? {
        val target = if (code.startsWith("!")) {
            "https://py.md/${code.removePrefix("!")}"
        } else {
            "https://cutt.ly/$code"
        }
        val request = Request.Builder().url(target).head().header("User-Agent", USER_AGENT).build()
        http.newBuilder().followRedirects(false).followSslRedirects(false).build()
            .newCall(request).execute().use { resp ->
                val location = resp.header("Location") ?: return null
                if (location.contains("/404")) return null
                val host = try { URI(location).host } catch (_: Exception) { "" }
                if (host.equals("cutt.ly", true) || host.equals("py.md", true)) return null
                return normalizeGitUrl(location)
            }
    }

    fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder().url(normalizeGitUrl(url)).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            return resp.body?.string().orEmpty()
        }
    }

    private fun guessName(url: String): String {
        return try {
            val path = URI(url).path.trim('/')
            path.split('/').getOrNull(1) ?: path.substringAfterLast('/').ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }

    data class ParsedRepo(
        val manifest: RepositoryManifest,
        val plugins: List<SitePlugin>,
        val resolvedUrl: String,
    )

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
