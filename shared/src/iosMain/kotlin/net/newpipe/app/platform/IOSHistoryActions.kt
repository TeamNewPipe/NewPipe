/*
  * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
  * SPDX-License-Identifier: GPL-3.0-or-later
  */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

/**
 * iOS implementation of [HistoryActions].
 *
 * Real impl will hook into the iOS-side history store once that lands; for
now
 * every method is a logged no-op so the iOS target still compiles.
 */
@Singleton(binds = [HistoryActions::class])
class IOSHistoryActions : HistoryActions {
    override fun wipeMetadataCache() {
        Logger.i(messageString = "wipeMetadataCache not implemented on iOS")
    }
    override fun deleteWatchHistory() {
        Logger.i(messageString = "deleteWatchHistory not implemented on iOS")
    }
    override fun deletePlaybackStates() {
        Logger.i(messageString = "deletePlaybackStates not implemented on iOS")
    }
    override fun deleteSearchHistory() {
        Logger.i(messageString = "deleteSearchHistory not implemented on iOS")
    }
    override fun clearRecaptchaCookies() {
        Logger.i(messageString = "clearRecaptchaCookies not implemented on iOS")
    }
    override fun hasRecaptchaCookies(): Boolean = false
}

