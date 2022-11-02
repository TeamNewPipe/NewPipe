package org.schabi.newpipe.local;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.holder.LocalItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistCardItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistGridItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamCardItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamGridItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamItemHolder;
import org.schabi.newpipe.local.holder.LocalStatisticStreamCardItemHolder;
import org.schabi.newpipe.local.holder.LocalStatisticStreamGridItemHolder;
import org.schabi.newpipe.local.holder.LocalStatisticStreamItemHolder;
import org.schabi.newpipe.local.holder.RemotePlaylistCardItemHolder;
import org.schabi.newpipe.local.holder.RemotePlaylistGridItemHolder;
import org.schabi.newpipe.local.holder.RemotePlaylistItemHolder;
import org.schabi.newpipe.util.FallbackViewHolder;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.OnClickGesture;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

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

public class LocalItemListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = LocalItemListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int HEADER_TYPE = 0;
    private static final int FOOTER_TYPE = 1;

    private static final int STREAM_STATISTICS_HOLDER_TYPE = 0x1000;
    private static final int STREAM_PLAYLIST_HOLDER_TYPE = 0x1001;
    private static final int STREAM_STATISTICS_GRID_HOLDER_TYPE = 0x1002;
    private static final int STREAM_STATISTICS_CARD_HOLDER_TYPE = 0x1003;
    private static final int STREAM_PLAYLIST_GRID_HOLDER_TYPE = 0x1004;
    private static final int STREAM_PLAYLIST_CARD_HOLDER_TYPE = 0x1005;

    private static final int LOCAL_PLAYLIST_HOLDER_TYPE = 0x2000;
    private static final int LOCAL_PLAYLIST_GRID_HOLDER_TYPE = 0x2001;
    private static final int LOCAL_PLAYLIST_CARD_HOLDER_TYPE = 0x2002;

    private static final int REMOTE_PLAYLIST_HOLDER_TYPE = 0x3000;
    private static final int REMOTE_PLAYLIST_GRID_HOLDER_TYPE = 0x3001;
    private static final int REMOTE_PLAYLIST_CARD_HOLDER_TYPE = 0x3002;

    private final LocalItemBuilder localItemBuilder;
    private final ArrayList<LocalItem> localItems;
    private final HistoryRecordManager recordManager;
    private final DateTimeFormatter dateTimeFormatter;

    private boolean showFooter = false;
    private View header = null;
    private View footer = null;
    private ItemViewMode itemViewMode = ItemViewMode.LIST;

    public LocalItemListAdapter(final Context context) {
        recordManager = new HistoryRecordManager(context);
        localItemBuilder = new LocalItemBuilder(context);
        localItems = new ArrayList<>();
        dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(Localization.getPreferredLocale(context));
    }

    public void setSelectedListener(final OnClickGesture<LocalItem> listener) {
        localItemBuilder.setOnItemSelectedListener(listener);
    }

    public void unsetSelectedListener() {
        localItemBuilder.setOnItemSelectedListener(null);
    }

    public void addItems(@Nullable final List<? extends LocalItem> data) {
        if (data == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "addItems() before > localItems.size() = "
                    + localItems.size() + ", data.size() = " + data.size());
        }

        final int offsetStart = sizeConsideringHeader();
        localItems.addAll(data);

        if (DEBUG) {
            Log.d(TAG, "addItems() after > offsetStart = " + offsetStart + ", "
                    + "localItems.size() = " + localItems.size() + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter);
        }
        notifyItemRangeInserted(offsetStart, data.size());

        if (footer != null && showFooter) {
            final int footerNow = sizeConsideringHeader();
            notifyItemMoved(offsetStart, footerNow);

            if (DEBUG) {
                Log.d(TAG, "addItems() footer from " + offsetStart
                        + " to " + footerNow);
            }
        }
    }

    public void removeItem(final LocalItem data) {
        final int index = localItems.indexOf(data);
        if (index != -1) {
            localItems.remove(index);
            notifyItemRemoved(index + (header != null ? 1 : 0));
        } else {
            // this happens when
            // 1) removeItem is called on infoItemDuplicate as in showStreamItemDialog of
            // LocalPlaylistFragment in this case need to implement delete object by it's duplicate

            // OR

            // 2)data not in itemList and UI is still not updated so notifyDataSetChanged()
            notifyDataSetChanged();
        }
    }

    public boolean swapItems(final int fromAdapterPosition, final int toAdapterPosition) {
        final int actualFrom = adapterOffsetWithoutHeader(fromAdapterPosition);
        final int actualTo = adapterOffsetWithoutHeader(toAdapterPosition);

        if (actualFrom < 0 || actualTo < 0) {
            return false;
        }
        if (actualFrom >= localItems.size() || actualTo >= localItems.size()) {
            return false;
        }

        localItems.add(actualTo, localItems.remove(actualFrom));
        notifyItemMoved(fromAdapterPosition, toAdapterPosition);
        return true;
    }

    public void clearStreamItemList() {
        if (localItems.isEmpty()) {
            return;
        }
        localItems.clear();
        notifyDataSetChanged();
    }

    public void setItemViewMode(final ItemViewMode itemViewMode) {
        this.itemViewMode = itemViewMode;
    }

    public void setHeader(final View header) {
        final boolean changed = header != this.header;
        this.header = header;
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public void setFooter(final View view) {
        this.footer = view;
    }

    public void showFooter(final boolean show) {
        if (DEBUG) {
            Log.d(TAG, "showFooter() called with: show = [" + show + "]");
        }
        if (show == showFooter) {
            return;
        }

        showFooter = show;
        if (show) {
            notifyItemInserted(sizeConsideringHeader());
        } else {
            notifyItemRemoved(sizeConsideringHeader());
        }
    }

    private int adapterOffsetWithoutHeader(final int offset) {
        return offset - (header != null ? 1 : 0);
    }

    private int sizeConsideringHeader() {
        return localItems.size() + (header != null ? 1 : 0);
    }

    public ArrayList<LocalItem> getItemsList() {
        return localItems;
    }

    @Override
    public int getItemCount() {
        int count = localItems.size();
        if (header != null) {
            count++;
        }
        if (footer != null && showFooter) {
            count++;
        }

        if (DEBUG) {
            Log.d(TAG, "getItemCount() called, count = " + count + ", "
                    + "localItems.size() = " + localItems.size() + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter);
        }
        return count;
    }

    @SuppressWarnings("FinalParameters")
    @Override
    public int getItemViewType(int position) {
        if (DEBUG) {
            Log.d(TAG, "getItemViewType() called with: position = [" + position + "]");
        }

        if (header != null && position == 0) {
            return HEADER_TYPE;
        } else if (header != null) {
            position--;
        }
        if (footer != null && position == localItems.size() && showFooter) {
            return FOOTER_TYPE;
        }
        final LocalItem item = localItems.get(position);
        switch (item.getLocalItemType()) {
            case PLAYLIST_LOCAL_ITEM:
                if (itemViewMode == ItemViewMode.CARD) {
                    return LOCAL_PLAYLIST_CARD_HOLDER_TYPE;
                } else if (itemViewMode == ItemViewMode.GRID) {
                    return LOCAL_PLAYLIST_GRID_HOLDER_TYPE;
                } else {
                    return LOCAL_PLAYLIST_HOLDER_TYPE;
                }
            case PLAYLIST_REMOTE_ITEM:
                if (itemViewMode == ItemViewMode.CARD) {
                    return REMOTE_PLAYLIST_CARD_HOLDER_TYPE;
                } else if (itemViewMode == ItemViewMode.GRID) {
                    return REMOTE_PLAYLIST_GRID_HOLDER_TYPE;
                } else {
                    return REMOTE_PLAYLIST_HOLDER_TYPE;
                }
            case PLAYLIST_STREAM_ITEM:
                if (itemViewMode == ItemViewMode.CARD) {
                    return STREAM_PLAYLIST_CARD_HOLDER_TYPE;
                } else if (itemViewMode == ItemViewMode.GRID) {
                    return STREAM_PLAYLIST_GRID_HOLDER_TYPE;
                } else {
                    return STREAM_PLAYLIST_HOLDER_TYPE;
                }
            case STATISTIC_STREAM_ITEM:
                if (itemViewMode == ItemViewMode.CARD) {
                    return STREAM_STATISTICS_CARD_HOLDER_TYPE;
                } else if (itemViewMode == ItemViewMode.GRID) {
                    return STREAM_STATISTICS_GRID_HOLDER_TYPE;
                } else {
                    return STREAM_STATISTICS_HOLDER_TYPE;
                }
            default:
                Log.e(TAG, "No holder type has been considered for item: ["
                        + item.getLocalItemType() + "]");
                return -1;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int type) {
        if (DEBUG) {
            Log.d(TAG, "onCreateViewHolder() called with: "
                    + "parent = [" + parent + "], type = [" + type + "]");
        }
        switch (type) {
            case HEADER_TYPE:
                return new HeaderFooterHolder(header);
            case FOOTER_TYPE:
                return new HeaderFooterHolder(footer);
            case LOCAL_PLAYLIST_HOLDER_TYPE:
                return new LocalPlaylistItemHolder(localItemBuilder, parent);
            case LOCAL_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalPlaylistGridItemHolder(localItemBuilder, parent);
            case LOCAL_PLAYLIST_CARD_HOLDER_TYPE:
                return new LocalPlaylistCardItemHolder(localItemBuilder, parent);
            case REMOTE_PLAYLIST_HOLDER_TYPE:
                return new RemotePlaylistItemHolder(localItemBuilder, parent);
            case REMOTE_PLAYLIST_GRID_HOLDER_TYPE:
                return new RemotePlaylistGridItemHolder(localItemBuilder, parent);
            case REMOTE_PLAYLIST_CARD_HOLDER_TYPE:
                return new RemotePlaylistCardItemHolder(localItemBuilder, parent);
            case STREAM_PLAYLIST_HOLDER_TYPE:
                return new LocalPlaylistStreamItemHolder(localItemBuilder, parent);
            case STREAM_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalPlaylistStreamGridItemHolder(localItemBuilder, parent);
            case STREAM_PLAYLIST_CARD_HOLDER_TYPE:
                return new LocalPlaylistStreamCardItemHolder(localItemBuilder, parent);
            case STREAM_STATISTICS_HOLDER_TYPE:
                return new LocalStatisticStreamItemHolder(localItemBuilder, parent);
            case STREAM_STATISTICS_GRID_HOLDER_TYPE:
                return new LocalStatisticStreamGridItemHolder(localItemBuilder, parent);
            case STREAM_STATISTICS_CARD_HOLDER_TYPE:
                return new LocalStatisticStreamCardItemHolder(localItemBuilder, parent);
            default:
                Log.e(TAG, "No view type has been considered for holder: [" + type + "]");
                return new FallbackViewHolder(new View(parent.getContext()));
        }
    }

    @SuppressWarnings("FinalParameters")
    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        if (DEBUG) {
            Log.d(TAG, "onBindViewHolder() called with: "
                    + "holder = [" + holder.getClass().getSimpleName() + "], "
                    + "position = [" + position + "]");
        }

        if (holder instanceof LocalItemHolder) {
            // If header isn't null, offset the items by -1
            if (header != null) {
                position--;
            }

            ((LocalItemHolder) holder)
                    .updateFromItem(localItems.get(position), recordManager, dateTimeFormatter);
        } else if (holder instanceof HeaderFooterHolder && position == 0 && header != null) {
            ((HeaderFooterHolder) holder).view = header;
        } else if (holder instanceof HeaderFooterHolder && position == sizeConsideringHeader()
                && footer != null && showFooter) {
            ((HeaderFooterHolder) holder).view = footer;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position,
                                 @NonNull final List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof LocalItemHolder) {
            for (final Object payload : payloads) {
                if (payload instanceof StreamStateEntity) {
                    ((LocalItemHolder) holder).updateState(localItems
                            .get(header == null ? position : position - 1), recordManager);
                } else if (payload instanceof Boolean) {
                    ((LocalItemHolder) holder).updateState(localItems
                            .get(header == null ? position : position - 1), recordManager);
                }
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int spanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(final int position) {
                final int type = getItemViewType(position);
                return type == HEADER_TYPE || type == FOOTER_TYPE ? spanCount : 1;
            }
        };
    }
}
