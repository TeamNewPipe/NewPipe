/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import org.koin.core.annotation.Singleton

/**
 * Forwards every update action to a bridge supplied by the host app.
 */
@Singleton(binds = [UpdateActions::class])
class AndroidUpdateActions(context: Context) :
    UpdateActions by (loadHost<UpdateActions>(context, HOST_CLASS) ?: NoOpUpdateActions) {

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeUpdateActions"
    }
}
