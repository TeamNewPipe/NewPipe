/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import net.newpipe.app.platform.UpdateActions
import org.schabi.newpipe.NewVersionWorker

class ComposeUpdateActions(private val context: Context) : UpdateActions {

    override fun checkForUpdates() = NewVersionWorker.enqueueNewVersionCheckingWork(context, isManual = true)
}
