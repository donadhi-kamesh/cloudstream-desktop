package dev.csdesktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.csdesktop.extloader.AppPaths
import dev.csdesktop.ui.CsApp
import dev.csdesktop.ui.theme.CsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import okio.Path.Companion.toOkioPath

fun main() {
    dev.csdesktop.log.Logcat.install()
    com.lagradost.cloudstream3.network.DesktopChromium.windowHost = dev.csdesktop.player.InAppBrowser
    Thread.setDefaultUncaughtExceptionHandler { _, error ->
        val fromPlugin = error.stackTrace.any { frame ->
            val c = frame.className
            c.startsWith("com.horis.") || c.startsWith("com.cncverse.") || c.startsWith("com.phisher")
                || c.startsWith("com.lagradost.cloudstream3.plugins")
        }
        dev.csdesktop.log.Logcat.e("Crash", error.stackTraceToString())
        if (fromPlugin && (error is NoSuchMethodError || error is NoSuchFieldError || error is IncompatibleClassChangeError || error is NoClassDefFoundError)) {
            System.err.println("Plugin: ${error::class.simpleName}: ${error.message}")
            return@setDefaultUncaughtExceptionHandler
        }
        val text = buildString {
            append(error::class.simpleName)
            append(": ")
            append(error.message ?: error.toString())
            error.stackTrace.take(12).forEach { append("\n  at ").append(it) }
        }
        javax.swing.JOptionPane.showMessageDialog(
            null,
            text,
            "CloudStream Desktop",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
    }
    System.setProperty("compose.swing.render.on.graphics", "true")
    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
        val appState = remember { AppState() }
        val isFullscreen by appState.isFullscreen.collectAsState()

        LaunchedEffect(isFullscreen) {
            windowState.placement = if (isFullscreen) {
                androidx.compose.ui.window.WindowPlacement.Fullscreen
            } else {
                androidx.compose.ui.window.WindowPlacement.Floating
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream Desktop",
            state = windowState,
        ) {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .crossfade(true)
                    .memoryCache {
                        MemoryCache.Builder()
                            .maxSizeBytes(64L * 1024 * 1024)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(AppPaths.imageCache.toOkioPath())
                            .maxSizeBytes(200L * 1024 * 1024)
                            .build()
                    }
                    .build()
            }
            CsTheme(dark = true) {
                Surface(Modifier.fillMaxSize()) {
                    DisposableEffect(Unit) {
                        appState.boot()
                        onDispose {
                            appState.close()
                            com.lagradost.cloudstream3.network.DesktopChromium.shutdown()
                            dev.csdesktop.player.InAppBrowser.close()
                        }
                    }
                    CsApp(appState)
                }
            }
        }
    }
}

@Suppress("unused")
private val swingDispatcher = Dispatchers.Swing
