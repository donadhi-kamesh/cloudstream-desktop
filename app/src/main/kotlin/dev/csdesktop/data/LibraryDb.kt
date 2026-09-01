package dev.csdesktop.data

import dev.csdesktop.extloader.AppPaths
import java.sql.Connection
import java.sql.DriverManager

class LibraryDb {
    val connection: Connection

    init {
        Class.forName("org.sqlite.JDBC")
        AppPaths.root.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${AppPaths.db.absolutePath}")
        connection.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            fun ensureColumn(table: String, column: String, spec: String) {
                st.executeQuery("PRAGMA table_info($table)").use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name").equals(column, ignoreCase = true)) return
                    }
                }
                st.execute("ALTER TABLE $table ADD COLUMN $column $spec")
            }
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS resume (
                    key TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    poster TEXT,
                    provider TEXT,
                    url TEXT,
                    position_ms INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    is_live INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS bookmarks (
                    key TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    poster TEXT,
                    provider TEXT,
                    url TEXT,
                    type TEXT,
                    plot TEXT,
                    added_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    key TEXT NOT NULL,
                    title TEXT NOT NULL,
                    poster TEXT,
                    provider TEXT,
                    url TEXT,
                    watched_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS downloads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    referer TEXT,
                    headers_json TEXT,
                    dest TEXT NOT NULL,
                    status TEXT NOT NULL,
                    bytes_done INTEGER NOT NULL DEFAULT 0,
                    bytes_total INTEGER NOT NULL DEFAULT 0,
                    error TEXT
                )
                """.trimIndent()
            )
            ensureColumn("resume", "is_live", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn("resume", "data_url", "TEXT")
            ensureColumn("resume", "audio_id", "INTEGER")
            ensureColumn("resume", "sub_id", "INTEGER")
            ensureColumn("history", "position_ms", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn("history", "duration_ms", "INTEGER NOT NULL DEFAULT 0")
            runCatching {
                st.execute("DELETE FROM history WHERE id NOT IN (SELECT MAX(id) FROM history GROUP BY key)")
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS history_key ON history(key)")
            }
        }
    }

    fun close() = runCatching { connection.close() }
}

data class ResumeEntry(
    val key: String,
    val title: String,
    val poster: String?,
    val provider: String?,
    val url: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isLive: Boolean,
    val updatedAt: Long,
    val dataUrl: String? = null,
    val audioId: Int? = null,
    val subId: Int? = null,
)

data class BookmarkEntry(
    val key: String,
    val title: String,
    val poster: String?,
    val provider: String?,
    val url: String?,
    val type: String?,
    val plot: String?,
    val addedAt: Long,
)

data class HistoryEntry(
    val id: Long,
    val key: String,
    val title: String,
    val poster: String?,
    val provider: String?,
    val url: String?,
    val watchedAt: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

class LibraryRepository(private val db: LibraryDb) {
    private val lock = Any()

    fun saveResume(entry: ResumeEntry) = synchronized(lock) {
        db.connection.prepareStatement(
            """
            INSERT INTO resume(key,title,poster,provider,url,position_ms,duration_ms,is_live,updated_at,data_url,audio_id,sub_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(key) DO UPDATE SET
                title=excluded.title, poster=excluded.poster, provider=excluded.provider,
                url=excluded.url, position_ms=excluded.position_ms, duration_ms=excluded.duration_ms,
                is_live=excluded.is_live, updated_at=excluded.updated_at, data_url=excluded.data_url,
                audio_id=excluded.audio_id, sub_id=excluded.sub_id
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, entry.key)
            ps.setString(2, entry.title)
            ps.setString(3, entry.poster)
            ps.setString(4, entry.provider)
            ps.setString(5, entry.url)
            ps.setLong(6, entry.positionMs)
            ps.setLong(7, entry.durationMs)
            ps.setInt(8, if (entry.isLive) 1 else 0)
            ps.setLong(9, entry.updatedAt)
            ps.setString(10, entry.dataUrl)
            if (entry.audioId != null) ps.setInt(11, entry.audioId) else ps.setNull(11, java.sql.Types.INTEGER)
            if (entry.subId != null) ps.setInt(12, entry.subId) else ps.setNull(12, java.sql.Types.INTEGER)
            ps.executeUpdate()
        }
    }

    fun resume(key: String): ResumeEntry? = synchronized(lock) {
        db.connection.prepareStatement("SELECT * FROM resume WHERE key=?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.toResume()
            }
        }
    }

    fun continueWatching(limit: Int = 24): List<ResumeEntry> = synchronized(lock) {
        db.connection.prepareStatement(
            "SELECT * FROM resume WHERE is_live=0 AND position_ms>500 AND (duration_ms=0 OR duration_ms<15000 OR position_ms < duration_ms-2000) ORDER BY updated_at DESC LIMIT ?"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<ResumeEntry>()
                while (rs.next()) out += rs.toResume()
                return out
            }
        }
    }

    fun addBookmark(entry: BookmarkEntry) {
        db.connection.prepareStatement(
            """
            INSERT INTO bookmarks(key,title,poster,provider,url,type,plot,added_at)
            VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(key) DO UPDATE SET title=excluded.title, poster=excluded.poster
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, entry.key)
            ps.setString(2, entry.title)
            ps.setString(3, entry.poster)
            ps.setString(4, entry.provider)
            ps.setString(5, entry.url)
            ps.setString(6, entry.type)
            ps.setString(7, entry.plot)
            ps.setLong(8, entry.addedAt)
            ps.executeUpdate()
        }
    }

    fun removeBookmark(key: String) {
        db.connection.prepareStatement("DELETE FROM bookmarks WHERE key=?").use {
            it.setString(1, key)
            it.executeUpdate()
        }
    }

    fun isBookmarked(key: String): Boolean {
        db.connection.prepareStatement("SELECT 1 FROM bookmarks WHERE key=?").use {
            it.setString(1, key)
            it.executeQuery().use { rs -> return rs.next() }
        }
    }

    fun bookmarks(): List<BookmarkEntry> {
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT * FROM bookmarks ORDER BY added_at DESC").use { rs ->
                val out = mutableListOf<BookmarkEntry>()
                while (rs.next()) {
                    out += BookmarkEntry(
                        key = rs.getString("key"),
                        title = rs.getString("title"),
                        poster = rs.getString("poster"),
                        provider = rs.getString("provider"),
                        url = rs.getString("url"),
                        type = rs.getString("type"),
                        plot = rs.getString("plot"),
                        addedAt = rs.getLong("added_at"),
                    )
                }
                return out
            }
        }
    }

    fun addHistory(entry: HistoryEntry) {
        db.connection.prepareStatement(
            """
            INSERT INTO history(key,title,poster,provider,url,watched_at,position_ms,duration_ms)
            VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(key) DO UPDATE SET
                title=excluded.title, poster=excluded.poster, provider=excluded.provider,
                url=excluded.url, watched_at=excluded.watched_at,
                position_ms=excluded.position_ms, duration_ms=excluded.duration_ms
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, entry.key)
            ps.setString(2, entry.title)
            ps.setString(3, entry.poster)
            ps.setString(4, entry.provider)
            ps.setString(5, entry.url)
            ps.setLong(6, entry.watchedAt)
            ps.setLong(7, entry.positionMs)
            ps.setLong(8, entry.durationMs)
            ps.executeUpdate()
        }
    }

    fun history(limit: Int = 100): List<HistoryEntry> {
        db.connection.prepareStatement("SELECT * FROM history ORDER BY watched_at DESC LIMIT ?").use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<HistoryEntry>()
                while (rs.next()) {
                    out += HistoryEntry(
                        id = rs.getLong("id"),
                        key = rs.getString("key"),
                        title = rs.getString("title"),
                        poster = rs.getString("poster"),
                        provider = rs.getString("provider"),
                        url = rs.getString("url"),
                        watchedAt = rs.getLong("watched_at"),
                        positionMs = runCatching { rs.getLong("position_ms") }.getOrDefault(0L),
                        durationMs = runCatching { rs.getLong("duration_ms") }.getOrDefault(0L),
                    )
                }
                return out
            }
        }
    }

    private fun java.sql.ResultSet.toResume() = ResumeEntry(
        key = getString("key"),
        title = getString("title"),
        poster = getString("poster"),
        provider = getString("provider"),
        url = getString("url"),
        positionMs = getLong("position_ms"),
        durationMs = getLong("duration_ms"),
        isLive = getInt("is_live") != 0,
        updatedAt = getLong("updated_at"),
        dataUrl = runCatching { getString("data_url") }.getOrNull(),
        audioId = runCatching { getInt("audio_id").takeIf { !wasNull() } }.getOrNull(),
        subId = runCatching { getInt("sub_id").takeIf { !wasNull() } }.getOrNull(),
    )
}
