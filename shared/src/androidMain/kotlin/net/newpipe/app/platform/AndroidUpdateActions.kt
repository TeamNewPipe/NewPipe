/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

@Singleton(binds = [UpdateActions::class])
class AndroidUpdateActions(
    private val legacyHooks: AndroidLegacyHooks
) : UpdateActions {
    override fun runManualCheck() = legacyHooks.runManualUpdateCheck()
}