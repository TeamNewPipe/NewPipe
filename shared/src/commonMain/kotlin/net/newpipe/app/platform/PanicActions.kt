/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import net.newpipe.app.screen.settings.model.PanicApp

interface PanicActions {
    fun getPanicApp(packageName: String?): PanicApp?
}

internal object NoOpPanicActions : PanicActions {

    override fun getPanicApp(packageName: String?): PanicApp? {
        logUnsupported("getPanicApp")
        return null
    }

    private fun logUnsupported(action: String) = Logger.w(messageString = "Panic action '$action' is unsupported on this platform")
}
