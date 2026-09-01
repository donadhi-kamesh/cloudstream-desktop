package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives a single persistent Edge/Chrome process over CDP so extractors can
 * intercept media URLs and solve Cloudflare / captcha in an in-app window.
 */
fun interface BrowserDoneListener {
    fun onDone(dontShowAgain: Boolean)
}

interface ChromiumWindowHost {
    fun attach(pid: Long)
    fun setVisible(visible: Boolean, title: String = "CloudStream")
    fun setStatus(status: String) {}
    fun setActionBar(doneLabel: String, checkboxLabel: String?, onDone: BrowserDoneListener) {}
    fun clearActionBar() {}
}

object DesktopChromium {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Any()
    private const val TAG = "DesktopChromium"
    @Volatile private var shared: BrowserSession? = null
    @JvmField var windowHost: ChromiumWindowHost? = null

    /** Hosts with a capture currently driving the page — others piggyback instead of re-navigating. */
    private val activeCaptureHosts = ConcurrentHashMap<String, Long>()
    /** Set when the user presses Done in the browser bar: waiting captures harvest and finish. */
    @Volatile private var userConfirmed: Boolean = false
    /** Set when the user closes the browser bar: waiting captures give up without reopening. */
    @Volatile private var userCancelled: Boolean = false

    data class CookieRow(val domain: String, val name: String, val value: String)

    fun currentUrl(): String? = runCatching { shared?.evaluate("location.href") }.getOrNull()

    data class Capture(
        val matched: Request?,
        val extra: List<Request>,
        val userAgent: String?,
    )

    class PageSession internal constructor(
        private val browser: BrowserSession,
        private val originalUrl: String,
    ) {
        fun isOpen(): Boolean = !browser.closed

        fun harvestCookies(pageUrl: String) {
            harvest(browser, pageUrl.ifBlank { originalUrl })
        }

        fun currentUrl(): String = browser.evaluate("location.href") ?: originalUrl

        fun title(): String = browser.evaluate("document.title") ?: ""

        fun evaluate(script: String): String? = browser.evaluate(script)

        fun click(x: Float, y: Float) = browser.click(x, y)

        fun drainRequests(): List<Request> = browser.drainRequests()

        fun close() {
            harvestCookies(originalUrl)
            // Keep the captcha window open until the user dismisses it.
        }

        fun injectJsInterfaces(ifaces: Map<String, Any?>) {
            browser.injectJsInterfaces(ifaces)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            shared?.destroyProcess()
            shared = null
        }
    }

    /**
     * Called by the browser bar's Done button: harvest cookies immediately and let
     * every waiting capture finish with what it has, so the flow never hangs on the
     * poll timeout after the user has solved a challenge.
     */
    fun confirmSolved() {
        userConfirmed = true
        shared?.let { browser ->
            harvest(browser, browser.evaluate("location.href").orEmpty())
        }
    }

    /** Called when the user dismisses the browser bar without solving. */
    fun cancelWaiting() {
        userCancelled = true
    }

    private fun ensureBrowser(userAgent: String?): BrowserSession = synchronized(lock) {
        val cur = shared
        if (cur != null && !cur.closed) return cur
        userConfirmed = false
        userCancelled = false
        val started = BrowserSession.start(userAgent)
        shared = started
        windowHost?.attach(started.pid)
        started
    }

    private fun filterBrowserHeaders(headers: Map<String, String>): Map<String, String> {
        // Never force identity headers onto the browser: a mismatched User-Agent or
        // stale Cookie makes Cloudflare re-challenge the browser forever.
        val blocked = setOf("user-agent", "cookie", "host", "content-length", "connection", "accept-encoding")
        return headers.filterKeys { key -> blocked.none { it.equals(key, true) } }
    }

    fun openPage(url: String, userAgent: String?): PageSession {
        val browser = ensureBrowser(userAgent)
        browser.enableNetwork()
        if (!userAgent.isNullOrBlank()) browser.setUserAgent(userAgent)
        windowHost?.setVisible(true, "CloudStream")
        val current = browser.evaluate("location.href").orEmpty()
        val alreadyThere = current.equals(url, true) ||
            (pageIsChallenge(browser) && hostOf(current) == hostOf(url))
        if (!alreadyThere) {
            browser.navigate(url, emptyMap())
            // Never block the caller on navigation: watch on a background thread and
            // retry once so the window can't sit on a white page forever.
            Thread {
                watchNavigation(browser, url)
            }.apply { isDaemon = true; start() }
        } else {
            Log.i(TAG, "reusing open challenge page $current")
        }
        return PageSession(browser, url)
    }

    private fun watchNavigation(browser: BrowserSession, url: String) {
        repeat(16) {
            Thread.sleep(500)
            val href = browser.evaluate("location.href").orEmpty()
            if (href.equals(url, true) || (href.isNotEmpty() && href != "about:blank")) return
        }
        // Only a page that never left about:blank is genuinely stuck. Re-navigating a
        // challenge page restarts the check the user is part-way through solving.
        if (pageIsChallenge(browser)) {
            Log.i(TAG, "navigation to $url landed on a challenge; leaving it alone")
            return
        }
        Log.w(TAG, "navigation to $url appears stuck; retrying once")
        browser.navigate(url, emptyMap())
        repeat(16) {
            Thread.sleep(500)
            val href = browser.evaluate("location.href").orEmpty()
            if (href.isNotEmpty() && href != "about:blank") return
        }
        Log.e(TAG, "navigation to $url did not take effect")
        windowHost?.setStatus("Could not load the page — see Logcat")
    }

    fun capture(
        request: Request,
        interceptUrl: Regex,
        additionalUrls: List<Regex>,
        userAgent: String?,
        script: String?,
        scriptCallback: ((String) -> Unit)?,
        timeoutMs: Long,
        requestCallBack: (Request) -> Boolean,
    ): Capture = captureInternal(request, interceptUrl, additionalUrls, userAgent, script, scriptCallback, timeoutMs, requestCallBack)

    private fun captureInternal(
        request: Request,
        interceptUrl: Regex,
        additionalUrls: List<Regex>,
        userAgent: String?,
        script: String?,
        scriptCallback: ((String) -> Unit)?,
        timeoutMs: Long,
        requestCallBack: (Request) -> Boolean,
    ): Capture {
        val browser = ensureBrowser(userAgent)
        val target = request.url.toString()
        val host = request.url.host
        // CloudflareKiller passes a regex that matches nothing ("^.") and only waits
        // for clearance cookies — mark it so the UI can show the right status.
        val challengeFlow = interceptUrl.pattern == ".^"
        val headers = request.headers.toMap()
        userConfirmed = false
        userCancelled = false

        try {
            browser.enableNetwork()
            if (!userAgent.isNullOrBlank()) browser.setUserAgent(userAgent)
            windowHost?.setVisible(true, "CloudStream")
            windowHost?.setStatus(
                if (challengeFlow) "Cloudflare check: solve it in the browser window"
                else "Watching page for media…"
            )
            val seenUrls = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
            val current = browser.evaluate("location.href").orEmpty()
            val alreadyThere = current.equals(target, true) ||
                (pageIsChallenge(browser) && hostOf(current) == hostOf(target))
            // Only one capture per host drives the page; concurrent captures for the
            // same host piggyback on the shared request stream instead of re-navigating
            // (which used to re-trigger the challenge in a loop).
            val driver = activeCaptureHosts.putIfAbsent(host, System.currentTimeMillis()) == null
            if (!alreadyThere && driver) {
                // On a challenge flow send no extra headers at all: the challenge is
                // scored partly on header set and order, so anything the extension added
                // for its own API call makes the check harder to pass.
                val navHeaders = if (challengeFlow) emptyMap() else filterBrowserHeaders(headers)
                browser.navigate(target, navHeaders)
                if (navHeaders.isNotEmpty()) {
                    // Extra headers were only needed for this navigation; drop them so they
                    // never leak onto later pages of this browser session.
                    Thread {
                        Thread.sleep(2_500)
                        runCatching { browser.clearExtraHeaders() }
                    }.apply { isDaemon = true; start() }
                }
            } else if (!driver) {
                Log.i(TAG, "capture piggybacking on active capture for $host")
            } else {
                Log.i(TAG, "capture reusing open page $current")
            }

            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(5_000L)
            var matched: Request? = null
            val extra = LinkedHashMap<String, Request>()
            var challengeShown = false
            while (System.currentTimeMillis() < deadline && !browser.closed) {
                for (req in browser.snapshotRequests()) {
                    val url = req.url.toString()
                    if (!seenUrls.add(url)) continue
                    if (interceptUrl.containsMatchIn(url) && matched == null) matched = req
                    if (additionalUrls.any { it.containsMatchIn(url) }) extra.putIfAbsent(url, req)
                }
                harvest(browser, target)
                val probe = matched ?: extra.values.firstOrNull() ?: request
                if (requestCallBack(probe)) {
                    return Capture(matched ?: extra.values.firstOrNull(), extra.values.toList(), browser.userAgent)
                }
                if (challengeFlow && DesktopCookieJar.hasClearance(target)) {
                    // Turnstile often leaves its widget in the DOM after passing, so waiting
                    // for the challenge UI to vanish kept the window open and blocked playback.
                    Log.i(TAG, "cf_clearance captured for $host; closing the check window")
                    harvest(browser, target)
                    return Capture(matched, extra.values.toList(), browser.userAgent)
                }
                if (userConfirmed) {
                    Log.i(TAG, "user confirmed the check is solved for $host")
                    harvest(browser, target)
                    return Capture(matched, extra.values.toList(), browser.userAgent)
                }
                if (userCancelled) {
                    Log.w(TAG, "capture cancelled by user for $target")
                    return Capture(matched, extra.values.toList(), browser.userAgent)
                }
                val challenged = pageIsChallenge(browser)
                if (challenged) {
                    if (!challengeShown) {
                        challengeShown = true
                        windowHost?.setStatus("Cloudflare check: solve it in the browser window")
                        windowHost?.setActionBar("Done", null) { confirmSolved() }
                    }
                    // Never force a reload here. Turnstile refreshes itself while it
                    // scores the session, and reloading throws that progress away — which
                    // is what produced the endless challenge loop.
                } else if (challengeShown && challengeFlow) {
                    Log.i(TAG, "challenge no longer showing for $host")
                    windowHost?.setStatus("Check passed, harvesting…")
                    Thread.sleep(1_500)
                    harvest(browser, target)
                    return Capture(matched, extra.values.toList(), browser.userAgent)
                }
                if (!script.isNullOrBlank()) {
                    browser.evaluate(script)?.let { scriptCallback?.invoke(it) }
                }
                Thread.sleep(1000)
            }
            harvest(browser, target)
            return Capture(matched, extra.values.toList(), browser.userAgent)
        } finally {
            harvest(browser, request.url.toString())
            activeCaptureHosts.remove(host)
            if (activeCaptureHosts.isEmpty()) {
                windowHost?.setStatus("")
                windowHost?.clearActionBar()
                windowHost?.setVisible(false)
                userConfirmed = false
                userCancelled = false
            }
        }
    }

    /**
     * Copies the browser's cookies into the shared jar, each under the domain the browser
     * actually scoped it to. Flattening the whole store onto the current page's host (as
     * this used to do) planted another site's `cf_clearance` on this one, so the next
     * request presented a clearance token Cloudflare had never issued for that host and
     * got challenged again — a loop that no amount of solving could break.
     */
    private fun harvest(browser: BrowserSession, pageUrl: String) {
        for (cookie in browser.cookieRows()) {
            val host = cookie.domain.trimStart('.')
            if (host.isNotEmpty()) DesktopCookieJar.put("https://$host/", "${cookie.name}=${cookie.value}")
        }
        val host = hostOf(pageUrl)
        if (host != null) {
            // document.cookie is scoped to the page by definition, so it is safe to
            // attribute to this host and picks up anything set after the CDP snapshot.
            val docCookie = browser.evaluate("document.cookie").orEmpty()
            if (docCookie.isNotBlank()) {
                DesktopCookieJar.put("https://$host/", docCookie)
            }
        }
        browser.userAgent?.let { WebViewResolver.webViewUserAgent = it }
    }

    fun looksLikeChallenge(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("challenges.cloudflare") ||
            u.contains("/cdn-cgi/challenge") ||
            u.contains("cf-challenge") ||
            u.contains("turnstile")
    }

    private const val CHALLENGE_PROBE =
        "location.href + '\\u0000' + document.title + '\\u0000' + " +
            "(document.querySelector('#challenge-stage, #challenge-form, #cf-challenge-running, " +
            ".cf-turnstile, iframe[src*=\"challenges.cloudflare\"]') ? '1' : '0')"

    /** One round trip instead of three: the polling loop runs once a second. */
    private fun pageIsChallenge(browser: BrowserSession): Boolean {
        val probe = browser.evaluate(CHALLENGE_PROBE) ?: return false
        val parts = probe.split('\u0000')
        val href = parts.getOrNull(0).orEmpty()
        val title = parts.getOrNull(1).orEmpty().lowercase()
        val hasChallengeEl = parts.getOrNull(2) == "1"
        return looksLikeChallenge(href) ||
            title.contains("just a moment") ||
            title.contains("verify you are human") ||
            title.contains("attention required") ||
            title.contains("cloudflare") ||
            hasChallengeEl
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    internal class BrowserSession(
        private val process: Process,
        private val ws: WebSocket,
        private val listener: CdpListener,
        val userAgent: String?,
    ) {
        val pid: Long = process.pid()
        val closed: Boolean get() = !process.isAlive
        private var jsIfaces: Map<String, Any?> = emptyMap()
        @Volatile private var runtimeEventsEnabled = false
        @Volatile private var isolatedContextId: Int? = null

        init {
            // The isolated world dies with its document.
            listener.onNavigated = { isolatedContextId = null }
        }

        fun enableNetwork() {
            for (domain in PAGE_DOMAINS) listener.send(ws, domain)
        }

        /**
         * Turns on Runtime events, needed only for Runtime.bindingCalled when a plugin
         * asks for JS interfaces. Never called on a Cloudflare path.
         */
        private fun ensureRuntimeEvents() {
            if (runtimeEventsEnabled) return
            runtimeEventsEnabled = true
            Log.w(TAG, "enabling Runtime events for JS interfaces — page can detect the debugger")
            listener.send(ws, "Runtime.enable")
        }

        fun injectJsInterfaces(ifaces: Map<String, Any?>) {
            jsIfaces = ifaces
            if (ifaces.isEmpty()) return
            ensureRuntimeEvents()
            listener.send(ws, "Runtime.addBinding", buildJsonObject { put("name", "csdesktopJs") })
            listener.onBinding = { payload -> dispatchJs(payload) }
            val script = buildString {
                append("(function(){")
                for ((name, obj) in ifaces) {
                    if (name.isNullOrBlank() || obj == null) continue
                    append("window[").append(jsonStr(name)).append("]=window[").append(jsonStr(name)).append("]||{};")
                    obj.javaClass.methods.filter {
                        java.lang.reflect.Modifier.isPublic(it.modifiers) && it.declaringClass != Any::class.java
                    }.forEach { m ->
                        append("window[").append(jsonStr(name)).append("][").append(jsonStr(m.name)).append("]=function(){")
                        append("try{window.csdesktopJs(JSON.stringify({iface:").append(jsonStr(name))
                        append(",method:").append(jsonStr(m.name)).append(",args:Array.from(arguments)}));}catch(e){}")
                        append("};")
                    }
                }
                append("})()")
            }
            evaluate(script)
            listener.send(
                ws,
                "Page.addScriptToEvaluateOnNewDocument",
                buildJsonObject { put("source", script) },
            )
        }

        private fun jsonStr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun dispatchJs(payload: String) {
            val el = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return
            val iface = el["iface"]?.jsonPrimitive?.contentOrNull ?: return
            val method = el["method"]?.jsonPrimitive?.contentOrNull ?: return
            val target = jsIfaces[iface] ?: return
            val argsEl = el["args"]
            val args = if (argsEl is kotlinx.serialization.json.JsonArray) {
                argsEl.map { it.jsonPrimitive.contentOrNull ?: it.toString() }
            } else emptyList()
            val match = target.javaClass.methods.firstOrNull { it.name == method } ?: return
            runCatching {
                val params = match.parameterTypes
                val converted = Array<Any?>(params.size) { i ->
                    val raw = args.getOrNull(i).orEmpty()
                    val p = params[i]
                    when {
                        p == java.lang.Boolean.TYPE || p == java.lang.Boolean::class.java ->
                            raw.equals("true", true) || raw == "1"
                        p == java.lang.Integer.TYPE || p == java.lang.Integer::class.java ->
                            raw.toIntOrNull() ?: 0
                        p == java.lang.Long.TYPE || p == java.lang.Long::class.java ->
                            raw.toLongOrNull() ?: 0L
                        else -> raw
                    }
                }
                match.invoke(target, *converted)
            }.onFailure { Log.w(TAG, "js interface $iface.$method: ${it.message}") }
        }

        /**
         * Intentionally does not override the browser's User-Agent. An override changes
         * the header and navigator.userAgent but leaves the Sec-CH-UA client hints
         * reporting the real build, and Cloudflare treats that contradiction as a bot —
         * which is why the Android app passes `userAgent = null` for Cloudflare too. The
         * real UA is reported back instead, so clearance cookies travel with the UA that
         * earned them.
         */
        fun setUserAgent(ua: String) {
            if (userAgent != null && !ua.equals(userAgent, true)) {
                Log.i(TAG, "ignoring requested user-agent override; using the browser's own")
            }
        }

        fun navigate(url: String, headers: Map<String, String>) {
            val filtered = headers.filterKeys { k ->
                !(k.equals("user-agent", true) || k.equals("cookie", true) || k.equals("host", true) ||
                    k.equals("content-length", true) || k.equals("connection", true) || k.equals("accept-encoding", true))
            }
            if (filtered.isNotEmpty()) {
                val extra = buildJsonObject {
                    filtered.forEach { (k, v) -> put(k, v) }
                }
                listener.send(ws, "Network.setExtraHTTPHeaders", buildJsonObject { put("headers", extra) })
            }
            listener.send(ws, "Page.navigate", buildJsonObject { put("url", url) })
        }

        fun clearExtraHeaders() {
            listener.send(ws, "Network.setExtraHTTPHeaders", buildJsonObject { put("headers", buildJsonObject {}) })
        }

        fun reload() {
            listener.send(ws, "Page.reload", buildJsonObject { put("ignoreCache", true) })
        }

        fun drainRequests(): List<Request> = listener.takeRequests()

        /** Copy of recent captured requests without consuming them — safe for concurrent captures. */
        fun snapshotRequests(): List<Request> = listener.snapshotRequests()

        /**
         * Evaluates in a private isolated world that shares the DOM but not the page's
         * globals, so probing for a challenge leaves no trace the page can observe. Falls
         * back to the main world only if no world could be created.
         */
        fun evaluate(script: String): String? {
            var context = isolatedContextId ?: createIsolatedWorld()
            var result = evaluateIn(script, context)
            if (result == null && context != null) {
                // The document changed under us; rebuild the world once and retry.
                context = createIsolatedWorld()
                result = evaluateIn(script, context)
            }
            if (result == null) result = evaluateIn(script, null)
            return result?.jsonObject?.get("result")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
        }

        private fun evaluateIn(script: String, contextId: Int?): JsonElement? {
            val result = listener.send(
                ws,
                "Runtime.evaluate",
                buildJsonObject {
                    put("expression", script)
                    put("returnByValue", true)
                    put("awaitPromise", true)
                    if (contextId != null) put("contextId", contextId)
                },
            ) ?: return null
            // A thrown expression is not a dead context: report it rather than retrying.
            if (result.jsonObject["exceptionDetails"] != null) return null
            return result
        }

        private fun createIsolatedWorld(): Int? {
            val frameId = listener.send(ws, "Page.getFrameTree")
                ?.jsonObject?.get("frameTree")?.jsonObject
                ?.get("frame")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                ?: return null
            val id = listener.send(
                ws,
                "Page.createIsolatedWorld",
                buildJsonObject {
                    put("frameId", frameId)
                    put("worldName", "csdesktop")
                    put("grantUniveralAccess", false)
                },
            )?.jsonObject?.get("executionContextId")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            isolatedContextId = id
            return id
        }

        fun cookies(): Map<String, String> = cookieRows().associate { it.name to it.value }

        fun cookieRows(): List<CookieRow> {
            // Storage.getCookies is the supported CDP command on current Edge/Chrome;
            // Network.getAllCookies is deprecated and Network.getCookies is page-scoped.
            val result = listener.send(ws, "Storage.getCookies")
                ?: listener.send(ws, "Network.getAllCookies")
                ?: return emptyList()
            val list = result.jsonObject["cookies"] ?: return emptyList()
            val out = ArrayList<CookieRow>()
            if (list is kotlinx.serialization.json.JsonArray) {
                for (item in list) {
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: continue
                    val domain = obj["domain"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    out += CookieRow(domain, name, value)
                }
            }
            return out
        }

        fun click(x: Float, y: Float) {
            val px = x.toInt()
            val py = y.toInt()
            val paramsPressed = buildJsonObject {
                put("type", "mousePressed")
                put("x", px)
                put("y", py)
                put("button", "left")
                put("clickCount", 1)
            }
            val paramsReleased = buildJsonObject {
                put("type", "mouseReleased")
                put("x", px)
                put("y", py)
                put("button", "left")
                put("clickCount", 1)
            }
            listener.send(ws, "Input.dispatchMouseEvent", paramsPressed)
            listener.send(ws, "Input.dispatchMouseEvent", paramsReleased)
        }

        fun destroyProcess() {
            runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "done") }
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }

        fun close() {
            harvestCookiesSafe()
        }

        private fun harvestCookiesSafe() {
            runCatching {
                for (cookie in cookieRows()) {
                    val host = cookie.domain.trimStart('.')
                    if (host.isNotEmpty()) DesktopCookieJar.put("https://$host/", "${cookie.name}=${cookie.value}")
                }
            }
        }

        companion object {
            /**
             * Command line for the challenge-solving browser.
             *
             * Cloudflare Turnstile scores the client and re-arms the challenge instead of
             * failing outright, so anything that marks the browser as automated shows up
             * as an endless reload rather than an error. Deliberately absent:
             * `--enable-automation` (it is what actually sets `navigator.webdriver`),
             * `--headless`, `--disable-gpu` (falls back to a software WebGL renderer,
             * a strong tell), `--no-sandbox` and `--disable-extensions`. What is present
             * is a real visible window on a persistent profile, so a solved challenge
             * carries over between runs.
             */
            fun launchArgs(exePath: String, port: Int, profileDir: String): List<String> = listOf(
                exePath,
                "--remote-debugging-port=$port",
                "--remote-allow-origins=*",
                "--user-data-dir=$profileDir",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-blink-features=AutomationControlled",
                "--disable-features=Translate,MediaRouter",
                "--lang=en-US,en",
                "--window-size=1100,800",
                "--app=about:blank",
            )

            /**
             * CDP domains enabled while a page is being watched. Runtime is absent on
             * purpose: with Runtime events on, V8 builds a preview object for every
             * console call, and Cloudflare's challenge script detects that by handing
             * console.debug an object whose getters record being read.
             */
            val PAGE_DOMAINS: List<String> = listOf("Network.enable", "Page.enable")

            /** Commands sent once, right after attaching to the browser. */
            val HANDSHAKE: List<String> = listOf("Page.enable", "Emulation.setAutomationOverride")

            fun start(userAgent: String?): BrowserSession {
                val exe = findBrowser()
                    ?: throw IllegalStateException(
                        "Microsoft Edge (or Chrome) was not found. CloudStream Desktop uses it as WebView " +
                            "to solve Cloudflare / captcha challenges, the same way Android CloudStream uses WebView."
                    )
                val port = freePort()
                val profile = File(dataDir(), "chromium-profile")
                profile.mkdirs()
                val pb = ProcessBuilder(launchArgs(exe.absolutePath, port, profile.absolutePath))
                pb.redirectErrorStream(true)
                val proc = pb.start()
                Thread({ proc.inputStream.bufferedReader().forEachLine { } }, "cs-chromium-log").apply { isDaemon = true; start() }
                val wsUrl = waitForWs(port)
                val listener = CdpListener()
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                val ws = client.newWebSocketBuilder()
                    .buildAsync(URI(wsUrl), listener)
                    .get(15, TimeUnit.SECONDS)
                listener.send(ws, "Page.enable")
                // Clears navigator.webdriver at the inspector level. No JS shims beyond
                // this: a headful browser launched without --enable-automation already
                // reports a genuine window.chrome, plugin list and WebGL vendor, and
                // patching those in the main world is itself detectable — the patched
                // property descriptors don't match a browser that never had the flag.
                listener.send(
                    ws,
                    HANDSHAKE.last(),
                    buildJsonObject { put("enabled", false) },
                )
                // Read the real UA rather than the requested one: it is what the browser
                // will actually send, so clearance cookies stay bound to it.
                val ua = listener.send(ws, "Runtime.evaluate", buildJsonObject {
                    put("expression", "navigator.userAgent")
                    put("returnByValue", true)
                })?.jsonObject?.get("result")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
                    ?: userAgent
                if (!ua.isNullOrBlank()) WebViewResolver.webViewUserAgent = ua
                Log.i(TAG, "Browser CDP ready at $wsUrl")
                return BrowserSession(proc, ws, listener, ua)
            }

            private fun waitForWs(port: Int): String {
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                val url = URI("http://127.0.0.1:$port/json/list")
                val deadline = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < deadline) {
                    val body = runCatching {
                        client.send(
                            HttpRequest.newBuilder(url).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString(),
                        ).body()
                    }.getOrNull()
                    if (!body.isNullOrBlank() && body.startsWith("[")) {
                        val arr = Json.parseToJsonElement(body)
                        if (arr is kotlinx.serialization.json.JsonArray) {
                            val page = arr.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "page" }
                                ?: arr.firstOrNull()
                            val ws = page?.jsonObject?.get("webSocketDebuggerUrl")?.jsonPrimitive?.contentOrNull
                            if (!ws.isNullOrBlank()) return ws
                        }
                    }
                    Thread.sleep(250)
                }
                throw IllegalStateException("Timed out waiting for Edge/Chrome DevTools on port $port")
            }

            private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

            private fun findBrowser(): File? {
                val names = listOf(
                    File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Microsoft/Edge/Application/msedge.exe"),
                    File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Microsoft/Edge/Application/msedge.exe"),
                    File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Google/Chrome/Application/chrome.exe"),
                    File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Google/Chrome/Application/chrome.exe"),
                    File("/usr/bin/microsoft-edge"),
                    File("/usr/bin/google-chrome"),
                    File("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"),
                    File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
                )
                return names.firstOrNull { it.isFile }
            }

            private fun dataDir(): File {
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
                return File(root, "webview").also { it.mkdirs() }
            }
        }
    }

    internal class CdpListener : WebSocket.Listener {
        private val buf = StringBuilder()
        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonElement?>>()
        private val requests = ArrayList<Request>()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        @Volatile var onBinding: ((String) -> Unit)? = null
        @Volatile var onNavigated: (() -> Unit)? = null

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buf.append(data)
            if (last) {
                val msg = buf.toString()
                buf.setLength(0)
                handle(msg)
            }
            webSocket.request(1)
            return null
        }

        private fun handle(msg: String) {
            val el = runCatching { json.parseToJsonElement(msg) }.getOrNull() as? JsonObject ?: return
            val id = el["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (id != null) {
                val result = el["result"]
                pending.remove(id)?.complete(result)
                return
            }
            val method = el["method"]?.jsonPrimitive?.contentOrNull ?: return
            if (method == "Page.frameNavigated" || method == "Page.navigatedWithinDocument") {
                onNavigated?.invoke()
                return
            }
            if (method == "Runtime.bindingCalled") {
                val payload = el["params"]?.jsonObject?.get("payload")?.jsonPrimitive?.contentOrNull
                if (!payload.isNullOrBlank()) onBinding?.invoke(payload)
                return
            }
            if (method == "Network.requestWillBeSent") {
                val req = el["params"]?.jsonObject?.get("request")?.jsonObject ?: return
                toOkHttp(req)?.let { captured ->
                    synchronized(requests) { requests += captured }
                }
            }
        }

        fun takeRequests(): List<Request> = synchronized(requests) {
            val copy = requests.toList()
            requests.clear()
            copy
        }

        fun snapshotRequests(): List<Request> = synchronized(requests) {
            // Bounded history so long capture loops don't grow unbounded.
            if (requests.size > 800) requests.subList(0, requests.size - 800).clear()
            requests.toList()
        }

        fun send(ws: WebSocket, method: String, params: JsonObject? = null): JsonElement? {
            val id = nextId.getAndIncrement()
            val future = CompletableFuture<JsonElement?>()
            pending[id] = future
            val payload = buildJsonObject {
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }
            ws.sendText(payload.toString(), true)
            return runCatching { future.get(8, TimeUnit.SECONDS) }.getOrNull()
        }

        private fun toOkHttp(req: JsonObject): Request? {
            val url = req["url"]?.jsonPrimitive?.contentOrNull ?: return null
            if (!url.startsWith("http")) return null
            val method = req["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
            val headerObj = req["headers"] as? JsonObject
            val builder = Request.Builder().url(url)
            headerObj?.forEach { (k, v) ->
                if (k.startsWith(":")) return@forEach
                val value = v.jsonPrimitive.contentOrNull ?: return@forEach
                runCatching { builder.addHeader(k, value) }
            }
            val body = req["postData"]?.jsonPrimitive?.contentOrNull
            return if (method.equals("GET", true) || method.equals("HEAD", true)) {
                builder.method(method, null).build()
            } else {
                builder.method(method, (body ?: "").toRequestBody()).build()
            }
        }
    }
}
