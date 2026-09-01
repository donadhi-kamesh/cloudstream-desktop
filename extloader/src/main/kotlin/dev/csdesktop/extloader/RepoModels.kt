package dev.csdesktop.extloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepositoryManifest(
    val name: String = "",
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList(),
)

@Serializable
data class SitePlugin(
    val url: String = "",
    val status: Int = 1,
    val version: Int = 1,
    val apiVersion: Int = 1,
    val name: String = "",
    val internalName: String = "",
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val jarUrl: String? = null,
    val jarHash: String? = null,
    val jarFileSize: Long? = null,
)

@Serializable
data class PluginManifestJson(
    val name: String? = null,
    val pluginClassName: String? = null,
    val requiresResources: Boolean = false,
    val version: Int? = null,
)

@Serializable
data class InstalledPlugin(
    val internalName: String,
    val displayName: String,
    val version: Int,
    val filePath: String,
    val jarPath: String,
    val sourceUrl: String?,
    val repositoryUrl: String?,
    val enabled: Boolean = true,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null,
    val tvTypes: List<String> = emptyList(),
    val iconUrl: String? = null,
    val pluginClassName: String? = null,
)

@Serializable
data class SavedRepository(
    val name: String,
    val url: String,
    val description: String? = null,
)

/** Official empty-by-default suggested repository. Never auto-installed. */
object OfficialRepos {
    const val EXTENSIONS_REPO =
        "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"

    val suggested: List<SavedRepository> = listOf(
        SavedRepository(
            name = "CloudStream official extensions",
            url = EXTENSIONS_REPO,
            description = "YouTube, Twitch, iptv-org, Librivox and other legal sources. Not installed until you add it.",
        )
    )
}
