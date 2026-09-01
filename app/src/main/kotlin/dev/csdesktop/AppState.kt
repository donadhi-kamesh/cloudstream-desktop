package dev.csdesktop

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.api.Log
import dev.csdesktop.log.Logcat
import dev.csdesktop.data.AppSettings
import dev.csdesktop.data.BackupRestore
import dev.csdesktop.data.BookmarkEntry
import dev.csdesktop.data.HistoryEntry
import dev.csdesktop.data.LibraryDb
import dev.csdesktop.data.LibraryRepository
import dev.csdesktop.data.ResumeEntry
import dev.csdesktop.data.SettingsStore
import dev.csdesktop.download.DownloadManager
import dev.csdesktop.extloader.Cs3Loader
import dev.csdesktop.extloader.InstalledPlugin
import dev.csdesktop.extloader.OfficialRepos
import dev.csdesktop.extloader.RepoClient
import dev.csdesktop.extloader.SavedRepository
import dev.csdesktop.extloader.SitePlugin
import dev.csdesktop.player.ClearKeyProxy
import dev.csdesktop.player.StreamProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

sealed class Destination {
    data object Home : Destination()
    data object Search : Destination()
    data object Live : Destination()
    data object Library : Destination()
    data object Downloads : Destination()
    data object Extensions : Destination()
    data object Settings : Destination()
    data object Logcat : Destination()
    data class Result(val provider: String, val url: String, val title: String) : Destination()
    data class Player(
        val title: String,
        val provider: String,
        val pageUrl: String,
        val dataUrl: String,
        val poster: String?,
        val type: TvType?,
        val startFromBeginning: Boolean = false,
    ) : Destination()
}

data class HomeRow(val provider: String, val list: HomePageList)
data class SearchHit(val provider: String, val item: SearchResponse)
data class PlayableLink(val link: ExtractorLink, val subtitles: List<SubtitleFile>)

class AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = LibraryDb()
    val library = LibraryRepository(db)
    val settingsStore = SettingsStore()
    val loader = Cs3Loader()
    val repoClient = RepoClient()
    val streamProxy = StreamProxy()
    val clearKeyProxy = ClearKeyProxy()
    val downloads = DownloadManager(db) { File(settingsStore.settings.downloadFolder) }

    private val _nav = MutableStateFlow<Destination>(Destination.Home)
    val nav: StateFlow<Destination> = _nav

    private val _home = MutableStateFlow<List<HomeRow>>(emptyList())
    val home: StateFlow<List<HomeRow>> = _home
    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading
    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading
    private val _typeFilter = MutableStateFlow<TvType?>(null)
    val typeFilter: StateFlow<TvType?> = _typeFilter
    private val _providerFilter = MutableStateFlow<String?>(null)
    val providerFilter: StateFlow<String?> = _providerFilter

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    private val _load = MutableStateFlow<LoadResponse?>(null)
    val load: StateFlow<LoadResponse?> = _load
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError
    private val _loadLoading = MutableStateFlow(false)
    val loadLoading: StateFlow<Boolean> = _loadLoading

    private val _links = MutableStateFlow<List<ExtractorLink>>(emptyList())
    val links: StateFlow<List<ExtractorLink>> = _links
    private val _subs = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val subs: StateFlow<List<SubtitleFile>> = _subs

    private val _live = MutableStateFlow<List<SearchHit>>(emptyList())
    val live: StateFlow<List<SearchHit>> = _live
    private val _liveLoading = MutableStateFlow(false)
    val liveLoading: StateFlow<Boolean> = _liveLoading

    private val _plugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins
    private val _repositories = MutableStateFlow<List<SavedRepository>>(settingsStore.repositories)
    val repositories: StateFlow<List<SavedRepository>> = _repositories
    private val _catalogByRepo = MutableStateFlow<Map<String, List<SitePlugin>>>(emptyMap())
    val catalogByRepo: StateFlow<Map<String, List<SitePlugin>>> = _catalogByRepo
    private val _repoPlugins = MutableStateFlow<List<Pair<SavedRepository, SitePlugin>>>(emptyList())
    val repoPlugins: StateFlow<List<Pair<SavedRepository, SitePlugin>>> = _repoPlugins
    private val _extStatus = MutableStateFlow<String?>(null)
    val extStatus: StateFlow<String?> = _extStatus
    private val _installing = MutableStateFlow<String?>(null)
    val installing: StateFlow<String?> = _installing

    /**
     * Provider names the user switched off. Observable because toggling a sub-provider
     * changes nothing about the installed-plugin list, so the Extensions screen had no
     * signal to recompose and its switches appeared stuck.
     */
    private val _disabledProviders = MutableStateFlow(settingsStore.settings.disabledProviders)
    val disabledProviders: StateFlow<Set<String>> = _disabledProviders

    /** Bumped whenever a plugin is loaded or unloaded, so provider sub-lists refresh. */
    private val _pluginGeneration = MutableStateFlow(0)
    val pluginGeneration: StateFlow<Int> = _pluginGeneration

    private val _playError = MutableStateFlow<String?>(null)
    val playError: StateFlow<String?> = _playError
    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack

    private val _continueWatching = MutableStateFlow<List<ResumeEntry>>(emptyList())
    val continueWatching: StateFlow<List<ResumeEntry>> = _continueWatching
    val bookmarks get() = library.bookmarks()
    val history get() = library.history()

    fun boot() {
        runCatching {
            val req = com.lagradost.cloudstream3.app
            for (field in req.javaClass.declaredFields) {
                field.isAccessible = true
                val value = field.get(req)
                if (value is okhttp3.OkHttpClient) {
                    field.set(req, value.newBuilder().cookieJar(com.lagradost.cloudstream3.network.DesktopCookieJar).build())
                }
            }
        }
        loader.loadAllInstalled()
        Log.i("Boot", "plugins=${loader.loadedPlugins().size} providers=${enabledProviders().size} extractors=${extractorApis.size} logcat=${Logcat.file.absolutePath}")
        refreshPlugins()
        refreshContinueWatching()
        refreshHome()
    }

    fun close() {
        streamProxy.stop()
        clearKeyProxy.stop()
        db.close()
    }

    fun go(dest: Destination) {
        _nav.value = dest
        when (dest) {
            Destination.Home -> {
                refreshContinueWatching()
                refreshHome()
            }
            Destination.Extensions -> refreshRepoCatalog()
            else -> {}
        }
    }

    fun allEnabledProviders(): List<MainAPI> {
        val disabled = settingsStore.settings.disabledProviders
        return loader.enabledProviders().ifEmpty { APIHolder.apis.toList() }
            .filter { it.name !in disabled }
    }

    fun enabledProviders(): List<MainAPI> {
        val filter = settingsStore.settings.homePluginFilter
        val all = allEnabledProviders()
        if (filter.isBlank()) return all
        val plug = loader.installed.firstOrNull {
            it.displayName.equals(filter, true) || it.internalName.equals(filter, true)
        }
        if (plug != null) {
            val fromPlugin = loader.loadedPlugins().firstOrNull { it.info.filePath == plug.filePath }?.providers
            if (!fromPlugin.isNullOrEmpty()) return fromPlugin
            return all.filter { it.sourcePlugin == plug.filePath }
        }
        return all.filter { it.sourcePlugin?.contains(filter, true) == true || it.name.equals(filter, true) }
    }

    fun setProviderFilter(provider: String?) {
        _providerFilter.value = provider
        if (_searchQuery.value.isNotBlank()) search(_searchQuery.value, provider)
    }

    fun search(query: String, provider: String? = _providerFilter.value) {
        _searchQuery.value = query
        _providerFilter.value = provider
        if (query.isBlank()) {
            _searchHits.value = emptyList()
            return
        }
        scope.launch {
            _searchLoading.value = true
            val sem = Semaphore(4)
            val type = _typeFilter.value
            val targetApis = if (provider != null) {
                allEnabledProviders().filter { it.name.equals(provider, ignoreCase = true) }
            } else {
                allEnabledProviders()
            }
            val hits = coroutineScope {
                targetApis.map { api ->
                    async {
                        sem.withPermit {
                            runCatching {
                                api.search(query).orEmpty()
                                    .filter { type == null || it.type == type }
                                    .map { SearchHit(api.name, it) }
                            }.getOrElse { emptyList() }
                        }
                    }
                }.awaitAll().flatten()
            }
            _searchHits.value = hits
            _searchLoading.value = false
        }
    }

    fun setTypeFilter(type: TvType?) {
        _typeFilter.value = type
        if (_searchQuery.value.isNotBlank()) search(_searchQuery.value, _providerFilter.value)
    }

    fun refreshHome() {
        scope.launch {
            _homeLoading.value = true
            _homeError.value = null
            val providers = enabledProviders().filter {
                it.hasMainPage || it.mainPage.any { page -> page.name.isNotBlank() }
            }
            if (providers.isEmpty()) {
                _home.value = emptyList()
                _homeLoading.value = false
                val loadFails = loader.loadErrors()
                _homeError.value = when {
                    loadFails.isNotEmpty() ->
                        "Plugin load failed: " + loadFails.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
                    enabledProviders().isNotEmpty() ->
                        "Installed providers did not expose a homepage. Try Search, or open a plugin's Settings."
                    loader.installed.any { it.enabled } ->
                        "Enabled plugins did not register any providers. Reinstall the extension or check Settings."
                    else -> null
                }
                return@launch
            }
            val sem = Semaphore(3)
            val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val rows = coroutineScope {
                providers.map { api ->
                    async {
                        sem.withPermit {
                            runCatching {
                                val pages = api.mainPage.ifEmpty {
                                    listOf(com.lagradost.cloudstream3.MainPageData("", "", false))
                                }
                                pages.flatMap { page ->
                                    val resp = api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                                    (resp?.items ?: emptyList()).map { HomeRow(api.name, it) }
                                }
                            }.getOrElse { t ->
                                Log.e("Home", "${api.name} getMainPage: ${t.stackTraceToString()}")
                                errors += "${api.name}: ${t.flattenMessages().ifBlank { t::class.simpleName ?: "error" }}"
                                emptyList()
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            _home.value = rows
            _homeLoading.value = false
            if (rows.isEmpty() && providers.isNotEmpty()) {
                _homeError.value = buildString {
                    val loadFails = loader.loadErrors()
                    if (loadFails.isNotEmpty()) {
                        append("Plugin load failed: ")
                        append(loadFails.entries.take(4).joinToString(" · ") { "${it.key}: ${it.value}" })
                        append(' ')
                    }
                    append("No homepage rows. ")
                    if (errors.isNotEmpty()) append(errors.take(8).joinToString(" · ") { it.take(240) })
                    else append("Providers returned empty lists. If a browser window opened, complete the Cloudflare check.")
                }
            } else if (rows.isEmpty()) {
                val loadFails = loader.loadErrors()
                if (loadFails.isNotEmpty()) {
                    _homeError.value = "Plugin load failed: " + loadFails.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
                }
            }
            refreshLiveIntoHome()
        }
    }

    private fun refreshLiveIntoHome() {
        scope.launch {
            val sem = Semaphore(3)
            val hits = coroutineScope {
                enabledProviders().filter { TvType.Live in it.supportedTypes }.map { api ->
                    async {
                        sem.withPermit {
                            runCatching {
                                val fromHome = if (api.hasMainPage) {
                                    api.mainPage.flatMap { page ->
                                        api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                                            ?.items.orEmpty().flatMap { it.list }
                                    }
                                } else emptyList()
                                fromHome.filter { it.type == TvType.Live }
                            }.getOrElse { emptyList() }
                        }
                    }
                }.awaitAll().flatten()
            }
            _live.value = hits.map { SearchHit(it.apiName, it) }
            if (hits.isEmpty()) return@launch
            val liveRow = HomeRow("Live", com.lagradost.cloudstream3.HomePageList("Live", hits))
            val current = _home.value
            if (current.none { it.list.name.equals("Live", true) && it.provider == "Live" }) {
                _home.value = listOf(liveRow) + current
            }
        }
    }

    fun setHomePluginFilter(name: String) {
        settingsStore.update { it.copy(homePluginFilter = name) }
        refreshHome()
    }

    fun openResult(provider: String, url: String, title: String) {
        go(Destination.Result(provider, url, title))
        scope.launch {
            _loadLoading.value = true
            _load.value = null
            _loadError.value = null
            val api = APIHolder.getApiFromNameNull(provider)
            if (api == null) {
                _loadError.value = "Provider '$provider' is not loaded."
                _loadLoading.value = false
                return@launch
            }
            val loaded = runCatching { api.load(url) }.onFailure {
                Log.e("Result", "load failed $provider $url: ${it.stackTraceToString()}")
                _loadError.value = it.message ?: "Failed to load"
            }.getOrNull()
            _load.value = loaded
            _loadLoading.value = false
            if (loaded == null && _loadError.value == null) _loadError.value = "Provider returned nothing for this title."
        }
    }

    fun play(provider: String, pageUrl: String, dataUrl: String, title: String, poster: String?, type: TvType?, startFromBeginning: Boolean = false) {
        go(Destination.Player(title, provider, pageUrl, dataUrl, poster, type, startFromBeginning))
        scope.launch {
            _links.value = emptyList()
            _subs.value = emptyList()
            _playError.value = null
            val api = APIHolder.getApiFromNameNull(provider)
            if (api == null) {
                _playError.value = "Provider '$provider' is not loaded."
                Log.e("Player", "no provider $provider")
                return@launch
            }
            val found = java.util.concurrent.CopyOnWriteArrayList<ExtractorLink>()
            val subtitles = java.util.concurrent.CopyOnWriteArrayList<SubtitleFile>()
            val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            fun accept(link: ExtractorLink) {
                if (!seen.add(link.url)) return
                found += link
                Log.i("Player", "link source=${link.source} name=${link.name} type=${link.type} q=${link.quality} url=${link.url.take(180)}")
            }
            Log.i("Player", "loadLinks provider=$provider title=$title extractors=${extractorApis.size} data=${dataUrl.take(240)}")
            runCatching {
                api.loadLinks(dataUrl, false, { subtitles += it }, { accept(it) })
            }.onFailure {
                Log.e("Player", "loadLinks failed: ${it.stackTraceToString()}")
                _playError.value = it.flattenMessages()
            }
            val tried = LinkedHashSet<String>()
            val queue = ArrayDeque<String>()
            fun enqueue(raw: String) {
                val u = raw.trim().trimEnd(',', ']', '}', ')')
                if (!u.startsWith("http") || looksLikeMediaUrl(u)) return
                val low = u.lowercase()
                if (low.contains("gstatic.com") || low.contains("sofascore") || low.contains("live-card-png")
                    || low.contains(".png") || low.contains(".jpg") || low.contains("/images")) return
                if (tried.size >= 24) return
                if (tried.add(u)) queue.addLast(u)
            }
            enqueue(dataUrl)
            urlsIn(dataUrl).forEach { enqueue(it) }
            found.toList().forEach { if (!isDirectMedia(it)) enqueue(it.url) }
            while (queue.isNotEmpty()) {
                val url = queue.removeFirst()
                Log.i("Player", "loadExtractor $url")
                runCatching {
                    val hit = loadExtractor(url, api.mainUrl, { subtitles += it }, { accept(it) })
                    Log.i("Player", "loadExtractor hit=$hit url=${url.take(120)}")
                }.onFailure {
                    Log.e("Player", "loadExtractor failed ${url.take(120)}: ${it.stackTraceToString()}")
                    if (_playError.value == null) _playError.value = it.flattenMessages()
                }
                found.toList().forEach { if (!isDirectMedia(it)) enqueue(it.url) }
            }
            val playable = found.filter { isDirectMedia(it) }.ifEmpty { found.toList() }
            _links.value = sortUrls(playable.toSet())
            _subs.value = subtitles.toList()
            Log.i("Player", "resolved ${playable.size} playable / ${found.size} total, ${subtitles.size} subs")
            if (playable.isEmpty() && _playError.value == null) {
                _playError.value = "No playable streams from $provider. Open Logcat in the sidebar, or ${Logcat.file.absolutePath}"
            }
            library.addHistory(
                HistoryEntry(0, "$provider|$pageUrl", title, poster, provider, pageUrl, System.currentTimeMillis())
            )
        }
    }

    fun playFromLoad(loaded: LoadResponse, startFromBeginning: Boolean = false) {
        when (loaded) {
            is MovieLoadResponse -> play(loaded.apiName, loaded.url, loaded.dataUrl, loaded.name, loaded.posterUrl, loaded.type, startFromBeginning)
            is LiveStreamLoadResponse -> play(loaded.apiName, loaded.url, loaded.dataUrl, loaded.name, loaded.posterUrl, loaded.type, startFromBeginning)
            is TvSeriesLoadResponse -> {
                val ep = loaded.episodes.firstOrNull() ?: return
                play(loaded.apiName, loaded.url, ep.data, "${loaded.name} ${ep.name ?: ""}".trim(), loaded.posterUrl, loaded.type, startFromBeginning)
            }
            is AnimeLoadResponse -> {
                val ep = loaded.episodes[com.lagradost.cloudstream3.DubStatus.Subbed]?.firstOrNull()
                    ?: loaded.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed]?.firstOrNull()
                    ?: loaded.episodes.values.flatten().firstOrNull() ?: return
                play(loaded.apiName, loaded.url, ep.data, "${loaded.name} ${ep.name ?: ""}".trim(), loaded.posterUrl, loaded.type, startFromBeginning)
            }
            else -> _snack.value = "This media type has no playable data URL."
        }
    }

    fun playEpisode(loaded: LoadResponse, episode: Episode, startFromBeginning: Boolean = false) {
        play(loaded.apiName, loaded.url, episode.data, "${loaded.name} — ${episode.name ?: "Episode ${episode.episode}"}", episode.posterUrl ?: loaded.posterUrl, loaded.type, startFromBeginning)
    }

    fun toggleBookmark(loaded: LoadResponse) {
        val key = "${loaded.apiName}|${loaded.url}"
        if (library.isBookmarked(key)) library.removeBookmark(key)
        else library.addBookmark(
            BookmarkEntry(key, loaded.name, loaded.posterUrl, loaded.apiName, loaded.url, loaded.type.name, loaded.plot, System.currentTimeMillis())
        )
        _snack.value = if (library.isBookmarked(key)) "Bookmarked" else "Removed bookmark"
    }

    fun refreshLive() {
        scope.launch {
            _liveLoading.value = true
            val sem = Semaphore(3)
            val hits = coroutineScope {
                enabledProviders().filter { TvType.Live in it.supportedTypes }.map { api ->
                    async {
                        sem.withPermit {
                            runCatching {
                                val fromHome = if (api.hasMainPage) {
                                    api.mainPage.flatMap { page ->
                                        api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                                            ?.items.orEmpty().flatMap { it.list }
                                    }
                                } else emptyList()
                                fromHome.filter { it.type == TvType.Live }.map { SearchHit(api.name, it) }
                            }.getOrElse { emptyList() }
                        }
                    }
                }.awaitAll().flatten()
            }
            _live.value = hits
            _liveLoading.value = false
        }
    }

    fun refreshPlugins() {
        _plugins.value = loader.installed
        _disabledProviders.value = settingsStore.settings.disabledProviders
        _pluginGeneration.value = _pluginGeneration.value + 1
    }

    fun refreshRepoCatalog() {
        scope.launch(Dispatchers.IO) {
            val grouped = linkedMapOf<String, List<SitePlugin>>()
            val flat = mutableListOf<Pair<SavedRepository, SitePlugin>>()
            val updatedRepos = mutableListOf<SavedRepository>()
            for (repo in settingsStore.repositories) {
                val result = runCatching { repoClient.fetchRepository(repo.url) }
                result.onSuccess { parsed ->
                    val named = repo.copy(name = parsed.manifest.name.ifBlank { repo.name }, description = parsed.manifest.description ?: repo.description)
                    updatedRepos += named
                    grouped[named.url] = parsed.plugins
                    parsed.plugins.forEach { flat += named to it }
                }.onFailure {
                    updatedRepos += repo
                    grouped[repo.url] = emptyList()
                    _extStatus.value = "Failed to read ${repo.name.ifBlank { repo.url }}: ${it.message}"
                }
            }
            if (updatedRepos != settingsStore.repositories) {
                settingsStore.setRepositories(updatedRepos)
            }
            _repositories.value = settingsStore.repositories
            _catalogByRepo.value = grouped
            _repoPlugins.value = flat
        }
    }

    fun addRepository(input: String) {
        scope.launch(Dispatchers.IO) {
            _extStatus.value = "Resolving repository…"
            runCatching {
                val parsed = repoClient.fetchRepository(input)
                settingsStore.addRepository(
                    SavedRepository(parsed.manifest.name.ifBlank { "Repository" }, parsed.resolvedUrl, parsed.manifest.description)
                )
                _repositories.value = settingsStore.repositories
                _extStatus.value = "Added ${parsed.manifest.name.ifBlank { "repository" }} (${parsed.plugins.size} extensions). Install one below."
                refreshRepoCatalog()
            }.onFailure {
                _extStatus.value = it.message ?: "Could not add repository"
            }
        }
    }

    fun removeRepository(url: String) {
        settingsStore.removeRepository(url)
        _repositories.value = settingsStore.repositories
        refreshRepoCatalog()
    }

    fun installPlugin(repo: SavedRepository, plugin: SitePlugin) {
        scope.launch(Dispatchers.IO) {
            _installing.value = plugin.internalName.ifBlank { plugin.name }
            _extStatus.value = "Installing ${plugin.name}…"
            runCatching {
                loader.installFromUrl(repoClient, plugin, repo.url)
                withContext(Dispatchers.Main) { refreshPlugins() }
                _extStatus.value = "Installed ${plugin.name}"
            }.onFailure {
                _extStatus.value = "Install failed for ${plugin.name}: ${it.flattenMessages()}"
            }
            _installing.value = null
        }
    }

    fun uninstallPlugin(name: String) {
        loader.uninstall(name)
        refreshPlugins()
    }

    fun setPluginEnabled(name: String, enabled: Boolean) {
        loader.setEnabled(name, enabled)
        refreshPlugins()
        refreshHome()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    fun backup(file: File) = BackupRestore.backup(file)
    fun restore(file: File) {
        BackupRestore.restore(file)
        loader.loadAllInstalled()
        refreshPlugins()
    }

    fun snackConsumed() { _snack.value = null }

    /**
     * Every MainAPI a plugin registered. Read live from the registry rather than only the
     * snapshot taken at load time: a plugin whose providers depend on its own settings
     * registers a different set after the user changes them.
     */
    fun getPluginProviders(internalName: String): List<MainAPI> {
        val plug = loader.installed.firstOrNull { it.internalName == internalName }
        val loaded = loader.loadedPlugins().firstOrNull { it.info.internalName == internalName }
        val paths = setOfNotNull(plug?.filePath, loaded?.info?.filePath)
        val live = APIHolder.allProviders.toList().filter { it.sourcePlugin in paths }
        val known = LinkedHashMap<String, MainAPI>()
        for (api in live + loaded?.providers.orEmpty()) known.putIfAbsent(api.name, api)
        return known.values.sortedBy { it.name.lowercase() }
    }

    fun isProviderEnabled(name: String): Boolean = name !in _disabledProviders.value

    fun setSubProviderEnabled(name: String, enabled: Boolean) {
        updateSettings { curr ->
            val set = curr.disabledProviders.toMutableSet()
            if (enabled) set.remove(name) else set.add(name)
            curr.copy(disabledProviders = set)
        }
        _disabledProviders.value = settingsStore.settings.disabledProviders
        refreshHome()
    }

    fun setPluginProvidersEnabled(internalName: String, enabled: Boolean) {
        val names = getPluginProviders(internalName).map { it.name }
        if (names.isEmpty()) return
        updateSettings { curr ->
            val set = curr.disabledProviders.toMutableSet()
            if (enabled) set.removeAll(names.toSet()) else set.addAll(names)
            curr.copy(disabledProviders = set)
        }
        _disabledProviders.value = settingsStore.settings.disabledProviders
        refreshHome()
    }

    /** Why a plugin has no providers to show, or null when it loaded fine. */
    fun pluginLoadError(internalName: String): String? {
        val info = loader.installed.firstOrNull { it.internalName == internalName } ?: return null
        return loader.loadErrors()[info.displayName]
    }

    fun hasPluginSettings(internalName: String): Boolean {
        val loaded = loader.loadedPlugins().firstOrNull { it.info.internalName == internalName } ?: return false
        val plugin = loaded.instance as? com.lagradost.cloudstream3.plugins.Plugin ?: return false
        return plugin.openSettings != null
    }

    fun openPluginSettings(internalName: String) {
        val loaded = loader.loadedPlugins().firstOrNull { it.info.internalName == internalName }
        if (loaded == null) {
            _snack.value = "Enable the extension before opening settings."
            return
        }
        val plugin = loaded.instance as? com.lagradost.cloudstream3.plugins.Plugin
        val open = plugin?.openSettings
        val ctx = loader.androidContext()
        if (ctx is dev.csdesktop.extloader.DesktopContext) {
            if (plugin?.resources != null) ctx.attachPluginResources(plugin.resources)
            ctx.pluginClassLoader = loaded.loader
        }
        dev.csdesktop.extloader.ExtensionUi.suppressPopups = false
        if (open != null) {
            javax.swing.SwingUtilities.invokeLater {
                val prev = Thread.currentThread().contextClassLoader
                Thread.currentThread().contextClassLoader = loaded.loader
                runCatching { open.invoke(ctx) }
                    .onFailure { t ->
                        Log.e("Extensions", "openSettings failed for ${loaded.info.displayName}: ${t.stackTraceToString()}")
                        showProviderSettingsFallback(internalName, loaded.info.displayName, t.message)
                    }
                Thread.currentThread().contextClassLoader = prev
            }
            return
        }
        showProviderSettingsFallback(internalName, loaded.info.displayName, null)
    }

    /** When a plugin has no openSettings (or it crashed), still let the user toggle its providers. */
    private fun showProviderSettingsFallback(internalName: String, title: String, error: String?) {
        val ctx = loader.androidContext()
        val root = android.widget.LinearLayout(ctx)
        root.orientation = android.widget.LinearLayout.VERTICAL
        if (!error.isNullOrBlank()) {
            val err = android.widget.TextView(ctx)
            err.text = "Could not open the extension's own menu ($error). Toggle providers below."
            root.addView(err)
        }
        val providers = getPluginProviders(internalName)
        if (providers.isEmpty()) {
            val empty = android.widget.TextView(ctx)
            empty.text = pluginLoadError(internalName) ?: "This extension has no extra settings."
            root.addView(empty)
        } else {
            val hint = android.widget.TextView(ctx)
            hint.text = "Providers in this extension"
            root.addView(hint)
            for (api in providers) {
                val box = android.widget.CheckBox(ctx)
                box.text = buildString {
                    append(api.name)
                    api.lang.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                }
                box.isChecked = isProviderEnabled(api.name)
                val name = api.name
                box.setOnCheckedChangeListener { _, checked -> setSubProviderEnabled(name, checked) }
                root.addView(box)
            }
        }
        val done = dev.csdesktop.extloader.DialogHost.DialogButton("Done", false, null)
        dev.csdesktop.extloader.ExtensionUi.present(title, null, root, listOf(done), null)
    }

    fun resumePlayback(entry: ResumeEntry) {
        val provider = entry.provider ?: return
        val page = entry.url ?: return
        val data = entry.dataUrl?.takeIf { it.isNotBlank() }
        if (data != null) {
            play(provider, page, data, entry.title, entry.poster, null)
        } else {
            openResult(provider, page, entry.title)
        }
    }

    fun refreshContinueWatching() {
        _continueWatching.value = library.continueWatching()
    }

    private var lastContinueRefresh = 0L

    fun saveResume(key: String, title: String, poster: String?, provider: String, url: String, dataUrl: String, pos: Long, dur: Long, live: Boolean, audioId: Int? = null, subId: Int? = null) {
        if (live) return
        if (pos < 500 && dur <= 0) return
        runCatching {
            val entry = ResumeEntry(key, title, poster, provider, url, pos.coerceAtLeast(0), dur.coerceAtLeast(0), false, System.currentTimeMillis(), dataUrl, audioId, subId)
            library.saveResume(entry)
            val pageKey = "$provider|$url"
            if (pageKey != key) library.saveResume(entry.copy(key = pageKey))
            library.addHistory(
                HistoryEntry(0, pageKey, title, poster, provider, url, System.currentTimeMillis(), pos.coerceAtLeast(0), dur.coerceAtLeast(0))
            )
            val now = System.currentTimeMillis()
            if (now - lastContinueRefresh > 2_000) {
                lastContinueRefresh = now
                refreshContinueWatching()
                Log.i("Resume", "saved ${pos / 1000}s / ${dur / 1000}s $title")
            }
        }.onFailure { Log.e("Resume", "save failed: ${it.message}") }
    }

    companion object {
        val officialRepoUrl = OfficialRepos.EXTENSIONS_REPO
    }
}

private fun Throwable.flattenMessages(): String =
    generateSequence(this) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .joinToString(" — ")
        .ifBlank { this::class.simpleName ?: "unknown error" }

private val HTTP_URL = Regex("""https?://[^\s"'<>\\]+""")

private fun urlsIn(data: String): List<String> {
    if (!data.contains("http", ignoreCase = true)) return emptyList()
    return HTTP_URL.findAll(data).map { it.value.trimEnd(',', ']', '}', ')', '\\') }.distinct().take(20).toList()
}

private fun looksLikeMediaUrl(url: String): Boolean {
    val path = url.lowercase().substringBefore('?').substringBefore('#')
    return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4") ||
        path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".m4v") ||
        path.endsWith(".ts") || path.contains(".m3u8")
}

private fun isDirectMedia(link: ExtractorLink): Boolean {
    if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) return false
    if (looksLikeMediaUrl(link.url)) return true
    if (link.type == ExtractorLinkType.M3U8 || link.type == ExtractorLinkType.DASH) return true
    if (link.type != ExtractorLinkType.VIDEO) return false
    val path = link.url.lowercase().substringBefore('?').substringBefore('#')
    val last = path.substringAfterLast('/')
    if (path.endsWith(".html") || path.endsWith(".php") || path.endsWith(".htm")) return false
    if (last.isBlank() || !last.contains('.')) return false
    return true
}
