package org.schabi.newpipe.info_list;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.GridLayoutManager;
import org.schabi.newpipe.download.DownloadedAvailabilityIndex;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.databinding.PignateFooterBinding;
import org.schabi.newpipe.dearrow.DeArrowService;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.holder.ChannelCardInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentInfoItemHolder;
import org.schabi.newpipe.info_list.holder.InfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistCardInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PostInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamCardInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamWideRelatedInfoItemHolder;
import org.schabi.newpipe.learning.VideoNoteBadges;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.FallbackViewHolder;
import org.schabi.newpipe.util.MembersOnlyContentHelper;
import org.schabi.newpipe.util.OnClickGesture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

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

public class InfoListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = InfoListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int HEADER_TYPE = 0;
    private static final int FOOTER_TYPE = 1;

    private static final int MINI_STREAM_HOLDER_TYPE = 0x100;
    private static final int STREAM_HOLDER_TYPE = 0x101;
    private static final int GRID_STREAM_HOLDER_TYPE = 0x102;
    private static final int CARD_STREAM_HOLDER_TYPE = 0x103;
    private static final int WIDE_RELATED_STREAM_HOLDER_TYPE = 0x104;
    private static final int MINI_CHANNEL_HOLDER_TYPE = 0x200;
    private static final int CHANNEL_HOLDER_TYPE = 0x201;
    private static final int GRID_CHANNEL_HOLDER_TYPE = 0x202;
    private static final int CARD_CHANNEL_HOLDER_TYPE = 0x203;
    private static final int MINI_PLAYLIST_HOLDER_TYPE = 0x300;
    private static final int PLAYLIST_HOLDER_TYPE = 0x301;
    private static final int GRID_PLAYLIST_HOLDER_TYPE = 0x302;
    private static final int CARD_PLAYLIST_HOLDER_TYPE = 0x303;
    private static final int COMMENT_HOLDER_TYPE = 0x400;
    private static final int POST_HOLDER_TYPE = 0x500;

    private final LayoutInflater layoutInflater;
    private final InfoItemBuilder infoItemBuilder;
    private Observer<Long> downloadedAvailabilityObserver;
    private final List<InfoItem> infoItemList;
    private final HistoryRecordManager recordManager;
    private final VideoNoteBadges noteBadges;
    private final ContentBlockingHelper.Target contentBlockingTarget;

    private boolean useMiniVariant = false;
    private boolean useWideRelatedVariant = false;
    private boolean showFooter = false;

    private ItemViewMode itemMode = ItemViewMode.LIST;

    private Supplier<View> headerSupplier = null;
    private BiConsumer<View, StreamInfoItem> streamRowBound;
    private Consumer<View> streamRowRecycled;

    public InfoListAdapter(final Context context,
                           @NonNull final ContentBlockingHelper.Target contentBlockingTarget) {
        layoutInflater = LayoutInflater.from(context);
        recordManager = new HistoryRecordManager(context);
        noteBadges = new VideoNoteBadges(context, this::notifyDataSetChanged);
        infoItemBuilder = new InfoItemBuilder(context);
        infoItemList = new ArrayList<>();
        this.contentBlockingTarget = contentBlockingTarget;
    }

    /**
     * Optional row decoration used by the local For You tab, not a global stream action.
     * @param bound a callback invoked when a stream row is bound
     * @param recycled a callback invoked when a row is recycled
     */
    public void setStreamRowListener(@Nullable final BiConsumer<View, StreamInfoItem> bound,
                                     @Nullable final Consumer<View> recycled) {
        streamRowBound = bound;
        streamRowRecycled = recycled;
    }

    public void setOnStreamSelectedListener(final OnClickGesture<StreamInfoItem> listener) {
        infoItemBuilder.setOnStreamSelectedListener(listener);
    }

    public void setOnChannelSelectedListener(final OnClickGesture<ChannelInfoItem> listener) {
        infoItemBuilder.setOnChannelSelectedListener(listener);
    }

    public void setOnPlaylistSelectedListener(final OnClickGesture<PlaylistInfoItem> listener) {
        infoItemBuilder.setOnPlaylistSelectedListener(listener);
    }

    public void setOnCommentsSelectedListener(final OnClickGesture<CommentsInfoItem> listener) {
        infoItemBuilder.setOnCommentsSelectedListener(listener);
    }

    public void setUseMiniVariant(final boolean useMiniVariant) {
        this.useMiniVariant = useMiniVariant;
    }

    public void setUseWideRelatedVariant(final boolean useWideRelatedVariant) {
        this.useWideRelatedVariant = useWideRelatedVariant;
    }

    public void setItemViewMode(final ItemViewMode itemViewMode) {
        this.itemMode = itemViewMode;
    }

    public void addInfoItemList(@Nullable final List<? extends InfoItem> data) {
        if (data == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "addInfoItemList() before > infoItemList.size() = "
                    + infoItemList.size() + ", data.size() = " + data.size());
        }

        final int offsetStart = sizeConsideringHeaderOffset();
        final List<InfoItem> visibleData = new ArrayList<>(data.size());
        final boolean hideMembersOnly = MembersOnlyContentHelper.shouldHide(
                infoItemBuilder.getContext());
        final ContentBlockingHelper.Rules blockingRules = ContentBlockingHelper.getRules(
                infoItemBuilder.getContext(), contentBlockingTarget);
        for (final InfoItem item : data) {
            if (!hideMembersOnly || !(item instanceof StreamInfoItem)
                    || !((StreamInfoItem) item).requiresMembership()) {
                if (blockingRules.isBlocked(item)) {
                    continue;
                }
                visibleData.add(item);
            }
        }
        infoItemList.addAll(visibleData);

        for (final InfoItem item : visibleData) {
            if (item instanceof StreamInfoItem) {
                loadDeArrowBranding((StreamInfoItem) item);
            }
        }

        if (DEBUG) {
            Log.d(TAG, "addInfoItemList() after > offsetStart = " + offsetStart + ", "
                    + "infoItemList.size() = " + infoItemList.size() + ", "
                    + "hasHeader = " + hasHeader() + ", "
                    + "showFooter = " + showFooter);
        }
        if (!visibleData.isEmpty()) {
            notifyItemRangeInserted(offsetStart, visibleData.size());
        }

        if (showFooter && !visibleData.isEmpty()) {
            final int footerNow = sizeConsideringHeaderOffset();
            notifyItemMoved(offsetStart, footerNow);

            if (DEBUG) {
                Log.d(TAG, "addInfoItemList() footer from " + offsetStart
                        + " to " + footerNow);
            }
        }
    }

    private void loadDeArrowBranding(@NonNull final StreamInfoItem item) {
        DeArrowService.getBranding(infoItemBuilder.getContext(), item)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(branding -> {
                    if (!DeArrowService.applyBranding(infoItemBuilder.getContext(), item,
                            branding)) {
                        return;
                    }
                    final int index = infoItemList.indexOf(item);
                    if (index >= 0) {
                        notifyItemChanged(index + (hasHeader() ? 1 : 0));
                    }
                }, throwable -> Log.w(TAG, "Unable to load DeArrow branding", throwable));
    }

    public void clearStreamItemList() {
        if (infoItemList.isEmpty()) {
            return;
        }
        infoItemList.clear();
        notifyDataSetChanged();
    }

    public void setHeaderSupplier(@Nullable final Supplier<View> headerSupplier) {
        final boolean changed = headerSupplier != this.headerSupplier;
        this.headerSupplier = headerSupplier;
        if (changed) {
            notifyDataSetChanged();
        }
    }

    protected boolean hasHeader() {
        return this.headerSupplier != null;
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
            notifyItemInserted(sizeConsideringHeaderOffset());
        } else {
            notifyItemRemoved(sizeConsideringHeaderOffset());
        }
    }

    private int sizeConsideringHeaderOffset() {
        final int i = infoItemList.size() + (hasHeader() ? 1 : 0);
        if (DEBUG) {
            Log.d(TAG, "sizeConsideringHeaderOffset() called → " + i);
        }
        return i;
    }

    public List<InfoItem> getItemsList() {
        return infoItemList;
    }

    @Nullable
    public StreamInfoItem getStreamAtPosition(final int position) {
        final int index = position - (hasHeader() ? 1 : 0);
        if (index < 0 || index >= infoItemList.size()) {
            return null;
        }
        final InfoItem item = infoItemList.get(index);
        return item instanceof StreamInfoItem ? (StreamInfoItem) item : null;
    }

    @Override
    public int getItemCount() {
        int count = infoItemList.size();
        if (hasHeader()) {
            count++;
        }
        if (showFooter) {
            count++;
        }

        if (DEBUG) {
            Log.d(TAG, "getItemCount() called with: "
                    + "count = " + count + ", infoItemList.size() = " + infoItemList.size() + ", "
                    + "hasHeader = " + hasHeader() + ", "
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
        if (position == infoItemList.size() && showFooter) {
            return FOOTER_TYPE;
        }
        final InfoItem item = infoItemList.get(position);
        switch (item.getInfoType()) {
            case STREAM:
                if (itemMode == ItemViewMode.CARD) {
                    return CARD_STREAM_HOLDER_TYPE;
                } else if (itemMode == ItemViewMode.GRID) {
                    return GRID_STREAM_HOLDER_TYPE;
                } else if (useWideRelatedVariant) {
                    return WIDE_RELATED_STREAM_HOLDER_TYPE;
                } else if (useMiniVariant) {
                    return MINI_STREAM_HOLDER_TYPE;
                } else {
                    return STREAM_HOLDER_TYPE;
                }
            case CHANNEL:
                if (itemMode == ItemViewMode.CARD) {
                    return CARD_CHANNEL_HOLDER_TYPE;
                } else if (itemMode == ItemViewMode.GRID) {
                    return GRID_CHANNEL_HOLDER_TYPE;
                } else if (useMiniVariant) {
                    return MINI_CHANNEL_HOLDER_TYPE;
                } else {
                    return CHANNEL_HOLDER_TYPE;
                }
            case PLAYLIST:
                if (itemMode == ItemViewMode.CARD) {
                    return CARD_PLAYLIST_HOLDER_TYPE;
                } else if (itemMode == ItemViewMode.GRID) {
                    return GRID_PLAYLIST_HOLDER_TYPE;
                } else if (useMiniVariant) {
                    return MINI_PLAYLIST_HOLDER_TYPE;
                } else {
                    return PLAYLIST_HOLDER_TYPE;
                }
            case COMMENT:
                return COMMENT_HOLDER_TYPE;
            case POST:
                return POST_HOLDER_TYPE;
            default:
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
            // #4475 and #3368
            // Always create a new instance otherwise the same instance
            // is sometimes reused which causes a crash
            case HEADER_TYPE:
                return new HFHolder(headerSupplier.get());
            case FOOTER_TYPE:
                return new HFHolder(PignateFooterBinding
                        .inflate(layoutInflater, parent, false)
                        .getRoot()
                );
            case MINI_STREAM_HOLDER_TYPE:
                return new StreamMiniInfoItemHolder(infoItemBuilder, parent);
            case STREAM_HOLDER_TYPE:
                return new StreamInfoItemHolder(infoItemBuilder, parent);
            case WIDE_RELATED_STREAM_HOLDER_TYPE:
                return new StreamWideRelatedInfoItemHolder(infoItemBuilder, parent);
            case GRID_STREAM_HOLDER_TYPE:
                return new StreamGridInfoItemHolder(infoItemBuilder, parent);
            case CARD_STREAM_HOLDER_TYPE:
                return new StreamCardInfoItemHolder(infoItemBuilder, parent);
            case MINI_CHANNEL_HOLDER_TYPE:
                return new ChannelMiniInfoItemHolder(infoItemBuilder, parent);
            case CHANNEL_HOLDER_TYPE:
                return new ChannelInfoItemHolder(infoItemBuilder, parent);
            case CARD_CHANNEL_HOLDER_TYPE:
                return new ChannelCardInfoItemHolder(infoItemBuilder, parent);
            case GRID_CHANNEL_HOLDER_TYPE:
                return new ChannelGridInfoItemHolder(infoItemBuilder, parent);
            case MINI_PLAYLIST_HOLDER_TYPE:
                return new PlaylistMiniInfoItemHolder(infoItemBuilder, parent);
            case PLAYLIST_HOLDER_TYPE:
                return new PlaylistInfoItemHolder(infoItemBuilder, parent);
            case GRID_PLAYLIST_HOLDER_TYPE:
                return new PlaylistGridInfoItemHolder(infoItemBuilder, parent);
            case CARD_PLAYLIST_HOLDER_TYPE:
                return new PlaylistCardInfoItemHolder(infoItemBuilder, parent);
            case COMMENT_HOLDER_TYPE:
                return new CommentInfoItemHolder(infoItemBuilder, parent);
            case POST_HOLDER_TYPE:
                return new PostInfoItemHolder(infoItemBuilder, parent);
            default:
                return new FallbackViewHolder(new View(parent.getContext()));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                 final int position) {
        if (DEBUG) {
            Log.d(TAG, "onBindViewHolder() called with: "
                    + "holder = [" + holder.getClass().getSimpleName() + "], "
                    + "position = [" + position + "]");
        }
        if (holder instanceof InfoItemHolder) {
            ((InfoItemHolder) holder).updateFromItem(
                    // If header is present, offset the items by -1
                    infoItemList.get(hasHeader() ? position - 1 : position), recordManager);
            final InfoItem boundItem = infoItemList.get(hasHeader() ? position - 1 : position);
            noteBadges.bindOnline(holder.itemView, boundItem);
            if (streamRowBound != null && boundItem instanceof StreamInfoItem) {
                streamRowBound.accept(holder.itemView, (StreamInfoItem) boundItem);
            } else if (streamRowRecycled != null) {
                streamRowRecycled.accept(holder.itemView);
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull final RecyclerView.ViewHolder holder) {
        if (streamRowRecycled != null) {
            streamRowRecycled.accept(holder.itemView);
        }
        if (holder instanceof InfoItemHolder) {
            ((InfoItemHolder) holder).recycle();
        }
        noteBadges.clear(holder.itemView);
        super.onViewRecycled(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        noteBadges.watch(recyclerView);
        if (downloadedAvailabilityObserver == null) {
            downloadedAvailabilityObserver = ignored -> notifyDataSetChanged();
            DownloadedAvailabilityIndex.INSTANCE.getUpdates().observeForever(
                    downloadedAvailabilityObserver);
        }
        DownloadedAvailabilityIndex.requestRefresh(recyclerView.getContext());
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull final RecyclerView recyclerView) {
        noteBadges.unwatch(recyclerView);
        if (downloadedAvailabilityObserver != null) {
            DownloadedAvailabilityIndex.INSTANCE.getUpdates().removeObserver(
                    downloadedAvailabilityObserver);
            downloadedAvailabilityObserver = null;
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int spanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(final int position) {
                final int type = getItemViewType(position);
                return type == HEADER_TYPE || type == FOOTER_TYPE || type == POST_HOLDER_TYPE
                        ? spanCount : 1;
            }
        };
    }

    static class HFHolder extends RecyclerView.ViewHolder {
        HFHolder(final View v) {
            super(v);
        }
    }
}
