/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.plugin

import android.content.Context
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import org.bouncycastle.util.encoders.Hex
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginManifest
import org.schabi.newpipe.extractor.plugin.PluginPackage
import org.schabi.newpipe.extractor.plugin.PluginRepository

/** A parsed catalog awaiting an explicit add or install confirmation. */
class PreparedLocalPluginCatalog internal constructor(
    val source: String,
    val json: String,
    internal val repository: PluginRepository
) {
    val name: String get() = repository.name
    val entries: List<PluginRepository.Entry> get() = repository.plugins
}

/** Untrusted package metadata and a private staged file; preparing it never loads its code. */
class PreparedPluginPackage internal constructor(
    internal val file: File,
    val manifest: PluginManifest,
    val sha256: String,
    val size: Long
) : Closeable {
    @Volatile private var closed = false

    /** A match chooses metadata for confirmation; installation still verifies its signature. */
    fun matches(entry: PluginRepository.Entry): Boolean = sha256.equals(entry.sha256, ignoreCase = true) &&
        runCatching { PluginInstallPolicy.checkManifest(entry, manifest, emptyMap()) }.isSuccess

    internal fun openStream(): InputStream {
        if (closed) throw PluginException("The selected plugin file is no longer available")
        return file.inputStream()
    }

    override fun close() {
        closed = true
        file.delete()
    }
}

/** Local transport is kept outside the public, HTTPS-oriented plugin SDK. */
internal object PluginLocalSources {
    const val MAX_CATALOG_BYTES = 2L * 1024 * 1024
    const val MAX_PACKAGE_BYTES = 32L * 1024 * 1024
    private const val LOCAL_HOST = "local-import.invalid"
    private val localId = Regex("local:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun isLocal(source: String): Boolean = localId.matches(source)

    fun requireSource(source: String): String = if (isLocal(source)) source else requireRemoteUrl(source)

    fun isLocalPackage(url: String): Boolean = runCatching { URI(url).host?.trimEnd('.').equals(LOCAL_HOST, ignoreCase = true) }.getOrDefault(false)

    fun requireRemoteUrl(url: String): String = PluginInstallPolicy.requireHttps(url).also {
        if (isLocalPackage(it)) throw PluginException("Select the local plugin package for this catalog entry")
    }

    fun prepareCatalog(input: InputStream, displayName: String): PreparedLocalPluginCatalog = parseCatalog("local:${UUID.randomUUID()}", readText(input), displayName)

    fun readCatalog(context: Context, source: String): PluginRepository = catalogFile(context, source).inputStream().use {
        parseCatalog(source, readText(it), "Local repository").repository
    }

    private fun parseCatalog(source: String, text: String, displayName: String): PreparedLocalPluginCatalog {
        requireLocal(source)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_CATALOG_BYTES) throw PluginException("Repository index is too large")
        val json = try {
            JsonParser.`object`().from(text)
        } catch (error: Exception) {
            throw PluginException("Invalid repository index", error)
        }
        val name = json.getString("name")?.takeIf { it.isNotBlank() }
            ?: displayName.trim().ifEmpty { "Local repository" }
        json["name"] = name
        val normalized = JsonWriter.string(json)
        if (normalized.toByteArray(Charsets.UTF_8).size > MAX_CATALOG_BYTES) throw PluginException("Repository index is too large")
        // This base exists only to let the unchanged SDK parse relative package references.
        // Neither it nor URLs resolved against it may ever reach the network downloader.
        val repository = PluginRepository.parse("https://$LOCAL_HOST/${source.removePrefix("local:")}/index.json", normalized)
        return PreparedLocalPluginCatalog(source, normalized, repository)
    }

    /** Returns whether a new snapshot was written, for rollback if preferences cannot be saved. */
    fun saveCatalog(context: Context, catalog: PreparedLocalPluginCatalog): Boolean {
        val validated = parseCatalog(catalog.source, catalog.json, catalog.name)
        val target = catalogFile(context, catalog.source)
        if (target.exists()) {
            val existing = target.inputStream().use(::readText)
            if (existing != validated.json) throw PluginException("This local repository identity is already in use")
            return false
        }
        val dir = target.parentFile!!
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create local repository directory")
        val temporary = File.createTempFile("catalog-", ".tmp", dir)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(validated.json.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) throw IOException("Cannot save local repository")
            return true
        } finally {
            temporary.delete()
        }
    }

    fun deleteCatalog(context: Context, source: String): Boolean = catalogFile(context, source).let { !it.exists() || it.delete() }

    private fun catalogFile(context: Context, source: String): File {
        requireLocal(source)
        return File(File(context.filesDir, "plugin-repositories"), source.removePrefix("local:") + ".json")
    }

    private fun requireLocal(source: String) {
        if (!isLocal(source)) throw PluginException("Invalid local repository identity")
    }

    fun preparePackage(context: Context, input: InputStream): PreparedPluginPackage {
        val dir = File(context.cacheDir, "plugin-imports")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot prepare local plugin file")
        val file = File.createTempFile("package-", ".ppp", dir)
        try {
            val size = FileOutputStream(file).use { output ->
                if (!file.setReadOnly()) throw IOException("Cannot protect selected plugin file")
                copyLimited(input, output, MAX_PACKAGE_BYTES, "Plugin package").also { output.fd.sync() }
            }
            val manifest = PluginPackage.readManifest(file)
            if (!PluginPackage.hasDex(file)) throw PluginException("Package has no Android code")
            return PreparedPluginPackage(file, manifest, Hex.toHexString(PluginPackage.sha256(file)), size)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun readText(input: InputStream): String {
        val bytes = ByteArrayOutputStream().use { output ->
            copyLimited(input, output, MAX_CATALOG_BYTES, "Repository index")
            output.toByteArray()
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun copyLimited(input: InputStream, output: OutputStream, limit: Long, label: String): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - total + 1).toInt())
            if (count < 0) return total
            if (count == 0) {
                val single = input.read()
                if (single < 0) return total
                if (++total > limit) throw PluginException("$label is too large")
                output.write(single)
            } else {
                total += count
                if (total > limit) throw PluginException("$label is too large")
                output.write(buffer, 0, count)
            }
        }
    }
}
