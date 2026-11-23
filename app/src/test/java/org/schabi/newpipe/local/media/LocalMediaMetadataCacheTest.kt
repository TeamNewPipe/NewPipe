/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalMediaMetadataCacheTest {
    @Test fun evictsLeastRecentlyUsedArtworkBeforeExceedingTheByteBudget() {
        val cache = LocalMediaMetadataCache(maxEntries = 8, maxArtworkBytes = 10)
        val first = metadata(4)
        cache.put("a", "a1", first, cache.generation())
        cache.put("b", "b1", metadata(4), cache.generation())
        assertSame(first, cache.get("a", "a1"))
        cache.put("c", "c1", metadata(4), cache.generation())
        assertNull(cache.get("b", "b1"))
        assertSame(first, cache.get("a", "a1"))
        assertEquals(8, cache.retainedArtworkBytes())
    }

    @Test fun oversizedOriginalArtworkIsNotRetainedOrChanged() {
        val cache = LocalMediaMetadataCache(maxEntries = 8, maxArtworkBytes = 10)
        val small = metadata(4)
        val large = metadata(11)
        val originalBytes = large.artwork!!
        cache.put("small", "v1", small, cache.generation())
        cache.put("large", "v1", large, cache.generation())
        assertNull(cache.get("large", "v1"))
        assertSame(small, cache.get("small", "v1"))
        assertSame(originalBytes, large.artwork)
        assertEquals(11, large.artwork!!.size)
        assertEquals(4, cache.retainedArtworkBytes())
    }

    @Test fun editedAndUnverifiableFilesInvalidateRetainedMetadata() {
        val cache = LocalMediaMetadataCache(maxArtworkBytes = 10)
        cache.put("same-uri", "old-file", metadata(8), cache.generation())
        assertNull(cache.get("same-uri", "new-file"))
        assertEquals(0, cache.retainedArtworkBytes())
        cache.put("same-uri", "new-file", metadata(6), cache.generation())
        assertNull(cache.get("same-uri", null))
        assertEquals(0, cache.retainedArtworkBytes())
    }

    @Test fun replacementAndEntryLimitsAccountForAllRetainedArtwork() {
        val cache = LocalMediaMetadataCache(maxEntries = 2, maxArtworkBytes = 10)
        cache.put("a", "v1", metadata(8), cache.generation())
        cache.put("a", "v2", metadata(3), cache.generation())
        assertEquals(3, cache.retainedArtworkBytes())
        cache.put("b", "v1", metadata(0), cache.generation())
        cache.put("c", "v1", metadata(0), cache.generation())
        assertNull(cache.get("a", "v2"))
        assertEquals(0, cache.retainedArtworkBytes())
    }

    @Test fun rescanPreventsLateLoadsFromRepopulatingTheCache() {
        val cache = LocalMediaMetadataCache(maxArtworkBytes = 10)
        val oldGeneration = cache.generation()
        cache.put("a", "v1", metadata(8), oldGeneration)
        cache.clear()
        cache.put("a", "v1", metadata(8), oldGeneration)
        assertNull(cache.get("a", "v1"))
        assertEquals(0, cache.retainedArtworkBytes())
        val current = metadata(4)
        cache.put("a", "v2", current, cache.generation())
        assertSame(current, cache.get("a", "v2"))
    }

    private fun metadata(size: Int) = LocalMediaEmbeddedMetadata(title = "Original title", artwork = ByteArray(size) { it.toByte() })
}
