/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.plugin

import android.content.Context
import android.os.Build
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * API-24 plugin DEX uses platform stream/function descriptors, while the API-23 app uses
 * desugared equivalents. Each side therefore owns its j$.util runtime, except the audited
 * DesugarCollections wrappers whose APIs match and whose serialized identity must be shared.
 * Extractor types, including j$.time.Duration, also resolve through the host class loader.
 */
object PluginRuntimeLoader {
    private const val ASSET_DIRECTORY = "plugin-runtime"
    private const val RUNTIME_ASSET = "$ASSET_DIRECTORY/desugar-api24.zip"
    private const val HASH_ASSET = "$ASSET_DIRECTORY/desugar-api24.sha256"
    private const val SHARED_COLLECTIONS = "j$.util.DesugarCollections"

    @Volatile private var runtime: ClassLoader? = null

    /** Creates one immutable runtime for all plugins; never loads executable code on API 23. */
    @JvmStatic
    @Synchronized
    fun parentForPlugins(context: Context): ClassLoader {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { "Executable plugins require Android 7 or newer" }
        runtime?.let { return it }
        val archive = extractRuntime(context)
        val optimized = File(archive.parentFile, "optimized").apply {
            if (!isDirectory && !mkdirs()) throw IOException("Cannot create plugin runtime cache")
        }
        return RuntimeClassLoader(archive, optimized, PluginRuntimeLoader::class.java.classLoader).also { runtime = it }
    }

    /** Includes serialized object-array descriptors, without initializing a runtime on restore. */
    @JvmStatic
    fun ownsClassName(name: String): Boolean {
        val component = name.dropWhile { it == '[' }
            .let { if (it.startsWith('L') && it.endsWith(';')) it.substring(1, it.length - 1) else it }
        return component.startsWith("j$.util.") && component != SHARED_COLLECTIONS &&
            !component.startsWith("$SHARED_COLLECTIONS\$")
    }

    /** Returns a class from an already loaded runtime, or null when none owns the name. */
    @JvmStatic
    fun findRuntimeClass(name: String): Class<*>? {
        if (!ownsClassName(name)) return null
        val loader = runtime ?: return null
        return try {
            Class.forName(name, false, loader)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private fun extractRuntime(context: Context): File {
        val expected = context.assets.open(HASH_ASSET).bufferedReader().use { it.readText().trim() }
        check(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid bundled plugin runtime checksum" }
        val directory = File(context.codeCacheDir, "plugin-runtime").apply {
            if (!isDirectory && !mkdirs()) throw IOException("Cannot create plugin runtime directory")
        }
        val destination = File(directory, "desugar-api24-$expected.zip")
        if (destination.isFile && sha256(destination) == expected) {
            if (!destination.setReadOnly()) throw IOException("Cannot protect plugin runtime")
            return destination
        }
        if (destination.exists() && !destination.delete()) throw IOException("Cannot replace invalid plugin runtime")
        val temporary = File.createTempFile("runtime-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                // Android 14 requires dynamically loaded archives to be read-only before writing.
                if (!temporary.setReadOnly()) throw IOException("Cannot protect plugin runtime")
                context.assets.open(RUNTIME_ASSET).use { it.copyTo(output) }
                output.fd.sync()
            }
            if (sha256(temporary) != expected) throw IOException("Bundled plugin runtime checksum mismatch")
            if (!temporary.renameTo(destination)) throw IOException("Cannot install bundled plugin runtime")
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class RuntimeClassLoader(archive: File, optimized: File, parent: ClassLoader?) :
        DexClassLoader(archive.absolutePath, optimized.absolutePath, null, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!ownsClassName(name)) return super.loadClass(name, resolve)
            return synchronized(this) {
                // Never fall back to the incompatible host utility classes for an isolated name.
                val loaded = findLoadedClass(name) ?: findClass(name)
                if (resolve) resolveClass(loaded)
                loaded
            }
        }
    }
}
