package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 * */
actual class WebViewResolver actual constructor(
    private val interceptUrl: Regex,
    private val additionalUrls: List<Regex>,
    private val userAgent: String?,
    private val useOkhttp: Boolean,
    private val script: String?,
    private val scriptCallback: ((String) -> Unit)?,
    private val timeout: Long,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (interceptUrl.containsMatchIn(request.url.toString())) {
            return chain.proceed(request)
        }
        val resolved = runBlocking {
            resolveUsingWebView(request) { hit ->
                interceptUrl.containsMatchIn(hit.url.toString())
            }.first
        }
        return chain.proceed(resolved ?: request)
    }

    actual companion object {
        actual val DEFAULT_TIMEOUT = 60_000L
        actual var webViewUserAgent: String? = null
    }

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    actual suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        return try {
            Log.i("WebViewResolver", "Opening embedded browser for ${request.url}")
            val capture = DesktopChromium.capture(
                request = request,
                interceptUrl = interceptUrl,
                additionalUrls = additionalUrls,
                userAgent = userAgent,
                script = script,
                scriptCallback = scriptCallback,
                timeoutMs = timeout,
                requestCallBack = requestCallBack,
            )
            capture.userAgent?.let { webViewUserAgent = it }
            capture.matched to capture.extra
        } catch (t: Throwable) {
            logError(t)
            Log.e("WebViewResolver", "WebView failed: ${t.message}")
            null to emptyList()
        }
    }
}
