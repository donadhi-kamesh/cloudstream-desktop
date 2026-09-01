package dev.csdesktop.log

import com.lagradost.cloudstream3.utils.AppDebug
import dev.csdesktop.extloader.AppPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CloudStream-style logcat: in-memory ring + `%APPDATA%/cs-desktop/logs/logcat.txt`.
 */
object Logcat {
    data class Line(
        val time: String,
        val level: String,
        val tag: String,
        val message: String,
    ) {
        val text: String get() = "$time $level/$tag: $message"
    }

    private const val MAX_LINES = 2500
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()
    private val buf = ArrayDeque<Line>(MAX_LINES)
    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines

    val file: File get() = File(AppPaths.logs, "logcat.txt")

    @JvmStatic
    fun install() {
        AppDebug.isDebug = true
        com.lagradost.api.Log.sink = { level, tag, message -> emit(level, tag, message) }
        captureStderr()
        i("Logcat", "logcat file: ${file.absolutePath}")
    }

    @JvmStatic
    fun v(tag: String, message: String) = emit("V", tag, message)

    @JvmStatic
    fun d(tag: String, message: String) = emit("D", tag, message)

    @JvmStatic
    fun i(tag: String, message: String) = emit("I", tag, message)

    @JvmStatic
    fun w(tag: String, message: String) = emit("W", tag, message)

    @JvmStatic
    fun e(tag: String, message: String) = emit("E", tag, message)

    @JvmStatic
    fun e(tag: String, message: String, error: Throwable) {
        emit("E", tag, message + "\n" + error.stackTraceToString())
    }

    @JvmStatic
    fun emit(level: String, tag: String, message: String) {
        val line = Line(fmt.format(Date()), level, tag ?: "?", message ?: "")
        synchronized(lock) {
            while (buf.size >= MAX_LINES) buf.removeFirst()
            buf.addLast(line)
            _lines.value = buf.toList()
            appendFile(line)
        }
    }

    fun snapshot(): String = synchronized(lock) { buf.joinToString("\n") { it.text } }

    fun clear() {
        synchronized(lock) {
            buf.clear()
            _lines.value = emptyList()
            file.writeText("")
        }
        i("Logcat", "cleared")
    }

    private fun appendFile(line: Line) {
        try {
            val f = file
            f.parentFile?.mkdirs()
            if (f.length() > MAX_FILE_BYTES) {
                val prev = File(f.parentFile, "logcat.prev.txt")
                prev.delete()
                f.renameTo(prev)
            }
            f.appendText(line.text + "\n")
        } catch (_: Throwable) {
        }
    }

    private fun captureStderr() {
        val original = System.err
        val buffer = StringBuilder()
        System.setErr(PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) {
                original.write(b)
                if (b == '\n'.code) {
                    val text = buffer.toString().trimEnd()
                    buffer.setLength(0)
                    if (text.isNotBlank()) emit("E", "stderr", text)
                } else {
                    buffer.append(b.toChar())
                }
            }
        }, true))
    }
}
