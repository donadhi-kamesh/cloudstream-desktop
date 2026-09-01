package dev.csdesktop.data

import dev.csdesktop.extloader.AppPaths
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupRestore {
    fun backup(dest: File) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream()).use { zos ->
            addDir(zos, AppPaths.plugins, "plugins")
            addDir(zos, AppPaths.pluginJars, "plugin-jars")
            addDir(zos, AppPaths.prefs, "prefs")
            val db = AppPaths.db
            if (db.isFile) addFile(zos, db, "library.db")
        }
    }

    fun restore(zip: File) {
        ZipFile(zip).use { z ->
            z.entries().asSequence().forEach { e ->
                if (e.isDirectory) return@forEach
                val out = when {
                    e.name == "library.db" -> AppPaths.db
                    e.name.startsWith("plugins/") -> File(AppPaths.plugins, e.name.removePrefix("plugins/"))
                    e.name.startsWith("plugin-jars/") -> File(AppPaths.pluginJars, e.name.removePrefix("plugin-jars/"))
                    e.name.startsWith("prefs/") -> File(AppPaths.prefs, e.name.removePrefix("prefs/"))
                    else -> return@forEach
                }
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun addDir(zos: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.isDirectory) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = dir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            addFile(zos, file, "$prefix/$rel")
        }
    }

    private fun addFile(zos: ZipOutputStream, file: File, name: String) {
        zos.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
