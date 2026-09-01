package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * DDoS-Guard cookie harvest via the same embedded browser used for Cloudflare.
 * Matches the Android CloudStream interceptor contract (`DdosGuardKiller(alwaysBypass)`).
 */
class DdosGuardKiller(private val alwaysBypass: Boolean = false) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = DesktopCookieJar.getMap(request.url.toString())
        if (!alwaysBypass && cookies.isNotEmpty()) {
            return chain.proceed(CloudflareKiller.withCookies(request, cookies))
        }
        val response = chain.proceed(request)
        val challenged = alwaysBypass ||
            response.header("Server").orEmpty().contains("ddos-guard", true) ||
            runCatching { response.peekBody(2048).string() }.getOrDefault("").contains("ddos-guard", true)
        if (!challenged) return response
        response.close()
        Log.i("DdosGuardKiller", "Opening WebView for ${request.url}")
        runBlocking {
            WebViewResolver(Regex(".^"), additionalUrls = listOf(Regex(".")), useOkhttp = false)
                .resolveUsingWebView(request) { DesktopCookieJar.getMap(request.url.toString()).isNotEmpty() }
        }
        val harvested = DesktopCookieJar.getMap(request.url.toString())
        return retry.newCall(CloudflareKiller.withCookies(request, harvested)).execute()
    }

    companion object {
        private val retry = OkHttpClient()
    }
}
