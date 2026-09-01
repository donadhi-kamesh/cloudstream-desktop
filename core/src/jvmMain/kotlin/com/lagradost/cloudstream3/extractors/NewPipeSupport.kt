package com.lagradost.cloudstream3.extractors

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

object NewPipeSupport {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var initialized = false

    fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val builder = okhttp3.Request.Builder().url(request.url())
                    request.headers().forEach { (name, values) ->
                        values.forEach { builder.addHeader(name, it) }
                    }
                    val method = request.httpMethod().uppercase()
                    val data = request.dataToSend()
                    when (method) {
                        "GET" -> builder.get()
                        "HEAD" -> builder.head()
                        "POST" -> builder.post(
                            (data ?: ByteArray(0)).toRequestBody(
                                "application/x-www-form-urlencoded".toMediaTypeOrNull()
                            )
                        )
                        else -> builder.method(
                            method,
                            data?.toRequestBody(null)
                        )
                    }
                    val resp = client.newCall(builder.build()).execute()
                    if (resp.code == 429) {
                        resp.close()
                        throw ReCaptchaException("HTTP 429", request.url())
                    }
                    val body = resp.body?.string()
                    val headers = LinkedHashMap<String, List<String>>()
                    for (name in resp.headers.names()) {
                        headers[name] = resp.headers.values(name)
                    }
                    return Response(
                        resp.code,
                        resp.message,
                        headers,
                        body,
                        resp.request.url.toString()
                    )
                }
            }, Localization("en", "US"))
            initialized = true
        }
    }
}
