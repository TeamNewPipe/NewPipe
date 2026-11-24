package org.schabi.newpipe.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginManifest
import org.schabi.newpipe.extractor.plugin.PluginRepository

class PluginInstallPolicyTest {
    @Test
    fun exactPublishedTwitch4ArchiveRequiresAndroid13WhileOtherPackagesUseDeclaredMinimum() {
        val affected = entry(
            "mohr.jonas.twitch",
            "Twitch",
            4,
            sha256 =
                "f44371de1ec69c32fbf37cd61f5ce00d2b676005233437b0d1fad3fdb1263681"
        )
        listOf(24, 32).forEach { sdk ->
            assertThrows(PluginException::class.java) { PluginInstallPolicy.requireCompatible(affected, 1051000, sdk) }
        }
        PluginInstallPolicy.requireCompatible(affected, 1051000, 33)
        PluginInstallPolicy.requireCompatible(entry("mohr.jonas.twitch", "Twitch", 5), 1051000, 24)
        PluginInstallPolicy.requireCompatible(entry("mohr.jonas.twitch", "Twitch", 4), 1051000, 24)
    }

    @Test
    fun repositoryChoiceRespectsEachVersionsAndroidRequirement() {
        val usable = "https://current.example/index.json" to entry("example.plugin", "Example", 5)
        val newer = "https://newer.example/index.json" to entry("example.plugin", "Example", 6, minAndroidSdk = 35)
        assertEquals(listOf(usable), PluginInstallPolicy.preferredEntries(listOf(newer, usable), 1051000, 24))
        assertEquals(listOf(newer), PluginInstallPolicy.preferredEntries(listOf(usable, newer), 1051000, 35))
    }

    @Test
    fun aNewerPublisherReleaseReplacesAStaleCommunityListingWithoutADuplicateRow() {
        val community = "https://community.example/index.json" to entry("mohr.jonas.twitch", "Twitch", 4)
        val publisher = "https://publisher.example/index.json" to entry("mohr.jonas.twitch", "Twitch", 5)
        assertEquals(listOf(publisher), PluginInstallPolicy.preferredEntries(listOf(community, publisher), 1051000))
    }

    @Test
    fun equalVersionsKeepRepositoryOrder() {
        val first = "https://first.example/index.json" to entry("example.plugin", "Example", 5)
        val second = "https://second.example/index.json" to entry("example.plugin", "Example", 5)
        assertEquals(listOf(first), PluginInstallPolicy.preferredEntries(listOf(first, second), 1051000))
        assertEquals(listOf(second), PluginInstallPolicy.preferredEntries(listOf(second, first), 1051000))
    }

    @Test
    fun aFutureIncompatibleVersionDoesNotHideTheLatestUsableVersion() {
        val compatible = "https://current.example/index.json" to entry("example.plugin", "Example", 5)
        val future = "https://future.example/index.json" to entry("example.plugin", "Example", 6, "1.2")
        assertEquals(listOf(compatible), PluginInstallPolicy.preferredEntries(listOf(future, compatible), 1051000))
        assertEquals(listOf(future), PluginInstallPolicy.preferredEntries(listOf(future), 1051000))
        assertFalse(future.second.isApiCompatible)
    }

    private val builtIns = mapOf(
        0 to "YouTube", 1 to "SoundCloud", 2 to "media.ccc.de",
        3 to "PeerTube", 4 to "Bandcamp", 5 to "BiliBili", 6 to "NicoNico",
        7 to "BitChute", 8 to "Rumble", 9 to "Odysee", 10 to "Vimeo"
    )

    @Test
    fun officialProviderIdentityCannotEvadeDuplicateCheckByRenaming() {
        assertEquals("SoundCloud", PluginInstallPolicy.builtInName("org.pipepipe.plugin.soundcloud", "Different name", builtIns))
        assertEquals("PeerTube", PluginInstallPolicy.builtInName("org.pipepipe.plugin.peertube", "Independent instance", builtIns))
    }

    @Test
    fun builtInNamesCannotEvadeDuplicateCheckByChangingPluginIdOrCase() {
        assertEquals("YouTube", PluginInstallPolicy.builtInName("another.publisher.video", " YOUTUBE ", builtIns))
        assertEquals("Vimeo", PluginInstallPolicy.builtInName("another.publisher.vimeo", "vimeo", builtIns))
        builtIns.values.forEach { name ->
            assertEquals(name, PluginInstallPolicy.builtInName("another.publisher.service", " ${name.uppercase()} ", builtIns))
        }
        assertNull(PluginInstallPolicy.builtInName("mohr.jonas.twitch", "Twitch", builtIns))
    }

    @Test
    fun packageCannotChangeIdentityAfterTheUserChoosesACatalogEntry() {
        val entry = entry("example.plugin", "Example")
        assertThrows(PluginException::class.java) {
            PluginInstallPolicy.checkManifest(entry, manifest("example.plugin", "YouTube"), builtIns)
        }
        assertThrows(PluginException::class.java) {
            PluginInstallPolicy.checkManifest(entry, manifest("another.plugin", "Example"), builtIns)
        }
    }

    @Test
    fun verifiedManifestStillCannotClaimBuiltInProviderOrReservedId() {
        assertThrows(PluginException::class.java) {
            PluginInstallPolicy.checkManifest(
                entry("third.party.video", "YouTube"),
                manifest("third.party.video", "YouTube"),
                builtIns
            )
        }
        assertThrows(PluginException::class.java) {
            PluginInstallPolicy.checkManifest(
                entry("example.plugin", "Example"),
                manifest("example.plugin", "Example", 10),
                builtIns
            )
        }
        PluginInstallPolicy.checkManifest(
            entry("example.plugin", "Example"),
            manifest("example.plugin", "Example"),
            builtIns
        )
    }

    @Test
    fun trustedPublisherCannotBeReplacedAfterUninstall() {
        PluginInstallPolicy.checkPublisher(null, "first-key")
        PluginInstallPolicy.checkPublisher("first-key", "first-key")
        // The caller retains this pin independently of the installed package list.
        assertThrows(PluginException::class.java) {
            PluginInstallPolicy.checkPublisher("first-key", "replacement-key")
        }
    }

    @Test
    fun serviceIdsMatchPipePipeAndDoNotDependOnInstallOrder() {
        assertEquals(59460859, PluginInstallPolicy.serviceId("mohr.jonas.twitch"))
        assertEquals(608649692, PluginInstallPolicy.serviceId("com.example.other"))
        assertEquals(59460859, PluginInstallPolicy.serviceId("mohr.jonas.twitch"))
    }

    @Test
    fun packageAndRedirectUrlsCannotDowngradeOrCarryCredentials() {
        assertEquals("https://example.org/index.json", PluginInstallPolicy.requireHttps("https://example.org/index.json"))
        listOf(
            "http://example.org/plugin.ppp",
            "file:///tmp/plugin.ppp",
            "https://user:password@example.org/a",
            "https://example.org/a#fragment",
            "https:///missing-host"
        ).forEach { url ->
            assertThrows(PluginException::class.java) { PluginInstallPolicy.requireHttps(url) }
        }
    }

    @Test
    fun cachedPluginConfigurationRequiresRestartOnlyWhenTheLoadedProfileChanges() {
        assertFalse(PluginInstallPolicy.needsProfileRestart(null, "profile-b", false))
        assertFalse(PluginInstallPolicy.needsProfileRestart("profile-a", "profile-b", false))
        assertFalse(PluginInstallPolicy.needsProfileRestart("profile-a", "profile-a", true))
        assertTrue(PluginInstallPolicy.needsProfileRestart("profile-a", "profile-b", true))
    }

    @Test
    fun reinstallingARebuiltPackageRequiresRestartEvenWhenItsVersionCodeIsUnchanged() {
        val loaded = mapOf("example.plugin" to "a".repeat(64))
        val rebuilt = mapOf("example.plugin" to "b".repeat(64))
        assertFalse(PluginInstallPolicy.needsPackageRestart(true, loaded, loaded))
        assertFalse(PluginInstallPolicy.needsPackageRestart(true, mapOf("example.plugin" to "A".repeat(64)), loaded))
        assertTrue(PluginInstallPolicy.needsPackageRestart(true, rebuilt, loaded))
        assertTrue(PluginInstallPolicy.needsPackageRestart(true, emptyMap(), loaded))
        assertTrue(PluginInstallPolicy.needsPackageRestart(true, loaded, emptyMap()))
        assertTrue(PluginInstallPolicy.needsPackageRestart(false, loaded, loaded))
        assertFalse(PluginInstallPolicy.needsPackageRestart(false, loaded, emptyMap()))
    }

    private fun metadata(pluginId: String, name: String) = JsonObject().apply {
        put("pluginId", pluginId)
        put("name", name)
        put("versionCode", 1)
        put("versionName", "1.0")
        put("apiVersion", "1.0")
        put("author", "Test author")
    }

    private fun manifest(pluginId: String, name: String, legacyId: Int = -1): PluginManifest {
        val json = metadata(pluginId, name).apply {
            put("entryClass", "example.Plugin")
            put("legacyServiceId", legacyId)
        }
        return PluginManifest.parse(JsonWriter.string(json))
    }

    private fun entry(
        pluginId: String,
        name: String,
        versionCode: Int = 1,
        apiVersion: String = "1.0",
        sha256: String = "0".repeat(64),
        minAndroidSdk: Int = 24
    ): PluginRepository.Entry {
        val entry = metadata(pluginId, name).apply {
            put("versionCode", versionCode)
            put("versionName", "$versionCode.0")
            put("apiVersion", apiVersion)
            put("minAndroidSdk", minAndroidSdk)
            put("package", "plugin.ppp")
            put("sha256", sha256)
            put("signature", "test-signature")
            put("publicKey", "BHXTB1DqhMt9/MVbz63M5/YPOkEtuDPh72ueB4DqUsc=")
        }
        val index = JsonObject().apply {
            put("formatVersion", 1)
            put("name", "Test repository")
            put("plugins", JsonArray(listOf(entry)))
        }
        return PluginRepository.parse("https://example.org/index.json", JsonWriter.string(index)).plugins.single()
    }
}
