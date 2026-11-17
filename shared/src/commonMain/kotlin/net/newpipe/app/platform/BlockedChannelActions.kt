/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.newpipe.app.model.BlockedChannel

interface BlockedChannelActions {
    fun getBlockedChannels(): Flow<List<BlockedChannel>>
    suspend fun unblockChannel(url: String)
}

internal object NoOpBlockedChannelActions : BlockedChannelActions {
    override fun getBlockedChannels(): Flow<List<BlockedChannel>> = flow {
        logUnsupported("getBlockedChannels")
        emit(emptyList())
    }

    override suspend fun unblockChannel(url: String) = logUnsupported("unblockChannel")

    private fun logUnsupported(action: String) = Logger.w(messageString = "Blocked channel action '$action' is unsupported on this platform")
}
