/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [UpdateActions::class])
class AndroidUpdateActions(context: Context) : UpdateActions {

    private val host: UpdateActions = loadHost(context)

    override fun checkForUpdates() = host.checkForUpdates()

    private fun loadHost(context: Context): UpdateActions = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as UpdateActions
    }.getOrElse { error ->
        Logger.w(messageString = "No host update actions found, falling back to no-op", throwable = error)
        NoOpUpdateActions
    }

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeUpdateActions"
    }
}
