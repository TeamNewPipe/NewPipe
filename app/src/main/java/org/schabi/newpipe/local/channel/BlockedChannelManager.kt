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
        filterBlocked(items, blockedChannelTable.getBlockedUrls().toHashSet())
    }

    fun filterStreamInfo(info: StreamInfo): Single<StreamInfo> = filterList(info.relatedItems).map { filteredItems ->
        info.relatedItems = filteredItems
        info
    }

    companion object {
        /**
         * Removes the streams uploaded by a blocked channel, and the blocked channels themselves.
         * Items that are neither streams nor channels (e.g. playlists) are always kept.
         */
        @JvmStatic
        fun <T : InfoItem> filterBlocked(items: List<T>, blockedUrls: Set<String>): List<T> {
            if (blockedUrls.isEmpty()) {
                return items
            }
            return items.filterNot { it.belongsToBlockedChannel(blockedUrls) }
        }

        private fun InfoItem.belongsToBlockedChannel(blockedUrls: Set<String>): Boolean {
            return when (this) {
                is StreamInfoItem -> !uploaderUrl.isNullOrBlank() && blockedUrls.contains(uploaderUrl)
                is ChannelInfoItem -> !url.isNullOrBlank() && blockedUrls.contains(url)
                else -> false
            }
        }
    }
}
