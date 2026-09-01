package dev.csdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.lagradost.api.Log
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.sun.jna.Native
import dev.csdesktop.AppState
import dev.csdesktop.Destination
import dev.csdesktop.player.DrmScheme
import dev.csdesktop.player.MpvRuntime
import dev.csdesktop.player.MpvSession
import dev.csdesktop.player.MpvVideoPanel
import dev.csdesktop.player.PlaybackEngine
import dev.csdesktop.player.PlayerRouter
import dev.csdesktop.player.ShakaPlayerHost
import dev.csdesktop.player.StreamProxy
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Overlay player: one [MpvVideoPanel] fills the screen; mpv owns the canvas HWND
 * and the Swing layer above it draws the controls. Compose handles lifecycle,
 * stream selection, resume, and PiP.
 */
@Composable
fun PlayerScreen(state: AppState, dest: Destination.Player) {
    val links by state.links.collectAsState()
    val subs by state.subs.collectAsState()
    val playError by state.playError.collectAsState()
    var selected by remember { mutableStateOf<ExtractorLink?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<MpvSession?>(null) }
    var panel by remember { mutableStateOf<MpvVideoPanel?>(null) }
    val fullscreen by state.isFullscreen.collectAsState()
    var pip by remember { mutableStateOf(false) }
    var pipPanel by remember { mutableStateOf<MpvVideoPanel?>(null) }
    val live = dest.type == TvType.Live
    val focus = remember { FocusRequester() }
    val shownError = error ?: playError
    val loading = links.isEmpty() && shownError == null
    val sessionRef = remember { AtomicReference<MpvSession?>(null) }
    val selectedRef = remember { AtomicReference<ExtractorLink?>(null) }
    val activePanel = if (pip) pipPanel else panel
    val currentPanel by rememberUpdatedState(activePanel)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SideEffect {
        sessionRef.set(session)
        selectedRef.set(selected)
    }

    LaunchedEffect(links) {
        if (links.isEmpty()) return@LaunchedEffect
        val still = selected?.let { cur -> links.any { it.url == cur.url && it.name == cur.name } } == true
        if (!still) {
            val wanted = state.settingsStore.settings.defaultQuality
            selected = links.minByOrNull { kotlin.math.abs(it.quality - wanted) } ?: links.first()
        }
    }

    LaunchedEffect(activePanel) {
        val videoSurface = activePanel ?: return@LaunchedEffect
        try {
            var hwnd = 0L
            repeat(40) {
                hwnd = Native.getComponentID(videoSurface.canvas)
                if (hwnd != 0L) return@repeat
                delay(50)
            }
            if (hwnd == 0L) {
                error = "Video surface was not ready. Try Play again."
                return@LaunchedEffect
            }
            val existing = session
            if (existing != null && existing.isAlive) {
                existing.setWid(hwnd)
                return@LaunchedEffect
            }
            val lib = MpvRuntime().ensureAvailable { }
            session = MpvSession(lib, hwnd, state.settingsStore.settings.hardwareDecode)
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        }
    }

    var lastPos by remember(dest) {
        mutableStateOf(
            state.library.resume("${dest.provider}|${dest.pageUrl}|${dest.dataUrl}")?.positionMs?.div(1000.0)
                ?: state.library.resume("${dest.provider}|${dest.pageUrl}")?.positionMs?.div(1000.0)
                ?: 0.0
        )
    }
    var aspect by remember { mutableStateOf("fit") }

    fun nextSource(from: ExtractorLink): ExtractorLink? {
        val i = links.indexOfFirst { it.url == from.url && it.name == from.name }
        return links.getOrNull(i + 1)
    }

    fun leaveFullscreen() {
        state.setFullscreen(false)
    }

    LaunchedEffect(selected, session) {
        val link = selected ?: return@LaunchedEffect
        val mpv = session ?: return@LaunchedEffect
        if (!mpv.isAlive) return@LaunchedEffect
        error = null
        currentPanel?.setBusy(true, "Opening ${link.name}…")
        val route = PlayerRouter.route(link, dest.type, if (live) 0 else null)
        Log.i("Player", "route engine=${route.engine} drm=${route.drm} skippable=${route.skippable} ${link.name}")
        if (route.skippable) {
            val nxt = nextSource(link)
            if (nxt != null) {
                Log.w("Player", "skip DRM source ${link.name} -> ${nxt.name}: ${route.error}")
                selected = nxt
                return@LaunchedEffect
            }
            error = route.error
            currentPanel?.setBusy(false, "")
            return@LaunchedEffect
        }
        if (route.error != null && !route.skippable) {
            error = route.error
            currentPanel?.setBusy(false, "")
            return@LaunchedEffect
        }
        if (route.engine == PlaybackEngine.WebView2Shaka) {
            val drm = link as? DrmExtractorLink
            if (drm == null) {
                val nxt = nextSource(link)
                if (nxt != null) selected = nxt else error = "DRM source is missing license data."
                currentPanel?.setBusy(false, "")
                return@LaunchedEffect
            }
            val req = ShakaPlayerHost.fromLink(drm, dest.title, route.drm)
            val html = ShakaPlayerHost().writeAndOpen(req)
            runCatching {
                com.lagradost.cloudstream3.network.DesktopChromium.openPage(html.toURI().toString(), null)
            }.onFailure {
                error = it.message
                val nxt = nextSource(link)
                if (nxt != null) selected = nxt
            }
            currentPanel?.setBusy(false, "")
            return@LaunchedEffect
        }
        try {
            val headers = HashMap<String, String>(link.headers)
            if (link.referer.isNotBlank()) headers.putIfAbsent("Referer", link.referer)
            else StreamProxy.originOf(link.url)?.let { headers.putIfAbsent("Referer", it) }
            com.lagradost.cloudstream3.network.DesktopCookieJar.getCookieHeader(link.url)?.let {
                headers.putIfAbsent("Cookie", it)
            }
            com.lagradost.cloudstream3.network.WebViewResolver.webViewUserAgent?.let {
                headers.putIfAbsent("User-Agent", it)
            }
            var playUrl = link.url
            val extra = mutableListOf<String>()
            if (link is DrmExtractorLink && route.drm == DrmScheme.ClearKey) {
                val prepared = state.clearKeyProxy.prepare(link)
                playUrl = prepared.manifestUrl
                extra += prepared.mpvArgs
            }
            val needsProxy = playUrl.startsWith("http") && (
                link.type == ExtractorLinkType.M3U8 ||
                    link.type == ExtractorLinkType.DASH ||
                    link.isM3u8 ||
                    link.isDash ||
                    playUrl.contains(".m3u8", true) ||
                    playUrl.contains(".mpd", true)
                )
            if (needsProxy && !playUrl.contains("127.0.0.1")) {
                playUrl = state.streamProxy.wrap(link.url, headers)
                Log.i("Player", "proxy wrap type=${link.type} -> $playUrl")
            }
            Log.i("Player", "mpv load type=${link.type} q=${link.quality} url=${link.url.take(180)}")
            val mpvHeaders = if (playUrl.contains("127.0.0.1")) {
                headers.filterKeys { it.equals("user-agent", true) }
            } else headers
            mpv.load(playUrl, extra, mpvHeaders)
            mpv.setAspect(aspect)
            subs.forEach { sub ->
                runCatching { mpv.addSubFile(sub.url) }
            }
            delay(400)
            val resume = if (!dest.startFromBeginning) {
                state.library.resume("${dest.provider}|${dest.pageUrl}|${dest.dataUrl}")
                    ?: state.library.resume("${dest.provider}|${dest.pageUrl}")
            } else null
            val resumeAt = when {
                lastPos > 1.0 -> lastPos
                !live -> resume?.positionMs?.takeIf { it > 1_000 }?.div(1000.0)
                else -> null
            }
            if (resumeAt != null && resumeAt > 1.0) {
                repeat(10) {
                    delay(300)
                    if (!mpv.isAlive) return@repeat
                    if (mpv.getString("idle-active") != "yes" || mpv.durationSeconds() > 0.0) {
                        mpv.seekAbsolute(resumeAt)
                        Log.i("Resume", "seek to ${resumeAt.toInt()}s")
                        return@repeat
                    }
                }
            }
            if (resume != null) {
                if (resume.audioId != null && resume.audioId > 0) {
                    runCatching { mpv.setAid(resume.audioId) }
                }
                if (resume.subId != null) {
                    runCatching { mpv.setSid(resume.subId) }
                }
            }
            var failed = true
            repeat(if (live) 10 else 16) {
                delay(500)
                if (!mpv.isAlive) return@repeat
                val idle = mpv.getString("idle-active") == "yes"
                if (mpv.durationSeconds() > 0.0 || !idle) {
                    failed = false
                    return@repeat
                }
            }
            if (failed && mpv.isAlive) {
                Log.e("Player", "mpv idle after load orig=${link.url.take(160)}")
                val nxt = nextSource(link)
                if (nxt != null) {
                    Log.w("Player", "auto-next ${link.name} -> ${nxt.name}")
                    selected = nxt
                    return@LaunchedEffect
                }
                error = "This stream could not be played. See Logcat."
            }
            currentPanel?.setBusy(false, "")
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.e("Player", "mpv load failed: ${t.stackTraceToString()}")
            val nxt = nextSource(link)
            if (nxt != null) {
                Log.w("Player", "auto-next after error ${link.name} -> ${nxt.name}")
                selected = nxt
            } else error = t.message ?: t.toString()
            currentPanel?.setBusy(false, "")
        }
    }

    // Poll mpv and push state into the overlay chrome.
    LaunchedEffect(session) {
        val mpv = session ?: return@LaunchedEffect
        while (isActive) {
            if (mpv.isAlive) {
                val pos = mpv.positionSeconds()
                val dur = mpv.durationSeconds()
                val cache = mpv.cacheSeconds()
                val isPaused = mpv.paused()
                val vol = mpv.volume()
                val isMuted = mpv.muted()
                val isBuffering = mpv.buffering() && !isPaused
                val tracks = runCatching { mpv.tracks() }.getOrDefault(emptyList())
                if (pos > 0.4) lastPos = pos
                val curAid = tracks.firstOrNull { it.type == "audio" && it.selected }?.id
                val curSid = tracks.firstOrNull { it.type == "sub" && it.selected }?.id
                if (!live && mpv.isAlive && pos >= 0.5) {
                    state.saveResume(
                        "${dest.provider}|${dest.pageUrl}|${dest.dataUrl}",
                        dest.title,
                        dest.poster,
                        dest.provider,
                        dest.pageUrl,
                        dest.dataUrl,
                        (pos * 1000).toLong(),
                        (dur * 1000).toLong(),
                        false,
                        audioId = curAid,
                        subId = curSid,
                    )
                }
                currentPanel?.setProgress(pos, dur, cache)
                currentPanel?.setPaused(isPaused)
                currentPanel?.setBuffering(isBuffering)
                currentPanel?.setVolume(vol, isMuted)
                currentPanel?.setTracks(
                    tracks.filter { it.type == "audio" },
                    tracks.filter { it.type == "sub" },
                    tracks.filter { it.type == "video" },
                )
            }
            if (!mpv.isAlive) break
            delay(250)
        }
    }

    // Push title / sources / busy / error into the overlay chrome.
    LaunchedEffect(panel, pipPanel, links, selected, shownError, loading, aspect, fullscreen, pip) {
        val subtitle = when {
            loading -> "Loading streams…"
            shownError != null -> shownError!!
            else -> selected?.let { "${it.name} · ${Qualities.getStringByInt(it.quality)}" } ?: dest.provider
        }
        panel?.let { p ->
            p.setTitle(dest.title, subtitle)
            p.setSources(links, selected)
            p.setBusy(loading, "Loading streams…")
            p.setError(shownError)
            p.setLive(live)
            p.setAspect(aspect)
            p.setFullscreen(fullscreen)
        }
        pipPanel?.let { p ->
            p.setTitle(dest.title, subtitle)
            p.setBusy(loading, "Loading streams…")
            p.setError(shownError)
            p.setLive(live)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = session?.positionSeconds()?.takeIf { it > 0.4 } ?: lastPos
            val dur = session?.durationSeconds() ?: 0.0
            if (!live && pos > 0.4) {
                state.saveResume(
                    "${dest.provider}|${dest.pageUrl}|${dest.dataUrl}",
                    dest.title,
                    dest.poster,
                    dest.provider,
                    dest.pageUrl,
                    dest.dataUrl,
                    (pos * 1000).toLong(),
                    (dur * 1000).toLong(),
                    false,
                )
            }
            session?.destroy()
            panel?.disposeChrome()
            pipPanel?.disposeChrome()
            state.setFullscreen(false)
        }
    }

    fun seekBy(seconds: Double) {
        if (!live) session?.seekRelative(seconds)
        panel?.showChromeTemporarily()
    }

    fun handleKey(key: Key, shift: Boolean): Boolean {
        val mpv = session
        when (key) {
            Key.Spacebar, Key.K -> mpv?.togglePause()
            Key.DirectionLeft, Key.J -> seekBy(-10.0)
            Key.DirectionRight, Key.L -> seekBy(10.0)
            Key.M -> mpv?.toggleMute()
            Key.F -> state.toggleFullscreen()
            Key.Escape -> {
                if (state.isFullscreen.value) {
                    state.setFullscreen(false)
                } else if (pip) {
                    pip = false
                    pipPanel = null
                } else {
                    state.go(Destination.Result(dest.provider, dest.pageUrl, dest.title))
                }
            }
            Key.DirectionUp -> mpv?.setVolume(((mpv.volume() + 5).coerceAtMost(130)))
            Key.DirectionDown -> mpv?.setVolume(((mpv.volume() - 5).coerceAtLeast(0)))
            else -> return false
        }
        return true
    }

    fun playerListener() = object : MpvVideoPanel.Listener {
        override fun onTogglePause() {
            sessionRef.get()?.togglePause()
        }

        override fun onSeekTo(seconds: Double) {
            sessionRef.get()?.seekAbsolute(seconds)
        }

        override fun onSeekBy(seconds: Double) {
            if (!live) sessionRef.get()?.seekRelative(seconds)
        }

        override fun onVolume(volume: Int) {
            val mpv = sessionRef.get() ?: return
            mpv.setVolume(volume)
            mpv.mute(volume <= 0)
        }

        override fun onToggleMute() {
            sessionRef.get()?.toggleMute()
        }

        override fun onSpeed(speed: Double) {
            sessionRef.get()?.setSpeed(speed)
        }

        override fun onSelectSource(link: ExtractorLink) {
            selectedRef.set(link)
            selected = link
        }

        override fun onSelectAudio(id: Int) {
            sessionRef.get()?.setAid(id)
        }

        override fun onSelectSub(id: Int) {
            sessionRef.get()?.setSid(id)
        }

        override fun onSelectVideo(id: Int) {
            sessionRef.get()?.setVid(id)
        }

        override fun onAspect(mode: String) {
            aspect = mode
            sessionRef.get()?.setAspect(mode)
        }

        override fun onFullscreen() {
            state.toggleFullscreen()
        }

        override fun onBack() {
            state.setFullscreen(false)
            state.go(Destination.Result(dest.provider, dest.pageUrl, dest.title))
        }

        override fun onPip() {
            pip = !pip
            if (!pip) pipPanel = null
        }

        override fun onDismissPip() {
            pip = false
            pipPanel = null
        }
    }

    val mainListener = remember { playerListener() }
    DisposableEffect(panel) {
        panel?.listener = mainListener
        onDispose { }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) handleKey(it.key, it.isShiftPressed) else false
            },
    ) {
        SwingPanel(
            background = Color.Black,
            factory = {
                MpvVideoPanel(mini = false).also { panel = it }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (pip) {
        Window(
            onCloseRequest = {
                pipPanel?.disposeChrome()
                pip = false
                pipPanel = null
            },
            title = dest.title,
            alwaysOnTop = true,
            state = rememberWindowState(width = 460.dp, height = 260.dp),
        ) {
            val pipListener = remember { playerListener() }
            SwingPanel(
                background = Color.Black,
                factory = {
                    MpvVideoPanel(mini = true).also {
                        pipPanel = it
                        it.listener = pipListener
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
