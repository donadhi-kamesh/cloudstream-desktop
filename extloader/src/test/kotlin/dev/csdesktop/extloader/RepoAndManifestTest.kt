package dev.csdesktop.extloader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepoAndManifestTest {
    private val json = RepoClient.defaultJson()

    @Test
    fun `parses official-style repo json`() {
        val body = """
            {
              "name": "CloudStream official extensions",
              "description": "Legal sources",
              "manifestVersion": 1,
              "pluginLists": [
                "https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json"
              ]
            }
        """.trimIndent()
        val parsed = json.decodeFromString<RepositoryManifest>(body)
        assertEquals("CloudStream official extensions", parsed.name)
        assertEquals(1, parsed.manifestVersion)
        assertEquals(1, parsed.pluginLists.size)
        assertTrue(parsed.pluginLists.first().endsWith("plugins.json"))
    }

    @Test
    fun `parses plugins json list`() {
        val body = """
            [
              {
                "url": "https://example.com/IptvOrg.cs3",
                "status": 1,
                "version": 4,
                "apiVersion": 1,
                "name": "iptv-org",
                "internalName": "IptvOrg",
                "authors": ["recloudstream"],
                "description": "Public IPTV lists",
                "tvTypes": ["Live"],
                "language": "en",
                "fileSize": 1234,
                "fileHash": "sha256-deadbeef"
              }
            ]
        """.trimIndent()
        val plugins = json.decodeFromString<List<SitePlugin>>(body)
        assertEquals(1, plugins.size)
        assertEquals("IptvOrg", plugins[0].internalName)
        assertEquals(1, plugins[0].status)
        assertEquals(listOf("Live"), plugins[0].tvTypes)
    }

    @Test
    fun `parses plugin manifest json`() {
        val manifest = ManifestParser.parse(
            """
            {
              "name": "iptv-org",
              "pluginClassName": "com.example.IptvPlugin",
              "requiresResources": false,
              "version": 4
            }
            """.trimIndent()
        )
        assertEquals("iptv-org", manifest.name)
        assertEquals("com.example.IptvPlugin", manifest.pluginClassName)
        assertEquals(4, manifest.version)
        assertEquals(false, manifest.requiresResources)
    }

    @Test
    fun `normalizes github blob urls to raw`() {
        val client = RepoClient()
        val raw = client.normalizeGitUrl(
            "https://github.com/recloudstream/extensions/blob/master/repo.json"
        )
        assertEquals(
            "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            raw,
        )
    }

    @Test
    fun `resolves https repo urls`() {
        val client = RepoClient()
        assertNotNull(client.resolveInput("https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"))
        assertEquals(
            "https://example.com/repo.json",
            client.resolveInput("cloudstreamrepo://https://example.com/repo.json"),
        )
    }

    @Test
    fun `resolves relative plugin urls against plugins json`() {
        val client = RepoClient()
        val base = "https://raw.githubusercontent.com/example/repo/builds/plugins.json"
        assertEquals(
            "https://raw.githubusercontent.com/example/repo/builds/MovieBlast.cs3",
            client.absolutize(base, "MovieBlast.cs3"),
        )
        assertEquals(
            "https://cdn.example.com/Plugin.jar",
            client.absolutize(base, "https://cdn.example.com/Plugin.jar"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/example/repo/builds/Plugin.jar",
            client.absolutize(base, "./Plugin.jar"),
        )
    }
}
