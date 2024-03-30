package org.schabi.newpipe.local

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.holder.LocalBookmarkPlaylistItemHolder
import org.schabi.newpipe.local.holder.LocalItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistCardItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistGridItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistStreamCardItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistStreamGridItemHolder
import org.schabi.newpipe.local.holder.LocalPlaylistStreamItemHolder
import org.schabi.newpipe.local.holder.LocalStatisticStreamCardItemHolder
import org.schabi.newpipe.local.holder.LocalStatisticStreamGridItemHolder
import org.schabi.newpipe.local.holder.LocalStatisticStreamItemHolder
import org.schabi.newpipe.local.holder.RemoteBookmarkPlaylistItemHolder
import org.schabi.newpipe.local.holder.RemotePlaylistCardItemHolder
import org.schabi.newpipe.local.holder.RemotePlaylistGridItemHolder
import org.schabi.newpipe.local.holder.RemotePlaylistItemHolder
import org.schabi.newpipe.util.FallbackViewHolder
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.OnClickGesture
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
class LocalItemListAdapter(context: Context?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val localItemBuilder: LocalItemBuilder
    val itemsList: ArrayList<LocalItem?>
    private val recordManager: HistoryRecordManager
    private val dateTimeFormatter: DateTimeFormatter
    private var showFooter: Boolean = false
    private var header: View? = null
    private var footer: View? = null
    private var itemViewMode: ItemViewMode? = ItemViewMode.LIST
    private var useItemHandle: Boolean = false

    init {
        recordManager = HistoryRecordManager(context)
        localItemBuilder = LocalItemBuilder(context)
        itemsList = ArrayList()
        dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(Localization.getPreferredLocale((context)!!))
    }

    fun setSelectedListener(listener: OnClickGesture<LocalItem?>?) {
        localItemBuilder.setOnItemSelectedListener(listener)
    }

    fun unsetSelectedListener() {
        localItemBuilder.setOnItemSelectedListener(null)
    }

    fun addItems(data: List<LocalItem?>?) {
        if (data == null) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, ("addItems() before > localItems.size() = "
                    + itemsList.size + ", data.size() = " + data.size))
        }
        val offsetStart: Int = sizeConsideringHeader()
        itemsList.addAll(data)
        if (DEBUG) {
            Log.d(TAG, ("addItems() after > offsetStart = " + offsetStart + ", "
                    + "localItems.size() = " + itemsList.size + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter))
        }
        notifyItemRangeInserted(offsetStart, data.size)
        if (footer != null && showFooter) {
            val footerNow: Int = sizeConsideringHeader()
            notifyItemMoved(offsetStart, footerNow)
            if (DEBUG) {
                Log.d(TAG, ("addItems() footer from " + offsetStart
                        + " to " + footerNow))
            }
        }
    }

    fun removeItem(data: LocalItem?) {
        val index: Int = itemsList.indexOf(data)
        if (index != -1) {
            itemsList.removeAt(index)
            notifyItemRemoved(index + (if (header != null) 1 else 0))
        } else {
            // this happens when
            // 1) removeItem is called on infoItemDuplicate as in showStreamItemDialog of
            // LocalPlaylistFragment in this case need to implement delete object by it's duplicate

            // OR

            // 2)data not in itemList and UI is still not updated so notifyDataSetChanged()
            notifyDataSetChanged()
        }
    }

    fun swapItems(fromAdapterPosition: Int, toAdapterPosition: Int): Boolean {
        val actualFrom: Int = adapterOffsetWithoutHeader(fromAdapterPosition)
        val actualTo: Int = adapterOffsetWithoutHeader(toAdapterPosition)
        if (actualFrom < 0 || actualTo < 0) {
            return false
        }
        if (actualFrom >= itemsList.size || actualTo >= itemsList.size) {
            return false
        }
        itemsList.add(actualTo, itemsList.removeAt(actualFrom))
        notifyItemMoved(fromAdapterPosition, toAdapterPosition)
        return true
    }

    fun clearStreamItemList() {
        if (itemsList.isEmpty()) {
            return
        }
        itemsList.clear()
        notifyDataSetChanged()
    }

    fun setItemViewMode(itemViewMode: ItemViewMode?) {
        this.itemViewMode = itemViewMode
    }

    fun setUseItemHandle(useItemHandle: Boolean) {
        this.useItemHandle = useItemHandle
    }

    fun setHeader(header: View) {
        val changed: Boolean = header !== this.header
        this.header = header
        if (changed) {
            notifyDataSetChanged()
        }
    }

    fun setFooter(view: View?) {
        footer = view
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
            notifyItemInserted(sizeConsideringHeader())
        } else {
            notifyItemRemoved(sizeConsideringHeader())
        }
    }

    private fun adapterOffsetWithoutHeader(offset: Int): Int {
        return offset - (if (header != null) 1 else 0)
    }

    private fun sizeConsideringHeader(): Int {
        return itemsList.size + (if (header != null) 1 else 0)
    }

    public override fun getItemCount(): Int {
        var count: Int = itemsList.size
        if (header != null) {
            count++
        }
        if (footer != null && showFooter) {
            count++
        }
        if (DEBUG) {
            Log.d(TAG, ("getItemCount() called, count = " + count + ", "
                    + "localItems.size() = " + itemsList.size + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter))
        }
        return count
    }

    public override fun getItemViewType(position: Int): Int {
        var position: Int = position
        if (DEBUG) {
            Log.d(TAG, "getItemViewType() called with: position = [" + position + "]")
        }
        if (header != null && position == 0) {
            return HEADER_TYPE
        } else if (header != null) {
            position--
        }
        if ((footer != null) && (position == itemsList.size) && showFooter) {
            return FOOTER_TYPE
        }
        val item: LocalItem? = itemsList.get(position)
        when (item!!.getLocalItemType()) {
            LocalItemType.PLAYLIST_LOCAL_ITEM -> if (useItemHandle) {
                return LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.CARD) {
                return LOCAL_PLAYLIST_CARD_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.GRID) {
                return LOCAL_PLAYLIST_GRID_HOLDER_TYPE
            } else {
                return LOCAL_PLAYLIST_HOLDER_TYPE
            }

            LocalItemType.PLAYLIST_REMOTE_ITEM -> if (useItemHandle) {
                return REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.CARD) {
                return REMOTE_PLAYLIST_CARD_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.GRID) {
                return REMOTE_PLAYLIST_GRID_HOLDER_TYPE
            } else {
                return REMOTE_PLAYLIST_HOLDER_TYPE
            }

            LocalItemType.PLAYLIST_STREAM_ITEM -> if (itemViewMode == ItemViewMode.CARD) {
                return STREAM_PLAYLIST_CARD_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.GRID) {
                return STREAM_PLAYLIST_GRID_HOLDER_TYPE
            } else {
                return STREAM_PLAYLIST_HOLDER_TYPE
            }

            LocalItemType.STATISTIC_STREAM_ITEM -> if (itemViewMode == ItemViewMode.CARD) {
                return STREAM_STATISTICS_CARD_HOLDER_TYPE
            } else if (itemViewMode == ItemViewMode.GRID) {
                return STREAM_STATISTICS_GRID_HOLDER_TYPE
            } else {
                return STREAM_STATISTICS_HOLDER_TYPE
            }

            else -> {
                Log.e(TAG, ("No holder type has been considered for item: ["
                        + item.getLocalItemType() + "]"))
                return -1
            }
        }
    }

    public override fun onCreateViewHolder(parent: ViewGroup,
                                           type: Int): RecyclerView.ViewHolder {
        if (DEBUG) {
            Log.d(TAG, ("onCreateViewHolder() called with: "
                    + "parent = [" + parent + "], type = [" + type + "]"))
        }
        when (type) {
            HEADER_TYPE -> return HeaderFooterHolder(header)
            FOOTER_TYPE -> return HeaderFooterHolder(footer)
            LOCAL_PLAYLIST_HOLDER_TYPE -> return LocalPlaylistItemHolder(localItemBuilder, parent)
            LOCAL_PLAYLIST_GRID_HOLDER_TYPE -> return LocalPlaylistGridItemHolder(localItemBuilder, parent)
            LOCAL_PLAYLIST_CARD_HOLDER_TYPE -> return LocalPlaylistCardItemHolder(localItemBuilder, parent)
            LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE -> return LocalBookmarkPlaylistItemHolder(localItemBuilder, parent)
            REMOTE_PLAYLIST_HOLDER_TYPE -> return RemotePlaylistItemHolder(localItemBuilder, parent)
            REMOTE_PLAYLIST_GRID_HOLDER_TYPE -> return RemotePlaylistGridItemHolder(localItemBuilder, parent)
            REMOTE_PLAYLIST_CARD_HOLDER_TYPE -> return RemotePlaylistCardItemHolder(localItemBuilder, parent)
            REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE -> return RemoteBookmarkPlaylistItemHolder(localItemBuilder, parent)
            STREAM_PLAYLIST_HOLDER_TYPE -> return LocalPlaylistStreamItemHolder(localItemBuilder, parent)
            STREAM_PLAYLIST_GRID_HOLDER_TYPE -> return LocalPlaylistStreamGridItemHolder(localItemBuilder, parent)
            STREAM_PLAYLIST_CARD_HOLDER_TYPE -> return LocalPlaylistStreamCardItemHolder(localItemBuilder, parent)
            STREAM_STATISTICS_HOLDER_TYPE -> return LocalStatisticStreamItemHolder(localItemBuilder, parent)
            STREAM_STATISTICS_GRID_HOLDER_TYPE -> return LocalStatisticStreamGridItemHolder(localItemBuilder, parent)
            STREAM_STATISTICS_CARD_HOLDER_TYPE -> return LocalStatisticStreamCardItemHolder(localItemBuilder, parent)
            else -> {
                Log.e(TAG, "No view type has been considered for holder: [" + type + "]")
                return FallbackViewHolder(View(parent.getContext()))
            }
        }
    }

    public override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var position: Int = position
        if (DEBUG) {
            Log.d(TAG, ("onBindViewHolder() called with: "
                    + "holder = [" + holder.javaClass.getSimpleName() + "], "
                    + "position = [" + position + "]"))
        }
        if (holder is LocalItemHolder) {
            // If header isn't null, offset the items by -1
            if (header != null) {
                position--
            }
            holder
                    .updateFromItem(itemsList.get(position), recordManager, dateTimeFormatter)
        } else if (holder is HeaderFooterHolder && (position == 0) && (header != null)) {
            holder.view = header
        } else if (holder is HeaderFooterHolder && (position == sizeConsideringHeader()
                        ) && (footer != null) && showFooter) {
            holder.view = footer
        }
    }

    public override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int,
                                         payloads: List<Any>) {
        if (!payloads.isEmpty() && holder is LocalItemHolder) {
            for (payload: Any? in payloads) {
                if (payload is StreamStateEntity) {
                    holder.updateState(itemsList
                            .get(if (header == null) position else position - 1), recordManager)
                } else if (payload is Boolean) {
                    holder.updateState(itemsList
                            .get(if (header == null) position else position - 1), recordManager)
                }
            }
        } else {
            onBindViewHolder(holder, position)
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

    companion object {
        private val TAG: String = LocalItemListAdapter::class.java.getSimpleName()
        private val DEBUG: Boolean = false
        private val HEADER_TYPE: Int = 0
        private val FOOTER_TYPE: Int = 1
        private val STREAM_STATISTICS_HOLDER_TYPE: Int = 0x1000
        private val STREAM_PLAYLIST_HOLDER_TYPE: Int = 0x1001
        private val STREAM_STATISTICS_GRID_HOLDER_TYPE: Int = 0x1002
        private val STREAM_STATISTICS_CARD_HOLDER_TYPE: Int = 0x1003
        private val STREAM_PLAYLIST_GRID_HOLDER_TYPE: Int = 0x1004
        private val STREAM_PLAYLIST_CARD_HOLDER_TYPE: Int = 0x1005
        private val LOCAL_PLAYLIST_HOLDER_TYPE: Int = 0x2000
        private val LOCAL_PLAYLIST_GRID_HOLDER_TYPE: Int = 0x2001
        private val LOCAL_PLAYLIST_CARD_HOLDER_TYPE: Int = 0x2002
        private val LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE: Int = 0x2003
        private val REMOTE_PLAYLIST_HOLDER_TYPE: Int = 0x3000
        private val REMOTE_PLAYLIST_GRID_HOLDER_TYPE: Int = 0x3001
        private val REMOTE_PLAYLIST_CARD_HOLDER_TYPE: Int = 0x3002
        private val REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE: Int = 0x3003
    }
}
