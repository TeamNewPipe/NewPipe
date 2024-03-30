package org.schabi.newpipe.info_list

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.databinding.PignateFooterBinding
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.holder.ChannelCardInfoItemHolder
import org.schabi.newpipe.info_list.holder.ChannelGridInfoItemHolder
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder
import org.schabi.newpipe.info_list.holder.CommentInfoItemHolder
import org.schabi.newpipe.info_list.holder.InfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistCardInfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistGridInfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamCardInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamGridInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.FallbackViewHolder
import org.schabi.newpipe.util.OnClickGesture
import java.util.function.Supplier

/*
* Created by Christian Schabesberger on 01.08.16.
*
* Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
* InfoListAdapter.java is part of NewPipe.
*
* NewPipe is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* NewPipe is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
*/
class InfoListAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val layoutInflater: LayoutInflater
    private val infoItemBuilder: InfoItemBuilder
    private val infoItemList: MutableList<InfoItem?>
    private val recordManager: HistoryRecordManager
    private var useMiniVariant: Boolean = false
    private var showFooter: Boolean = false
    private var itemMode: ItemViewMode? = ItemViewMode.LIST
    private var headerSupplier: Supplier<View>? = null

    init {
        layoutInflater = LayoutInflater.from(context)
        recordManager = HistoryRecordManager(context)
        infoItemBuilder = InfoItemBuilder(context)
        infoItemList = ArrayList()
    }

    fun setOnStreamSelectedListener(listener: OnClickGesture<StreamInfoItem?>?) {
        infoItemBuilder.setOnStreamSelectedListener(listener)
    }

    fun setOnChannelSelectedListener(listener: OnClickGesture<ChannelInfoItem?>?) {
        infoItemBuilder.setOnChannelSelectedListener(listener)
    }

    fun setOnPlaylistSelectedListener(listener: OnClickGesture<PlaylistInfoItem?>?) {
        infoItemBuilder.setOnPlaylistSelectedListener(listener)
    }

    fun setOnCommentsSelectedListener(listener: OnClickGesture<CommentsInfoItem?>?) {
        infoItemBuilder.setOnCommentsSelectedListener(listener)
    }

    fun setUseMiniVariant(useMiniVariant: Boolean) {
        this.useMiniVariant = useMiniVariant
    }

    fun setItemViewMode(itemViewMode: ItemViewMode?) {
        itemMode = itemViewMode
    }

    fun addInfoItemList(data: List<InfoItem?>?) {
        if (data == null) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, ("addInfoItemList() before > infoItemList.size() = "
                    + infoItemList.size + ", data.size() = " + data.size))
        }
        val offsetStart: Int = sizeConsideringHeaderOffset()
        infoItemList.addAll(data)
        if (DEBUG) {
            Log.d(TAG, ("addInfoItemList() after > offsetStart = " + offsetStart + ", "
                    + "infoItemList.size() = " + infoItemList.size + ", "
                    + "hasHeader = " + hasHeader() + ", "
                    + "showFooter = " + showFooter))
        }
        notifyItemRangeInserted(offsetStart, data.size)
        if (showFooter) {
            val footerNow: Int = sizeConsideringHeaderOffset()
            notifyItemMoved(offsetStart, footerNow)
            if (DEBUG) {
                Log.d(TAG, ("addInfoItemList() footer from " + offsetStart
                        + " to " + footerNow))
            }
        }
    }

    fun clearStreamItemList() {
        if (infoItemList.isEmpty()) {
            return
        }
        infoItemList.clear()
        notifyDataSetChanged()
    }

    fun setHeaderSupplier(headerSupplier: Supplier<View>?) {
        val changed: Boolean = headerSupplier !== this.headerSupplier
        this.headerSupplier = headerSupplier
        if (changed) {
            notifyDataSetChanged()
        }
    }

    protected fun hasHeader(): Boolean {
        return headerSupplier != null
    }

    fun showFooter(show: Boolean) {
        if (DEBUG) {
            Log.d(TAG, "showFooter() called with: show = [" + show + "]")
        }
        if (show == showFooter) {
            return
        }
        showFooter = show
        if (show) {
            notifyItemInserted(sizeConsideringHeaderOffset())
        } else {
            notifyItemRemoved(sizeConsideringHeaderOffset())
        }
    }

    private fun sizeConsideringHeaderOffset(): Int {
        val i: Int = infoItemList.size + (if (hasHeader()) 1 else 0)
        if (DEBUG) {
            Log.d(TAG, "sizeConsideringHeaderOffset() called → " + i)
        }
        return i
    }

    fun getItemsList(): MutableList<InfoItem?> {
        return infoItemList
    }

    public override fun getItemCount(): Int {
        var count: Int = infoItemList.size
        if (hasHeader()) {
            count++
        }
        if (showFooter) {
            count++
        }
        if (DEBUG) {
            Log.d(TAG, ("getItemCount() called with: "
                    + "count = " + count + ", infoItemList.size() = " + infoItemList.size + ", "
                    + "hasHeader = " + hasHeader() + ", "
                    + "showFooter = " + showFooter))
        }
        return count
    }

    public override fun getItemViewType(position: Int): Int {
        var position: Int = position
        if (DEBUG) {
            Log.d(TAG, "getItemViewType() called with: position = [" + position + "]")
        }
        if (hasHeader() && position == 0) {
            return HEADER_TYPE
        } else if (hasHeader()) {
            position--
        }
        if (position == infoItemList.size && showFooter) {
            return FOOTER_TYPE
        }
        val item: InfoItem? = infoItemList.get(position)
        when (item!!.getInfoType()) {
            InfoType.STREAM -> if (itemMode == ItemViewMode.CARD) {
                return CARD_STREAM_HOLDER_TYPE
            } else if (itemMode == ItemViewMode.GRID) {
                return GRID_STREAM_HOLDER_TYPE
            } else if (useMiniVariant) {
                return MINI_STREAM_HOLDER_TYPE
            } else {
                return STREAM_HOLDER_TYPE
            }

            InfoType.CHANNEL -> if (itemMode == ItemViewMode.CARD) {
                return CARD_CHANNEL_HOLDER_TYPE
            } else if (itemMode == ItemViewMode.GRID) {
                return GRID_CHANNEL_HOLDER_TYPE
            } else if (useMiniVariant) {
                return MINI_CHANNEL_HOLDER_TYPE
            } else {
                return CHANNEL_HOLDER_TYPE
            }

            InfoType.PLAYLIST -> if (itemMode == ItemViewMode.CARD) {
                return CARD_PLAYLIST_HOLDER_TYPE
            } else if (itemMode == ItemViewMode.GRID) {
                return GRID_PLAYLIST_HOLDER_TYPE
            } else if (useMiniVariant) {
                return MINI_PLAYLIST_HOLDER_TYPE
            } else {
                return PLAYLIST_HOLDER_TYPE
            }

            InfoType.COMMENT -> return COMMENT_HOLDER_TYPE
            else -> return -1
        }
    }

    public override fun onCreateViewHolder(parent: ViewGroup,
                                           type: Int): RecyclerView.ViewHolder {
        if (DEBUG) {
            Log.d(TAG, ("onCreateViewHolder() called with: "
                    + "parent = [" + parent + "], type = [" + type + "]"))
        }
        when (type) {
            HEADER_TYPE -> return HFHolder(headerSupplier!!.get())
            FOOTER_TYPE -> return HFHolder(PignateFooterBinding
                    .inflate(layoutInflater, parent, false)
                    .getRoot()
            )

            MINI_STREAM_HOLDER_TYPE -> return StreamMiniInfoItemHolder(infoItemBuilder, parent)
            STREAM_HOLDER_TYPE -> return StreamInfoItemHolder(infoItemBuilder, parent)
            GRID_STREAM_HOLDER_TYPE -> return StreamGridInfoItemHolder(infoItemBuilder, parent)
            CARD_STREAM_HOLDER_TYPE -> return StreamCardInfoItemHolder(infoItemBuilder, parent)
            MINI_CHANNEL_HOLDER_TYPE -> return ChannelMiniInfoItemHolder(infoItemBuilder, parent)
            CHANNEL_HOLDER_TYPE -> return ChannelInfoItemHolder(infoItemBuilder, parent)
            CARD_CHANNEL_HOLDER_TYPE -> return ChannelCardInfoItemHolder(infoItemBuilder, parent)
            GRID_CHANNEL_HOLDER_TYPE -> return ChannelGridInfoItemHolder(infoItemBuilder, parent)
            MINI_PLAYLIST_HOLDER_TYPE -> return PlaylistMiniInfoItemHolder(infoItemBuilder, parent)
            PLAYLIST_HOLDER_TYPE -> return PlaylistInfoItemHolder(infoItemBuilder, parent)
            GRID_PLAYLIST_HOLDER_TYPE -> return PlaylistGridInfoItemHolder(infoItemBuilder, parent)
            CARD_PLAYLIST_HOLDER_TYPE -> return PlaylistCardInfoItemHolder(infoItemBuilder, parent)
            COMMENT_HOLDER_TYPE -> return CommentInfoItemHolder(infoItemBuilder, parent)
            else -> return FallbackViewHolder(View(parent.getContext()))
        }
    }

    public override fun onBindViewHolder(holder: RecyclerView.ViewHolder,
                                         position: Int) {
        if (DEBUG) {
            Log.d(TAG, ("onBindViewHolder() called with: "
                    + "holder = [" + holder.javaClass.getSimpleName() + "], "
                    + "position = [" + position + "]"))
        }
        if (holder is InfoItemHolder) {
            holder.updateFromItem( // If header is present, offset the items by -1
                    infoItemList.get(if (hasHeader()) position - 1 else position), recordManager)
        }
    }

    fun getSpanSizeLookup(spanCount: Int): SpanSizeLookup {
        return object : SpanSizeLookup() {
            public override fun getSpanSize(position: Int): Int {
                val type: Int = getItemViewType(position)
                return if (type == HEADER_TYPE || type == FOOTER_TYPE) spanCount else 1
            }
        }
    }

    internal class HFHolder(v: View?) : RecyclerView.ViewHolder((v)!!)
    companion object {
        private val TAG: String = InfoListAdapter::class.java.getSimpleName()
        private val DEBUG: Boolean = false
        private val HEADER_TYPE: Int = 0
        private val FOOTER_TYPE: Int = 1
        private val MINI_STREAM_HOLDER_TYPE: Int = 0x100
        private val STREAM_HOLDER_TYPE: Int = 0x101
        private val GRID_STREAM_HOLDER_TYPE: Int = 0x102
        private val CARD_STREAM_HOLDER_TYPE: Int = 0x103
        private val MINI_CHANNEL_HOLDER_TYPE: Int = 0x200
        private val CHANNEL_HOLDER_TYPE: Int = 0x201
        private val GRID_CHANNEL_HOLDER_TYPE: Int = 0x202
        private val CARD_CHANNEL_HOLDER_TYPE: Int = 0x203
        private val MINI_PLAYLIST_HOLDER_TYPE: Int = 0x300
        private val PLAYLIST_HOLDER_TYPE: Int = 0x301
        private val GRID_PLAYLIST_HOLDER_TYPE: Int = 0x302
        private val CARD_PLAYLIST_HOLDER_TYPE: Int = 0x303
        private val COMMENT_HOLDER_TYPE: Int = 0x400
    }
}
