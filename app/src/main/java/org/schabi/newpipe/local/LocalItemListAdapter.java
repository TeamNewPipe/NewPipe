package org.schabi.newpipe.local;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.learning.VideoNoteBadges;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.holder.LocalBookmarkPlaylistItemHolder;
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
import org.schabi.newpipe.local.holder.RemoteBookmarkPlaylistItemHolder;
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
import java.util.Map;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private static final int SEPARATOR_TYPE = 2;

    private static final int STREAM_STATISTICS_HOLDER_TYPE = 0x1000;
    private static final int STREAM_PLAYLIST_HOLDER_TYPE = 0x1001;
    private static final int STREAM_STATISTICS_GRID_HOLDER_TYPE = 0x1002;
    private static final int STREAM_STATISTICS_CARD_HOLDER_TYPE = 0x1003;
    private static final int STREAM_PLAYLIST_GRID_HOLDER_TYPE = 0x1004;
    private static final int STREAM_PLAYLIST_CARD_HOLDER_TYPE = 0x1005;

    private static final int LOCAL_PLAYLIST_HOLDER_TYPE = 0x2000;
    private static final int LOCAL_PLAYLIST_GRID_HOLDER_TYPE = 0x2001;
    private static final int LOCAL_PLAYLIST_CARD_HOLDER_TYPE = 0x2002;
    private static final int LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE = 0x2003;
    private static final int LOCAL_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE = 0x2004;
    private static final int LOCAL_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE = 0x2005;

    private static final int REMOTE_PLAYLIST_HOLDER_TYPE = 0x3000;
    private static final int REMOTE_PLAYLIST_GRID_HOLDER_TYPE = 0x3001;
    private static final int REMOTE_PLAYLIST_CARD_HOLDER_TYPE = 0x3002;
    private static final int REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE = 0x3003;
    private static final int REMOTE_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE = 0x3004;
    private static final int REMOTE_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE = 0x3005;

    private final LocalItemBuilder localItemBuilder;
    private final ArrayList<LocalItem> localItems;
    private final HistoryRecordManager recordManager;
    private final VideoNoteBadges noteBadges;
    private final DateTimeFormatter dateTimeFormatter;

    private final ArrayList<Integer> rows = new ArrayList<>();
    private Map<Integer, String> separators = Collections.emptyMap();
    private Function<List<LocalItem>, Map<Integer, String>> separatorProvider;
    private IntConsumer editSeparator;

    private java.util.function.BiConsumer<Integer, Integer> moveSeparatorRows;
    private IntConsumer removeSeparatorRow;
    private IntConsumer insertSeparatorRow;
    private java.util.function.Consumer<RecyclerView.ViewHolder> separatorDrag;

    public void setSeparatorGestures(
            final java.util.function.BiConsumer<Integer, Integer> move,
            final IntConsumer remove, final IntConsumer insert,
            final java.util.function.Consumer<RecyclerView.ViewHolder> drag) {
        moveSeparatorRows = move;
        removeSeparatorRow = remove;
        insertSeparatorRow = insert;
        separatorDrag = drag;
    }

    public void setSeparators(
            @Nullable final Function<List<LocalItem>, Map<Integer, String>> provider,
            @Nullable final IntConsumer editor) {
        separatorProvider = provider;
        editSeparator = editor;
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        separators = separatorProvider == null ? Collections.emptyMap()
                : separatorProvider.apply(localItems);
        rows.clear();
        if (separatorProvider == null) {
            return;
        }
        int item = 0;
        for (int row = 0; row < localItems.size() + separators.size(); row++) {
            rows.add(separators.containsKey(row) ? -row - 1 : item++);
        }
    }

    private boolean showFooter = false;
    private Supplier<View> headerSupplier = null;
    private View footer = null;
    private ItemViewMode itemViewMode = ItemViewMode.LIST;
    private boolean useItemHandle = false;
    private boolean itemHandleEnabled = true;

    public LocalItemListAdapter(final Context context) {
        recordManager = new HistoryRecordManager(context);
        noteBadges = new VideoNoteBadges(context, this::notifyDataSetChanged);
        localItemBuilder = new LocalItemBuilder(context);
        localItems = new ArrayList<>();

        dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(Localization.getPreferredLocale(context));
    }

    public void setSelectedListener(final OnClickGesture<LocalItem> listener) {
        localItemBuilder.setOnItemSelectedListener(listener);
    }

    public void setSelectionToggle(@Nullable final Consumer<LocalItem> listener) {
        localItemBuilder.setSelectionToggle(listener);
        notifyDataSetChanged();
    }

    public boolean isSelecting() {
        return localItemBuilder.isSelecting();
    }

    public void unsetSelectedListener() {
        localItemBuilder.setOnItemSelectedListener(null);
    }

    public void setUploaderSelectedListener(final Consumer<LocalItem> listener) {
        localItemBuilder.setOnUploaderSelectedListener(listener);
    }

    public void unsetUploaderSelectedListener() {
        localItemBuilder.setOnUploaderSelectedListener(null);
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
        rebuildRows();
        if (separatorProvider != null) {
            notifyDataSetChanged();
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "addItems() after > offsetStart = " + offsetStart + ", "
                    + "localItems.size() = " + localItems.size() + ", "
                    + "header = " + hasHeader() + ", footer = " + footer + ", "
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
            removeItemAt(index);
        } else {
            // this happens when
            // 1) removeItem is called on infoItemDuplicate as in showStreamItemDialog of
            // LocalPlaylistFragment in this case need to implement delete object by it's duplicate

            // OR

            // 2)data not in itemList and UI is still not updated so notifyDataSetChanged()
            notifyDataSetChanged();
        }
    }

    /**
     * Converts an adapter position to the corresponding item index.
     *
     * @param adapterPosition position reported by the RecyclerView
     * @return the item index, or {@code -1} when the position is a header, footer, or invalid
     */
    public int getItemIndex(final int adapterPosition) {
        final int row = adapterPosition - (hasHeader() ? 1 : 0);
        return row >= 0 && row < rowCount() ? Math.max(-1, itemIndexAtRow(row)) : -1;
    }

    /**
     * Converts an item index to its RecyclerView adapter position.
     *
     * @param itemIndex index within {@link #getItemsList()}
     * @return adapter position, or {@link RecyclerView#NO_POSITION} when invalid
     */
    public int getAdapterPositionForItemIndex(final int itemIndex) {
        if (itemIndex < 0 || itemIndex >= localItems.size()) {
            return RecyclerView.NO_POSITION;
        }
        final int row = separatorProvider == null ? itemIndex : rows.indexOf(itemIndex);
        return row + (hasHeader() ? 1 : 0);
    }

    /**
     * Removes the item at an exact index, preserving duplicate playlist entries correctly.
     *
     * @param itemIndex index within {@link #getItemsList()}
     * @return the removed item, or {@code null} when the index is invalid
     */
    @Nullable
    public LocalItem removeItemAt(final int itemIndex) {
        if (itemIndex < 0 || itemIndex >= localItems.size()) {
            return null;
        }

        final int position = getAdapterPositionForItemIndex(itemIndex);
        if (removeSeparatorRow != null && separatorProvider != null) {
            removeSeparatorRow.accept(position - (hasHeader() ? 1 : 0));
        }
        final LocalItem removedItem = localItems.remove(itemIndex);
        rebuildRows();
        if (separatorProvider == null) {
            notifyItemRemoved(position);
        } else {
            notifyDataSetChanged();
        }
        return removedItem;
    }

    /**
     * Inserts an item at the requested index.
     *
     * @param itemIndex index within {@link #getItemsList()}
     * @param item item to insert
     */
    public void insertItemAt(final int itemIndex, @NonNull final LocalItem item) {
        final int position = getAdapterPositionForItemIndex(itemIndex);
        insertItemAt(itemIndex, item,
                position == RecyclerView.NO_POSITION ? sizeConsideringHeader() : position);
    }

    /**
     * Restores both item order and the exact presentation row when undoing removal.
     *
     * @param itemIndex index in the playback item list
     * @param item restored item
     * @param adapterPosition original row before removal
     */
    public void insertItemAt(final int itemIndex, @NonNull final LocalItem item,
                             final int adapterPosition) {
        final int insertionIndex = Math.max(0, Math.min(itemIndex, localItems.size()));
        if (insertSeparatorRow != null && separatorProvider != null) {
            final int row = Math.max(0, Math.min(rows.size(),
                    adapterPosition - (hasHeader() ? 1 : 0)));
            insertSeparatorRow.accept(row);
        }
        localItems.add(insertionIndex, item);
        rebuildRows();
        if (separatorProvider == null) {
            notifyItemInserted(getAdapterPositionForItemIndex(insertionIndex));
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean swapItems(final int fromAdapterPosition, final int toAdapterPosition) {
        final int from = fromAdapterPosition - (hasHeader() ? 1 : 0);
        final int to = toAdapterPosition - (hasHeader() ? 1 : 0);
        if (from < 0 || to < 0 || from >= rowCount() || to >= rowCount() || from == to) {
            return false;
        }
        if (separatorProvider == null) {
            localItems.add(to, localItems.remove(from));
            notifyItemMoved(fromAdapterPosition, toAdapterPosition);
            return true;
        }
        final ArrayList<LocalItem> original = new ArrayList<>(localItems);
        rows.add(to, rows.remove(from));
        localItems.clear();
        for (final int row : rows) {
            if (row >= 0) {
                localItems.add(original.get(row));
            }
        }
        if (moveSeparatorRows != null && separatorProvider != null) {
            moveSeparatorRows.accept(from, to);
        }
        rebuildRows();
        notifyItemMoved(fromAdapterPosition, toAdapterPosition);
        return true;
    }

    public void clearStreamItemList() {
        if (localItems.isEmpty()) {
            return;
        }
        localItems.clear();
        rebuildRows();
        notifyDataSetChanged();
    }

    public void setItemViewMode(final ItemViewMode itemViewMode) {
        this.itemViewMode = itemViewMode;
    }

    public void setUseItemHandle(final boolean useItemHandle) {
        this.useItemHandle = useItemHandle;
    }

    /**
     * Controls dragging without changing the bookmark layout. Rebind after updating.
     *
     * @param enabled whether bookmark drag handles should be visible and enabled
     */
    public void setItemHandleEnabled(final boolean enabled) {
        itemHandleEnabled = enabled;
    }

    public void setHeaderSupplier(@Nullable final Supplier<View> headerSupplier) {
        final boolean changed = headerSupplier != this.headerSupplier;
        this.headerSupplier = headerSupplier;
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public void setFooter(final View view) {
        this.footer = view;
    }

    protected boolean hasHeader() {
        return this.headerSupplier != null;
    }

    @Deprecated(since = "Calling this method with `true` may cause crashes, see "
            + "https://github.com/TeamNewPipe/NewPipe/pull/12996#pullrequestreview-3713317115")
    public void showFooter(final boolean show) {
        if (DEBUG) {
            Log.d(TAG, "showFooter() called with: show = [" + show + "]");
        }
        if (show == showFooter) {
            return;
        }

        showFooter = show;
        if (show) {
            Log.w(TAG, "Calling LocalItemListAdapter.showFooter(true) may cause crashes, see https"
                    + "://github.com/TeamNewPipe/NewPipe/pull/12996#pullrequestreview-3713317115");
            notifyItemInserted(sizeConsideringHeader());
        } else {
            notifyItemRemoved(sizeConsideringHeader());
        }
    }

    private int rowCount() {
        return separatorProvider == null ? localItems.size() : rows.size();
    }

    private int itemIndexAtRow(final int row) {
        return separatorProvider == null ? row : rows.get(row);
    }

    private int sizeConsideringHeader() {
        return rowCount() + (hasHeader() ? 1 : 0);
    }

    public ArrayList<LocalItem> getItemsList() {
        return localItems;
    }

    @Override
    public int getItemCount() {
        int count = rowCount();
        if (hasHeader()) {
            count++;
        }
        if (footer != null && showFooter) {
            count++;
        }

        if (DEBUG) {
            Log.d(TAG, "getItemCount() called, count = " + count + ", "
                    + "localItems.size() = " + localItems.size() + ", "
                    + "header = " + hasHeader() + ", footer = " + footer + ", "
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

        if (hasHeader() && position == 0) {
            return HEADER_TYPE;
        } else if (hasHeader()) {
            position--;
        }
        if (footer != null && position == rowCount() && showFooter) {
            return FOOTER_TYPE;
        }
        final int itemIndex = itemIndexAtRow(position);
        if (itemIndex < 0) {
            return SEPARATOR_TYPE;
        }
        final LocalItem item = localItems.get(itemIndex);
        switch (item.getLocalItemType()) {
            case PLAYLIST_LOCAL_ITEM:
                if (useItemHandle) {
                    return switch (itemViewMode) {
                        case CARD -> LOCAL_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE;
                        case GRID -> LOCAL_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE;
                        default -> LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE;
                    };
                } else if (itemViewMode == ItemViewMode.CARD) {
                    return LOCAL_PLAYLIST_CARD_HOLDER_TYPE;
                } else if (itemViewMode == ItemViewMode.GRID) {
                    return LOCAL_PLAYLIST_GRID_HOLDER_TYPE;
                } else {
                    return LOCAL_PLAYLIST_HOLDER_TYPE;
                }
            case PLAYLIST_REMOTE_ITEM:
                if (useItemHandle) {
                    return switch (itemViewMode) {
                        case CARD -> REMOTE_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE;
                        case GRID -> REMOTE_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE;
                        default -> REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE;
                    };
                } else if (itemViewMode == ItemViewMode.CARD) {
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
            case SEPARATOR_TYPE:
                return new HeaderFooterHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_separator, parent, false));
            case HEADER_TYPE:
                return new HeaderFooterHolder(headerSupplier.get());
            case FOOTER_TYPE:
                return new HeaderFooterHolder(footer);
            case LOCAL_PLAYLIST_HOLDER_TYPE:
                return new LocalPlaylistItemHolder(localItemBuilder, parent);
            case LOCAL_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalPlaylistGridItemHolder(localItemBuilder, parent);
            case LOCAL_PLAYLIST_CARD_HOLDER_TYPE:
                return new LocalPlaylistCardItemHolder(localItemBuilder, parent);
            case LOCAL_BOOKMARK_PLAYLIST_HOLDER_TYPE:
                return new LocalBookmarkPlaylistItemHolder(localItemBuilder, parent);
            case LOCAL_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE:
                return new LocalBookmarkPlaylistItemHolder(localItemBuilder,
                        R.layout.list_playlist_card_item, parent);
            case LOCAL_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalBookmarkPlaylistItemHolder(localItemBuilder,
                        R.layout.list_playlist_grid_item, parent);
            case REMOTE_PLAYLIST_HOLDER_TYPE:
                return new RemotePlaylistItemHolder(localItemBuilder, parent);
            case REMOTE_PLAYLIST_GRID_HOLDER_TYPE:
                return new RemotePlaylistGridItemHolder(localItemBuilder, parent);
            case REMOTE_PLAYLIST_CARD_HOLDER_TYPE:
                return new RemotePlaylistCardItemHolder(localItemBuilder, parent);
            case REMOTE_BOOKMARK_PLAYLIST_HOLDER_TYPE:
                return new RemoteBookmarkPlaylistItemHolder(localItemBuilder, parent);
            case REMOTE_BOOKMARK_PLAYLIST_CARD_HOLDER_TYPE:
                return new RemoteBookmarkPlaylistItemHolder(localItemBuilder,
                        R.layout.list_playlist_card_item, parent);
            case REMOTE_BOOKMARK_PLAYLIST_GRID_HOLDER_TYPE:
                return new RemoteBookmarkPlaylistItemHolder(localItemBuilder,
                        R.layout.list_playlist_grid_item, parent);
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
            position = getItemIndex(position);

            ((LocalItemHolder) holder)
                    .updateFromItem(localItems.get(position), recordManager, dateTimeFormatter);
            noteBadges.bindLocal(holder.itemView, localItems.get(position));
            if (holder instanceof LocalBookmarkPlaylistItemHolder
                    || holder instanceof RemoteBookmarkPlaylistItemHolder
                    || holder instanceof LocalPlaylistStreamItemHolder) {
                final View handle = holder.itemView.findViewById(R.id.itemHandle);
                handle.setEnabled(itemHandleEnabled && !isSelecting());
                handle.setVisibility(itemHandleEnabled && !isSelecting()
                        ? View.VISIBLE : View.GONE);
            }
        } else if (getItemViewType(position) == SEPARATOR_TYPE) {
            final int row = position - (hasHeader() ? 1 : 0);
            final TextView label = holder.itemView.findViewById(R.id.separator_label);
            label.setText(separators.get(-rows.get(row) - 1));
            final View handle = holder.itemView.findViewById(R.id.separator_handle);
            handle.setVisibility(itemHandleEnabled && !isSelecting()
                        ? View.VISIBLE : View.GONE);
            handle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                        && itemHandleEnabled && separatorDrag != null) {
                    separatorDrag.accept(holder);
                    return true;
                }
                return false;
            });
            handle.setOnClickListener(view -> {
                if (!itemHandleEnabled) {
                    return;
                }
                final int current = holder.getBindingAdapterPosition();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(view.getContext())
                        .setTitle(label.getText())
                        .setItems(new String[]{view.getContext()
                                .getString(R.string.subscription_sort_up),
                                view.getContext().getString(R.string.subscription_sort_down)},
                                (dialog, which) -> swapItems(current,
                                        current + (which == 0 ? -1 : 1)))
                        .show();
            });
            ViewCompat.setAccessibilityHeading(label, true);
            label.setOnLongClickListener(view -> {
                final int current = holder.getBindingAdapterPosition() - (hasHeader() ? 1 : 0);
                if (editSeparator == null || current < 0 || current >= rows.size()
                        || rows.get(current) >= 0) {
                    return false;
                }
                editSeparator.accept(-rows.get(current) - 1);
                return true;
            });
        } else if (holder instanceof HeaderFooterHolder && position == 0 && hasHeader()) {
            ((HeaderFooterHolder) holder).view = headerSupplier.get();
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
                            .get(getItemIndex(position)), recordManager);
                } else if (payload instanceof Boolean) {
                    ((LocalItemHolder) holder).updateState(localItems
                            .get(getItemIndex(position)), recordManager);
                }
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onViewRecycled(@NonNull final RecyclerView.ViewHolder holder) {
        holder.itemView.setSelected(false);
        noteBadges.clear(holder.itemView);
        super.onViewRecycled(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        noteBadges.watch(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull final RecyclerView recyclerView) {
        noteBadges.unwatch(recyclerView);
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int spanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(final int position) {
                final int type = getItemViewType(position);
                return type == HEADER_TYPE || type == FOOTER_TYPE || type == SEPARATOR_TYPE
                        ? spanCount : 1;
            }
        };
    }
}
