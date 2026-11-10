/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.database.block.BlockedChannelEntity
import org.schabi.newpipe.local.channel.BlockedChannelManager

class BlockedChannelsViewModel(application: Application) : AndroidViewModel(application) {
    private val blockedChannelManager = BlockedChannelManager(application)
    private val disposables = CompositeDisposable()
    private val mutableBlockedChannels = MutableStateFlow<List<BlockedChannelEntity>>(emptyList())
    val blockedChannels: StateFlow<List<BlockedChannelEntity>> = mutableBlockedChannels

    init {
        disposables.add(
            blockedChannelManager.blockedChannels()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { mutableBlockedChannels.value = it }
        )
    }

    fun unblockChannel(channel: BlockedChannelEntity) {
        disposables.add(
            blockedChannelManager.unblockChannel(channel.url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, { })
        )
    }

    override fun onCleared() {
        disposables.dispose()
        super.onCleared()
    }
}
