/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSeenVisualsTest {
    @Test
    fun watchedTakesPrecedenceOverMerelySeen() {
        assertEquals(FeedItemVisualState.UNSEEN, FeedSeenVisuals.state(false, false))
        assertEquals(FeedItemVisualState.SEEN, FeedSeenVisuals.state(false, true))
        assertEquals(FeedItemVisualState.WATCHED, FeedSeenVisuals.state(true, false))
        assertEquals(FeedItemVisualState.WATCHED, FeedSeenVisuals.state(true, true))
        assertTrue(FeedItemVisualState.WATCHED.opacity < FeedItemVisualState.SEEN.opacity)
        assertTrue(FeedItemVisualState.SEEN.opacity < FeedItemVisualState.UNSEEN.opacity)
    }

    @Test
    fun playbackProgressDeterminesWatchedNotScrollMarkers() {
        assertFalse(FeedSeenVisuals.isWatched(300, null))
        assertFalse(FeedSeenVisuals.isWatched(300, 60_000))
        assertFalse(FeedSeenVisuals.isWatched(0, 60_000))
        assertTrue(FeedSeenVisuals.isWatched(300, 290_000))
    }
}
