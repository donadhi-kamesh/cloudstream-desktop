package dev.csdesktop.player

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID
import com.lagradost.cloudstream3.utils.PLAYREADY_DRM_UUID
import com.lagradost.cloudstream3.utils.WIDEVINE_DRM_UUID
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerRouterTest {
    @Test
    fun `clear HLS uses mpv`() = runBlocking {
        val link = newExtractorLink("src", "1080", "https://example.com/vod.m3u8")
        val route = PlayerRouter.route(link, TvType.Movie, 3_600_000)
        assertEquals(PlaybackEngine.Mpv, route.engine)
        assertEquals(PlayerMode.Vod, route.mode)
        assertEquals(DrmScheme.None, route.drm)
        assertNull(route.error)
    }

    @Test
    fun `live type hides seek and skips resume`() = runBlocking {
        val link = newExtractorLink("src", "live", "https://example.com/live.m3u8")
        val route = PlayerRouter.route(link, TvType.Live, null)
        assertEquals(PlayerMode.Live, route.mode)
        assertEquals(PlaybackEngine.Mpv, route.engine)
    }

    @Test
    fun `zero duration is treated as live`() = runBlocking {
        val link = newExtractorLink("src", "endless", "https://example.com/a.mp4")
        val route = PlayerRouter.route(link, TvType.Movie, 0)
        assertEquals(PlayerMode.Live, route.mode)
    }

    @Test
    fun `clearkey stays on mpv`() = runBlocking {
        val link = newDrmExtractorLink("src", "ck", "https://example.com/a.mpd", uuid = CLEARKEY_DRM_UUID) {
            kid = "01010101010101010101010101010101"
            key = "02020202020202020202020202020202"
        }
        val route = PlayerRouter.route(link, TvType.Movie, 1000)
        assertEquals(DrmScheme.ClearKey, route.drm)
        assertEquals(PlaybackEngine.Mpv, route.engine)
        assertNull(route.error)
    }

    @Test
    fun `widevine uses shaka webview2`() = runBlocking {
        val link = newDrmExtractorLink("src", "wv", "https://example.com/a.mpd", uuid = WIDEVINE_DRM_UUID) {
            licenseUrl = "https://license.example/widevine"
        }
        val route = PlayerRouter.route(link)
        assertEquals(DrmScheme.Widevine, route.drm)
        assertEquals(PlaybackEngine.WebView2Shaka, route.engine)
    }

    @Test
    fun `playready uses shaka webview2`() = runBlocking {
        val link = newDrmExtractorLink("src", "pr", "https://example.com/a.mpd", uuid = PLAYREADY_DRM_UUID) {
            licenseUrl = "https://license.example/playready"
        }
        val route = PlayerRouter.route(link)
        assertEquals(DrmScheme.PlayReady, route.drm)
        assertEquals(PlaybackEngine.WebView2Shaka, route.engine)
    }

    @Test
    fun `cenc dash tagged as clearkey is treated as widevine and skipped without license`() = runBlocking {
        val link = newDrmExtractorLink(
            "src",
            "AQ",
            "https://otte.cache.aiv-cdn.net/live/enc/x/cenc.mpd",
            uuid = CLEARKEY_DRM_UUID,
        ) {
            kid = "01010101010101010101010101010101"
            key = "02020202020202020202020202020202"
        }
        val route = PlayerRouter.route(link, TvType.Live, null)
        assertEquals(DrmScheme.Widevine, route.drm)
        assertEquals(true, route.skippable)
    }

    @Test
    fun `unknown drm scheme is an explicit error`() = runBlocking {
        val other = kotlin.uuid.Uuid.parse("00112233-4455-6677-8899-aabbccddeeff")
        val link = newDrmExtractorLink("src", "x", "https://example.com/a.mpd", uuid = other)
        val route = PlayerRouter.route(link)
        assertEquals(DrmScheme.Unknown, route.drm)
        assertNotNull(route.error)
        assertTrue(route.error!!.contains("Unsupported DRM scheme"))
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.jupiter.api.Assertions.assertTrue(condition)
    }
}
