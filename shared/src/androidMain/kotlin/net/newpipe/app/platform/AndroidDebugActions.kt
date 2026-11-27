/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import org.koin.core.annotation.Singleton

/**
 * Forwards every debug action to a bridge supplied by the host app.
 */
@Singleton(binds = [DebugActions::class])
class AndroidDebugActions(context: Context) :
    DebugActions by (loadHost<DebugActions>(context, HOST_CLASS) ?: NoOpDebugActions) {

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeDebugActions"
    }
}
