package dev.csdesktop.extloader

import java.io.File

/**
 * Resolves CloudStream Desktop data directories.
 *
 * Windows: `%APPDATA%/cs-desktop`
 * macOS: `~/Library/Application Support/cs-desktop`
 * Linux: `$XDG_DATA_HOME/cs-desktop` or `~/.local/share/cs-desktop`
 */
object AppPaths {
    val root: File by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val dir = when {
            os.contains("win") -> {
                val appdata = System.getenv("APPDATA") ?: (System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming")
                File(appdata, "cs-desktop")
            }
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/cs-desktop")
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                if (!xdg.isNullOrBlank()) File(xdg, "cs-desktop")
                else File(System.getProperty("user.home"), ".local/share/cs-desktop")
            }
        }
        dir.mkdirs()
        dir
    }

    val plugins: File get() = dir("plugins")
    val pluginJars: File get() = dir("plugin-jars")
    val downloads: File get() = dir("downloads")
    val cache: File get() = dir("cache")
    val imageCache: File get() = dir("cache/images")
    val mpv: File get() = dir("mpv")
    val tools: File get() = dir("tools")
    val db: File get() = File(root, "library.db")
    val prefs: File get() = dir("prefs")
    val backups: File get() = dir("backups")
    val logs: File get() = dir("logs")

    private fun dir(name: String): File = File(root, name).also { it.mkdirs() }
}
