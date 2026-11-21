/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedPaginationTest {
    @Test
    fun expandsOnlyWhenTheCurrentPageIsFull() {
        assertEquals(500, FeedPagination.nextLimit(500, 499))
        assertEquals(750, FeedPagination.nextLimit(500, 500))
        assertEquals(1_000, FeedPagination.nextLimit(750, 750))
    }

    @Test
    fun olderCachedPagesAdvanceInBoundedWindowsWithoutIncreasingUiRowCount() {
        val initial = FeedPagination.Window()
        assertEquals(null, FeedPagination.older(initial, 500))
        val last = FeedPagination.Window(limit = 3_000)
        assertEquals(null, FeedPagination.older(last, 2_999))

        val older = requireNotNull(FeedPagination.older(last, 3_000))
        assertEquals(3_000, older.offset)
        assertEquals(500, older.limit)
        assertEquals(750, FeedPagination.expanded(older, 500).limit)
        assertEquals(3_000, FeedPagination.expanded(older, 500).offset)

        assertEquals(initial, FeedPagination.newer(older))
        assertEquals(null, FeedPagination.newer(initial))
    }

    @Test
    fun expansionRemainsBoundedToAvoidHomeScreenMemoryRegression() {
        assertEquals(3_000, FeedPagination.nextLimit(2_750, 2_750))
        assertEquals(3_000, FeedPagination.nextLimit(3_000, 3_000))
    }
}
