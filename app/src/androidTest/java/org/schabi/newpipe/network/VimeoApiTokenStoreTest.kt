/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.network

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VimeoApiTokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testId = UUID.randomUUID().toString()
    private val keyAlias = "wizestream_vimeo_api_test_$testId"
    private val testDirectory = context.cacheDir.resolve("vimeo-api-store-$testId")
    private val noBackupDirectory = testDirectory.resolve("no-backup")
    private val backedUpDirectory = testDirectory.resolve("files")
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir(): File = noBackupDirectory

        override fun getFilesDir(): File = backedUpDirectory
    }
    private val credentialFile get() = noBackupDirectory.resolve(VimeoApiTokenStore.FILE_NAME)
    private val store get() = VimeoApiTokenStore(isolatedContext, keyAlias)

    @Before
    fun createIsolatedStorage() {
        assertTrue(noBackupDirectory.mkdirs())
        assertTrue(backedUpDirectory.mkdirs())
    }

    @After
    fun removeIsolatedStorageAndKey() {
        try {
            keyStore().deleteEntry(keyAlias)
        } finally {
            testDirectory.deleteRecursively()
        }
    }

    @Test
    fun tokenSurvivesANewStoreInstanceOnlyAsRandomizedCiphertextInNoBackupStorage() {
        assertTrue(store.save(TOKEN))
        assertEquals(TOKEN, store.read())
        assertEquals(setOf(VimeoApiTokenStore.FILE_NAME), noBackupDirectory.list()!!.toSet())
        assertTrue(backedUpDirectory.listFiles()!!.isEmpty())
        assertTrue(keyStore().containsAlias(keyAlias))

        val firstCiphertext = credentialFile.readBytes()
        assertFalse(String(firstCiphertext, Charsets.ISO_8859_1).contains(TOKEN))
        assertTrue(store.save(TOKEN))
        val secondCiphertext = credentialFile.readBytes()
        assertFalse("Saving the same token must use a fresh encryption nonce", firstCiphertext.contentEquals(secondCiphertext))
        assertFalse(String(secondCiphertext, Charsets.ISO_8859_1).contains(TOKEN))
        assertEquals(TOKEN, store.read())
    }

    @Test
    fun invalidReplacementKeepsExistingCredentialAndClearIsIdempotent() {
        assertTrue(store.save(TOKEN))
        assertFalse(store.save("Bearer $TOKEN"))
        assertEquals(TOKEN, store.read())

        assertTrue(store.clear())
        assertNull(store.read())
        assertFalse(credentialFile.exists())
        assertTrue(noBackupDirectory.listFiles()!!.isEmpty())
        assertTrue(store.clear())
    }

    @Test
    fun missingOrReplacedKeystoreKeyDisablesOldCiphertextAndAllowsFreshSetup() {
        assertTrue(store.save(TOKEN))
        val oldCiphertext = credentialFile.readBytes()
        keyStore().deleteEntry(keyAlias)
        assertNull("An unavailable key must not expose or accept the stored credential", store.read())

        assertTrue(store.save(REPLACEMENT_TOKEN))
        assertEquals(REPLACEMENT_TOKEN, store.read())
        credentialFile.writeBytes(oldCiphertext)
        assertNull("Ciphertext from an invalidated key must fail authentication", store.read())

        assertTrue(store.save(TOKEN))
        assertEquals(TOKEN, store.read())
    }

    @Test
    fun modifiedCiphertextFailsClosedWithoutReturningAUsableToken() {
        assertTrue(store.save(TOKEN))
        val damaged = credentialFile.readBytes().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        credentialFile.writeBytes(damaged)
        assertNull(store.read())
        assertTrue(store.clear())
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        // Fictitious credentials; these tests never make an HTTP request.
        private const val TOKEN = "test-public-token"
        private const val REPLACEMENT_TOKEN = "replacement-public-token"
    }
}
