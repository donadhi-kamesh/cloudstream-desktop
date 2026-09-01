package dev.csdesktop.extloader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Converts Dalvik `classes*.dex` from a `.cs3` APK into a JVM jar using
 * dex2jar (downloaded on first use into the app tools dir, not shipped in git).
 *
 * Conversion runs **in-process**. The packaged Windows runtime has no `java.exe`,
 * so spawning `Dex2jarCmd` always failed after the tools zip was fetched.
 */
class DexToJarConverter(
    private val toolsDir: File = File(AppPaths.tools, "dex2jar"),
    private val zipFile: File = File(toolsDir.parentFile, "dex-tools-v2.4.zip"),
    private val http: OkHttpClient = RepoClient.defaultClient(),
    private val downloadUrl: String = DEX2JAR_URL,
) {
    fun convert(apk: File, outputJar: File): File = synchronized(this) {
        outputJar.parentFile?.mkdirs()
        val work = Files.createTempDirectory("cs3-dex").toFile()
        try {
            val dexFiles = extractDex(apk, work)
            if (dexFiles.isEmpty()) {
                throw IllegalArgumentException("${apk.name} contains no classes.dex")
            }
            ensureTools()
            val jars = dexFiles.mapIndexed { index, dex ->
                val out = File(work, "part-$index.jar")
                runDex2Jar(dex, out)
                out
            }
            mergeJars(jars, outputJar)
            copyManifestIntoJar(apk, outputJar)
            return outputJar
        } finally {
            work.deleteRecursively()
        }
    }

    private fun extractDex(apk: File, dest: File): List<File> {
        val files = mutableListOf<File>()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .forEach { entry ->
                    val out = File(dest, File(entry.name).name)
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    files += out
                }
        }
        return files.sortedBy { it.name }
    }

    private fun runDex2Jar(dex: File, jar: File) {
        val libDir = File(toolsDir, "lib")
        val jars = libDir.listFiles()?.filter { it.extension.equals("jar", ignoreCase = true) }.orEmpty()
        if (jars.isEmpty()) {
            throw IllegalStateException("dex2jar lib/ is empty at ${libDir.absolutePath}")
        }
        val urls = jars.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { cl ->
            val prev = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = cl
            try {
                val clazz = Class.forName("com.googlecode.d2j.dex.Dex2jar", true, cl)
                val from = clazz.getMethod("from", File::class.java)
                val instance = from.invoke(null, dex)
                val to = instance.javaClass.getMethod("to", Path::class.java)
                to.invoke(instance, jar.toPath())
            } catch (e: InvocationTargetException) {
                val cause = e.cause ?: e
                throw IllegalStateException("dex2jar failed for ${dex.name}: ${cause.message}", cause)
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException("dex2jar could not be loaded from ${libDir.absolutePath}: ${e.message}", e)
            } finally {
                Thread.currentThread().contextClassLoader = prev
            }
        }
        if (!jar.isFile) {
            throw IllegalStateException("dex2jar produced no jar for ${dex.name}")
        }
    }

    private fun mergeJars(jars: List<File>, output: File) {
        if (jars.size == 1) {
            jars.first().copyTo(output, overwrite = true)
            return
        }
        java.util.zip.ZipOutputStream(FileOutputStream(output)).use { zos ->
            val seen = HashSet<String>()
            for (jar in jars) {
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.isDirectory || !seen.add(entry.name)) return@forEach
                        zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        zip.getInputStream(entry).copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    private fun copyManifestIntoJar(apk: File, jar: File) {
        val tmp = File(jar.parentFile, jar.name + ".tmp")
        java.util.zip.ZipOutputStream(FileOutputStream(tmp)).use { zos ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    zip.getInputStream(entry).copyTo(zos)
                    zos.closeEntry()
                }
            }
            ZipFile(apk).use { zip ->
                val man = zip.getEntry("manifest.json")
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("manifest.json") }
                if (man != null) {
                    zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                    zip.getInputStream(man).copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        tmp.copyTo(jar, overwrite = true)
        tmp.delete()
    }

    fun ensureTools() {
        if (translatorJar()?.isFile == true) return
        toolsDir.mkdirs()
        if (!isZipFile(zipFile)) {
            download(downloadUrl, zipFile)
        }
        if (!isZipFile(zipFile)) {
            throw IllegalStateException("Downloaded dex2jar archive is not a zip (${zipFile.absolutePath})")
        }
        unzip(zipFile, toolsDir.parentFile)
        val extracted = File(toolsDir.parentFile, "dex-tools-v2.4")
        if (extracted.isDirectory && extracted.canonicalPath != toolsDir.canonicalPath) {
            extracted.copyRecursively(toolsDir, overwrite = true)
        }
        if (translatorJar()?.isFile != true) {
            throw IllegalStateException("Failed to install dex2jar tools into ${toolsDir.absolutePath}")
        }
    }

    private fun translatorJar(): File? {
        val lib = File(toolsDir, "lib")
        val exact = File(lib, "dex-translator-v2.4.jar")
        if (exact.isFile) return exact
        return lib.listFiles()?.firstOrNull { it.name.startsWith("dex-translator") && it.extension == "jar" }
    }

    private fun download(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", RepoClient.USER_AGENT)
            .header("Accept", "application/octet-stream")
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code} downloading dex2jar from $url")
                }
                val body = resp.body ?: throw IllegalStateException("Empty dex2jar download from $url")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (dest.exists() && !dest.delete()) {
                dest.writeBytes(tmp.readBytes())
                tmp.delete()
            } else if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Could not download dex2jar tools from $url: ${e.message}", e)
        }
    }

    private fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val destCanon = dest.canonicalFile
        ZipFile(zip).use { z ->
            z.entries().asSequence().forEach { entry ->
                val out = File(dest, entry.name).canonicalFile
                if (!out.path.startsWith(destCanon.path + File.separator) && out != destCanon) {
                    return@forEach
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    z.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    companion object {
        const val DEX2JAR_URL =
            "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip"

        fun isZipFile(file: File): Boolean {
            if (!file.isFile || file.length() < 4) return false
            file.inputStream().use { input ->
                val a = input.read()
                val b = input.read()
                return a == 0x50 && b == 0x4b
            }
        }
    }
}
