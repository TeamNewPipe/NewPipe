/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [UpdateActions::class])
class IOSUpdateActions : UpdateActions {
    override fun runManualCheck() {
        Logger.i(messageString = "runManualCheck not implemented on iOS")
    }
}