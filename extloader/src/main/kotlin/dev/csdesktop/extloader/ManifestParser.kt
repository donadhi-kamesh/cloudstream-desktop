package dev.csdesktop.extloader

import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

object ManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(text: String): PluginManifestJson = json.decodeFromString(text)

    fun readFromApk(apk: File): PluginManifestJson {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("manifest.json")
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("manifest.json") }
                ?: throw IllegalArgumentException("${apk.name} has no manifest.json (not a CloudStream .cs3 plugin)")
            zip.getInputStream(entry).bufferedReader().use { reader ->
                return parse(reader.readText())
            }
        }
    }
}
