/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

/** Retains original artwork within a byte budget; oversized covers are returned without caching. */
internal class LocalMediaMetadataCache(
    private val maxEntries: Int = 128,
    private val maxArtworkBytes: Int = 8 * 1024 * 1024
) {
    private data class Entry(val identity: String, val metadata: LocalMediaEmbeddedMetadata) {
        val artworkBytes = metadata.artwork?.size ?: 0
    }

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var artworkBytes = 0
    private var epoch = 0L

    init {
        require(maxEntries > 0 && maxArtworkBytes >= 0)
    }

    @Synchronized
    fun generation(): Long = epoch

    @Synchronized
    fun get(uri: String, identity: String?): LocalMediaEmbeddedMetadata? {
        val entry = entries[uri] ?: return null
        if (identity != null && entry.identity == identity) return entry.metadata
        remove(uri)
        return null
    }

    @Synchronized
    fun put(uri: String, identity: String, metadata: LocalMediaEmbeddedMetadata, generation: Long) {
        if (generation != epoch) return
        remove(uri)
        val entry = Entry(identity, metadata)
        if (entry.artworkBytes > maxArtworkBytes) return
        while (entries.isNotEmpty() && (entries.size >= maxEntries || entry.artworkBytes > maxArtworkBytes - artworkBytes)) {
            remove(entries.keys.first())
        }
        entries[uri] = entry
        artworkBytes += entry.artworkBytes
    }

    @Synchronized
    fun clear() {
        epoch++
        entries.clear()
        artworkBytes = 0
    }

    @Synchronized
    internal fun retainedArtworkBytes(): Int = artworkBytes

    private fun remove(uri: String) {
        entries.remove(uri)?.let { artworkBytes -= it.artworkBytes }
    }
}
