package dev.csdesktop.download

import dev.csdesktop.data.LibraryDb
import dev.csdesktop.extloader.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class DownloadStatus { Queued, Running, Paused, Done, Error }

data class DownloadItem(
    val id: Long,
    val title: String,
    val url: String,
    val referer: String?,
    val dest: File,
    var status: DownloadStatus,
    var bytesDone: Long,
    var bytesTotal: Long,
    var error: String? = null,
)

class DownloadManager(
    private val db: LibraryDb,
    private val folder: () -> File,
) {
    private val http = OkHttpClient()
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val paused = ConcurrentHashMap<Long, AtomicBoolean>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun list(): List<DownloadItem> {
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT * FROM downloads ORDER BY id DESC").use { rs ->
                val out = mutableListOf<DownloadItem>()
                while (rs.next()) {
                    out += DownloadItem(
                        id = rs.getLong("id"),
                        title = rs.getString("title"),
                        url = rs.getString("url"),
                        referer = rs.getString("referer"),
                        dest = File(rs.getString("dest")),
                        status = runCatching { DownloadStatus.valueOf(rs.getString("status")) }.getOrDefault(DownloadStatus.Queued),
                        bytesDone = rs.getLong("bytes_done"),
                        bytesTotal = rs.getLong("bytes_total"),
                        error = rs.getString("error"),
                    )
                }
                return out
            }
        }
    }

    fun enqueue(title: String, url: String, referer: String?, headers: Map<String, String>): DownloadItem {
        folder().mkdirs()
        val dest = File(folder(), sanitize(title) + extensionFor(url))
        db.connection.prepareStatement(
            "INSERT INTO downloads(title,url,referer,headers_json,dest,status,bytes_done,bytes_total) VALUES(?,?,?,?,?,?,0,0)"
        ).use { ps ->
            ps.setString(1, title)
            ps.setString(2, url)
            ps.setString(3, referer)
            ps.setString(4, headers.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            ps.setString(5, dest.absolutePath)
            ps.setString(6, DownloadStatus.Queued.name)
            ps.executeUpdate()
        }
        val item = list().first()
        start(item.id)
        return item
    }

    fun start(id: Long) {
        val item = list().firstOrNull { it.id == id } ?: return
        paused[id] = AtomicBoolean(false)
        update(id, DownloadStatus.Running)
        jobs[id] = scope.launch {
            runCatching { download(item) }
                .onFailure { err ->
                    update(id, DownloadStatus.Error, error = err.message)
                }
        }
    }

    fun pause(id: Long) {
        paused[id]?.set(true)
        jobs[id]?.cancel()
        update(id, DownloadStatus.Paused)
    }

    fun resume(id: Long) = start(id)

    private fun download(item: DownloadItem) {
        val ffmpeg = findBinary("ffmpeg")
        val isHls = item.url.contains(".m3u8") || item.url.contains("m3u8")
        if (isHls && ffmpeg != null) {
            val pb = ProcessBuilder(
                ffmpeg.absolutePath, "-y",
                "-headers", item.referer?.let { "Referer: $it\r\n" } ?: "",
                "-i", item.url,
                "-c", "copy",
                item.dest.absolutePath,
            )
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.inputStream.copyTo(System.out)
            val code = p.waitFor()
            if (code == 0) update(item.id, DownloadStatus.Done, item.dest.length(), item.dest.length())
            else update(item.id, DownloadStatus.Error, error = "ffmpeg exit $code")
            return
        }
        val builder = Request.Builder().url(item.url)
        item.referer?.let { builder.header("Referer", it) }
        if (item.dest.exists() && item.dest.length() > 0) {
            builder.header("Range", "bytes=${item.dest.length()}-")
        }
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                update(item.id, DownloadStatus.Error, error = "HTTP ${resp.code}")
                return
            }
            val total = resp.body?.contentLength() ?: -1
            val start = if (resp.code == 206) item.dest.length() else 0L
            val out = java.io.RandomAccessFile(item.dest, "rw")
            if (resp.code != 206) out.setLength(0)
            out.seek(start)
            resp.body?.byteStream()?.use { input ->
                val buf = ByteArray(64 * 1024)
                var done = start
                while (true) {
                    if (paused[item.id]?.get() == true) break
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    update(item.id, DownloadStatus.Running, done, if (total > 0) start + total else 0)
                }
                out.close()
                if (paused[item.id]?.get() == true) update(item.id, DownloadStatus.Paused, done, if (total > 0) start + total else 0)
                else update(item.id, DownloadStatus.Done, done, done)
            }
        }
    }

    private fun update(id: Long, status: DownloadStatus, done: Long? = null, total: Long? = null, error: String? = null) {
        db.connection.prepareStatement(
            "UPDATE downloads SET status=?, bytes_done=COALESCE(?, bytes_done), bytes_total=COALESCE(?, bytes_total), error=? WHERE id=?"
        ).use { ps ->
            ps.setString(1, status.name)
            if (done != null) ps.setLong(2, done) else ps.setObject(2, null)
            if (total != null) ps.setLong(3, total) else ps.setObject(3, null)
            ps.setString(4, error)
            ps.setLong(5, id)
            ps.executeUpdate()
        }
    }

    companion object {
        fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "download" }
        fun extensionFor(url: String): String {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".mp4") -> ".mp4"
                path.endsWith(".mkv") -> ".mkv"
                path.endsWith(".webm") -> ".webm"
                path.endsWith(".ts") -> ".ts"
                path.endsWith(".m3u8") -> ".mp4"
                path.endsWith(".mpd") -> ".mp4"
                else -> ".bin"
            }
        }
        fun findBinary(name: String): File? {
            val exe = if (System.getProperty("os.name").lowercase().contains("win")) "$name.exe" else name
            val path = System.getenv("PATH") ?: return null
            return path.split(File.pathSeparator).map { File(it, exe) }.firstOrNull { it.isFile }
        }
    }
}
