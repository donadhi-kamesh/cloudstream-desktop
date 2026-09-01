package dev.csdesktop.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.JSlider
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * Modern video surface + overlay chrome.
 * mpv renders directly into [Canvas] HWND. An attached transparent [JWindow] hosts [ChromeOverlay]
 * with focusableWindowState = false so it never steals focus or triggers window minimization.
 */
class MpvVideoPanel(val mini: Boolean = false) : JPanel(BorderLayout()) {

    interface Listener {
        fun onTogglePause() {}
        fun onSeekTo(seconds: Double) {}
        fun onSeekBy(seconds: Double) {}
        fun onVolume(volume: Int) {}
        fun onToggleMute() {}
        fun onSpeed(speed: Double) {}
        fun onSelectSource(link: ExtractorLink) {}
        fun onSelectAudio(id: Int) {}
        fun onSelectSub(id: Int) {}
        fun onSelectVideo(id: Int) {}
        fun onAspect(mode: String) {}
        fun onFullscreen() {}
        fun onBack() {}
        fun onPip() {}
        fun onDismissPip() {}
    }

    @Volatile var listener: Listener = object : Listener {}
        set(value) { field = value; overlay.listener = value }

    val canvas = Canvas()

    private val overlay = ChromeOverlay(mini)
    private var overlayWindow: JWindow? = null
    private var attachedWindow: Window? = null

    private val windowListener = object : WindowAdapter() {
        override fun windowIconified(e: WindowEvent) {
            overlayWindow?.isVisible = false
        }
        override fun windowDeiconified(e: WindowEvent) {
            syncOverlay()
        }
        override fun windowActivated(e: WindowEvent) {
            syncOverlay()
        }
        override fun windowClosed(e: WindowEvent) {
            disposeChrome()
        }
    }

    private val windowComponentListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) {
            syncOverlay()
        }
        override fun componentResized(e: ComponentEvent) {
            syncOverlay()
        }
    }

    init {
        background = Color.BLACK
        isOpaque = true
        add(canvas, BorderLayout.CENTER)

        val canvasMouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                overlay.showChrome()
                overlay.handleCanvasPress(e)
            }
            override fun mouseReleased(e: MouseEvent) {
                overlay.handleCanvasRelease(e)
            }
            override fun mouseMoved(e: MouseEvent) {
                overlay.showChrome()
                overlay.handleCanvasMove(e)
            }
            override fun mouseDragged(e: MouseEvent) {
                overlay.showChrome()
                overlay.handleCanvasDrag(e)
            }
        }
        canvas.addMouseListener(canvasMouseAdapter)
        canvas.addMouseMotionListener(canvasMouseAdapter)
        canvas.addMouseWheelListener { e ->
            overlay.showChrome()
            overlay.handleMouseWheel(e)
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) { syncOverlay() }
            override fun componentResized(e: ComponentEvent) {
                canvas.setBounds(0, 0, width, height)
                syncOverlay()
            }
            override fun componentShown(e: ComponentEvent) { syncOverlay() }
            override fun componentHidden(e: ComponentEvent) { overlayWindow?.isVisible = false }
        })

        addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
            override fun ancestorMoved(e: HierarchyEvent) { syncOverlay() }
            override fun ancestorResized(e: HierarchyEvent) {
                canvas.setBounds(0, 0, width, height)
                syncOverlay()
            }
        })

        addHierarchyListener { e ->
            if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                syncOverlay()
            }
        }
    }

    override fun addNotify() {
        super.addNotify()
        SwingUtilities.invokeLater { syncOverlay() }
    }

    override fun removeNotify() {
        detachParent()
        overlayWindow?.isVisible = false
        super.removeNotify()
    }

    override fun doLayout() {
        super.doLayout()
        canvas.setBounds(0, 0, width, height)
        syncOverlay()
    }

    override fun getPreferredSize(): Dimension = Dimension(640, 360)

    private fun attachParent(parent: Window) {
        if (attachedWindow == parent) return
        detachParent()
        attachedWindow = parent
        parent.addWindowListener(windowListener)
        parent.addComponentListener(windowComponentListener)
    }

    private fun detachParent() {
        attachedWindow?.removeWindowListener(windowListener)
        attachedWindow?.removeComponentListener(windowComponentListener)
        attachedWindow = null
    }

    fun syncOverlay() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { syncOverlay() }
            return
        }
        if (!isShowing || !canvas.isShowing || width <= 0 || height <= 0) {
            overlayWindow?.isVisible = false
            return
        }
        val parent = SwingUtilities.getWindowAncestor(this)
        if (parent == null || !parent.isShowing) {
            overlayWindow?.isVisible = false
            return
        }
        if (parent is Frame && (parent.extendedState and Frame.ICONIFIED) != 0) {
            overlayWindow?.isVisible = false
            return
        }

        attachParent(parent)

        if (overlayWindow == null || overlayWindow?.owner != parent) {
            overlayWindow?.dispose()
            overlayWindow = JWindow(parent).apply {
                type = Window.Type.POPUP
                background = Color(0, 0, 0, 0)
                (contentPane as? JComponent)?.isOpaque = false
                contentPane = overlay
                focusableWindowState = false
                isAutoRequestFocus = false
                if (mini) isAlwaysOnTop = true
            }
        }

        try {
            canvas.setBounds(0, 0, width, height)
            val pt = canvas.locationOnScreen
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            overlayWindow?.setBounds(pt.x, pt.y, w, h)
            if (overlayWindow?.isVisible != true) {
                overlayWindow?.isVisible = true
            }
            overlay.repaint()
        } catch (_: java.awt.IllegalComponentStateException) {
            overlayWindow?.isVisible = false
        }
    }

    // ---- state pushed from Compose ----

    fun setTitle(title: String, subtitle: String) = overlay.edt { overlay.titleText = title; overlay.subtitleText = subtitle }
    fun setProgress(position: Double, duration: Double, cache: Double) =
        overlay.edt { overlay.position = position; overlay.duration = duration; overlay.cache = cache }
    fun setPaused(paused: Boolean) = overlay.edt {
        val changed = overlay.paused != paused
        overlay.paused = paused
        if (changed) overlay.pulse(paused)
    }
    fun setBuffering(buffering: Boolean) = overlay.edt { overlay.buffering = buffering }
    fun setBusy(busy: Boolean, message: String) = overlay.edt { overlay.busy = busy; overlay.busyMessage = message }
    fun setError(message: String?) = overlay.edt { overlay.errorText = message }
    fun setVolume(volume: Int, muted: Boolean) = overlay.edt { overlay.volume = volume; overlay.muted = muted }
    fun setSpeed(speed: Double) = overlay.edt { overlay.speed = speed }
    fun setLive(live: Boolean) = overlay.edt { overlay.live = live }
    fun setFullscreen(fullscreen: Boolean) = overlay.edt { overlay.fullscreen = fullscreen }
    fun setAspect(mode: String) = overlay.edt { overlay.aspect = mode }
    fun setSources(links: List<ExtractorLink>, selected: ExtractorLink?) = overlay.edt {
        overlay.sources = links
        overlay.selectedSource = selected
    }
    fun setTracks(audio: List<MpvTrack>, subs: List<MpvTrack>, video: List<MpvTrack>) = overlay.edt {
        overlay.audioTracks = audio
        overlay.subTracks = subs
        overlay.videoTracks = video
    }

    fun showChromeTemporarily() = overlay.edt { overlay.showChrome() }
    fun disposeChrome() = overlay.edt {
        overlay.stopTimers()
        detachParent()
        overlayWindow?.isVisible = false
        overlayWindow?.dispose()
        overlayWindow = null
    }
}

private inline fun ChromeOverlay.edt(crossinline block: ChromeOverlay.() -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) this.block() else SwingUtilities.invokeLater { this.block() }
}

private class ChromeOverlay(
    private val mini: Boolean,
) : JComponent() {

    var listener: MpvVideoPanel.Listener = object : MpvVideoPanel.Listener {}

    // playback state
    @Volatile var position = 0.0
    @Volatile var duration = 0.0
    @Volatile var cache = 0.0
    @Volatile var paused = false
    @Volatile var buffering = false
    @Volatile var volume = 100
    @Volatile var muted = false
    @Volatile var speed = 1.0
    @Volatile var live = false
    @Volatile var fullscreen = false
    @Volatile var aspect = "fit"
    @Volatile var busy = true
    @Volatile var busyMessage = "Loading streams…"
    @Volatile var errorText: String? = null

    // chrome state
    @Volatile var chromeVisible = true
    @Volatile var menuOpen = false
    @Volatile var scrubbing = false
    var scrubRatio = 0f
    private var hoverId: String? = null
    private var hoverSeek = false
    private var hoverSeekRatio = 0f
    private var pulseAlpha = 0f
    private var pulsePlay = false
    private var skipFeedbackText: String? = null
    private var skipFeedbackAlpha = 0f
    private var spinnerAngle = 0.0
    var titleText = ""
    var subtitleText = ""

    var sources: List<ExtractorLink> = emptyList()
    var selectedSource: ExtractorLink? = null
    var audioTracks: List<MpvTrack> = emptyList()
    var subTracks: List<MpvTrack> = emptyList()
    var videoTracks: List<MpvTrack> = emptyList()

    private val buttons = ArrayList<Button>()
    private val blankCursor: Cursor = Toolkit.getDefaultToolkit()
        .createCustomCursor(Toolkit.getDefaultToolkit().getImage(""), Point(0, 0), "blank")

    private val idleTimer = Timer(3200) { hideChrome() }
    private val animTimer = Timer(30) {
        spinnerAngle = (spinnerAngle + 12.0) % 360.0
        if (pulseAlpha > 0f) pulseAlpha = (pulseAlpha - 0.05f).coerceAtLeast(0f)
        if (skipFeedbackAlpha > 0f) skipFeedbackAlpha = (skipFeedbackAlpha - 0.05f).coerceAtLeast(0f)
        if (pulseAlpha > 0f || skipFeedbackAlpha > 0f || buffering || busy || chromeVisible) repaint()
    }

    init {
        isOpaque = false
        isFocusable = false
        idleTimer.isRepeats = false
        animTimer.start()

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                handleCanvasPress(e)
            }
            override fun mouseReleased(e: MouseEvent) {
                handleCanvasRelease(e)
            }
            override fun mouseExited(e: MouseEvent) {
                hoverId = null
                hoverSeek = false
                repaint()
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                handleCanvasMove(e)
            }
            override fun mouseDragged(e: MouseEvent) {
                handleCanvasDrag(e)
            }
        })
        addMouseWheelListener { e ->
            handleMouseWheel(e)
        }
    }

    fun handleCanvasPress(e: MouseEvent) {
        val wasVisible = chromeVisible
        showChrome()
        val id = buttonAt(e.point)
        if (id != null) {
            press(id)
            return
        }
        val seek = seekBarRect()
        if (seek.contains(e.point) && !live && duration > 0) {
            scrubbing = true
            scrubRatio = ((e.point.x - seek.x).toFloat() / seek.width.toFloat()).coerceIn(0f, 1f)
            repaint()
            return
        }
        if (e.clickCount == 2 && !mini) {
            listener.onTogglePause()
            listener.onFullscreen()
        } else {
            listener.onTogglePause()
            showChrome()
        }
    }

    fun handleCanvasRelease(e: MouseEvent) {
        if (scrubbing) {
            scrubbing = false
            listener.onSeekTo(scrubRatio.toDouble() * duration)
            repaint()
        }
    }

    fun handleCanvasMove(e: MouseEvent) {
        showChrome()
        val overButton = buttonAt(e.point) != null
        val seek = if (!live && duration > 0) seekBarRect() else null
        val overSeek = seek?.contains(e.point) == true && chromeVisible

        hoverSeek = overSeek
        if (overSeek && seek != null) {
            hoverSeekRatio = ((e.point.x - seek.x).toFloat() / seek.width.toFloat()).coerceIn(0f, 1f)
        }

        cursor = if (overButton || overSeek) {
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        } else {
            Cursor.getDefaultCursor()
        }
        val id = buttonAt(e.point)
        if (id != hoverId) {
            hoverId = id
            repaint()
        } else if (overSeek) {
            repaint()
        }
    }

    fun handleCanvasDrag(e: MouseEvent) {
        showChrome()
        if (scrubbing && duration > 0) {
            val seek = seekBarRect()
            scrubRatio = ((e.point.x - seek.x).toFloat() / seek.width.toFloat()).coerceIn(0f, 1f)
            repaint()
        }
    }

    fun handleMouseWheel(e: MouseWheelEvent) {
        showChrome()
        val rot = e.wheelRotation
        if (rot != 0) {
            val newVol = (volume - rot * 5).coerceIn(0, 130)
            listener.onVolume(newVol)
            pulseFeedback("Volume ${newVol}%")
        }
    }

    fun stopTimers() {
        idleTimer.stop()
        animTimer.stop()
    }

    fun showChrome() {
        if (!chromeVisible) {
            chromeVisible = true
            cursor = Cursor.getDefaultCursor()
            repaint()
        }
        if (menuOpen || scrubbing || paused) return
        idleTimer.restart()
    }

    private fun hideChrome() {
        if (menuOpen || scrubbing || paused || busy || errorText != null) return
        chromeVisible = false
        hoverId = null
        hoverSeek = false
        cursor = blankCursor
        repaint()
    }

    internal fun pulse(play: Boolean) {
        pulsePlay = play
        pulseAlpha = 1f
    }

    private fun pulseFeedback(text: String) {
        skipFeedbackText = text
        skipFeedbackAlpha = 1f
        repaint()
    }

    // ---- geometry ----

    private fun topBarHeight(): Int = if (mini) 40 else 72
    private fun bottomBarHeight(): Int = when {
        mini -> 48
        live -> 64
        else -> 96
    }

    private fun seekBarRect(): Rectangle {
        val h = height
        val w = width
        return if (mini) {
            Rectangle(16, h - 22, (w - 32).coerceAtLeast(10), 16)
        } else {
            Rectangle(24, h - 64, (w - 48).coerceAtLeast(10), 20)
        }
    }

    private fun layoutButtons(): List<Button> {
        val list = ArrayList<Button>()
        val w = width
        val h = height
        if (mini) {
            // Chrome-style PiP: Large center Play/Pause, top right Expand & Close
            val cx = w / 2
            val cy = h / 2
            list += Button("play", Rectangle(cx - 24, cy - 24, 48, 48), if (paused) Icons.PLAY else Icons.PAUSE, null, circular = true)
            list += Button("pip", Rectangle(w - 68, 8, 28, 28), Icons.PIP, null)
            list += Button("pipclose", Rectangle(w - 34, 8, 28, 28), Icons.CLOSE, null)
            return list
        }

        // Top bar
        val ty = 16
        list += Button("back", Rectangle(18, ty, 38, 38), Icons.BACK, null)
        list += Button("pip", Rectangle(w - 56, ty, 38, 38), Icons.PIP, null)

        // Bottom transport row (aligned along Y center: h - 26)
        val cy = h - 26

        // Left transport controls
        var x = 20
        list += Button("play", Rectangle(x, cy - 16, 34, 34), if (paused) Icons.PLAY else Icons.PAUSE, null)
        x += 42
        if (!live) {
            list += Button("rew", Rectangle(x, cy - 14, 28, 28), Icons.REW, null)
            x += 34
            list += Button("fwd", Rectangle(x, cy - 14, 28, 28), Icons.FWD, null)
            x += 34
        }
        list += Button("volume", Rectangle(x, cy - 14, 28, 28), if (muted || volume == 0) Icons.VOLUME_OFF else Icons.VOLUME, null)

        // Right transport controls
        var rx = w - 24
        list += Button("fullscreen", Rectangle(rx - 32, cy - 16, 32, 32), if (fullscreen) Icons.FULLSCREEN_EXIT else Icons.FULLSCREEN, null)
        rx -= 40
        val speedLabel = "${if (speed == speed.toLong().toDouble()) speed.toLong() else speed}x"
        val speedW = textButtonWidth(speedLabel, 64)
        list += Button("speed", Rectangle(rx - speedW, cy - 15, speedW, 30), null, speedLabel)
        rx -= speedW + 8
        val aspectLabel = aspect.uppercase()
        val aspectW = textButtonWidth(aspectLabel, 68)
        list += Button("aspect", Rectangle(rx - aspectW, cy - 15, aspectW, 30), null, aspectLabel)
        rx -= aspectW + 8
        val hasActiveSub = subTracks.any { it.selected && it.id > 0 }
        list += Button("subs", Rectangle(rx - 32, cy - 16, 32, 32), if (hasActiveSub) Icons.CAPTIONS_ON else Icons.CAPTIONS, null)
        rx -= 38
        list += Button("audio", Rectangle(rx - 32, cy - 16, 32, 32), Icons.AUDIO, null)
        rx -= 38
        if (videoTracks.isNotEmpty()) {
            list += Button("video", Rectangle(rx - 32, cy - 16, 32, 32), Icons.VIDEO, null)
            rx -= 38
        }
        val quality = qualityLabel()
        val qualW = textButtonWidth(quality, 160)
        list += Button("source", Rectangle(rx - qualW, cy - 15, qualW, 30), null, quality)
        return list
    }

    private fun textButtonWidth(label: String, max: Int): Int =
        (label.length * 8 + 24).coerceAtMost(max).coerceAtLeast(46)

    private fun qualityLabel(): String {
        val sel = selectedSource
        val qStr = sel?.let { Qualities.getStringByInt(it.quality) }?.takeIf { it.isNotBlank() }
            ?: sel?.name?.takeIf { it.isNotBlank() }
            ?: "Auto"
        return if (sources.size > 1) "$qStr (${sources.size})" else qStr
    }

    private fun cachedButtons(): List<Button> {
        buttons.clear()
        buttons.addAll(layoutButtons())
        return buttons
    }

    private fun buttonAt(p: Point): String? {
        if (!chromeVisible && !busy && errorText == null) return null
        return cachedButtons().firstOrNull { it.rect.contains(p) }?.id
    }

    private fun press(id: String) {
        when (id) {
            "play" -> listener.onTogglePause()
            "rew" -> {
                listener.onSeekBy(-10.0)
                pulseFeedback("-10s")
            }
            "fwd" -> {
                listener.onSeekBy(10.0)
                pulseFeedback("+10s")
            }
            "volume" -> showVolumePopup()
            "source" -> showSourcesPopup()
            "audio" -> showTracksPopup("Audio Tracks", audioTracks) { listener.onSelectAudio(it) }
            "video" -> showTracksPopup("Video Streams", videoTracks) { listener.onSelectVideo(it) }
            "subs" -> {
                val popup = JPopupMenu()
                stylePopup(popup)
                val off = JRadioButtonMenuItem("Subtitles off", subTracks.none { it.selected && it.id > 0 })
                styleRadioItem(off)
                off.addActionListener { listener.onSelectSub(0) }
                popup.add(off)
                if (subTracks.isNotEmpty()) popup.addSeparator()
                subTracks.forEach { t ->
                    val item = JRadioButtonMenuItem(t.label(), t.selected)
                    styleRadioItem(item)
                    item.addActionListener { listener.onSelectSub(t.id) }
                    popup.add(item)
                }
                showPopup(popup, "subs")
            }
            "speed" -> showSpeedPopup()
            "aspect" -> showAspectPopup()
            "fullscreen" -> listener.onFullscreen()
            "pip" -> listener.onPip()
            "back" -> listener.onBack()
            "pipclose" -> listener.onDismissPip()
        }
        repaint()
    }

    private fun showPopup(popup: JPopupMenu, anchorId: String) {
        val anchor = cachedButtons().firstOrNull { it.id == anchorId }?.rect ?: Rectangle(width / 2, height / 2, 1, 1)
        popup.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) { menuOpen = true }
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) { menuOpen = false; showChrome() }
            override fun popupMenuCanceled(e: PopupMenuEvent?) { menuOpen = false; showChrome() }
        })
        val x = (anchor.x + anchor.width / 2 - 130).coerceIn(8, (width - 270).coerceAtLeast(8))
        val y = if (anchor.y > height / 2) (anchor.y - popup.preferredSize.height - 10) else (anchor.y + anchor.height + 10)
        popup.show(this, x.coerceAtLeast(8), y.coerceAtLeast(8))
    }

    private fun showVolumePopup() {
        val popup = JPopupMenu()
        stylePopup(popup)
        val slider = JSlider(0, 130, volume)
        slider.preferredSize = Dimension(160, 26)
        slider.isOpaque = false
        slider.background = Color(0x18181C)
        slider.foreground = ACCENT
        val panel = JPanel(BorderLayout(8, 0))
        panel.isOpaque = true
        panel.background = Color(0x18181C)
        panel.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        val label = JLabel("${volume}%")
        label.foreground = Color.WHITE
        label.font = FONT_SMALL_BOLD
        label.preferredSize = Dimension(38, 20)
        slider.addChangeListener {
            label.text = "${slider.value}%"
            listener.onVolume(slider.value)
        }
        panel.add(slider, BorderLayout.CENTER)
        panel.add(label, BorderLayout.EAST)
        popup.add(panel)
        showPopup(popup, "volume")
    }

    private fun showSourcesPopup() {
        val popup = JPopupMenu()
        stylePopup(popup)
        val header = JMenuItem("Available Sources (${sources.size})").apply { isEnabled = false }
        styleHeaderItem(header)
        popup.add(header)
        popup.addSeparator()
        if (sources.isEmpty()) {
            val empty = JMenuItem("No sources available").apply { isEnabled = false }
            styleItem(empty)
            popup.add(empty)
        }
        sources.forEach { link ->
            val active = selectedSource?.url == link.url && selectedSource?.name == link.name
            val qStr = Qualities.getStringByInt(link.quality).takeIf { it.isNotBlank() } ?: "${link.quality}p"
            val item = JRadioButtonMenuItem("${link.name} · $qStr", active)
            styleRadioItem(item)
            item.addActionListener { listener.onSelectSource(link) }
            popup.add(item)
        }
        showPopup(popup, "source")
    }

    private fun showTracksPopup(title: String, tracks: List<MpvTrack>, pick: (Int) -> Unit) {
        val popup = JPopupMenu()
        stylePopup(popup)
        val header = JMenuItem(title).apply { isEnabled = false }
        styleHeaderItem(header)
        popup.add(header)
        popup.addSeparator()
        if (tracks.isEmpty()) {
            val none = JMenuItem("None available").apply { isEnabled = false }
            styleItem(none)
            popup.add(none)
        } else {
            tracks.forEach { t ->
                val item = JRadioButtonMenuItem(t.label(), t.selected)
                styleRadioItem(item)
                item.addActionListener { pick(t.id) }
                popup.add(item)
            }
        }
        showPopup(popup, "audio")
    }

    private fun showSpeedPopup() {
        val popup = JPopupMenu()
        stylePopup(popup)
        val header = JMenuItem("Playback Speed").apply { isEnabled = false }
        styleHeaderItem(header)
        popup.add(header)
        popup.addSeparator()
        listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { value ->
            val active = value == speed
            val item = JRadioButtonMenuItem("${if (value == value.toLong().toDouble()) value.toLong() else value}x", active)
            styleRadioItem(item)
            item.addActionListener { listener.onSpeed(value) }
            popup.add(item)
        }
        showPopup(popup, "speed")
    }

    private fun showAspectPopup() {
        val popup = JPopupMenu()
        stylePopup(popup)
        val header = JMenuItem("Aspect Ratio").apply { isEnabled = false }
        styleHeaderItem(header)
        popup.add(header)
        popup.addSeparator()
        listOf("fit", "fill", "zoom", "16:9", "4:3").forEach { mode ->
            val active = mode == aspect
            val item = JRadioButtonMenuItem(mode.uppercase(), active)
            styleRadioItem(item)
            item.addActionListener { listener.onAspect(mode) }
            popup.add(item)
        }
        showPopup(popup, "aspect")
    }

    private fun styleItem(item: JMenuItem) {
        item.isOpaque = true
        item.background = Color(0x18181C)
        item.foreground = Color(0xD4D4D8)
        item.font = FONT_SMALL
        item.border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
    }

    private fun styleHeaderItem(item: JMenuItem) {
        item.isOpaque = true
        item.background = Color(0x18181C)
        item.foreground = Color(0xA1A1AA)
        item.font = FONT_BADGE
        item.border = BorderFactory.createEmptyBorder(6, 12, 4, 12)
    }

    private fun styleRadioItem(item: JRadioButtonMenuItem) {
        item.isOpaque = true
        item.background = Color(0x18181C)
        item.foreground = if (item.isSelected) ACCENT_LIGHT else Color.WHITE
        item.font = if (item.isSelected) FONT_SMALL_BOLD else FONT_SMALL
        item.border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
    }

    private fun stylePopup(popup: JPopupMenu) {
        popup.background = Color(0x18181C)
        popup.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x383842), 1, true),
            BorderFactory.createEmptyBorder(4, 2, 4, 2),
        )
    }

    // ---- painting ----

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

            // Essential: 1-alpha quad maintains Windows DWM mouse tracking
            g2.color = Color(0, 0, 0, 1)
            g2.fillRect(0, 0, width, height)

            if (chromeVisible || busy || errorText != null) {
                paintChrome(g2)
            }
            paintCenterStates(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintChrome(g2: Graphics2D) {
        val w = width
        val h = height
        val top = topBarHeight()
        val bottom = bottomBarHeight()

        if (!mini) {
            // Top cinematic vignette
            g2.paint = java.awt.GradientPaint(
                0f, 0f, Color(0xD00A0A0E.toInt(), true),
                0f, (top * 1.6f), Color(0x00000000, true),
            )
            g2.fillRect(0, 0, w, (top * 1.6f).toInt())

            // Bottom cinematic vignette
            val gradStart = (h - bottom - 36).toFloat()
            g2.paint = java.awt.GradientPaint(
                0f, gradStart, Color(0x00000000, true),
                0f, h.toFloat(), Color(0xEB0A0A0E.toInt(), true),
            )
            g2.fillRect(0, gradStart.toInt(), w, (bottom + 36))
        } else {
            // PiP subtle gradient
            g2.paint = java.awt.GradientPaint(
                0f, 0f, Color(0x99000000.toInt(), true),
                0f, h.toFloat(), Color(0xCC000000.toInt(), true),
            )
            g2.fillRect(0, 0, w, h)
        }

        // Draw buttons
        for (b in cachedButtons()) {
            drawButton(g2, b)
        }

        if (!mini) {
            // Header titles with drop-shadow
            g2.font = FONT_TITLE
            val title = ellipsize(g2, titleText, w - 240)
            g2.color = Color(0x99000000.toInt(), true)
            g2.drawString(title, 69, 37)
            g2.color = Color.WHITE
            g2.drawString(title, 68, 36)

            // Subtitle / stream info
            g2.font = FONT_SMALL
            val sub = ellipsize(g2, subtitleText, w - 240)
            g2.color = if (errorText != null) Color(0xFFFCA5A5.toInt()) else Color(0xB3FFFFFF.toInt())
            g2.drawString(sub, 68, 54)

            if (live) {
                drawLiveBadge(g2, w - 70, 22)
            }

            // Left-aligned Time readout in transport bar: curTime / duration
            if (!live) {
                val curTime = if (scrubbing) scrubRatio.toDouble() * duration else position
                val curText = formatTime(curTime)
                val durText = formatTime(duration)
                val fullTimeText = "$curText / $durText"
                g2.font = FONT_SMALL_BOLD
                g2.color = Color(0xDDFFFFFF.toInt(), true)
                val timeX = 186
                g2.drawString(fullTimeText, timeX, h - 21)
            }
        } else {
            // PiP title
            g2.font = FONT_SMALL_BOLD
            val title = ellipsize(g2, titleText, w - 100)
            g2.color = Color.WHITE
            g2.drawString(title, 14, 24)
        }

        // Seek bar
        if (!live) {
            val seek = seekBarRect()
            val ratio = if (scrubbing) scrubRatio else (if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f)
            val cacheRatio = if (duration > 0) (cache / duration).toFloat().coerceIn(0f, 1f) else 0f
            val trackY = seek.y + seek.height / 2f
            val trackH = if (mini) 4f else (if (hoverSeek || scrubbing) 7f else 5f)
            val x0 = seek.x.toFloat()
            val x1 = (seek.x + seek.width).toFloat()

            // Track background
            g2.color = Color(0x40FFFFFF, true)
            g2.fill(RoundRectangle2D.Float(x0, trackY - trackH / 2f, x1 - x0, trackH, trackH, trackH))

            // Hover preview track
            if (hoverSeek && hoverSeekRatio > 0f && !scrubbing) {
                g2.color = Color(0x30FFFFFF, true)
                g2.fill(RoundRectangle2D.Float(x0, trackY - trackH / 2f, (x1 - x0) * hoverSeekRatio, trackH, trackH, trackH))
            }

            // Buffered cache range
            if (cacheRatio > 0f) {
                g2.color = Color(0x70FFFFFF, true)
                g2.fill(RoundRectangle2D.Float(x0, trackY - trackH / 2f, (x1 - x0) * cacheRatio.coerceAtMost(1f), trackH, trackH, trackH))
            }

            // Played progress
            if (ratio > 0f) {
                g2.paint = java.awt.GradientPaint(
                    x0, trackY, ACCENT,
                    x0 + (x1 - x0) * ratio, trackY, ACCENT_LIGHT,
                )
                g2.fill(RoundRectangle2D.Float(x0, trackY - trackH / 2f, (x1 - x0) * ratio, trackH, trackH, trackH))
            }

            // Thumb
            if (!mini) {
                val thumbX = x0 + (x1 - x0) * ratio
                val thumbR = if (scrubbing) 8f else (if (hoverSeek) 7f else 5.5f)

                // Glow halo
                g2.color = Color(0x668B5CF6.toInt(), true)
                g2.fill(Ellipse2D.Float(thumbX - thumbR * 1.8f, trackY - thumbR * 1.8f, thumbR * 3.6f, thumbR * 3.6f))

                // Inner circle
                g2.color = Color.WHITE
                g2.fill(Ellipse2D.Float(thumbX - thumbR, trackY - thumbR, thumbR * 2f, thumbR * 2f))

                // Floating target time preview bubble
                val bubbleRatio = if (scrubbing) scrubRatio else (if (hoverSeek) hoverSeekRatio else -1f)
                if (bubbleRatio >= 0f && duration > 0) {
                    val bubbleTime = formatTime(bubbleRatio.toDouble() * duration)
                    g2.font = FONT_SMALL_BOLD
                    val fm = g2.fontMetrics
                    val bubbleW = fm.stringWidth(bubbleTime) + 18
                    val bubbleH = 24
                    val targetX = x0 + (x1 - x0) * bubbleRatio
                    val bx = (targetX - bubbleW / 2f).coerceIn(12f, (w - bubbleW - 12).toFloat())
                    val by = seek.y - 28f

                    g2.color = Color(0x99000000.toInt(), true)
                    g2.fill(RoundRectangle2D.Float(bx + 1, by + 1, bubbleW.toFloat(), bubbleH.toFloat(), 8f, 8f))
                    g2.color = Color(0xFA18181C.toInt(), true)
                    g2.fill(RoundRectangle2D.Float(bx, by, bubbleW.toFloat(), bubbleH.toFloat(), 8f, 8f))
                    g2.color = Color(0x40FFFFFF, true)
                    g2.draw(RoundRectangle2D.Float(bx, by, bubbleW.toFloat(), bubbleH.toFloat(), 8f, 8f))

                    g2.color = Color.WHITE
                    g2.drawString(bubbleTime, (bx + (bubbleW - fm.stringWidth(bubbleTime)) / 2f).toInt(), (by + 16).toInt())
                }
            }
        }
    }

    private fun drawLiveBadge(g2: Graphics2D, rightX: Int, y: Int) {
        g2.font = FONT_BADGE
        val text = "LIVE"
        val tw = g2.fontMetrics.stringWidth(text)
        val th = 20
        val x = rightX - tw - 16
        g2.color = Color(0xEF4444)
        g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), (tw + 20).toFloat(), th.toFloat(), 6f, 6f))

        g2.color = Color.WHITE
        g2.fillOval(x + 6, y + 6, 6, 6)
        g2.drawString(text, x + 16, y + 14)
    }

    private fun drawButton(g2: Graphics2D, b: Button) {
        val hovered = hoverId == b.id
        val rect = b.rect
        val isPill = b.label != null

        if (b.circular) {
            g2.color = if (hovered) Color(0xCC18181C.toInt(), true) else Color(0x9918181C.toInt(), true)
            g2.fill(Ellipse2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat()))
        } else if (hovered) {
            g2.color = if (isPill) Color(0x40FFFFFF, true) else Color(0x33FFFFFF, true)
            val corner = if (isPill) 10f else 8f
            g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), corner, corner))
        } else if (isPill) {
            g2.color = Color(0x22FFFFFF, true)
            g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 10f, 10f))
            g2.color = Color(0x20FFFFFF, true)
            g2.draw(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 10f, 10f))
        }

        val cx = rect.x + rect.width / 2.0
        val cy = rect.y + rect.height / 2.0
        val iconColor = if (hovered) Color.WHITE else Color(0xEEFFFFFF.toInt(), true)

        if (b.icon != null) {
            val iconSize = if (b.circular) 28 else (if (b.id == "play") 22 else 18)
            Icons.draw(g2, b.icon, (cx - iconSize / 2f).toInt(), (cy - iconSize / 2f).toInt(), iconSize, iconColor)
        } else if (b.label != null) {
            g2.font = FONT_BUTTON
            val fm = g2.fontMetrics
            val text = ellipsize(g2, b.label, rect.width - 12)
            g2.color = if (hovered) Color.WHITE else Color(0xEEFFFFFF.toInt(), true)
            g2.drawString(text, (cx - fm.stringWidth(text) / 2f).toInt(), (cy + fm.ascent / 2f - 2).toInt())
        }
    }

    private fun paintCenterStates(g2: Graphics2D) {
        val w = width
        val h = height

        // Error Screen
        val err = errorText
        if (err != null) {
            g2.color = Color(0xD90B0B0E.toInt(), true)
            g2.fillRect(0, 0, w, h)
            g2.font = FONT_ERROR
            val lines = wrap(g2, err, (w * 0.75f).toInt())
            var y = h / 2 - lines.size * 14 - 10
            g2.color = Color(0xFFFCA5A5.toInt())
            for (line in lines) {
                g2.drawString(line, (w - g2.fontMetrics.stringWidth(line)) / 2, y)
                y += 24
            }
            g2.font = FONT_SMALL
            g2.color = Color(0xB3FFFFFF.toInt())
            val hint = "Press Esc or the back button to return"
            g2.drawString(hint, (w - g2.fontMetrics.stringWidth(hint)) / 2, y + 14)
            return
        }

        // Busy / Initial Loading
        if (busy) {
            paintSpinner(g2, w / 2, h / 2 - 10, 26, ACCENT_LIGHT)
            g2.font = FONT_TITLE
            g2.color = Color.WHITE
            g2.drawString(busyMessage, (w - g2.fontMetrics.stringWidth(busyMessage)) / 2, h / 2 + 42)
            return
        }

        // Buffering Mid-stream
        if (buffering) {
            paintSpinner(g2, w / 2, h / 2, 22, Color.WHITE)
        }

        // Skip / Volume feedback toast
        if (skipFeedbackAlpha > 0f && skipFeedbackText != null) {
            val g = g2.create() as Graphics2D
            g.composite = AlphaComposite.SrcOver.derive(skipFeedbackAlpha)
            g.font = FONT_TITLE
            val fm = g.fontMetrics
            val tw = fm.stringWidth(skipFeedbackText!!) + 28
            val th = 34
            val bx = (w - tw) / 2
            val by = (h * 0.28f).toInt()
            g.color = Color(0xCC18181C.toInt(), true)
            g.fill(RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), tw.toFloat(), th.toFloat(), 12f, 12f))
            g.color = Color.WHITE
            g.drawString(skipFeedbackText!!, bx + 14, by + 22)
            g.dispose()
        }

        // Central Play / Pause Ripple Pulse
        if (pulseAlpha > 0f && !mini) {
            val g = g2.create() as Graphics2D
            g.composite = AlphaComposite.SrcOver.derive(pulseAlpha)
            val r = (38 + (1f - pulseAlpha) * 20f)
            g.color = Color(0x9918181C.toInt(), true)
            g.fill(Ellipse2D.Float(w / 2f - r, h / 2f - r, r * 2f, r * 2f))
            g.color = Color.WHITE
            Icons.draw(
                g,
                if (pulsePlay) Icons.PLAY else Icons.PAUSE,
                (w / 2f - r * 0.5f).toInt(),
                (h / 2f - r * 0.5f).toInt(),
                r.toInt(),
                Color.WHITE,
            )
            g.dispose()
        }
    }

    private fun paintSpinner(g2: Graphics2D, cx: Int, cy: Int, r: Int, color: Color) {
        val arc = Arc2D.Double((cx - r).toDouble(), (cy - r).toDouble(), (r * 2).toDouble(), (r * 2).toDouble(), spinnerAngle, 110.0, Arc2D.OPEN)
        g2.stroke = BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = color
        g2.draw(arc)
        g2.stroke = BasicStroke(1f)
    }

    private fun ellipsize(g2: Graphics2D, text: String, max: Int): String {
        if (text.isEmpty()) return text
        if (g2.fontMetrics.stringWidth(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && g2.fontMetrics.stringWidth("$t…") > max) {
            t = t.dropLast(1)
        }
        return "$t…"
    }

    private fun wrap(g2: Graphics2D, text: String, max: Int): List<String> {
        val words = text.split(' ')
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (word in words) {
            val candidate = if (cur.isEmpty()) word else "${cur} $word"
            if (g2.fontMetrics.stringWidth(candidate) > max && cur.isNotEmpty()) {
                lines.add(cur.toString())
                cur = StringBuilder(word)
            } else {
                cur.clear()
                cur.append(candidate)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines.take(6)
    }

    companion object {
        val ACCENT = Color(0x8B5CF6)
        val ACCENT_LIGHT = Color(0xA78BFA)

        val FONT_TITLE = Font("Segoe UI", Font.BOLD, 16)
        val FONT_SMALL = Font("Segoe UI", Font.PLAIN, 13)
        val FONT_SMALL_BOLD = Font("Segoe UI", Font.BOLD, 13)
        val FONT_BUTTON = Font("Segoe UI", Font.BOLD, 12)
        val FONT_BADGE = Font("Segoe UI", Font.BOLD, 11)
        val FONT_ERROR = Font("Segoe UI", Font.PLAIN, 15)

        fun formatTime(seconds: Double): String {
            val s = seconds.toInt().coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }

    private class Button(
        val id: String,
        val rect: Rectangle,
        val icon: Int?,
        val label: String?,
        val circular: Boolean = false,
    )
}

/** High-contrast crisp vector icons painted with Graphics2D. */
private object Icons {
    const val PLAY = 1
    const val PAUSE = 2
    const val REW = 3
    const val FWD = 4
    const val VOLUME = 5
    const val VOLUME_OFF = 6
    const val FULLSCREEN = 7
    const val FULLSCREEN_EXIT = 8
    const val PIP = 9
    const val BACK = 10
    const val CAPTIONS = 11
    const val CAPTIONS_ON = 12
    const val AUDIO = 13
    const val ASPECT = 14
    const val CLOSE = 15
    const val VIDEO = 16

    fun draw(g2: Graphics2D, icon: Int, x: Int, y: Int, size: Int, color: Color) {
        val g = g2.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = color
            val f = size / 20f
            g.translate(x, y)
            g.scale(f.toDouble(), f.toDouble())
            g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            when (icon) {
                PLAY -> {
                    val p = Path2D.Float()
                    p.moveTo(6.0, 3.5)
                    p.lineTo(17.0, 10.0)
                    p.lineTo(6.0, 16.5)
                    p.closePath()
                    g.fill(p)
                }
                PAUSE -> {
                    g.fill(RoundRectangle2D.Float(4.5f, 3.5f, 4f, 13f, 2f, 2f))
                    g.fill(RoundRectangle2D.Float(11.5f, 3.5f, 4f, 13f, 2f, 2f))
                }
                REW -> {
                    drawSkip(g, forward = false)
                }
                FWD -> {
                    drawSkip(g, forward = true)
                }
                VOLUME -> {
                    drawSpeaker(g)
                    val wave1 = Arc2D.Double(10.0, 6.0, 6.0, 8.0, -60.0, 120.0, Arc2D.OPEN)
                    val wave2 = Arc2D.Double(12.5, 4.0, 8.0, 12.0, -60.0, 120.0, Arc2D.OPEN)
                    g.draw(wave1)
                    g.draw(wave2)
                }
                VOLUME_OFF -> {
                    drawSpeaker(g)
                    g.drawLine(12, 7, 18, 13)
                    g.drawLine(18, 7, 12, 13)
                }
                FULLSCREEN -> drawCorners(g, inward = false)
                FULLSCREEN_EXIT -> drawCorners(g, inward = true)
                PIP -> {
                    g.draw(RoundRectangle2D.Float(2f, 4f, 16f, 12f, 3f, 3f))
                    g.color = color
                    g.fill(RoundRectangle2D.Float(9.5f, 9.5f, 7f, 5f, 2f, 2f))
                }
                BACK -> {
                    val p = Path2D.Float()
                    p.moveTo(12.5, 4.0)
                    p.lineTo(6.5, 10.0)
                    p.lineTo(12.5, 16.0)
                    g.draw(p)
                }
                CAPTIONS -> {
                    g.draw(RoundRectangle2D.Float(2f, 4f, 16f, 12f, 3f, 3f))
                    g.font = Font("Segoe UI", Font.BOLD, 7)
                    g.drawString("CC", 5, 12)
                }
                CAPTIONS_ON -> {
                    g.fill(RoundRectangle2D.Float(2f, 4f, 16f, 12f, 3f, 3f))
                    g.color = Color(0x18181C)
                    g.font = Font("Segoe UI", Font.BOLD, 7)
                    g.drawString("CC", 5, 12)
                }
                AUDIO -> {
                    g.draw(RoundRectangle2D.Float(3f, 7f, 14f, 10f, 3f, 3f))
                    g.drawLine(6, 12, 6, 12)
                    g.drawLine(10, 9, 10, 15)
                    g.drawLine(14, 11, 14, 13)
                }
                VIDEO -> {
                    g.draw(RoundRectangle2D.Float(2.5f, 4.5f, 15f, 11f, 2.5f, 2.5f))
                    val p = Path2D.Float()
                    p.moveTo(8.5, 7.5)
                    p.lineTo(13.0, 10.0)
                    p.lineTo(8.5, 12.5)
                    p.closePath()
                    g.fill(p)
                }
                ASPECT -> {
                    g.draw(RoundRectangle2D.Float(2.5f, 4.5f, 15f, 11f, 2.5f, 2.5f))
                    g.drawLine(6, 10, 14, 10)
                }
                CLOSE -> {
                    g.drawLine(5, 5, 15, 15)
                    g.drawLine(15, 5, 5, 15)
                }
            }
        } finally {
            g.dispose()
        }
    }

    private fun drawSpeaker(g: Graphics2D) {
        val p = Path2D.Float()
        p.moveTo(3.0, 7.5)
        p.lineTo(6.5, 7.5)
        p.lineTo(10.5, 4.0)
        p.lineTo(10.5, 16.0)
        p.lineTo(6.5, 12.5)
        p.lineTo(3.0, 12.5)
        p.closePath()
        g.fill(p)
    }

    private fun drawSkip(g: Graphics2D, forward: Boolean) {
        if (forward) {
            val p = Path2D.Float()
            p.moveTo(4.0, 5.0)
            p.lineTo(11.0, 10.0)
            p.lineTo(4.0, 15.0)
            p.closePath()
            g.fill(p)
            val p2 = Path2D.Float()
            p2.moveTo(10.5, 5.0)
            p2.lineTo(17.5, 10.0)
            p2.lineTo(10.5, 15.0)
            p2.closePath()
            g.fill(p2)
        } else {
            val p = Path2D.Float()
            p.moveTo(9.5, 5.0)
            p.lineTo(2.5, 10.0)
            p.lineTo(9.5, 15.0)
            p.closePath()
            g.fill(p)
            val p2 = Path2D.Float()
            p2.moveTo(16.0, 5.0)
            p2.lineTo(9.0, 10.0)
            p2.lineTo(16.0, 15.0)
            p2.closePath()
            g.fill(p2)
        }
    }

    private fun drawCorners(g: Graphics2D, inward: Boolean) {
        val o = if (inward) 5.5f else 2.5f
        val len = 4.5f
        // top-left
        g.drawLine(o.toInt(), (o + len).toInt(), o.toInt(), o.toInt())
        g.drawLine(o.toInt(), o.toInt(), (o + len).toInt(), o.toInt())
        // top-right
        g.drawLine((19 - o - len).toInt(), o.toInt(), (19 - o).toInt(), o.toInt())
        g.drawLine((19 - o).toInt(), o.toInt(), (19 - o).toInt(), (o + len).toInt())
        // bottom-left
        g.drawLine(o.toInt(), (19 - o - len).toInt(), o.toInt(), (19 - o).toInt())
        g.drawLine(o.toInt(), (19 - o).toInt(), (o + len).toInt(), (19 - o).toInt())
        // bottom-right
        g.drawLine((19 - o - len).toInt(), (19 - o).toInt(), (19 - o).toInt(), (19 - o).toInt())
        g.drawLine((19 - o).toInt(), (19 - o).toInt(), (19 - o).toInt(), (19 - o - len).toInt())
    }
}
