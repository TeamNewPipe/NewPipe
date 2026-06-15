/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.database.block.BlockedChannelEntity
import org.schabi.newpipe.databinding.ItemBlockedChannelBinding

class BlockedChannelsAdapter(
    private val onUnblock: (BlockedChannelEntity) -> Unit
) : ListAdapter<BlockedChannelEntity, BlockedChannelsAdapter.BlockedChannelViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedChannelViewHolder {
        val binding = ItemBlockedChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BlockedChannelViewHolder(binding, onUnblock)
    }

    override fun onBindViewHolder(holder: BlockedChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BlockedChannelViewHolder(
        private val binding: ItemBlockedChannelBinding,
        private val onUnblock: (BlockedChannelEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BlockedChannelEntity) {
            binding.channelName.text = item.name
            binding.unblockButton.setOnClickListener { onUnblock(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BlockedChannelEntity>() {
        override fun areItemsTheSame(
            oldItem: BlockedChannelEntity,
            newItem: BlockedChannelEntity
        ): Boolean = oldItem.url == newItem.url

        override fun areContentsTheSame(
            oldItem: BlockedChannelEntity,
            newItem: BlockedChannelEntity
        ): Boolean = oldItem == newItem
    }
}
