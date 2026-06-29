/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from the History & Cache settings screen.
 */
interface HistoryActions {
    fun wipeMetadataCache()
    fun deleteWatchHistory()
    fun deletePlaybackStates()
    fun deleteSearchHistory()

    fun clearRecaptchaCookies()
    fun hasRecaptchaCookies(): Boolean
}

