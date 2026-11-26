/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.plugin

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginPackage
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PluginLocalSourcesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val state get() = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)
    private val key = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)
    private val otherKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 1).toByte() }, 0)

    @Before
    fun prepare() {
        clear()
        PluginManager.setEnabled(context, true)
    }

    @After
    fun clear() {
        state.edit().clear().commit()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        KidsMode.lock()
        File(context.filesDir, "plugins").deleteRecursively()
        File(context.filesDir, "plugin-repositories").deleteRecursively()
        File(context.cacheDir, "plugin-imports").deleteRecursively()
    }

    @Test
    fun importedCatalogPersistsWithoutItsOriginalDocumentAndPreservesRemoteRepositories() {
        val fixture = fixture()
        val document = File(context.cacheDir, "selected-index.json").apply { writeText(fixture.index) }
        val catalog = document.inputStream().use { PluginManager.prepareLocalCatalog(it, "Selected index") }
        assertTrue(document.delete())
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
        assertEquals("Fixture catalog", catalog.name)
        assertTrue(PluginManager.isLocalRepository(catalog.source))
        PluginManager.addRepository(context, REMOTE)
        assertEquals(catalog.source, PluginManager.addLocalRepository(context, catalog))
        PluginManager.addLocalRepository(context, catalog)
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY, REMOTE, catalog.source), PluginManager.getRepositories(context))

        val reloaded = PluginManager.fetchRepository(ContextWrapper(context), catalog.source)
        assertEquals(catalog.name, reloaded.name)
        assertEquals(fixture.entry.pluginId, reloaded.plugins.single().pluginId)
        assertEquals(fixture.entry.sha256, reloaded.plugins.single().sha256)
        assertTrue(PluginManager.requiresLocalPackage(catalog.source, reloaded.plugins.single()))
        PluginManager.removeRepository(context, catalog.source)
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY, REMOTE), PluginManager.getRepositories(context))
        assertTrue(File(context.filesDir, "plugin-repositories").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun localCatalogNeverDownloadsPackagesEvenWhenAnEntryContainsAnAbsoluteHttpsUrl() {
        val signed = fixture().also { it.metadata["package"] = "https://publisher.example/plugin.ppp" }
        val catalog = signed.localCatalog()
        val entry = catalog.entries.single()
        assertFalse(PluginManager.requiresLocalPackage(entry))
        assertTrue(PluginManager.requiresLocalPackage(catalog.source, entry))
        assertThrows(PluginException::class.java) { PluginManager.install(context, catalog.source, entry) }
        val relative = fixture().localCatalog().entries.single()
        assertThrows(PluginException::class.java) { PluginManager.fetchSourceText(relative.packageUrl) }
        assertThrows(PluginException::class.java) { PluginManager.addRepository(context, relative.packageUrl) }
        assertTrue(PluginManager.getInstalled(context).isEmpty())
    }

    @Test
    fun stagedNativeAndCompatiblePackagesInstallUsingCompanionCatalogsWithoutLoadingCode() {
        for (host in listOf("wizestream", "pipepipe")) {
            val fixture = fixture(id = "org.example.imports.$host", name = "Fixture $host", host = host)
            val catalog = fixture.localCatalog()
            fixture.bytes.inputStream().use { input ->
                PluginManager.prepareLocalPackage(context, input).use { prepared ->
                    assertTrue(prepared.matches(catalog.entries.single()))
                    assertEquals(fixture.bytes.size.toLong(), prepared.size)
                    assertEquals(fixture.entry.sha256, prepared.sha256)
                    assertFalse(PluginManager.isLoaded(prepared.manifest.pluginId))
                    PluginManager.installLocalPackage(context, catalog.source, catalog.entries.single(), prepared)
                    val installed = PluginManager.getInstalled(context).single { it.pluginId == fixture.entry.pluginId }
                    assertEquals(catalog.source, installed.repositoryUrl)
                    assertEquals(fixture.entry.publicKey, PluginManager.trustedPublisher(context, installed.pluginId))
                    val packageFile = File(File(context.filesDir, "plugins"), installed.fileName)
                    assertArrayEquals(fixture.bytes, packageFile.readBytes())
                    PluginPackage.verify(packageFile, installed.sha256, installed.signature, installed.publicKey)
                    assertFalse(PluginManager.isLoaded(installed.pluginId))
                }
            }
        }
        assertTrue(File(context.cacheDir, "plugin-imports").listFiles().orEmpty().isEmpty())
        // Installing one selected file does not also subscribe to its companion catalog.
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
    }

    @Test
    fun remoteCatalogMetadataCanVerifyLocalBytesAndTheExistingDownloadedUpdatePathStillWorks() {
        val first = fixture()
        first.bytes.inputStream().use { PluginManager.installLocalPackage(context, REMOTE, first.entry, it) }
        val second = fixture(version = 2)
        PluginManager.installDownloaded(context, REMOTE, second.entry, second.bytes)
        val installed = PluginManager.getInstalled(context).single()
        assertEquals(2, installed.versionCode)
        assertEquals(REMOTE, installed.repositoryUrl)
        assertEquals(first.entry.publicKey, installed.publicKey)
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
    }

    @Test
    fun installationRechecksTheStagedBytesAfterPreviewAndKeepsThePriorReceiptOnFailure() {
        install(fixture())
        val before = state.all
        val update = fixture(version = 2)
        PluginManager.prepareLocalPackage(context, update.bytes.inputStream()).use { prepared ->
            assertTrue(prepared.matches(update.entry))
            assertTrue(prepared.file.setWritable(true))
            prepared.file.appendBytes(byteArrayOf(42))
            assertThrows(PluginException::class.java) { PluginManager.installLocalPackage(context, REMOTE, update.entry, prepared) }
        }
        assertEquals(before, state.all)
        assertEquals(1, PluginManager.getInstalled(context).single().versionCode)
        assertFalse(File(context.filesDir, "plugins").listFiles().orEmpty().any { it.name.startsWith("install-") })
    }

    @Test
    fun updatesRetainPublisherAndVersionRulesIncludingAfterUninstall() {
        val original = fixture(version = 2)
        install(original)
        val before = state.all
        for (invalid in listOf(fixture(version = 1), fixture(version = 2), fixture(version = 3, signingKey = otherKey))) {
            assertThrows(PluginException::class.java) { install(invalid) }
            assertEquals(before, state.all)
        }
        PluginManager.uninstall(context, original.entry.pluginId)
        assertTrue(PluginManager.getInstalled(context).isEmpty())
        assertEquals(original.entry.publicKey, PluginManager.trustedPublisher(context, original.entry.pluginId))
        val uninstalled = state.all
        assertThrows(PluginException::class.java) { install(fixture(version = 3, signingKey = otherKey)) }
        assertEquals(uninstalled, state.all)
    }

    @Test
    fun invalidSignaturesMetadataBuiltInsAndRequirementsCannotCreateAnInstallationOrPublisherPin() {
        val invalid = listOf(
            fixture().also { it.metadata["signature"] = "invalid signature" },
            fixture().also { it.metadata["name"] = "Changed after signing" },
            fixture().also { it.metadata["package"] = "http://publisher.example/plugin.ppp" },
            fixture(name = "YouTube"),
            fixture(manifestValues = mapOf("legacyServiceId" to 10)),
            fixture(manifestValues = mapOf("minHostVersionCode" to BuildConfig.VERSION_CODE + 1)),
            fixture(manifestValues = mapOf("minAndroidSdk" to 35)),
            fixture(manifestValues = mapOf("requiredCapabilities" to JsonArray(listOf("unknown-capability"))))
        )
        val before = state.all
        for (candidate in invalid) {
            assertThrows(PluginException::class.java) { install(candidate) }
            assertEquals(before, state.all)
            assertTrue(PluginManager.getInstalled(context).isEmpty())
            assertNull(PluginManager.trustedPublisher(context, candidate.entry.pluginId))
        }
    }

    @Test
    fun disablingPluginsOrLockingParentAccessAfterPreviewPreventsInstallation() {
        val fixture = fixture()
        PluginManager.prepareLocalPackage(context, fixture.bytes.inputStream()).use { prepared ->
            PluginManager.setEnabled(context, false)
            assertThrows(PluginException::class.java) { PluginManager.installLocalPackage(context, REMOTE, fixture.entry, prepared) }
            assertThrows(PluginException::class.java) { PluginManager.addRepository(context, REMOTE) }
            assertThrows(PluginException::class.java) { PluginManager.addLocalRepository(context, fixture.localCatalog()) }
            PluginManager.setEnabled(context, true)
            assertTrue(KidsMode.setPin(context, "1234"))
            KidsMode.grantParentAccess()
            assertTrue(KidsMode.setEnabled(context, ProfileManager.getActiveProfileId(context), true))
            KidsMode.lock()
            assertThrows(PluginException::class.java) { PluginManager.installLocalPackage(context, REMOTE, fixture.entry, prepared) }
            assertThrows(PluginException::class.java) { PluginManager.addLocalRepository(context, fixture.localCatalog()) }
            assertTrue(PluginManager.getInstalled(context).isEmpty())
        }
    }

    @Test
    fun catalogAndPackageStreamsAreBoundedAndFailedPreparationLeavesNoStagedPackage() {
        val unnamed = fixture().index.replace("\"Fixture catalog\"", "\"\"")
        assertEquals("Offline index", PluginManager.prepareLocalCatalog(unnamed.byteInputStream(), "Offline index").name)
        assertThrows(PluginException::class.java) { PluginManager.fetchRepository(context, "local:../../outside.json") }
        val largeCatalog = CountingInput(PluginLocalSources.MAX_CATALOG_BYTES + 100)
        assertThrows(PluginException::class.java) { PluginManager.prepareLocalCatalog(largeCatalog) }
        assertEquals(PluginLocalSources.MAX_CATALOG_BYTES + 1, largeCatalog.readCount)
        val largePackage = CountingInput(PluginLocalSources.MAX_PACKAGE_BYTES + 100)
        assertThrows(PluginException::class.java) { PluginManager.prepareLocalPackage(context, largePackage) }
        assertEquals(PluginLocalSources.MAX_PACKAGE_BYTES + 1, largePackage.readCount)
        assertThrows(Exception::class.java) { PluginManager.prepareLocalCatalog(byteArrayOf(0xc3.toByte(), 0x28).inputStream()) }
        assertThrows(PluginException::class.java) { PluginManager.prepareLocalPackage(context, byteArrayOf(1, 2, 3).inputStream()) }
        assertThrows(PluginException::class.java) { PluginManager.prepareLocalPackage(context, fixture(includeDex = false).bytes.inputStream()) }
        assertTrue(File(context.cacheDir, "plugin-imports").listFiles().orEmpty().isEmpty())
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
    }

    @Test
    fun snapshotWriteRollsBackWhenRepositoryPreferencesCannotBeCommitted() {
        val catalog = fixture().localCatalog()
        val failing = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = super.getSharedPreferences(name, mode)
                if (name != "plugins") return preferences
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = preferences.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }

                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }
        assertThrows(IllegalStateException::class.java) { PluginManager.addLocalRepository(failing, catalog) }
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
        assertTrue(File(context.filesDir, "plugin-repositories").listFiles().orEmpty().isEmpty())
    }

    private fun install(fixture: Fixture) {
        fixture.bytes.inputStream().use { PluginManager.installLocalPackage(context, REMOTE, fixture.entry, it) }
    }

    private fun fixture(
        version: Int = 1,
        id: String = "org.example.imports.fixture",
        name: String = "Import fixture",
        host: String = "wizestream",
        signingKey: Ed25519PrivateKeyParameters = key,
        includeDex: Boolean = true,
        manifestValues: Map<String, Any> = emptyMap()
    ): Fixture {
        val manifest = JsonObject().apply {
            put("pluginId", id)
            put("name", name)
            put("versionCode", version)
            put("versionName", "$version.0")
            put("host", host)
            put("apiVersion", if (host == "wizestream") "2.0" else "1.1")
            put("author", "Fixture author")
            put("entryClass", "org.example.imports.MustNotBeLoaded")
            putAll(manifestValues)
        }
        val files = linkedMapOf("plugin.json" to JsonWriter.string(manifest).toByteArray())
        if (includeDex) files["classes.dex"] = byteArrayOf(1, 2, 3)
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for ((path, data) in files) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val signature = Ed25519Signer().run {
            init(true, signingKey)
            update(digest, 0, digest.size)
            generateSignature()
        }
        val metadata = JsonObject(manifest).apply {
            remove("entryClass")
            put("package", "fixture-$version.ppp")
            put("sha256", Hex.toHexString(digest))
            put("signature", Base64.toBase64String(signature))
            put("publicKey", Base64.toBase64String(signingKey.generatePublicKey().encoded))
        }
        return Fixture(bytes, metadata)
    }

    private class Fixture(val bytes: ByteArray, val metadata: JsonObject) {
        val index: String get() = JsonWriter.string(
            JsonObject().apply {
                put("formatVersion", 1)
                put("name", "Fixture catalog")
                put("plugins", JsonArray(listOf(metadata)))
            }
        )

        val entry: PluginRepository.Entry get() = PluginRepository.parse(REMOTE, index).plugins.single()

        fun localCatalog(): PreparedLocalPluginCatalog = index.byteInputStream().use { PluginManager.prepareLocalCatalog(it) }
    }

    private class CountingInput(private val length: Long) : InputStream() {
        var readCount = 0L
            private set

        override fun read(): Int = if (readCount < length) {
            readCount++
            0
        } else {
            -1
        }

        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (readCount == length) return -1
            val actual = minOf(count.toLong(), length - readCount).toInt()
            buffer.fill(0, offset, offset + actual)
            readCount += actual
            return actual
        }
    }

    companion object {
        private const val REMOTE = "https://publisher.example/repository/index.json"
    }
}
