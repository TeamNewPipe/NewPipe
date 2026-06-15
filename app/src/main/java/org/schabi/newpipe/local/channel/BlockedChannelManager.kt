/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.channel

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.block.BlockedChannelDAO
import org.schabi.newpipe.database.block.BlockedChannelEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class BlockedChannelManager(context: Context) {
    private val blockedChannelTable: BlockedChannelDAO =
        NewPipeDatabase.getInstance(context).blockedChannelDAO()

    fun blockedChannels(): Flowable<List<BlockedChannelEntity>> = blockedChannelTable.getAll()

    fun blockChannel(url: String, name: String): Completable = Completable.fromAction {
        blockedChannelTable.upsert(
            BlockedChannelEntity(
                url = url,
                name = name.ifBlank { url }
            )
        )
    }

    fun unblockChannel(url: String): Completable = Completable.fromAction {
        blockedChannelTable.deleteByUrl(url)
    }

    fun <T : InfoItem> filterList(items: List<T>): Single<List<T>> = Single.fromCallable {
        val blockedUrls = blockedChannelTable.getBlockedUrls().toHashSet()
        if (blockedUrls.isEmpty()) {
            items
        } else {
            items.filterNot { it.belongsToBlockedChannel(blockedUrls) }
        }
    }

    fun filterStreamInfo(info: StreamInfo): Single<StreamInfo> = filterList(info.relatedItems).map { filteredItems ->
        info.relatedItems = filteredItems
        info
    }

    private fun InfoItem.belongsToBlockedChannel(blockedUrls: Set<String>): Boolean {
        return when (this) {
            is StreamInfoItem -> !uploaderUrl.isNullOrBlank() && blockedUrls.contains(uploaderUrl)
            is ChannelInfoItem -> !url.isNullOrBlank() && blockedUrls.contains(url)
            else -> false
        }
    }
}
