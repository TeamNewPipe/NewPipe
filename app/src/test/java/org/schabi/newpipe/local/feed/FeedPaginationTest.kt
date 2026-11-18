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
    fun expansionRemainsBoundedToAvoidHomeScreenMemoryRegression() {
        assertEquals(3_000, FeedPagination.nextLimit(2_750, 2_750))
        assertEquals(3_000, FeedPagination.nextLimit(3_000, 3_000))
    }
}
