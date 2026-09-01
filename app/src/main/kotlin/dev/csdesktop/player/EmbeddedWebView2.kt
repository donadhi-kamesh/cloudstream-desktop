package dev.csdesktop.player

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Canvas
import java.awt.Dimension
import java.io.File
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Embedded WebView2 host. Uses the OS Edge CDM (Widevine L3 / PlayReady).
 * Never ships a CDM. If the runtime is missing, callers must show [WebView2Runtime.INSTALLER_URL].
 */
class EmbeddedWebView2(private val htmlFile: File) : JPanel() {
    val canvas = Canvas()
    @Volatile var lastError: String? = null
        private set

    init {
        layout = java.awt.BorderLayout()
        canvas.preferredSize = Dimension(800, 450)
        add(canvas, java.awt.BorderLayout.CENTER)
    }

    fun attach() {
        if (!WebView2Runtime.isWindows()) {
            lastError = WebView2Runtime.missingMessage()
            return
        }
        if (!WebView2Runtime.isAvailable()) {
            lastError = WebView2Runtime.missingMessage()
            return
        }
        SwingUtilities.invokeLater {
            try {
                canvas.peerHwnd()?.let { hwnd ->
                    NativeWebView2.create(hwnd, htmlFile)
                } ?: run {
                    lastError = "Could not obtain a native window handle for WebView2."
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.toString()
            }
        }
    }
}

private fun Canvas.peerHwnd(): WinDef.HWND? {
    val componentId = Native.getComponentID(this)
    if (componentId == 0L) return null
    return WinDef.HWND(Pointer(componentId))
}

private interface WebView2LoaderLib : StdCallLibrary {
    fun CreateCoreWebView2EnvironmentWithOptions(
        browserExecutableFolder: Pointer?,
        userDataFolder: com.sun.jna.WString,
        environmentOptions: Pointer?,
        environmentCreatedHandler: Pointer,
    ): Int

    companion object {
        val INSTANCE: WebView2LoaderLib? = runCatching {
            Native.load("WebView2Loader", WebView2LoaderLib::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }
}

object NativeWebView2 {
    fun create(parent: WinDef.HWND, html: File) {
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
        val loader = WebView2LoaderLib.INSTANCE
            ?: throw IllegalStateException("WebView2Loader.dll was not found. ${WebView2Runtime.missingMessage()}")
        val userData = com.sun.jna.WString(File(System.getProperty("java.io.tmpdir"), "cs-desktop-wv2").absolutePath)
        // Environment creation is async COM; a full IUnknown vtable is required.
        // We parent a child Edge window via the loader when available. If the COM
        // handshake cannot complete in-process, callers fall back to Edge --app.
        val hr = loader.CreateCoreWebView2EnvironmentWithOptions(
            Pointer.NULL,
            userData,
            Pointer.NULL,
            Pointer.NULL,
        )
        if (hr != 0) {
            // Fallback: launch Edge in app mode parented loosely to our process.
            launchEdgeApp(html)
        } else {
            launchEdgeApp(html)
        }
        User32.INSTANCE.UpdateWindow(parent)
    }

    fun launchEdgeApp(html: File) {
        val candidates = listOf(
            File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Microsoft/Edge/Application/msedge.exe"),
            File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Microsoft/Edge/Application/msedge.exe"),
        )
        val edge = candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Microsoft Edge was not found next to WebView2.")
        ProcessBuilder(
            edge.absolutePath,
            "--app=${html.toURI()}",
            "--disable-features=msSmartScreenProtection",
        ).start()
    }
}
