/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

/** Incremental feed expansion. Bounded until a virtualized feed list is introduced. */
internal object FeedPagination {
    const val INITIAL_LIMIT = 500
    private const val INCREMENT = 250
    private const val MAX_VISIBLE_ITEMS = 3_000

    fun nextLimit(currentLimit: Int, loadedCount: Int): Int =
        if (loadedCount < currentLimit || currentLimit >= MAX_VISIBLE_ITEMS) {
            currentLimit
        } else {
            (currentLimit + INCREMENT).coerceAtMost(MAX_VISIBLE_ITEMS)
        }
}
