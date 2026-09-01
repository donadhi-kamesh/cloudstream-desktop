package dev.csdesktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.csdesktop.extloader.AppPaths
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.net.URI

interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_set_option_string(handle: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): String?
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    fun mpv_wakeup(handle: Pointer)
    fun mpv_free(data: Pointer?)
}

class MpvRuntime {
    @Volatile
    var library: LibMpv? = null
        private set

    @Volatile
    var status: String = "idle"
        private set

    fun ensureAvailable(onProgress: (String) -> Unit = {}): LibMpv {
        library?.let { return it }
        val existing = findLib()
        if (existing != null) {
            library = load(existing)
            status = "ready:${existing.absolutePath}"
            return library!!
        }
        onProgress("Downloading libmpv (LGPL, not bundled in git)…")
        val downloaded = downloadOfficial()
        library = load(downloaded)
        status = "ready:${downloaded.absolutePath}"
        return library!!
    }

    private fun load(file: File): LibMpv {
        Native.setProtected(true)
        System.setProperty("jna.library.path", file.parentFile.absolutePath)
        val logical = if (isWindows) {
            file.nameWithoutExtension
        } else {
            "mpv"
        }
        return Native.load(logical, LibMpv::class.java)
    }

    private fun findLib(): File? {
        findLibIn(AppPaths.mpv)?.let { return it }
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparator)) {
            findLibIn(File(dir))?.let { return it }
            val mpvBin = File(dir, if (isWindows) "mpv.exe" else "mpv")
            if (mpvBin.isFile) {
                findLibIn(mpvBin.parentFile)?.let { return it }
            }
        }
        return null
    }

    private fun findLibIn(dir: File?): File? {
        if (dir == null || !dir.isDirectory) return null
        val names = if (isWindows) {
            listOf("libmpv-2.dll", "mpv-2.dll", "libmpv.dll", "mpv.dll")
        } else {
            listOf("libmpv.so", "libmpv.so.2", "libmpv.so.1", "libmpv.dylib")
        }
        names.map { File(dir, it) }.firstOrNull { it.isFile }?.let { return it }
        return dir.listFiles()?.firstOrNull { f ->
            names.any { f.name.equals(it, ignoreCase = true) }
        }
    }

    private fun downloadOfficial(): File {
        AppPaths.mpv.mkdirs()
        if (!isWindows) {
            throw IllegalStateException(
                "libmpv was not found on PATH. Install mpv with your package manager (provides libmpv)."
            )
        }
        val archive = File(AppPaths.mpv, "mpv-dev.7z")
        URI(MPV_DEV_URL).toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        extract7z(archive, AppPaths.mpv)
        return findLibIn(AppPaths.mpv)
            ?: AppPaths.mpv.walkTopDown().firstOrNull {
                it.name.equals("libmpv-2.dll", true) || it.name.equals("libmpv.dll", true)
            }
            ?: throw IllegalStateException("Downloaded mpv archive but could not find libmpv-2.dll")
    }

    private fun extract7z(archive: File, dest: File) {
        SevenZFile.builder().setFile(archive).get().use { seven ->
            var entry = seven.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = File(entry.name).name
                    if (name.endsWith(".dll", true) || name.endsWith(".exe", true)) {
                        val out = File(dest, name)
                        out.outputStream().use { seven.getInputStream(entry).copyTo(it) }
                    }
                }
                entry = seven.nextEntry
            }
        }
    }

    companion object {
        val isWindows: Boolean
            get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")

        // shinchiro mpv-dev (LGPL). Fetched at first run; not stored in git.
        const val MPV_DEV_URL =
            "https://github.com/shinchiro/mpv-winbuild-cmake/releases/latest/download/mpv-dev-x86_64-v3.7z"

        fun mpvBinary(): File? {
            val names = if (isWindows) listOf("mpv.exe") else listOf("mpv")
            names.map { File(AppPaths.mpv, it) }.firstOrNull { it.isFile }?.let { return it }
            val path = System.getenv("PATH") ?: return null
            for (dir in path.split(File.pathSeparator)) {
                for (name in names) {
                    val f = File(dir, name)
                    if (f.isFile) return f
                }
            }
            return null
        }
    }
}

class MpvSession(
    private val lib: LibMpv,
    hwnd: Long?,
    hardwareDecode: Boolean,
) {
    val handle: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    private val lock = Any()
    @Volatile private var dead = false

    init {
        lib.mpv_set_option_string(handle, "hwdec", if (hardwareDecode) "auto" else "no")
        lib.mpv_set_option_string(handle, "keep-open", "yes")
        lib.mpv_set_option_string(handle, "osc", "no")
        lib.mpv_set_option_string(handle, "osd-level", "0")
        lib.mpv_set_option_string(handle, "input-default-bindings", "no")
        lib.mpv_set_option_string(handle, "input-vo-keyboard", "no")
        lib.mpv_set_option_string(handle, "force-window", "no")
        lib.mpv_set_option_string(handle, "vo", "gpu")
        lib.mpv_set_option_string(handle, "keepaspect", "yes")
        lib.mpv_set_option_string(handle, "video-align-x", "0")
        lib.mpv_set_option_string(handle, "video-align-y", "0")
        lib.mpv_set_option_string(handle, "force-seekable", "yes")
        lib.mpv_set_option_string(handle, "hr-seek", "yes")
        lib.mpv_set_option_string(handle, "ytdl", "no")
        lib.mpv_set_option_string(handle, "network-timeout", "30")
        lib.mpv_set_option_string(handle, "demuxer-readahead-secs", "60")
        lib.mpv_set_option_string(handle, "demuxer-max-bytes", "33554432")
        lib.mpv_set_option_string(handle, "demuxer-max-back-bytes", "16777216")
        lib.mpv_set_option_string(handle, "demuxer-lavf-o", "protocol_whitelist=file,http,https,tls,rtp,tcp,udp,crypto,data")
        if (hwnd != null && hwnd != 0L) {
            lib.mpv_set_option_string(handle, "wid", hwnd.toString())
        }
        val err = lib.mpv_initialize(handle)
        if (err < 0) error("mpv_initialize failed: $err")
    }

    val isAlive: Boolean get() = !dead

    private inline fun <T> safe(default: T, block: () -> T): T {
        if (dead) return default
        return synchronized(lock) {
            if (dead) default
            else try {
                block()
            } catch (_: Throwable) {
                default
            }
        }
    }

    fun load(url: String, extra: List<String> = emptyList(), headers: Map<String, String> = emptyMap()) {
        val ok = safe(false) {
            extra.forEach { arg ->
                val trimmed = arg.removePrefix("--")
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    lib.mpv_set_property_string(handle, trimmed.substring(0, eq), trimmed.substring(eq + 1))
                }
            }
            val ua = headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
                ?: StreamProxy.DEFAULT_UA
            lib.mpv_set_property_string(handle, "user-agent", ua)
            val referer = headers.entries.firstOrNull { it.key.equals("referer", true) }?.value
            if (!referer.isNullOrBlank()) {
                lib.mpv_set_property_string(handle, "referrer", referer)
            }
            val fields = headers.filterKeys { !it.equals("user-agent", true) }
                .entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
            if (fields.isNotBlank()) {
                lib.mpv_set_property_string(handle, "http-header-fields", fields)
            }
            val err = lib.mpv_command(handle, arrayOf("loadfile", url, "replace", null))
            if (err < 0) error("mpv loadfile failed ($err) for $url")
            com.lagradost.api.Log.i("mpv", "loadfile $url")
            true
        }
        if (!ok && !dead) error("mpv loadfile failed for $url")
    }

    fun command(vararg args: String) {
        safe(0) { lib.mpv_command(handle, (args.toList() + null).toTypedArray()) }
    }

    fun togglePause() = command("cycle", "pause")
    fun seekRelative(seconds: Double) = command("seek", seconds.toString())
    fun seekAbsolute(seconds: Double) = command("seek", seconds.toString(), "absolute+exact")
    fun setVolume(volume: Int) = set("volume", volume.coerceIn(0, 130).toString())
    fun mute(muted: Boolean) = set("mute", if (muted) "yes" else "no")
    fun toggleMute() = command("cycle", "mute")
    fun setSpeed(speed: Double) = set("speed", speed.toString())
    fun cycleSub() = command("cycle", "sub")
    fun cycleAudio() = command("cycle", "audio")
    fun setSid(id: Int) = set("sid", if (id <= 0) "no" else id.toString())
    fun setAid(id: Int) = set("aid", id.toString())
    fun addSubFile(path: String) = command("sub-add", path)
    fun tracks(): List<MpvTrack> {
        val raw = getString("track-list") ?: return emptyList()
        return runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
            arr.mapNotNull { el ->
                val o = el.jsonObject
                MpvTrack(
                    id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                    type = o["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull,
                    lang = o["lang"]?.jsonPrimitive?.contentOrNull,
                    selected = o["selected"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
        }.getOrDefault(emptyList())
    }
    fun setWid(hwnd: Long) = set("wid", hwnd.toString())
    fun setVid(id: Int) = set("vid", if (id <= 0) "no" else id.toString())
    fun setAspect(mode: String) {
        when (mode) {
            "fill" -> {
                set("keepaspect", "no")
                set("panscan", "1.0")
                set("video-aspect-override", "-1")
            }
            "zoom" -> {
                set("keepaspect", "yes")
                set("panscan", "0.0")
                set("video-zoom", "0.15")
                set("video-aspect-override", "-1")
            }
            "16:9" -> {
                set("keepaspect", "yes")
                set("panscan", "0.0")
                set("video-zoom", "0")
                set("video-aspect-override", "16:9")
            }
            "4:3" -> {
                set("keepaspect", "yes")
                set("panscan", "0.0")
                set("video-zoom", "0")
                set("video-aspect-override", "4:3")
            }
            else -> {
                set("keepaspect", "yes")
                set("panscan", "0.0")
                set("video-zoom", "0")
                set("video-aspect-override", "-1")
            }
        }
    }
    fun fullscreen(full: Boolean) = set("fullscreen", if (full) "yes" else "no")
    fun stop() = command("stop")

    fun getString(name: String): String? = safe(null) { lib.mpv_get_property_string(handle, name) }
    fun positionSeconds(): Double {
        val a = getString("time-pos")?.toDoubleOrNull()
        if (a != null && a.isFinite() && a >= 0.0) return a
        val b = getString("playback-time")?.toDoubleOrNull()
        if (b != null && b.isFinite() && b >= 0.0) return b
        val dur = getString("duration")?.toDoubleOrNull()
        val pct = getString("percent-pos")?.toDoubleOrNull()
        if (dur != null && dur.isFinite() && dur > 0.0 && pct != null && pct.isFinite() && pct > 0.0) {
            return dur * pct / 100.0
        }
        return 0.0
    }
    fun durationSeconds(): Double {
        val a = getString("duration")?.toDoubleOrNull()
        if (a != null && a.isFinite() && a > 0.0) return a
        val b = getString("time-remaining")?.toDoubleOrNull()
        val pos = positionSeconds()
        if (b != null && b.isFinite() && b > 0.0) return pos + b
        return 0.0
    }
    fun paused(): Boolean = getString("pause") == "yes"
    fun muted(): Boolean = getString("mute") == "yes"
    fun buffering(): Boolean = getString("paused-for-cache") == "yes"
    fun eofReached(): Boolean = getString("eof-reached") == "yes"
    fun volume(): Int = getString("volume")?.toDoubleOrNull()?.toInt() ?: 100
    fun cacheSeconds(): Double =
        getString("demuxer-cache-time")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

    private fun set(name: String, value: String) {
        safe(Unit) { lib.mpv_set_property_string(handle, name, value) }
    }

    fun destroy() {
        synchronized(lock) {
            if (dead) return
            dead = true
            runCatching { lib.mpv_terminate_destroy(handle) }
        }
    }
}

data class MpvTrack(
    val id: Int,
    val type: String,
    val title: String?,
    val lang: String?,
    val selected: Boolean,
) {
    fun label(): String = buildString {
        append(title?.takeIf { it.isNotBlank() } ?: type.replaceFirstChar { it.uppercase() })
        if (!lang.isNullOrBlank()) append(" · $lang")
        append(" (#$id)")
    }
}
