package com.lagradost.cloudstream3.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the browser configuration that Cloudflare Turnstile scores. Turnstile does not
 * fail a suspicious client outright, it re-arms the challenge — so every flag below is
 * the difference between a solved page and the infinite reload loop.
 */
class ChallengeBrowserTest {

    private val args = DesktopChromium.BrowserSession.launchArgs(
        exePath = "C:\\Edge\\msedge.exe",
        port = 9222,
        profileDir = "C:\\profile",
    )

    @Test
    fun launchArgsCarryNoAutomationTells() {
        for (flag in listOf(
            "--enable-automation",
            "--headless",
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--disable-extensions",
            "--disable-dev-shm-usage",
            "--disable-web-security",
            "--test-type",
        )) {
            assertFalse(
                args.any { it.startsWith(flag) },
                "$flag marks the browser as automated and re-arms the challenge",
            )
        }
    }

    @Test
    fun launchArgsDeflagBlinkAndKeepARealWindow() {
        assertTrue(
            args.contains("--disable-blink-features=AutomationControlled"),
            "without this, navigator.webdriver stays true",
        )
        assertTrue(args.any { it.startsWith("--window-size=") }, "a real window size is expected")
        assertEquals("C:\\Edge\\msedge.exe", args.first(), "the executable must lead the command line")
        assertTrue(args.contains("--user-data-dir=C:\\profile"), "a persistent profile keeps solved clearances")
        assertTrue(args.contains("--remote-debugging-port=9222"))
    }

    @Test
    fun runtimeEventsAreNotEnabledForOrdinaryPages() {
        // Runtime.enable makes V8 build a preview for every console call; the challenge
        // script detects that by passing console.debug an object with recording getters.
        assertFalse(
            DesktopChromium.BrowserSession.PAGE_DOMAINS.any { it.startsWith("Runtime.") },
            "Runtime must stay off by default, got ${DesktopChromium.BrowserSession.PAGE_DOMAINS}",
        )
        assertFalse(
            DesktopChromium.BrowserSession.HANDSHAKE.any { it.startsWith("Runtime.") },
            "the startup handshake must not enable Runtime, got ${DesktopChromium.BrowserSession.HANDSHAKE}",
        )
    }

    @Test
    fun handshakeTurnsOffTheAutomationOverride() {
        assertTrue(
            DesktopChromium.BrowserSession.HANDSHAKE.contains("Emulation.setAutomationOverride"),
            "clearing navigator.webdriver at the inspector level beats patching it in JS",
        )
    }

    @Test
    fun networkAndPageStayEnabledSoCapturesStillWork() {
        assertTrue(DesktopChromium.BrowserSession.PAGE_DOMAINS.contains("Network.enable"))
        assertTrue(DesktopChromium.BrowserSession.PAGE_DOMAINS.contains("Page.enable"))
    }
}
