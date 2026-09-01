package dev.csdesktop.player

import com.lagradost.api.Log
import com.lagradost.cloudstream3.network.BrowserDoneListener
import com.lagradost.cloudstream3.network.ChromiumWindowHost
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * Captcha / plugin WebView host. The Edge window stays clickable: we never put a
 * modal dialog over it. A slim status bar sits at the bottom of the screen with a
 * live status message plus Done / Close controls.
 */
object InAppBrowser : ChromiumWindowHost {
    private const val TAG = "InAppBrowser"
    private val BarBg = Color(0x12, 0x12, 0x16)
    private val BarBorder = Color(0x2E, 0x2E, 0x33)
    private val TextMain = Color(0xE5, 0xE5, 0xE5)
    private val Accent = Color(0x9F, 0x6C, 0xF6)

    @Volatile private var attachedPid: Long = 0
    @Volatile private var child: WinDef.HWND? = null
    @Volatile private var bar: JFrame? = null
    @Volatile private var statusLabel: JLabel? = null

    override fun attach(pid: Long) {
        attachedPid = pid
        Thread({
            repeat(60) {
                val hwnd = findLargestWindow(pid)
                if (hwnd != null) {
                    child = hwnd
                    bringToFront(hwnd)
                    hideOtherWindows(pid, hwnd)
                    Log.i(TAG, "browser window ready pid=$pid")
                    return@Thread
                }
                Thread.sleep(200)
            }
            Log.w(TAG, "could not find browser HWND for pid=$pid")
        }, "cs-embed-browser").apply { isDaemon = true; start() }
    }

    override fun setVisible(visible: Boolean, title: String) {
        SwingUtilities.invokeLater {
            if (visible) {
                child?.let { bringToFront(it) }
                    ?: attachedPid.takeIf { it != 0L }?.let { pid ->
                        findLargestWindow(pid)?.let {
                            child = it
                            bringToFront(it)
                        }
                    }
            } else {
                child?.let { hideWindow(it) }
                    ?: attachedPid.takeIf { it != 0L }?.let { pid ->
                        findLargestWindow(pid)?.let { hideWindow(it) }
                    }
            }
        }
    }

    override fun setStatus(status: String) {
        SwingUtilities.invokeLater {
            val label = statusLabel
            label?.text = status
            label?.isVisible = status.isNotBlank()
            bar?.revalidate()
            bar?.repaint()
        }
    }

    override fun setActionBar(doneLabel: String, checkboxLabel: String?, onDone: BrowserDoneListener) {
        SwingUtilities.invokeLater {
            bar?.dispose()
            val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
            val frame = JFrame("CloudStream")
            frame.isUndecorated = true
            frame.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            frame.isAlwaysOnTop = true
            frame.type = java.awt.Window.Type.UTILITY
            val panel = JPanel(BorderLayout(14, 0))
            panel.background = BarBg
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BarBorder, 1),
                BorderFactory.createEmptyBorder(10, 16, 10, 16),
            )
            val hint = JLabel(
                if (checkboxLabel.isNullOrBlank()) {
                    "Solve the check in the browser window. It continues automatically once passed."
                } else {
                    checkboxLabel
                },
            )
            hint.foreground = TextMain
            hint.font = Font("Segoe UI", Font.PLAIN, 13)
            val status = JLabel(" ")
            status.foreground = Color(0xB3, 0xB3, 0xB8)
            status.font = Font("Segoe UI", Font.PLAIN, 12)
            statusLabel = status

            val text = JPanel(BorderLayout(4, 8))
            text.isOpaque = false
            text.add(hint, BorderLayout.NORTH)
            text.add(status, BorderLayout.SOUTH)

            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
            actions.isOpaque = false
            val check = if (!checkboxLabel.isNullOrBlank()) {
                JCheckBox(checkboxLabel).also {
                    it.foreground = Color.WHITE
                    it.background = panel.background
                    it.isOpaque = false
                    actions.add(it)
                }
            } else null

            val openBrowser = JButton("Open in Default Browser")
            openBrowser.isContentAreaFilled = false
            openBrowser.foreground = Color(0x60, 0xA5, 0xFA)
            openBrowser.font = Font("Segoe UI", Font.PLAIN, 12)
            openBrowser.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            openBrowser.addActionListener {
                val url = com.lagradost.cloudstream3.network.DesktopChromium.currentUrl()
                if (!url.isNullOrBlank()) {
                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)) }
                }
            }
            actions.add(openBrowser)

            val close = JButton("Close")
            close.isContentAreaFilled = false
            close.foreground = Color(0xB3, 0xB3, 0xB8)
            close.font = Font("Segoe UI", Font.PLAIN, 12)
            close.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            close.addActionListener {
                // Cancel any waiting capture without running the Done callback.
                com.lagradost.cloudstream3.network.DesktopChromium.cancelWaiting()
                frame.isVisible = false
                frame.dispose()
                if (bar === frame) bar = null
            }
            actions.add(close)

            val done = JButton(doneLabel.ifBlank { "Done" })
            done.background = Accent
            done.foreground = Color.WHITE
            done.isOpaque = true
            done.isContentAreaFilled = true
            done.border = BorderFactory.createEmptyBorder(6, 16, 6, 16)
            done.font = Font("Segoe UI", Font.BOLD, 12)
            done.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            done.addActionListener {
                onDone.onDone(check?.isSelected == true)
                com.lagradost.cloudstream3.network.DesktopChromium.confirmSolved()
                frame.isVisible = false
                frame.dispose()
                if (bar === frame) bar = null
            }
            actions.add(done)

            panel.add(text, BorderLayout.CENTER)
            panel.add(actions, BorderLayout.EAST)
            frame.contentPane = panel
            frame.size = Dimension(screen.width.coerceAtMost(1100), 68)
            frame.setLocation(screen.x + (screen.width - frame.width) / 2, screen.y + screen.height - 90)
            frame.isVisible = true
            bar = frame
            child?.let { bringToFront(it) }
        }
    }

    override fun clearActionBar() {
        SwingUtilities.invokeLater {
            statusLabel = null
            bar?.isVisible = false
            bar?.dispose()
            bar = null
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            clearActionBar()
        }
    }

    private fun hideWindow(hwnd: WinDef.HWND) {
        runCatching { User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE) }
    }

    private fun bringToFront(hwnd: WinDef.HWND) {
        runCatching {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE)
            User32.INSTANCE.SetForegroundWindow(hwnd)
        }
    }

    private fun hideOtherWindows(pid: Long, keep: WinDef.HWND) {
        val keepPtr = Pointer.nativeValue(keep.pointer)
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val out = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, out)
            if (out.value.toLong() != pid) return@EnumWindows true
            if (Pointer.nativeValue(hwnd.pointer) == keepPtr) return@EnumWindows true
            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                val title = windowTitle(hwnd)
                if (title.contains("DevTools", true) || title.contains("CloudStream", true)) {
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE)
                }
            }
            true
        }, null)
    }

    private fun findLargestWindow(pid: Long): WinDef.HWND? {
        var best: WinDef.HWND? = null
        var bestArea = 0
        val rect = WinDef.RECT()
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val out = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, out)
            if (out.value.toLong() != pid) return@EnumWindows true
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return@EnumWindows true
            val title = windowTitle(hwnd)
            if (title.contains("DevTools", true)) return@EnumWindows true
            if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) return@EnumWindows true
            val area = (rect.right - rect.left) * (rect.bottom - rect.top)
            if (area > bestArea) {
                bestArea = area
                best = hwnd
            }
            true
        }, null)
        return best
    }

    private fun windowTitle(hwnd: WinDef.HWND): String {
        val len = User32.INSTANCE.GetWindowTextLength(hwnd) + 1
        val buf = CharArray(len.coerceAtLeast(2))
        User32.INSTANCE.GetWindowText(hwnd, buf, buf.size)
        return String(buf).trim('\u0000', ' ')
    }
}
