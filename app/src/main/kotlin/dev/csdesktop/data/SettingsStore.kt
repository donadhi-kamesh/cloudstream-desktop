package dev.csdesktop.data

import dev.csdesktop.extloader.AppPaths
import dev.csdesktop.extloader.OfficialRepos
import dev.csdesktop.extloader.SavedRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

@Serializable
data class AppSettings(
    val defaultQuality: Int = 1080,
    val resizeMode: String = "fit",
    val skipSeconds: Int = 85,
    val hardwareDecode: Boolean = true,
    val theme: String = "dark",
    val language: String = "en",
    val downloadFolder: String = AppPaths.downloads.absolutePath,
    val openSubtitlesKey: String = "",
    val anilistToken: String = "",
    val malToken: String = "",
    val simklToken: String = "",
    val homePluginFilter: String = "",
    val jsDelivrProxy: Boolean = false,
    val disabledProviders: Set<String> = emptySet(),
)

class SettingsStore {
    private val file = File(AppPaths.prefs, "settings.json")
    private val reposFile = File(AppPaths.prefs, "repositories.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Volatile
    var settings: AppSettings = load()
        private set

    @Volatile
    var repositories: List<SavedRepository> = loadRepos()
        private set

    fun update(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        file.writeText(json.encodeToString(settings))
    }

    fun setRepositories(list: List<SavedRepository>) {
        repositories = list
        reposFile.writeText(json.encodeToString(list))
    }

    fun addRepository(repo: SavedRepository) {
        setRepositories((repositories + repo).distinctBy { it.url })
    }

    fun removeRepository(url: String) {
        setRepositories(repositories.filter { it.url != url })
    }

    private fun load(): AppSettings {
        if (!file.isFile) return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(file.readText()) }.getOrElse { AppSettings() }
    }

    private fun loadRepos(): List<SavedRepository> {
        if (!reposFile.isFile) return emptyList()
        return runCatching { json.decodeFromString<List<SavedRepository>>(reposFile.readText()) }.getOrElse { emptyList() }
    }

    companion object {
        val suggestedRepos get() = OfficialRepos.suggested
    }
}

fun Properties.toMap(): Map<String, String> = stringPropertyNames().associateWith { getProperty(it) }
