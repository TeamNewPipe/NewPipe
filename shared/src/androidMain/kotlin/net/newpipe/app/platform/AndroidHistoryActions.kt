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
class AndroidHistoryActions(context: Context) :
    HistoryActions by (loadHost<HistoryActions>(context, HOST_CLASS) ?: NoOpHistoryActions) {

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeHistoryActions"
    }
}
