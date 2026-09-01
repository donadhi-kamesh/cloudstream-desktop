package com.lagradost.cloudstream3.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cookie jar shared by WebViewResolver, CloudflareKiller, android.webkit.CookieManager,
 * and OkHttp. Matches CloudStream's WebView CookieManager + request cookie behavior.
 */
object DesktopCookieJar : CookieJar {
    private val byHost = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
    private val persistFile: File by lazy {
        // Tests point this at a temp dir so they never touch the user's real cookies.
        System.getProperty("csdesktop.cookieStore")?.takeIf { it.isNotBlank() }?.let {
            return@lazy File(it).also { f -> f.parentFile?.mkdirs() }
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val root = when {
            os.contains("win") -> {
                val appdata = System.getenv("APPDATA")
                    ?: (System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming")
                File(appdata, "cs-desktop")
            }
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/cs-desktop")
            else -> File(System.getProperty("user.home"), ".local/share/cs-desktop")
        }
        File(root, "cookies.properties").also { it.parentFile?.mkdirs() }
    }

    init {
        load()
    }

    @Synchronized
    fun put(url: String, cookieHeader: String) {
        val host = hostOf(url) ?: return
        val map = byHost.getOrPut(host) { ConcurrentHashMap() }
        var changed = false
        cookieHeader.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=').trim()
            if (name.isNotEmpty() && name.lowercase() !in IGNORE && map.put(name, value) != value) {
                changed = true
            }
        }
        // The capture loop harvests once a second; only touch disk when something moved.
        if (changed) save()
    }

    @Synchronized
    fun putAll(url: String, cookies: Map<String, String>) {
        val host = hostOf(url) ?: return
        val map = byHost.getOrPut(host) { ConcurrentHashMap() }
        var changed = false
        cookies.forEach { (k, v) ->
            if (k.isNotBlank() && map.put(k, v) != v) changed = true
        }
        if (changed) save()
    }

    fun getMap(url: String): Map<String, String> {
        val host = hostOf(url) ?: return emptyMap()
        val merged = LinkedHashMap<String, String>()
        byHost.forEach { (stored, map) ->
            if (hostMatches(host, stored)) merged.putAll(map)
        }
        return merged
    }

    fun getCookieHeader(url: String): String? {
        val map = getMap(url)
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun hasClearance(url: String): Boolean =
        getMap(url).keys.any { it.equals("cf_clearance", true) }

    fun clear() {
        byHost.clear()
        save()
    }

    fun flush() = save()

    @Synchronized
    private fun save() {
        runCatching {
            val p = Properties()
            byHost.forEach { (host, map) ->
                map.forEach { (name, value) ->
                    p.setProperty("$host\t$name", value)
                }
            }
            FileOutputStream(persistFile).use { p.store(it, "cs-desktop cookies") }
        }
    }

    @Synchronized
    private fun load() {
        val f = persistFile
        if (!f.isFile) return
        runCatching {
            val p = Properties()
            FileInputStream(f).use { p.load(it) }
            p.stringPropertyNames().forEach { key ->
                val host = key.substringBefore('\t')
                val name = key.substringAfter('\t', "")
                if (host.isNotBlank() && name.isNotBlank()) {
                    byHost.getOrPut(host) { ConcurrentHashMap() }[name] = p.getProperty(key).orEmpty()
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        putAll(url.toString(), cookies.associate { it.name to it.value })
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val merged = getMap(url.toString())
        if (merged.isEmpty()) return emptyList()
        return merged.mapNotNull { (name, value) ->
            runCatching { Cookie.Builder().domain(host).name(name).value(value).build() }.getOrNull()
        }
    }

    private fun hostOf(url: String): String? {
        val http = url.toHttpUrlOrNull()
        if (http != null) return http.host
        return runCatching { java.net.URI(url).host }.getOrNull()
    }

    private fun hostMatches(requestHost: String, cookieHost: String): Boolean {
        val a = requestHost.trimStart('.').lowercase()
        val b = cookieHost.trimStart('.').lowercase()
        return a == b || a.endsWith(".$b") || b.endsWith(".$a")
    }

    private val IGNORE = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "priority")
}
