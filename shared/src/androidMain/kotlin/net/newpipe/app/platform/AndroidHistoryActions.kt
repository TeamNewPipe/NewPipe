/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

/**
 * Forwards every history action to a bridge supplied by the host app.
 */
@Singleton(binds = [HistoryActions::class])
class AndroidHistoryActions(context: Context) : HistoryActions by loadHost(context) {

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeHistoryActions"

        private fun loadHost(context: Context): HistoryActions = runCatching {
            Class.forName(HOST_CLASS)
                .getDeclaredConstructor(Context::class.java)
                .newInstance(context) as HistoryActions
        }.getOrElse { error ->
            Logger.w(
                messageString = "No host history actions found, falling back to no-op",
                throwable = error
            )
            NoOpHistoryActions
        }
    }
}
