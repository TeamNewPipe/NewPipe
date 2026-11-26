/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.plugin.PluginException

class PluginSourceResolverTest {
    @Test
    fun directCatalogKeepsItsDirectoryForRelativePackages() {
        val url = "https://publisher.example/plugins/stable/index.json"
        val fixture = Fixture(url to catalog("Publisher", entry("example.first", "packages/first.ppp")))
        val source = fixture.resolve(url).single()

        assertEquals(url, source.repository.url)
        assertEquals("https://publisher.example/plugins/stable/packages/first.ppp", source.entries.single().packageUrl)
        assertEquals(listOf(url), fixture.requests)
    }

    @Test
    fun githubProjectUsesItsActualDefaultBranchAndContentsDownloadUrl() {
        val raw = "https://raw.githubusercontent.com/owner/plugins/production/index.json"
        val fixture = Fixture(
            githubApi to """{"default_branch":"production"}""",
            "$githubApi/contents/index.json?ref=production" to """{"type":"file","download_url":"$raw"}""",
            raw to catalog("Production", entry("example.first", "first.ppp"))
        )

        val source = fixture.resolve("https://github.com/owner/plugins.git/").single()
        assertEquals(raw, source.repository.url)
        assertEquals("https://raw.githubusercontent.com/owner/plugins/production/first.ppp", source.entries.single().packageUrl)
        assertFalse(fixture.requests.any { "/main/" in it || "ref=main" in it })
    }

    @Test
    fun rollingRepositoryTagIsFoundEvenWhenOutsideTheRecentReleasePage() {
        val index = "$githubWeb/releases/download/repo/index.json"
        val fixture = Fixture(
            githubApi to """{"default_branch":"trunk"}""",
            "$githubApi/releases/tags/repo" to githubRelease("repo", index),
            "$githubApi/releases?per_page=5" to "[]",
            index to catalog("Rolling", entry("example.first", "first.ppp"))
        )

        assertEquals(index, fixture.resolve(githubWeb).single().repository.url)
        assertTrue(fixture.requests.contains("$githubApi/releases/tags/repo"))
    }

    @Test
    fun distinctReleaseAndRootCatalogsRemainAvailableForUserSelection() {
        val release = "$githubWeb/releases/download/v3/index.json"
        val root = "https://raw.githubusercontent.com/owner/plugins/trunk/index.json"
        val fixture = Fixture(
            githubApi to """{"default_branch":"trunk"}""",
            "$githubApi/releases/latest" to githubRelease("v3", release),
            "$githubApi/contents/index.json?ref=trunk" to """{"download_url":"$root"}""",
            release to catalog("Release", entry("example.first", "first.ppp")),
            root to catalog("Root", entry("example.second", "second.ppp"))
        )

        assertEquals(listOf(release, root), fixture.resolve(githubWeb).map { it.repository.url })
    }

    @Test
    fun explicitReleasePageFindsItsCatalogWithoutFetchingTheHtmlPage() {
        val page = "$githubWeb/releases/tag/v1"
        val index = "$githubWeb/releases/download/v1/index.json"
        val fixture = Fixture(
            "$githubApi/releases/tags/v1" to githubRelease("v1", index),
            index to catalog("Version one", entry("example.first", "first.ppp"))
        )

        assertEquals(index, fixture.resolve(page).single().repository.url)
        assertFalse(page in fixture.requests)
    }

    @Test
    fun githubBrowserAndRawLinksSelectOnlyTheirExplicitCatalog() {
        val raw = "https://raw.githubusercontent.com/owner/plugins/refs/heads/testing/catalog/index.json"
        val fixture = Fixture(raw to catalog("Testing", entry("example.first", "first.ppp")))
        val browser = "$githubWeb/blob/refs/heads/testing/catalog/index.json?raw=true"

        assertEquals(raw, fixture.resolve(browser).single().repository.url)
        assertEquals(listOf(raw), fixture.requests)
        fixture.requests.clear()
        assertEquals(raw, fixture.resolve(raw).single().repository.url)
        assertEquals(listOf(raw), fixture.requests)
    }

    @Test
    fun directPackageSelectsOnlyItsSignedCatalogEntryAndNeverFetchesTheBinary() {
        val packageUrl = "$githubWeb/releases/download/v4/first.ppp"
        val index = "$githubWeb/releases/download/v4/index.json"
        val fixture = Fixture(index to catalog("Two plugins", entry("example.first", "first.ppp"), entry("example.second", "second.ppp")))

        val source = fixture.resolve(packageUrl).single()
        assertEquals(listOf("example.first"), source.entries.map { it.pluginId })
        assertEquals(2, source.repository.plugins.size)
        assertFalse(packageUrl in fixture.requests)
    }

    @Test
    fun latestGithubPackageAliasMatchesTheVersionedReleaseEntry() {
        val requested = "$githubWeb/releases/latest/download/first.ppp"
        val versioned = "$githubWeb/releases/download/v4/first.ppp"
        val index = "$githubWeb/releases/download/v4/index.json"
        val fixture = Fixture(
            "$githubApi/releases/latest" to githubRelease("v4", index, versioned),
            index to catalog("Version four", entry("example.first", "first.ppp"))
        )

        assertEquals(versioned, fixture.resolve(requested).single().entries.single().packageUrl)
        assertFalse(requested in fixture.requests)
        assertFalse(versioned in fixture.requests)
    }

    @Test
    fun latestGithubPackageDoesNotMatchOlderReleasesWithTheSameFileName() {
        val latestIndex = "$githubWeb/releases/download/v4/index.json"
        val olderIndex = "$githubWeb/releases/download/v3/index.json"
        val latest = githubRelease("v4", latestIndex, "$githubWeb/releases/download/v4/first.ppp")
        val older = githubRelease("v3", olderIndex, "$githubWeb/releases/download/v3/first.ppp")
        val fixture = Fixture(
            "$githubApi/releases/latest" to latest,
            "$githubApi/releases/tags/v3" to older,
            "$githubApi/releases?per_page=5" to "[$latest,$older]",
            latestIndex to catalog("Latest", entry("example.first", "first.ppp")),
            olderIndex to catalog("Older", entry("example.first", "first.ppp"))
        )

        assertEquals(listOf(latestIndex), fixture.resolve("$githubWeb/releases/latest/download/first.ppp").map { it.repository.url })
        assertEquals(listOf(olderIndex), fixture.resolve("$githubWeb/releases/download/v3/first.ppp").map { it.repository.url })
    }

    @Test
    fun unsignedOrDifferentPackageMetadataCannotTurnAPackageUrlIntoAnInstall() {
        val packageUrl = "https://publisher.example/first.ppp"
        val unsigned = entry("example.first", "first.ppp").apply { remove("signature") }
        val fixture = Fixture("https://publisher.example/index.json" to catalog("Unsigned", unsigned, entry("example.second", "second.ppp")))

        val error = assertThrows(PluginException::class.java) { fixture.resolve(packageUrl) }
        assertTrue(error.message!!.contains("No signed catalog entry matches"))
        assertEquals(listOf("https://publisher.example/index.json"), fixture.requests)
    }

    @Test
    fun gitlabNestedNamespacesAndSlashDefaultBranchUseTheCorrectRawDirectory() {
        val api = "https://gitlab.com/api/v4/projects/group%2Fsubgroup%2Fplugins"
        val raw = "https://gitlab.com/group/subgroup/plugins/-/raw/releases%2Fstable/index.json"
        val fixture = Fixture(
            api to """{"default_branch":"releases/stable","path_with_namespace":"group/subgroup/plugins"}""",
            raw to catalog("Stable", entry("example.first", "packages/first.ppp"))
        )

        val source = fixture.resolve("https://gitlab.com/group/subgroup/plugins").single()
        assertEquals(raw, source.repository.url)
        assertEquals("https://gitlab.com/group/subgroup/plugins/-/raw/releases%2Fstable/packages/first.ppp", source.entries.single().packageUrl)
        assertTrue(api in fixture.requests)
    }

    @Test
    fun gitlabBrowserLinkUsesRawContentWithoutAnApiEndpointAsPackageBase() {
        val raw = "https://gitlab.com/group/subgroup/plugins/-/raw/develop/catalog/index.json"
        val fixture = Fixture(raw to catalog("Develop", entry("example.first", "first.ppp")))

        val source = fixture.resolve(raw.replace("/-/raw/", "/-/blob/")).single()
        assertEquals(raw, source.repository.url)
        assertEquals(raw.replace("index.json", "first.ppp"), source.entries.single().packageUrl)
        assertEquals(listOf(raw), fixture.requests)
    }

    @Test
    fun gitlabReleaseLinksUseActualCatalogLocationAndMatchPermanentPackageAliases() {
        val web = "https://gitlab.com/group/subgroup/plugins"
        val api = "https://gitlab.com/api/v4/projects/group%2Fsubgroup%2Fplugins"
        val requested = "$web/-/releases/v2/downloads/first.ppp"
        val index = "https://cdn.example/v2/files/index.json"
        val packageUrl = "https://cdn.example/v2/files/first.ppp"
        val release = """{"tag_name":"v2","assets":{"links":[
          {"name":"index.json","url":"$index","direct_asset_url":"$web/-/releases/v2/downloads/index.json"},
          {"name":"first.ppp","url":"$packageUrl","direct_asset_url":"$requested"}
        ]}}"""
        val fixture = Fixture(
            "$api/releases/v2" to release,
            index to catalog("Release", entry("example.first", "first.ppp"))
        )

        val source = fixture.resolve(requested).single()
        assertEquals(index, source.repository.url)
        assertEquals(packageUrl, source.entries.single().packageUrl)
        assertFalse(requested in fixture.requests)
    }

    @Test
    fun gitlabLatestPackageDoesNotMatchAnOlderReleaseWithTheSameAssetPath() {
        val web = "https://gitlab.com/group/plugins"
        val api = "https://gitlab.com/api/v4/projects/group%2Fplugins"
        val latestIndex = "https://cdn.example/v4/index.json"
        val olderIndex = "https://cdn.example/v3/index.json"
        fun release(version: Int) = """{"tag_name":"v$version","assets":{"links":[
          {"name":"index.json","url":"https://cdn.example/v$version/index.json","direct_asset_url":"$web/-/releases/v$version/downloads/index.json"},
          {"name":"first.ppp","url":"https://cdn.example/v$version/first.ppp","direct_asset_url":"$web/-/releases/v$version/downloads/first.ppp"}
        ]}}"""
        val fixture = Fixture(
            "$api/releases/permalink/latest" to release(4),
            "$api/releases/v3" to release(3),
            "$api/releases?per_page=5" to "[${release(4)},${release(3)}]",
            latestIndex to catalog("Latest", entry("example.first", "first.ppp")),
            olderIndex to catalog("Older", entry("example.first", "first.ppp"))
        )

        assertEquals(listOf(latestIndex), fixture.resolve("$web/-/releases/permalink/latest/downloads/first.ppp").map { it.repository.url })
        assertEquals(listOf(olderIndex), fixture.resolve("$web/-/releases/v3/downloads/first.ppp").map { it.repository.url })
    }

    @Test
    fun explicitGitlabReleaseCatalogUsesItsActualLinkedDirectoryForRelativePackages() {
        val web = "https://gitlab.com/group/plugins"
        val api = "https://gitlab.com/api/v4/projects/group%2Fplugins"
        val actual = "https://cdn.example/v2/nested/index.json"
        val versioned = "$web/-/releases/v2/downloads/index.json"
        val release = """{"tag_name":"v2","assets":{"links":[
          {"name":"index.json","url":"$actual","direct_asset_url":"$versioned"}
        ]}}"""
        val fixture = Fixture(
            "$api/releases/v2" to release,
            "$api/releases/permalink/latest" to release,
            actual to catalog("Linked", entry("example.first", "first.ppp"))
        )
        for (input in listOf(versioned, "$web/-/releases/permalink/latest/downloads/index.json")) {
            val source = fixture.resolve(input).single()
            assertEquals(actual, source.repository.url)
            assertEquals("https://cdn.example/v2/nested/first.ppp", source.entries.single().packageUrl)
            assertFalse(input in fixture.requests)
        }
    }

    @Test
    fun gitlabRawApiLinkResolvesHeadAndEncodedFilePathBeforeParsingTheCatalog() {
        val input = "https://gitlab.com/api/v4/projects/42/repository/files/catalog%2Findex.json/raw?ref=HEAD"
        val raw = "https://gitlab.com/group/subgroup/plugins/-/raw/production/catalog/index.json"
        val fixture = Fixture(
            "https://gitlab.com/api/v4/projects/42" to """{"web_url":"https://gitlab.com/group/subgroup/plugins","default_branch":"production"}""",
            raw to catalog("Raw file", entry("example.first", "first.ppp"))
        )

        val source = fixture.resolve(input).single()
        assertEquals(raw, source.repository.url)
        assertEquals(raw.replace("index.json", "first.ppp"), source.entries.single().packageUrl)
        assertFalse(input in fixture.requests)
    }

    @Test
    fun githubContentsApiUsesItsRawDownloadLocationForRelativePackages() {
        val api = "$githubApi/contents/catalog/index.json?ref=release%2Fstable"
        val raw = "https://raw.githubusercontent.com/owner/plugins/release%2Fstable/catalog/index.json"
        val fixture = Fixture(
            api to """{"type":"file","download_url":"$raw"}""",
            raw to catalog("API file", entry("example.first", "first.ppp"))
        )

        val source = fixture.resolve(api).single()
        assertEquals(raw, source.repository.url)
        assertEquals(raw.replace("index.json", "first.ppp"), source.entries.single().packageUrl)
    }

    @Test
    fun duplicateReleaseCandidatesAreFetchedAndReturnedOnce() {
        val index = "$githubWeb/releases/download/repo/index.json"
        val release = githubRelease("repo", index)
        val fixture = Fixture(
            "$githubApi/releases/tags/repo" to release,
            "$githubApi/releases/latest" to release,
            "$githubApi/releases?per_page=5" to "[$release,$release]",
            index to catalog("Only one", entry("example.first", "first.ppp"))
        )

        assertEquals(1, fixture.resolve(githubWeb).size)
        assertEquals(1, fixture.requests.count { it == index })
    }

    @Test
    fun malformedOrInsecureCandidatesDoNotHideAnotherValidCatalog() {
        val broken = "$githubWeb/releases/download/repo/index.json"
        val valid = "$githubWeb/releases/download/v2/index.json"
        val release = githubRelease("v2", "http://publisher.example/index.json", valid)
        val fixture = Fixture(
            "$githubApi/releases/tags/repo" to githubRelease("repo", broken),
            "$githubApi/releases/latest" to release,
            broken to "<html>not a catalog</html>",
            valid to catalog("Valid", entry("example.first", "first.ppp"), entry("example.unsafe", "http://publisher.example/unsafe.ppp"))
        )

        val source = fixture.resolve(githubWeb).single()
        assertEquals(valid, source.repository.url)
        assertEquals(listOf("example.first"), source.entries.map { it.pluginId })
        assertTrue(fixture.requests.all { it.startsWith("https://") })
    }

    @Test
    fun discoveryIsBoundedEvenIfApiReturnsTooManyReleasesAndAssets() {
        val releases = (1..20).map { number ->
            githubRelease("v$number", *(1..20).map { "https://cdn.example/v$number/catalog$it/index.json" }.toTypedArray())
        }
        val fixture = Fixture("$githubApi/releases?per_page=5" to "[${releases.joinToString(",")}]")
        fixture.fallback = { url -> if (url.startsWith("https://cdn.example/")) catalog("Bounded", entry("example.first", "first.ppp")) else null }

        val results = fixture.resolve(githubWeb)
        assertEquals(8, results.size)
        assertTrue(fixture.requests.size <= 16)
        assertFalse(fixture.requests.any { "/v6/" in it || "page=2" in it })
    }

    @Test
    fun rejectsUnsafeInputBeforeTheTransportAndPropagatesCancellation() {
        val fixture = Fixture()
        for (url in listOf("http://publisher.example/index.json", "https://user:secret@publisher.example/index.json", "https://publisher.example/index.json#fragment")) {
            assertThrows(PluginException::class.java) { fixture.resolve(url) }
        }
        assertTrue(fixture.requests.isEmpty())
        assertThrows(CancellationException::class.java) {
            PluginSourceResolver.resolve(githubWeb) { throw CancellationException("Cancelled") }
        }
    }

    @Test
    fun inaccessibleProjectReportsTheTransportFailureWithoutAssumingMain() {
        val error = assertThrows(PluginException::class.java) {
            PluginSourceResolver.resolve(githubWeb) { throw IOException("HTTP 403: rate limit") }
        }
        assertTrue(error.cause is IOException)
        assertEquals("HTTP 403: rate limit", error.cause!!.message)
    }

    @Test
    fun explicitEmptyCatalogIsValidButMissingOrEntirelyInvalidEntriesAreReported() {
        val url = "https://publisher.example/index.json"
        assertTrue(Fixture(url to catalog("Empty")).resolve(url).single().entries.isEmpty())
        val unsigned = entry("example.first", "first.ppp").apply { remove("signature") }
        for (body in listOf("""{"formatVersion":1,"name":"Missing plugins"}""", catalog("Invalid", unsigned))) {
            val error = assertThrows(PluginException::class.java) { Fixture(url to body).resolve(url) }
            assertTrue(error.message!!.startsWith("Cannot read plugin catalog:"))
            assertTrue(error.cause is PluginException)
        }
    }

    private class Fixture(vararg responses: Pair<String, String>) {
        val requests = mutableListOf<String>()
        val responses = responses.toMap()
        var fallback: (String) -> String? = { null }

        fun resolve(input: String): List<ResolvedPluginSource> = PluginSourceResolver.resolve(input) { url ->
            requests += url
            responses[url] ?: fallback(url) ?: throw IOException("HTTP 404: $url")
        }
    }

    private fun catalog(name: String, vararg entries: JsonObject): String = JsonWriter.string(
        JsonObject().apply {
            put("formatVersion", 1)
            put("name", name)
            put("plugins", JsonArray(entries.toList()))
        }
    )

    private fun entry(id: String, packageUrl: String): JsonObject = JsonObject().apply {
        put("pluginId", id)
        put("name", id)
        put("versionCode", 1)
        put("versionName", "1.0")
        put("apiVersion", "1.0")
        put("package", packageUrl)
        put("sha256", "0".repeat(64))
        put("signature", "catalog-signature-verified-later-by-installer")
        put("publicKey", "BHXTB1DqhMt9/MVbz63M5/YPOkEtuDPh72ueB4DqUsc=")
    }

    private fun githubRelease(tag: String, vararg urls: String): String = JsonWriter.string(
        JsonObject().apply {
            put("tag_name", tag)
            put("draft", false)
            put(
                "assets",
                JsonArray(
                    urls.map { url ->
                        JsonObject().apply {
                            put("name", url.substringAfterLast('/'))
                            put("browser_download_url", url)
                        }
                    }
                )
            )
        }
    )

    private val githubWeb = "https://github.com/owner/plugins"
    private val githubApi = "https://api.github.com/repos/owner/plugins"
}
