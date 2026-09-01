package dev.csdesktop.extloader

import android.content.Context
import android.content.SharedPreferencesStore
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class Cs3Loader(
    private val context: Context = DesktopContext.create(),
    private val dex: DexToJarConverter = DexToJarConverter(),
    private val json: Json = RepoClient.defaultJson(),
) {
    private val loaded = ConcurrentHashMap<String, LoadedPlugin>()
    private val loadErrors = ConcurrentHashMap<String, String>()
    private val stateFile = File(AppPaths.root, "installed-plugins.json")

    @Volatile
    var installed: List<InstalledPlugin> = loadState()
        private set

    data class LoadedPlugin(
        val info: InstalledPlugin,
        val instance: BasePlugin,
        val loader: URLClassLoader,
        val providers: List<MainAPI>,
        val extractors: List<ExtractorApi>,
    )

    fun loadedPlugins(): Collection<LoadedPlugin> = loaded.values

    fun enabledProviders(): List<MainAPI> {
        val disabled = installed.filter { !it.enabled }.map { it.filePath }.toSet()
        return APIHolder.apis.toList().filter { it.sourcePlugin == null || it.sourcePlugin !in disabled }
    }

    fun loadErrors(): Map<String, String> = loadErrors.toMap()

    fun loadAllInstalled() {
        loadErrors.clear()
        for (plugin in installed.filter { it.enabled }) {
            runCatching { loadInstalled(plugin) }
                .onFailure {
                    Log.e(TAG, "Failed to load ${plugin.displayName}: ${it.stackTraceToString()}")
                    val cause = generateSequence(it) { t -> t.cause }.mapNotNull { t -> t.message }.joinToString(" — ")
                    loadErrors[plugin.displayName] = "${it::class.simpleName}: ${cause.ifBlank { it.toString() }}"
                }
        }
    }

    fun installFromUrl(client: RepoClient, plugin: SitePlugin, repositoryUrl: String?): InstalledPlugin {
        val internal = plugin.internalName.ifBlank { plugin.name }.ifBlank { "plugin" }
        val base = sanitize(internal)
        AppPaths.plugins.mkdirs()
        AppPaths.pluginJars.mkdirs()
        val jarDest = File(AppPaths.pluginJars, "$base.jar")
        val cs3Dest = File(AppPaths.plugins, "$base.cs3")

        val jarUrl = plugin.jarUrl?.takeIf { it.isNotBlank() }
        if (jarUrl != null) {
            val jarResult = runCatching {
                val jarBytes = client.downloadBytes(jarUrl)
                verifyHash(plugin.jarHash ?: plugin.fileHash, jarBytes, plugin.internalName)
                jarDest.writeBytes(jarBytes)
            }
            if (jarResult.isSuccess) {
                if (plugin.url.isNotBlank() && plugin.url != jarUrl) {
                    runCatching { cs3Dest.writeBytes(client.downloadBytes(plugin.url)) }
                }
                return finalizeInstall(jarDest, if (cs3Dest.isFile) cs3Dest else jarDest, plugin.url, repositoryUrl, plugin)
            }
            Log.w(TAG, "JVM jar download failed for ${plugin.name}, falling back to .cs3: ${jarResult.exceptionOrNull()?.message}")
        }

        if (plugin.url.isBlank()) throw IllegalStateException("Plugin ${plugin.name} has no download URL")
        val bytes = client.downloadBytes(plugin.url)
        verifyHash(plugin.fileHash, bytes, plugin.internalName)
        cs3Dest.writeBytes(bytes)
        return installFile(cs3Dest, plugin.url, repositoryUrl, plugin)
    }

    private fun verifyHash(expected: String?, bytes: ByteArray, name: String) {
        if (expected.isNullOrBlank()) return
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            val actual = sha256(bytes).removePrefix("sha256-").lowercase()
            val want = expected.removePrefix("sha256-").lowercase()
            if (actual != want) {
                Log.w(TAG, "Hash mismatch for $name (continuing; file looks like a zip). expected=$expected actual=sha256-$actual")
            }
            return
        }
        throw IllegalStateException("Downloaded $name is not a zip/jar/cs3 plugin (${bytes.size} bytes)")
    }

    private fun finalizeInstall(
        jar: File,
        packageFile: File,
        sourceUrl: String?,
        repositoryUrl: String?,
        meta: SitePlugin?,
    ): InstalledPlugin {
        val manifest = runCatching { ManifestParser.readFromApk(packageFile) }.getOrNull()
            ?: runCatching { ManifestParser.readFromApk(jar) }.getOrNull()
            ?: throw IllegalArgumentException("${packageFile.name} has no manifest.json")
        val internal = meta?.internalName?.ifBlank { null } ?: manifest.name ?: packageFile.nameWithoutExtension
        val info = InstalledPlugin(
            internalName = internal,
            displayName = meta?.name ?: manifest.name ?: internal,
            version = meta?.version ?: manifest.version ?: 0,
            filePath = packageFile.absolutePath,
            jarPath = jar.absolutePath,
            sourceUrl = sourceUrl,
            repositoryUrl = repositoryUrl,
            enabled = true,
            authors = meta?.authors ?: emptyList(),
            description = meta?.description ?: "",
            language = meta?.language,
            tvTypes = meta?.tvTypes ?: emptyList(),
            iconUrl = meta?.iconUrl,
            pluginClassName = manifest.pluginClassName,
        )
        loadInstalled(info)
        installed = installed.filter { it.internalName != internal } + info
        persist()
        return info
    }

    fun installFile(
        cs3: File,
        sourceUrl: String? = null,
        repositoryUrl: String? = null,
        meta: SitePlugin? = null,
    ): InstalledPlugin {
        val manifest = ManifestParser.readFromApk(cs3)
        val internal = meta?.internalName?.ifBlank { null }
            ?: manifest.name
            ?: cs3.nameWithoutExtension
        val jar = File(AppPaths.pluginJars, sanitize(internal) + ".jar")
        dex.convert(cs3, jar)
        return finalizeInstall(jar, cs3, sourceUrl, repositoryUrl, meta)
    }

    fun uninstall(internalName: String) {
        val info = installed.firstOrNull { it.internalName == internalName } ?: return
        unload(info.filePath)
        File(info.filePath).delete()
        File(info.jarPath).delete()
        installed = installed.filter { it.internalName != internalName }
        persist()
    }

    fun setEnabled(internalName: String, enabled: Boolean) {
        val info = installed.firstOrNull { it.internalName == internalName } ?: return
        if (enabled) {
            installed = installed.map { if (it.internalName == internalName) it.copy(enabled = true) else it }
            persist()
            loadInstalled(info.copy(enabled = true))
        } else {
            unload(info.filePath)
            installed = installed.map { if (it.internalName == internalName) it.copy(enabled = false) else it }
            persist()
        }
    }

    fun unload(filePath: String) {
        val loadedPlugin = loaded.remove(filePath) ?: loaded.values.firstOrNull { it.info.filePath == filePath }
        if (loadedPlugin != null) {
            loaded.remove(loadedPlugin.info.filePath)
            runCatching { loadedPlugin.instance.beforeUnload() }
            loadedPlugin.providers.forEach { APIHolder.removePluginMapping(it) }
            APIHolder.allProviders.withLock {
                loadedPlugin.providers.forEach { APIHolder.allProviders.remove(it) }
            }
            extractorApis.withLock {
                loadedPlugin.extractors.forEach { extractorApis.remove(it) }
            }
        }
    }

    private fun loadInstalled(info: InstalledPlugin) {
        val jar = File(info.jarPath)
        val cs3 = File(info.filePath)
        if (!jar.isFile && cs3.isFile) {
            dex.convert(cs3, jar)
        }
        if (!jar.isFile) throw IllegalStateException("Missing converted jar for ${info.displayName}")
        val urls = mutableListOf(jar.toURI().toURL())
        if (cs3.isFile) urls += cs3.toURI().toURL()
        val loader = URLClassLoader(urls.toTypedArray(), javaClass.classLoader)
        val manifest = runCatching {
            loader.getResourceAsStream("manifest.json")?.use { it.bufferedReader().readText() }
                ?.let { ManifestParser.parse(it) }
        }.getOrNull() ?: ManifestParser.readFromApk(cs3)
        val className = manifest.pluginClassName
            ?: throw IllegalStateException("manifest.json for ${info.displayName} has no pluginClassName")
        val clazz = Class.forName(className, true, loader)
        val instance = try {
            clazz.getDeclaredConstructor().newInstance() as BasePlugin
        } catch (t: Throwable) {
            throw IllegalStateException("Plugin class $className failed to construct: ${t.message}", t)
        }
        instance.filename = info.filePath
        if (instance is Plugin) {
            runCatching {
                instance.resources = resourcesFromApk(cs3, loader)
            }
            val prev = Thread.currentThread().contextClassLoader
            val desktop = context as? DesktopContext
            val prevPluginCl = desktop?.pluginClassLoader
            val prevSuppress = ExtensionUi.suppressPopups
            try {
                Thread.currentThread().contextClassLoader = loader
                desktop?.pluginClassLoader = loader
                if (instance.resources != null) desktop?.attachPluginResources(instance.resources)
                ExtensionUi.suppressPopups = true
                instance.load(context)
            } catch (t: Throwable) {
                throw IllegalStateException("Plugin ${info.displayName} load() failed: ${t::class.simpleName}: ${t.message}", t)
            } finally {
                ExtensionUi.suppressPopups = prevSuppress
                Thread.currentThread().contextClassLoader = prev
                desktop?.pluginClassLoader = prevPluginCl
            }
        } else {
            instance.load()
        }
        val providers = APIHolder.allProviders.toList().filter { it.sourcePlugin == info.filePath }
        val extractors = extractorApis.toList().filter { it.sourcePlugin == info.filePath }
        loaded[info.filePath] = LoadedPlugin(info, instance, loader, providers, extractors)
        Log.i(TAG, "Loaded plugin ${info.displayName} (${providers.size} providers, ${extractors.size} extractors)")
    }

    fun androidContext(): android.content.Context = context

    private fun resourcesFromApk(apk: File, classLoader: ClassLoader): Resources {
        val assets = AssetManager()
        val unpack = File(AppPaths.cache, "plugin-assets/${apk.nameWithoutExtension}")
        unpack.mkdirs()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                val name = e.name
                if (name.startsWith("assets/") || name.startsWith("res/") || name == "resources.arsc") {
                    val rel = when {
                        name.startsWith("assets/") -> name.removePrefix("assets/")
                        else -> name
                    }
                    val out = File(unpack, rel)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        assets.setAssetsDir(unpack)
        val resources = Resources(assets)
        val arscFile = File(unpack, "resources.arsc")
        if (arscFile.isFile) resources.loadArsc(arscFile)
        val resDir = File(unpack, "res")
        if (resDir.isDirectory) resources.setResRoot(resDir)
        resources.bindClassLoader(classLoader)
        return resources
    }

    private fun persist() {
        stateFile.writeText(json.encodeToString(installed))
    }

    private fun loadState(): List<InstalledPlugin> {
        if (!stateFile.isFile) return emptyList()
        return runCatching {
            json.decodeFromString<List<InstalledPlugin>>(stateFile.readText())
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "Cs3Loader"
        fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        fun sha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return "sha256-" + digest.joinToString("") { "%02x".format(it) }
        }
    }
}

class DesktopContext : AppCompatActivity() {
    @Volatile
    var pluginClassLoader: ClassLoader? = null

    override fun getClassLoader(): ClassLoader {
        return pluginClassLoader ?: javaClass.classLoader
    }

    fun attachPluginResources(res: android.content.res.Resources?) {
        if (res != null) resources = res
    }

    companion object {
        fun create(): DesktopContext {
            SharedPreferencesStore.init(AppPaths.prefs)
            val ctx = DesktopContext()
            ctx.filesDir = AppPaths.root
            ctx.cacheDir = AppPaths.cache
            ctx.packageName = "dev.csdesktop"
            ctx.prefsStore = SharedPreferencesStore.get()
            CommonActivity.setActivityInstance(ctx)
            return ctx
        }
    }
}
