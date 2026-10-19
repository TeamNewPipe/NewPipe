/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [UpdateActions::class])
class AndroidUpdateActions : UpdateActions {

    override fun enqueueUpdateCheck(manual: Boolean) {
        // TODO: Wire to the app module's NewVersionWorker.enqueueNewVersionCheckingWork()
        //  once a hook is available. See UpdateSettingsFragment.
        Logger.w(messageString = "enqueueUpdateCheck() not wired yet (manual=$manual)")
    }
}