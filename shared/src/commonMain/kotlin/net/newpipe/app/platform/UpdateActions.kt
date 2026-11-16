/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger

/**
 * Platform-specific actions triggered from the Updates settings screen
 */
interface UpdateActions {

    fun checkForUpdates()
}

internal object NoOpUpdateActions : UpdateActions {

    override fun checkForUpdates() = Logger.w(messageString = "Update action 'checkForUpdates' is unsupported on this platform")
}
