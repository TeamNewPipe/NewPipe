/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger

/**
 * Platform-specific actions triggered from the History and cache settings screen.
 */
interface HistoryActions {

    fun wipeMetadataCache()
    suspend fun clearWatchHistory(): Boolean
    suspend fun clearPlaybackStates(): Boolean
    suspend fun clearSearchHistory(): Boolean
    fun clearRecaptchaCookies()
}

internal object NoOpHistoryActions : HistoryActions {

    override fun wipeMetadataCache() = logUnsupported("wipeMetadataCache")

    override suspend fun clearWatchHistory() = logUnsupportedAndFail("clearWatchHistory")

    override suspend fun clearPlaybackStates() = logUnsupportedAndFail("clearPlaybackStates")

    override suspend fun clearSearchHistory() = logUnsupportedAndFail("clearSearchHistory")

    override fun clearRecaptchaCookies() = logUnsupported("clearRecaptchaCookies")

    private fun logUnsupported(action: String) = Logger.w(messageString = "History action '$action' is unsupported on this platform")

    private fun logUnsupportedAndFail(action: String): Boolean {
        logUnsupported(action)
        return false
    }
}
