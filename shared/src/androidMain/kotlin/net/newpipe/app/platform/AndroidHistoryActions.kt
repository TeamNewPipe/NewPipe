/*
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
   * SPDX-License-Identifier: GPL-3.0-or-later
   */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [HistoryActions::class])
class AndroidHistoryActions(
    private val legacyHooks: AndroidLegacyHooks
) : HistoryActions {

    override fun wipeMetadataCache() = legacyHooks.wipeMetadataCache()
    override fun deleteWatchHistory() = legacyHooks.deleteWatchHistory()
    override fun deletePlaybackStates() = legacyHooks.deletePlaybackStates()
    override fun deleteSearchHistory() = legacyHooks.deleteSearchHistory()
    override fun clearRecaptchaCookies() =
        legacyHooks.clearRecaptchaCookies()
    override fun hasRecaptchaCookies(): Boolean =
        legacyHooks.hasRecaptchaCookies()
}