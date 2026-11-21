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
    private const val MAX_WINDOW_START = 300_000

    /** A bounded UI window; older cached rows remain in SQLite rather than the active adapter. */
    data class Window(val limit: Int = INITIAL_LIMIT, val offset: Int = 0)

    fun expanded(window: Window, loadedCount: Int): Window {
        return window.copy(limit = nextLimit(window.limit, loadedCount))
    }

    fun older(window: Window, loadedCount: Int): Window? {
        if (window.limit != MAX_VISIBLE_ITEMS || loadedCount < MAX_VISIBLE_ITEMS ||
            window.offset > MAX_WINDOW_START - MAX_VISIBLE_ITEMS
        ) {
            return null
        }
        return Window(offset = window.offset + MAX_VISIBLE_ITEMS)
    }

    fun newer(window: Window): Window? = if (window.offset > 0) {
        Window(offset = (window.offset - MAX_VISIBLE_ITEMS).coerceAtLeast(0))
    } else {
        null
    }

    fun nextLimit(currentLimit: Int, loadedCount: Int): Int {
        return if (loadedCount < currentLimit || currentLimit >= MAX_VISIBLE_ITEMS) {
            currentLimit
        } else {
            (currentLimit + INCREMENT).coerceAtMost(MAX_VISIBLE_ITEMS)
        }
    }
}
