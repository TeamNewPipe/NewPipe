package org.schabi.newpipe.player.playqueue;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.playqueue.events.AppendEvent;
import org.schabi.newpipe.player.playqueue.events.ErrorEvent;
import org.schabi.newpipe.player.playqueue.events.MoveEvent;
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent;
import org.schabi.newpipe.player.playqueue.events.RemoveEvent;
import org.schabi.newpipe.player.playqueue.events.SelectEvent;
import org.schabi.newpipe.util.FallbackViewHolder;

import java.util.List;

import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

/**
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoListAdapter.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class PlayQueueAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = PlayQueueAdapter.class.toString();

    private static final int ITEM_VIEW_TYPE_ID = 0;
    private static final int FOOTER_VIEW_TYPE_ID = 1;

    private final PlayQueueItemBuilder playQueueItemBuilder;
    private final PlayQueue playQueue;
    private boolean showFooter = false;
    private View footer = null;

    private Disposable playQueueReactor;

    public PlayQueueAdapter(final Context context, final PlayQueue playQueue) {
        if (playQueue.getBroadcastReceiver() == null) {
            throw new IllegalStateException("Play Queue has not been initialized.");
        }

        this.playQueueItemBuilder = new PlayQueueItemBuilder(context);
        this.playQueue = playQueue;

        playQueue.getBroadcastReceiver().toObservable().subscribe(getReactor());
    }

    private Observer<PlayQueueEvent> getReactor() {
        return new Observer<PlayQueueEvent>() {
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                if (playQueueReactor != null) {
                    playQueueReactor.dispose();
                }
                playQueueReactor = d;
            }

            @Override
            public void onNext(@NonNull final PlayQueueEvent playQueueMessage) {
                if (playQueueReactor != null) {
                    onPlayQueueChanged(playQueueMessage);
                }
            }

            @Override
            public void onError(@NonNull final Throwable e) { }

            @Override
            public void onComplete() {
                dispose();
            }
        };

    }

    private void onPlayQueueChanged(final PlayQueueEvent message) {
        switch (message.type()) {
            case RECOVERY:
                // Do nothing.
                break;
            case SELECT:
                final SelectEvent selectEvent = (SelectEvent) message;
                notifyItemChanged(selectEvent.getOldIndex());
                notifyItemChanged(selectEvent.getNewIndex());
                break;
            case APPEND:
                final AppendEvent appendEvent = (AppendEvent) message;
                notifyItemRangeInserted(playQueue.size(), appendEvent.getAmount());
                break;
            case ERROR:
                final ErrorEvent errorEvent = (ErrorEvent) message;
                notifyItemChanged(errorEvent.getErrorIndex());
                notifyItemChanged(errorEvent.getQueueIndex());
                break;
            case REMOVE:
                final RemoveEvent removeEvent = (RemoveEvent) message;
                notifyItemRemoved(removeEvent.getRemoveIndex());
                notifyItemChanged(removeEvent.getQueueIndex());
                break;
            case MOVE:
                final MoveEvent moveEvent = (MoveEvent) message;
                notifyItemMoved(moveEvent.getFromIndex(), moveEvent.getToIndex());
                break;
            case INIT:
            case REORDER:
            default:
                notifyDataSetChanged();
                break;
        }
    }

    public void dispose() {
        if (playQueueReactor != null) {
            playQueueReactor.dispose();
        }
        playQueueReactor = null;
    }

    public void setSelectedListener(final PlayQueueItemBuilder.OnSelectedListener listener) {
        playQueueItemBuilder.setOnSelectedListener(listener);
    }

    public void unsetSelectedListener() {
        playQueueItemBuilder.setOnSelectedListener(null);
    }

    public void setFooter(final View footer) {
        this.footer = footer;
        notifyItemChanged(playQueue.size());
    }

    public void showFooter(final boolean show) {
        showFooter = show;
        notifyItemChanged(playQueue.size());
    }

    public List<PlayQueueItem> getItems() {
        return playQueue.getStreams();
    }

    @Override
    public int getItemCount() {
        int count = playQueue.getStreams().size();
        if (footer != null && showFooter) {
            count++;
        }
        return count;
    }

    @Override
    public int getItemViewType(final int position) {
        if (footer != null && position == playQueue.getStreams().size() && showFooter) {
            return FOOTER_VIEW_TYPE_ID;
        }

        return ITEM_VIEW_TYPE_ID;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int type) {
        switch (type) {
            case FOOTER_VIEW_TYPE_ID:
                return new HFHolder(footer);
            case ITEM_VIEW_TYPE_ID:
                return new PlayQueueItemHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.play_queue_item, parent, false));
            default:
                Log.e(TAG, "Attempting to create view holder with undefined type: " + type);
                return new FallbackViewHolder(new View(parent.getContext()));
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof PlayQueueItemHolder) {
            final PlayQueueItemHolder itemHolder = (PlayQueueItemHolder) holder;

            // Build the list item
            playQueueItemBuilder
                    .buildStreamInfoItem(itemHolder, playQueue.getStreams().get(position));

            // Check if the current item should be selected/highlighted
            final boolean isSelected = playQueue.getIndex() == position;
            itemHolder.itemSelected.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            itemHolder.itemView.setSelected(isSelected);
        } else if (holder instanceof HFHolder && position == playQueue.getStreams().size()
                && footer != null && showFooter) {
            ((HFHolder) holder).view = footer;
        }
    }

    public class HFHolder extends RecyclerView.ViewHolder {
        public View view;

        public HFHolder(final View v) {
            super(v);
            view = v;
        }
    }
}
