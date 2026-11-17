/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.model.BlockedChannel
import net.newpipe.app.platform.BlockedChannelActions
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BlockedChannelsViewModel(
    private val actions: BlockedChannelActions
) : ViewModel() {
    val blockedChannels = actions.getBlockedChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unblockChannel(channel: BlockedChannel) {
        viewModelScope.launch {
            actions.unblockChannel(channel.url)
        }
    }
}
