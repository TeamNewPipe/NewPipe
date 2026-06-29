/*                                                                           
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>         
   * SPDX-License-Identifier: GPL-3.0-or-later                                 
   */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [HistoryActions::class])
class JVMHistoryActions : HistoryActions {
    override fun wipeMetadataCache() {
        Logger.i(messageString = "wipeMetadataCache not implemented on JVM")
    }
    override fun deleteWatchHistory() {
        Logger.i(messageString = "deleteWatchHistory not implemented on JVM")
    }
    override fun deletePlaybackStates() {
        Logger.i(messageString = "deletePlaybackStates not implemented on JVM")
    }
    override fun deleteSearchHistory() {
        Logger.i(messageString = "deleteSearchHistory not implemented on JVM")
    }
    override fun clearRecaptchaCookies() {
        Logger.i(messageString = "clearRecaptchaCookies not implemented on JVM")
    }
    override fun hasRecaptchaCookies(): Boolean = false
}