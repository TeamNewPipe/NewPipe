/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSeenLedgerTest {
    @Test
    fun identitiesAreStableForSameVideoButDifferentForOtherVideosOrServices() {
        val video = FeedSeenLedger.identity(0, "https://example.org/watch?v=one")
        assertEquals(video, FeedSeenLedger.identity(0, "https://example.org/watch?v=one"))
        assertNotEquals(video, FeedSeenLedger.identity(0, "https://example.org/watch?v=two"))
        assertNotEquals(video, FeedSeenLedger.identity(1, "https://example.org/watch?v=one"))
        assertEquals(32, video.length)
        assertEquals("", FeedSeenLedger.identity(0, ""))
    }

    @Test
    fun markersRoundTripWithoutMutatingAnyPlaybackState() {
        val first = FeedSeenLedger.identity(0, "https://example.org/first")
        val second = FeedSeenLedger.identity(0, "https://example.org/second")
        val ledger = FeedSeenLedger.restore(null)
        assertTrue(ledger.update(first, true))
        assertFalse(ledger.update(first, true))
        assertTrue(ledger.update(second, true))
        val restored = FeedSeenLedger.restore(ledger.serialize())
        assertTrue(restored.contains(first))
        assertTrue(restored.contains(second))
        assertTrue(restored.update(first, false))
        assertFalse(restored.contains(first))
        assertTrue(ledger.contains(first))
    }

    @Test
    fun oldestMarkersAreEvictedBeforeExceedingMemoryBudget() {
        val ledger = FeedSeenLedger.restore(null)
        for (number in 0..FeedSeenLedger.MAX_ENTRIES) {
            ledger.update(number.toString(16).padStart(32, '0'), true)
        }
        assertFalse(ledger.contains("0".padStart(32, '0')))
        assertTrue(ledger.contains(FeedSeenLedger.MAX_ENTRIES.toString(16).padStart(32, '0')))
        assertEquals(FeedSeenLedger.MAX_ENTRIES, ledger.serialize().split('\n').size)
    }

    @Test
    fun damagedOrLegacyPreferencesCannotBecomeArbitraryMarkers() {
        val valid = FeedSeenLedger.identity(0, "https://example.org/video")
        val ledger = FeedSeenLedger.restore("corrupt\n$valid\n$valid\nhttps://example.org/watch")
        assertEquals(valid, ledger.serialize())
        assertFalse(ledger.update("", true))
    }
}
