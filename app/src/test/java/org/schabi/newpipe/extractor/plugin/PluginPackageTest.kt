/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginPackageTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val signingKey = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)
    private val publicKey = Base64.toBase64String(signingKey.generatePublicKey().encoded)

    @Test
    fun verifiesTheSignedDigestAndRejectsTamperingWrongKeysAndMalformedSignatures() {
        val file = packageFile(mapOf("plugin.json" to manifest.toByteArray(), "classes.dex" to byteArrayOf(1, 2, 3)))
        val digest = PluginPackage.sha256(file)
        val hash = Hex.toHexString(digest)
        val signature = sign(digest)
        PluginPackage.verify(file, hash, signature, publicKey)
        PluginPackage.verify(file, hash.uppercase(), signature, publicKey)
        assertTrue(PluginPackage.hasDex(file))
        assertEquals("org.example.fixture", PluginPackage.readManifest(file).pluginId)

        val wrongKey = Base64.toBase64String(Ed25519PrivateKeyParameters(ByteArray(32) { 42 }, 0).generatePublicKey().encoded)
        assertThrows(PluginException::class.java) { PluginPackage.verify(file, hash, signature, wrongKey) }
        assertThrows(PluginException::class.java) { PluginPackage.verify(file, "0".repeat(64), signature, publicKey) }
        assertFalse(PluginPackage.verifySignature(digest, "not base64", publicKey))
        assertFalse(PluginPackage.verifySignature(digest, signature, ""))
        file.appendBytes(byteArrayOf(4))
        assertThrows(PluginException::class.java) { PluginPackage.verify(file, hash, signature, publicKey) }
    }

    @Test
    fun packageReadersBoundManifestAndIconDataAndRequireTheManifest() {
        assertThrows(PluginException::class.java) { PluginPackage.readManifest(packageFile(mapOf("classes.dex" to byteArrayOf(1)))) }
        assertThrows(PluginException::class.java) { PluginPackage.readManifest(packageFile(mapOf("plugin.json" to ByteArray(65537) { 32 }))) }
        val withIcon = manifest.dropLast(1) + ",\"icon\":\"icon.png\"}"
        val file = packageFile(mapOf("plugin.json" to withIcon.toByteArray(), "icon.png" to ByteArray(262145)))
        assertThrows(PluginException::class.java) { PluginPackage.readIcon(file, PluginPackage.readManifest(file)) }
        assertFalse(PluginPackage.hasDex(file))
    }

    @Test
    fun catalogResolvesPackagesAndSkipsMalformedEntriesWithoutHidingValidPlugins() {
        val valid = entry()
        val invalidApi = entry().apply { put("apiVersion", ".") }
        val invalidVersion = entry().apply { put("versionCode", 1.5) }
        val invalidUrl = entry().apply { put("package", "file:///tmp/plugin.ppp") }
        val invalidKey = entry().apply { put("publicKey", "broken") }
        val repo = PluginRepository.parse("https://example.org/repo/index.json", catalog(valid, invalidApi, invalidVersion, invalidUrl, invalidKey))
        assertEquals(1, repo.plugins.size)
        assertEquals("https://example.org/repo/fixture-1.ppp", repo.plugins.single().packageUrl)
        assertEquals("pipepipe", repo.plugins.single().host)
        assertEquals(24, repo.plugins.single().minAndroidSdk)
        assertTrue(repo.plugins.single().isApiCompatible)
    }

    @Test
    fun nativeCatalogRequirementsAreCheckedBeforeThePackageIsDownloaded() {
        val native = entry().apply {
            put("host", "wizestream")
            put("apiVersion", "2.0")
            put("minHostVersionCode", 1051001)
            put("minAndroidSdk", 33)
            put("requiredCapabilities", JsonArray(listOf("profile-preferences")))
        }
        val entry = PluginRepository.parse("https://example.org/index.json", catalog(native)).plugins.single()
        assertEquals("wizestream", entry.host)
        assertEquals(33, entry.minAndroidSdk)
        assertTrue(entry.isApiCompatible)
        assertThrows(PluginException::class.java) { WizeStreamPluginApi.requireCompatible(entry, 1051000) }
        WizeStreamPluginApi.requireCompatible(entry, 1051001)
    }

    private fun entry(): JsonObject = JsonObject().apply {
        put("pluginId", "org.example.fixture")
        put("name", "Fixture")
        put("versionCode", 1)
        put("apiVersion", "1.0")
        put("package", "fixture-1.ppp")
        put("sha256", "0".repeat(64))
        put("signature", "unused until installation")
        put("publicKey", publicKey)
    }

    private fun catalog(vararg entries: JsonObject): String = JsonWriter.string(
        JsonObject().apply {
            put("formatVersion", 1)
            put("plugins", JsonArray(entries.toList()))
        }
    )

    private fun sign(digest: ByteArray): String = Base64.toBase64String(
        Ed25519Signer().run {
            init(true, signingKey)
            update(digest, 0, digest.size)
            generateSignature()
        }
    )

    private fun packageFile(entries: Map<String, ByteArray>): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, data) ->
                output.putNextEntry(ZipEntry(name))
                output.write(data)
                output.closeEntry()
            }
        }
    }

    companion object {
        private const val manifest = """{"pluginId":"org.example.fixture","name":"Fixture","versionCode":1,"apiVersion":"1.0","author":"Fixture author","entryClass":"org.example.FixturePlugin"}"""
    }
}
