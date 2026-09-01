package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * CloudStream Cloudflare challenge solver. Same contract as the Android app:
 * extensions pass `interceptor = CloudflareKiller()` to `app.get`, and on a
 * 403/503 JS challenge we open Edge/Chrome (WebView equivalent) to harvest
 * `cf_clearance` cookies.
 */
class CloudflareKiller : Interceptor {
    val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()
    private val hostLocks = ConcurrentHashMap<String, Any>()

    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()
        val host = runCatching { URI(url).host }.getOrNull()
        val cookies = (host?.let { savedCookies[it] } ?: emptyMap()) + DesktopCookieJar.getMap(url)
        return headersOf(userAgentHeaders, cookies)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        // Clearance cookies + browser UA are bound together by Cloudflare: always send
        // whatever previous webview passes captured, so a solved challenge never loops.
        val merged = (savedCookies[host] ?: emptyMap()) + DesktopCookieJar.getMap(request.url.toString())
        val first = chain.proceed(withCookies(request, merged))
        if (!isCloudflareChallenge(first)) return first
        first.close()
        synchronized(hostLocks.getOrPut(host) { Any() }) {
            val cached = (savedCookies[host] ?: emptyMap()) + DesktopCookieJar.getMap(request.url.toString())
            if (cached.keys.any { it.equals("cf_clearance", true) }) {
                return retryClient.newCall(withCookies(request, cached)).execute()
            }
            if (recentlyFailed(host)) {
                // A bypass just ran for this host and produced no clearance. Opening the
                // browser again right away only loops the challenge; retry with what we have.
                Log.w(TAG, "Cloudflare at ${request.url} still challenging after a fresh bypass; not reopening the browser")
                return retryClient.newCall(withCookies(request, cached)).execute()
            }
            Log.i(TAG, "Cloudflare challenge at ${request.url}, opening WebView")
            val bypassed = runBlocking { bypass(request) }
            markBypass(host)
            if (bypassed != null) return bypassed
            Log.w(TAG, "Failed Cloudflare at ${request.url} — no clearance was captured")
            return retryClient.newCall(withCookies(request, cached)).execute()
        }
    }

    /**
     * True when a bypass for this host finished without producing cf_clearance within
     * the last [REOPEN_COOLDOWN_MS] — protects against extension retries reopening the
     * challenge window in a loop.
     */
    private fun recentlyFailed(host: String): Boolean {
        val last = lastBypassAt[host] ?: return false
        val failed = !bypassSucceeded.getOrDefault(host, false)
        return failed && System.currentTimeMillis() - last < REOPEN_COOLDOWN_MS
    }

    private fun markBypass(host: String) {
        lastBypassAt[host] = System.currentTimeMillis()
        bypassSucceeded[host] = (savedCookies[host]?.keys?.any { it.equals("cf_clearance", true) } == true)
    }

    private suspend fun bypass(request: Request): Response? {
        val resolver = WebViewResolver(
            interceptUrl = Regex(".^"),
            additionalUrls = listOf(Regex(".")),
            userAgent = null,
            useOkhttp = false,
            timeout = 180_000L,
        )
        resolver.resolveUsingWebView(request) {
            // Only a real clearance token ends the wait. Accepting any cookie (Cloudflare
            // sets __cf_bm on the challenge page itself) declared success before the
            // challenge was solved, so the retry was challenged again — and each retry
            // reopened the browser, which is the loop users saw.
            if (DesktopCookieJar.hasClearance(request.url.toString())) {
                val map = DesktopCookieJar.getMap(request.url.toString())
                savedCookies[request.url.host] = map
                Log.i(TAG, "clearance captured for ${request.url.host}: ${map.keys}")
                true
            } else {
                false
            }
        }
        val cookies = DesktopCookieJar.getMap(request.url.toString())
        if (cookies.isEmpty()) return null
        if (!DesktopCookieJar.hasClearance(request.url.toString())) {
            Log.w(TAG, "no cf_clearance for ${request.url.host}; retrying with ${cookies.keys}")
        }
        savedCookies[request.url.host] = cookies
        return retryClient.newCall(withCookies(request, cookies)).execute()
    }

    companion object {
        private const val TAG = "CloudflareKiller"
        private const val REOPEN_COOLDOWN_MS = 60_000L
        private val ERROR_CODES = setOf(403, 503)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare-nginx", "cloudflare")
        private val retryClient = OkHttpClient()
        private val lastBypassAt = ConcurrentHashMap<String, Long>()
        private val bypassSucceeded = ConcurrentHashMap<String, Boolean>()

        fun isCloudflareChallenge(response: Response): Boolean {
            val server = response.header("Server").orEmpty().lowercase()
            if (response.code !in ERROR_CODES && response.header("cf-mitigated") == null) return false
            if (server in CLOUDFLARE_SERVERS || response.header("cf-mitigated") != null) return true
            val peek = runCatching { response.peekBody(4096).string() }.getOrDefault("")
            return peek.contains("cf-browser-verification", true) ||
                peek.contains("Just a moment", true) ||
                peek.contains("challenge-platform", true)
        }

        fun withCookies(request: Request, cookies: Map<String, String>): Request {
            val ua = WebViewResolver.webViewUserAgent
            val builder = request.newBuilder()
            // Clearance cookies are bound to the exact User-Agent that solved the
            // challenge, so they must travel together. Without cookies the extension's
            // own User-Agent (mobile endpoints etc.) is left untouched.
            if (!ua.isNullOrBlank() && cookies.isNotEmpty()) {
                builder.header("User-Agent", ua)
            }
            if (cookies.isNotEmpty()) {
                builder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            return builder.build()
        }

        fun headersOf(extra: Map<String, String>, cookies: Map<String, String>): Headers {
            val b = Headers.Builder()
            extra.forEach { (k, v) -> runCatching { b[k] = v } }
            if (cookies.isNotEmpty()) {
                b["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
            return b.build()
        }
    }
}
