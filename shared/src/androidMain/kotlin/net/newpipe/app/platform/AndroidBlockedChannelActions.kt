/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [BlockedChannelActions::class])
class AndroidBlockedChannelActions(context: Context) : BlockedChannelActions {
    private val host: BlockedChannelActions = try {
        Class.forName("org.schabi.newpipe.settings.ComposeBlockedChannelActions")
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as BlockedChannelActions
    } catch (error: ClassNotFoundException) {
        Logger.w(messageString = "No host blocked channel actions found", throwable = error)
        NoOpBlockedChannelActions
    }

    override fun getBlockedChannels() = host.getBlockedChannels()

    override suspend fun unblockChannel(url: String) = host.unblockChannel(url)
}
