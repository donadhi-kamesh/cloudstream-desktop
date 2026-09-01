package dev.csdesktop.player

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.PLAYREADY_DRM_UUID
import com.lagradost.cloudstream3.utils.WIDEVINE_DRM_UUID
import kotlin.uuid.Uuid

enum class DrmScheme {
    None,
    ClearKey,
    Widevine,
    PlayReady,
    Unknown,
}

enum class PlaybackEngine {
    Mpv,
    WebView2Shaka,
}

enum class PlayerMode {
    Vod,
    Live,
}

data class PlayerRoute(
    val engine: PlaybackEngine,
    val mode: PlayerMode,
    val drm: DrmScheme,
    val error: String? = null,
    val skippable: Boolean = false,
)

object PlayerRouter {
    fun looksLikeWidevineCenc(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("cenc.mpd") ||
            u.contains("/cenc/") ||
            u.contains("widevine") ||
            u.contains("aiv-cdn.net") ||
            u.contains("ott.cache") ||
            u.contains("otte.cache")
    }

    fun drmScheme(link: ExtractorLink): DrmScheme {
        val cenc = looksLikeWidevineCenc(link.url)
        if (link is DrmExtractorLink) {
            val hasClearKey = !link.kid.isNullOrBlank() && !link.key.isNullOrBlank()
            if (cenc) return DrmScheme.Widevine
            return when (link.uuid) {
                CLEARKEY_DRM_UUID -> if (hasClearKey) DrmScheme.ClearKey else DrmScheme.Widevine
                WIDEVINE_DRM_UUID -> DrmScheme.Widevine
                PLAYREADY_DRM_UUID -> DrmScheme.PlayReady
                else -> DrmScheme.Unknown
            }
        }
        if (cenc && (link.type == ExtractorLinkType.DASH || link.url.contains(".mpd", true))) {
            return DrmScheme.Widevine
        }
        return DrmScheme.None
    }

    fun playerMode(type: TvType?, durationMs: Long?): PlayerMode {
        if (type == TvType.Live) return PlayerMode.Live
        if (durationMs != null && durationMs <= 0L) return PlayerMode.Live
        return PlayerMode.Vod
    }

    fun route(
        link: ExtractorLink,
        type: TvType? = null,
        durationMs: Long? = null,
    ): PlayerRoute {
        val mode = playerMode(type, durationMs)
        val drm = drmScheme(link)
        return when (drm) {
            DrmScheme.None -> PlayerRoute(
                engine = PlaybackEngine.Mpv,
                mode = mode,
                drm = drm,
            )
            DrmScheme.ClearKey -> PlayerRoute(
                engine = PlaybackEngine.Mpv,
                mode = mode,
                drm = drm,
            )
            DrmScheme.Widevine, DrmScheme.PlayReady -> {
                val license = (link as? DrmExtractorLink)?.licenseUrl
                if (license.isNullOrBlank()) {
                    PlayerRoute(
                        engine = PlaybackEngine.WebView2Shaka,
                        mode = mode,
                        drm = drm,
                        error = "Source uses ${drm.name} CENC without a license URL.",
                        skippable = true,
                    )
                } else {
                    PlayerRoute(
                        engine = PlaybackEngine.WebView2Shaka,
                        mode = mode,
                        drm = drm,
                    )
                }
            }
            DrmScheme.Unknown -> {
                val named = (link as DrmExtractorLink).uuid.toHexString()
                PlayerRoute(
                    engine = PlaybackEngine.Mpv,
                    mode = mode,
                    drm = drm,
                    error = "Unsupported DRM scheme ($named).",
                    skippable = true,
                )
            }
        }
    }

    private fun Uuid.toHexString(): String = toString()
}
