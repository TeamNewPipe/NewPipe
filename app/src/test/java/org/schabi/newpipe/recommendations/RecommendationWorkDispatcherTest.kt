/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.recommendations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationWorkDispatcherTest {
    @Test
    fun rejectsQueuedUpdatesAfterProfileChangesOrReset() {
        assertTrue(RecommendationWorkDispatcher.isCurrent("first", "first", 1L, 1L, false))
        assertFalse(RecommendationWorkDispatcher.isCurrent("first", "other", 1L, 1L, false))
        assertFalse(RecommendationWorkDispatcher.isCurrent("first", "first", 1L, 2L, false))
    }

    @Test
    fun neverAppliesQueuedUpdatesInIncognito() {
        assertFalse(RecommendationWorkDispatcher.isCurrent("first", "first", 1L, 1L, true))
    }
}
