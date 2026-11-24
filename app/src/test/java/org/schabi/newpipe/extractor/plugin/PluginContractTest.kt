/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.youtube.YoutubeService

class PluginContractTest {
    @Test
    fun legacyPackagesKeepTheirContractAndCannotAccidentallySelectTheNativeApi() {
        for (version in listOf("1.0", "1.1")) {
            val manifest = manifest(api = version)
            assertEquals("pipepipe", manifest.host)
            assertTrue(manifest.isApiCompatible)
            assertEquals(0, manifest.minHostVersionCode)
            assertEquals(24, manifest.minAndroidSdk)
            assertTrue(manifest.requiredCapabilities.isEmpty())
        }
        for (version in listOf("1.2", "2.0", "0.1")) {
            assertFalse(manifest(api = version).isApiCompatible)
        }
        assertFalse(PluginManifest.isApiCompatible(1, -1))
        assertFalse(manifest(api = "1.1", extra = mapOf("host" to "wizestream")).isApiCompatible)
        assertFalse(manifest(extra = mapOf("host" to "another-host")).isApiCompatible)
    }

    @Test
    fun nativePackagesRequireAnExplicitHostSupportedVersionAndCapabilities() {
        val native = manifest(
            api = "2.0",
            extra = mapOf(
                "host" to "wizestream",
                "minHostVersionCode" to 1051000,
                "requiredCapabilities" to JsonArray(listOf("profile-preferences"))
            )
        )
        assertTrue(native.isApiCompatible)
        WizeStreamPluginApi.requireCompatible(native, 1051000)
        assertThrows(PluginException::class.java) { WizeStreamPluginApi.requireCompatible(native, 1050999) }
        val unsupported = manifest(api = "2.0", extra = mapOf("host" to "wizestream", "requiredCapabilities" to JsonArray(listOf("unknown-capability"))))
        assertThrows(PluginException::class.java) { WizeStreamPluginApi.requireCompatible(unsupported, 1051000) }
        assertFalse(manifest(api = "2.1", extra = mapOf("host" to "wizestream")).isApiCompatible)
    }

    @Test
    fun malformedVersionsAndUnsafeManifestPathsAreRejected() {
        for (api in listOf("1", "1.0.0", "-1.0", "1.-1", "999999999999999999.0")) {
            assertThrows(api, PluginException::class.java) { manifest(api = api) }
        }
        for (code in listOf(-1, 1.5, Int.MAX_VALUE.toLong() + 1)) {
            assertThrows(PluginException::class.java) { manifest(extra = mapOf("versionCode" to code)) }
        }
        for (path in listOf("../icon.png", "/icon.png", "icons/../../icon.png")) {
            assertThrows(PluginException::class.java) { manifest(extra = mapOf("icon" to path)) }
        }
        assertThrows(PluginException::class.java) { manifest(extra = mapOf("requiredCapabilities" to "profile-preferences")) }
        for (sdk in listOf(-1, 0, 24.5, 10001)) {
            assertThrows(PluginException::class.java) { manifest(extra = mapOf("minAndroidSdk" to sdk)) }
        }
        assertEquals(33, manifest(extra = mapOf("minAndroidSdk" to 33)).minAndroidSdk)
    }

    @Test
    fun loadingPreservesServiceIdentityAndSelectsOnlyTheDeclaredEntryContract() {
        val context = context()
        val pipe = PluginLoader.createService(javaClass.classLoader, manifest(), 12345, context)
        assertEquals(12345, pipe.serviceId)
        assertEquals("Fixture", pipe.serviceInfo.name)

        val native = manifest(api = "2.0", entryClass = NativeFixturePlugin::class.java.name, extra = mapOf("host" to "wizestream"))
        val nativeService = PluginLoader.createService(javaClass.classLoader, native, 12346, context)
        assertEquals(12346, nativeService.serviceId)
        assertEquals("profile-a", NativeFixturePlugin.lastContext!!.getProfileId())
        assertEquals(1051000, NativeFixturePlugin.lastContext!!.getHostVersionCode())

        assertThrows(PluginException::class.java) {
            PluginLoader.createService(javaClass.classLoader, manifest(extra = mapOf("name" to "Wrong")), 12345, context)
        }
        assertThrows(PluginException::class.java) {
            PluginLoader.createService(javaClass.classLoader, manifest(entryClass = WrongIdFixturePlugin::class.java.name), 12345, context)
        }
        assertThrows(PluginException::class.java) {
            PluginLoader.createService(javaClass.classLoader, manifest(api = "2.0", extra = mapOf("host" to "wizestream")), 12345, context)
        }
        assertThrows(PluginException::class.java) {
            PluginLoader.createService(javaClass.classLoader, manifest(entryClass = NativeFixturePlugin::class.java.name), 12345, context)
        }
    }

    @Test
    fun unsupportedNativeRequirementsFailBeforeEntryConstruction() {
        NativeFixturePlugin.constructions = 0
        val native = manifest(
            api = "2.0",
            entryClass = NativeFixturePlugin::class.java.name,
            extra = mapOf("host" to "wizestream", "minHostVersionCode" to 1051001)
        )
        assertThrows(PluginException::class.java) {
            PluginLoader.createService(javaClass.classLoader, native, 12345, context())
        }
        assertEquals(0, NativeFixturePlugin.constructions)
    }

    class PipeFixturePlugin : PipePipePlugin {
        override fun createService(serviceId: Int, context: PluginContext): StreamingService = NamedService(serviceId, "Fixture")
    }

    class WrongIdFixturePlugin : PipePipePlugin {
        override fun createService(serviceId: Int, context: PluginContext): StreamingService = NamedService(serviceId + 1, "Fixture")
    }

    class NativeFixturePlugin : WizeStreamPlugin {
        init {
            constructions++
        }

        override fun createService(serviceId: Int, context: WizeStreamPluginContext): StreamingService {
            lastContext = context
            return NamedService(serviceId, "Fixture")
        }

        companion object {
            var constructions = 0
            var lastContext: WizeStreamPluginContext? = null
        }
    }

    internal class NamedService(id: Int, private val displayName: String, private val fixtureBaseUrl: String = "https://$id.example.org") : YoutubeService(id) {
        override fun getServiceInfo(): ServiceInfo = ServiceInfo(displayName, emptyList())

        override fun getBaseUrl(): String = fixtureBaseUrl
    }

    private fun context(): WizeStreamPluginContext = object : WizeStreamPluginContext {
        override fun getHostVersionCode(): Int = 1051000
        override fun getHostVersionName(): String = "1.51.0"
        override fun getProfileId(): String = "profile-a"
        override fun getPluginId(): String = "org.example.fixture"
        override fun getPreferences(): PluginPreferences = object : PluginPreferences {
            override fun getString(key: String): String? = null
            override fun getBoolean(key: String): Boolean = false
            override fun putString(key: String, value: String?) = Unit
            override fun putBoolean(key: String, value: Boolean) = Unit
            override fun addChangeListener(listener: PluginPreferences.ChangeListener) = Unit
        }
    }

    private fun manifest(
        api: String = "1.0",
        entryClass: String = PipeFixturePlugin::class.java.name,
        extra: Map<String, Any?> = emptyMap()
    ): PluginManifest {
        val json = JsonObject().apply {
            put("pluginId", "org.example.fixture")
            put("name", "Fixture")
            put("versionCode", 1)
            put("apiVersion", api)
            put("author", "Fixture author")
            put("entryClass", entryClass)
            extra.forEach { (key, value) -> put(key, value) }
        }
        return PluginManifest.parse(JsonWriter.string(json))
    }
}
