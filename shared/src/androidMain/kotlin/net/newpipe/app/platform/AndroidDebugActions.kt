/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

/**
 * Forwards every debug action to a bridge supplied by the host app.
 *
 */
@Singleton(binds = [DebugActions::class])
class AndroidDebugActions(context: Context) : DebugActions {

    private val host: DebugActions = loadHost(context)

    override val isLeakCanaryAvailable: Boolean
        get() = host.isLeakCanaryAvailable

    override fun showMemoryLeaks() = host.showMemoryLeaks()

    override fun checkNewStreams() = host.checkNewStreams()

    override fun createErrorNotification() = host.createErrorNotification()

    override fun reportDummyError() = host.reportDummyError()

    private fun loadHost(context: Context): DebugActions = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as DebugActions
    }.getOrElse { error ->
        Logger.w(messageString = "No host debug actions found, falling back to no-op", throwable = error)
        NoOpDebugActions
    }

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeDebugActions"
    }
}
