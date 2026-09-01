package com.lagradost.cloudstream3.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cookie half of the Cloudflare fix. A `cf_clearance` token is issued for one host
 * and one User-Agent; presenting one that belongs to a different host gets the request
 * challenged again, which is what used to keep the browser reopening forever.
 */
class ClearanceCookieTest {

    /** The jar is process-wide and persists, so each test works on hosts nobody else uses. */
    private fun host(label: String): String =
        "$label-${Random.nextLong(0, Long.MAX_VALUE)}.example.com"

    @Test
    fun clearanceStaysOnTheHostThatWasIssuedIt() {
        val solved = host("solved")
        val other = host("other")
        DesktopCookieJar.put("https://$solved/", "cf_clearance=abc123; path=/; HttpOnly")
        DesktopCookieJar.put("https://$other/", "__cf_bm=xyz")

        assertTrue(DesktopCookieJar.hasClearance("https://$solved/watch"))
        assertFalse(
            DesktopCookieJar.hasClearance("https://$other/watch"),
            "another host's clearance must never be presented here",
        )
        assertEquals("abc123", DesktopCookieJar.getMap("https://$solved/")["cf_clearance"])
        assertNull(DesktopCookieJar.getMap("https://$other/")["cf_clearance"])
    }

    @Test
    fun cookieAttributesAreNotStoredAsCookies() {
        val h = host("attrs")
        DesktopCookieJar.put("https://$h/", "cf_clearance=tok; Path=/; Domain=.example.com; Secure; SameSite=None; Max-Age=3600")
        val map = DesktopCookieJar.getMap("https://$h/")
        assertEquals(setOf("cf_clearance"), map.keys, "only the cookie itself may be stored, got $map")
    }

    @Test
    fun subdomainsShareTheClearance() {
        // Cloudflare issues clearance on the registrable domain, and playback usually
        // continues on a media subdomain.
        val h = host("share")
        DesktopCookieJar.put("https://$h/", "cf_clearance=tok")
        assertTrue(DesktopCookieJar.hasClearance("https://cdn.$h/stream.m3u8"))
    }

    @Test
    fun clearanceIsEnoughToFinishAChallengeEvenIfTheWidgetStaysInTheDom() {
        val h = host("stay")
        DesktopCookieJar.put("https://$h/", "cf_clearance=tok")
        assertTrue(
            DesktopCookieJar.hasClearance("https://$h/watch"),
            "playback must not wait for the challenge page to vanish once clearance exists",
        )
    }

    @Test
    fun aChallengePageCookieAloneIsNotClearance() {
        // __cf_bm is set by the challenge page itself. Treating it as success is exactly
        // what declared the bypass done before it was.
        val h = host("bm")
        DesktopCookieJar.put("https://$h/", "__cf_bm=only")
        assertFalse(DesktopCookieJar.hasClearance("https://$h/"))
        assertTrue(DesktopCookieJar.getMap("https://$h/").isNotEmpty())
    }

    @Test
    fun clearanceAndBrowserUserAgentTravelTogether() {
        val previous = WebViewResolver.webViewUserAgent
        try {
            WebViewResolver.webViewUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edg/126.0"
            val request = Request.Builder().url("https://example.com/api").build()
            val withCookies = CloudflareKiller.withCookies(request, mapOf("cf_clearance" to "tok"))
            assertEquals("cf_clearance=tok", withCookies.header("Cookie"))
            assertEquals(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edg/126.0",
                withCookies.header("User-Agent"),
                "clearance is bound to the UA that solved the challenge",
            )

            val noCookies = CloudflareKiller.withCookies(request, emptyMap())
            assertNull(noCookies.header("Cookie"))
            assertNull(
                noCookies.header("User-Agent"),
                "with nothing to carry, the extension's own UA must be left alone",
            )
        } finally {
            WebViewResolver.webViewUserAgent = previous
        }
    }

    @Test
    fun challengeResponsesAreRecognised() {
        assertTrue(challenge(403, server = "cloudflare"))
        assertTrue(challenge(503, server = "cloudflare-nginx"))
        assertTrue(challenge(403, body = "<title>Just a moment...</title>"))
        assertTrue(challenge(403, body = "cf-browser-verification"))
        assertTrue(challenge(200, mitigated = "challenge"), "cf-mitigated marks a challenge on any status")
    }

    @Test
    fun ordinaryResponsesAreNotTreatedAsChallenges() {
        assertFalse(challenge(200, server = "nginx", body = "{\"ok\":true}"))
        assertFalse(challenge(404, server = "nginx", body = "not found"))
        assertFalse(
            challenge(403, server = "nginx", body = "you are not allowed"),
            "a plain 403 from a non-Cloudflare origin must not open the browser",
        )
    }

    private fun challenge(
        code: Int,
        server: String? = null,
        body: String = "",
        mitigated: String? = null,
    ): Boolean {
        val request = Request.Builder().url("https://example.com/").build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody(null))
        if (server != null) builder.header("Server", server)
        if (mitigated != null) builder.header("cf-mitigated", mitigated)
        return CloudflareKiller.isCloudflareChallenge(builder.build())
    }
}
