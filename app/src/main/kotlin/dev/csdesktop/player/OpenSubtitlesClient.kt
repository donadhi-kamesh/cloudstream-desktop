package dev.csdesktop.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OpenSubtitlesClient(
    private val apiKey: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun search(query: String, language: String = "en"): List<SubtitleHit> {
        if (apiKey.isBlank()) return emptyList()
        val url = "https://api.opensubtitles.com/api/v1/subtitles?query=${enc(query)}&languages=$language"
        val request = Request.Builder()
            .url(url)
            .header("Api-Key", apiKey)
            .header("User-Agent", "cs-desktop v1.0")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val parsed = json.decodeFromString<OsResponse>(resp.body?.string().orEmpty())
            return parsed.data.map {
                SubtitleHit(
                    id = it.id,
                    language = it.attributes.language.orEmpty(),
                    fileName = it.attributes.files.firstOrNull()?.fileName ?: it.attributes.release.orEmpty(),
                    fileId = it.attributes.files.firstOrNull()?.fileId,
                )
            }
        }
    }

    fun downloadUrl(fileId: Long): String? {
        if (apiKey.isBlank() || fileId <= 0) return null
        val request = Request.Builder()
            .url("https://api.opensubtitles.com/api/v1/download")
            .header("Api-Key", apiKey)
            .header("User-Agent", "cs-desktop v1.0")
            .header("Content-Type", "application/json")
            .post("""{"file_id":$fileId}""".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val parsed = json.decodeFromString<OsDownload>(resp.body?.string().orEmpty())
            return parsed.link
        }
    }

    @Serializable private data class OsResponse(val data: List<OsItem> = emptyList())
    @Serializable private data class OsItem(val id: String = "", val attributes: OsAttrs = OsAttrs())
    @Serializable private data class OsAttrs(
        val language: String? = null,
        val release: String? = null,
        val files: List<OsFile> = emptyList(),
    )
    @Serializable private data class OsFile(
        val file_id: Long? = null,
        val file_name: String? = null,
    ) {
        val fileId get() = file_id
        val fileName get() = file_name
    }
    @Serializable private data class OsDownload(val link: String? = null)

    data class SubtitleHit(
        val id: String,
        val language: String,
        val fileName: String,
        val fileId: Long?,
    )

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
    }
}
