/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi

/** Device-local credential: never stored in preferences, backups, profiles, or sync payloads. */
class VimeoApiTokenStore(context: Context, private val keyAlias: String = KEY_ALIAS) {
    private val file = AtomicFile(context.noBackupFilesDir.resolve(FILE_NAME))

    @Synchronized
    fun save(value: String): Boolean {
        var output: FileOutputStream? = null
        return try {
            val token = VimeoPublicApi.normalizeToken(value)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val bytes = byteArrayOf(VERSION, cipher.iv.size.toByte()) + cipher.iv + encrypted
            output = file.startWrite()
            output.write(bytes)
            file.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(file::failWrite)
            false
        }
    }

    @Synchronized
    fun read(): String? = try {
        if (file.baseFile.length() > MAX_FILE_BYTES) {
            null
        } else {
            val bytes = file.readFully()
            if (bytes.size !in 30..MAX_FILE_BYTES || bytes[0] != VERSION || bytes[1].toInt() !in 12..16) {
                null
            } else {
                val ivEnd = 2 + bytes[1].toInt()
                val key = keyStore().getKey(keyAlias, null) as? SecretKey
                key?.let {
                    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, it, GCMParameterSpec(128, bytes.copyOfRange(2, ivEnd)))
                    }
                    VimeoPublicApi.normalizeToken(String(cipher.doFinal(bytes.copyOfRange(ivEnd, bytes.size)), Charsets.UTF_8))
                }
            }
        }
    } catch (_: Exception) {
        // A missing or invalidated key disables optional API access without exposing the secret.
        null
    }

    @Synchronized
    fun clear(): Boolean {
        file.delete()
        return !file.baseFile.exists()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }

    private fun encryptionKey(): SecretKey = keyStore().getKey(keyAlias, null) as? SecretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()

    companion object {
        internal const val FILE_NAME = "vimeo_public_api_token_v1"
        private const val KEY_ALIAS = "wizestream_vimeo_public_api_v1"
        private const val KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val MAX_FILE_BYTES = 1_024
    }
}
