package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.InfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder;
import org.schabi.newpipe.util.FallbackViewHolder;
import org.schabi.newpipe.util.OnClickGesture;

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

public class InfoListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = InfoListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int HEADER_TYPE = 0;
    private static final int FOOTER_TYPE = 1;

    private static final int MINI_STREAM_HOLDER_TYPE = 0x100;
    private static final int STREAM_HOLDER_TYPE = 0x101;
    private static final int MINI_CHANNEL_HOLDER_TYPE = 0x200;
    private static final int CHANNEL_HOLDER_TYPE = 0x201;
    private static final int MINI_PLAYLIST_HOLDER_TYPE = 0x300;
    private static final int PLAYLIST_HOLDER_TYPE = 0x301;
    private static final int MINI_COMMENT_HOLDER_TYPE = 0x400;
    private static final int COMMENT_HOLDER_TYPE = 0x401;

    private final InfoItemBuilder infoItemBuilder;
    private final ArrayList<InfoItem> infoItemList;
    private boolean useMiniVariant = false;
    private boolean showFooter = false;
    private View header = null;
    private View footer = null;

    public class HFHolder extends RecyclerView.ViewHolder {
        public View view;

        public HFHolder(View v) {
            super(v);
            view = v;
        }
    }

    public InfoListAdapter(Activity a) {
        infoItemBuilder = new InfoItemBuilder(a);
        infoItemList = new ArrayList<>();
    }

    public void setOnStreamSelectedListener(OnClickGesture<StreamInfoItem> listener) {
        infoItemBuilder.setOnStreamSelectedListener(listener);
    }

    public void setOnChannelSelectedListener(OnClickGesture<ChannelInfoItem> listener) {
        infoItemBuilder.setOnChannelSelectedListener(listener);
    }

    public void setOnPlaylistSelectedListener(OnClickGesture<PlaylistInfoItem> listener) {
        infoItemBuilder.setOnPlaylistSelectedListener(listener);
    }

    public void setOnCommentsSelectedListener(OnClickGesture<CommentsInfoItem> listener) {
        infoItemBuilder.setOnCommentsSelectedListener(listener);
    }

    public void useMiniItemVariants(boolean useMiniVariant) {
        this.useMiniVariant = useMiniVariant;
    }

    public void addInfoItemList(List<InfoItem> data) {
        if (data != null) {
            if (DEBUG) {
                Log.d(TAG, "addInfoItemList() before > infoItemList.size() = " + infoItemList.size() + ", data.size() = " + data.size());
            }

            int offsetStart = sizeConsideringHeaderOffset();
            infoItemList.addAll(data);

            if (DEBUG) {
                Log.d(TAG, "addInfoItemList() after > offsetStart = " + offsetStart + ", infoItemList.size() = " + infoItemList.size() + ", header = " + header + ", footer = " + footer + ", showFooter = " + showFooter);
            }

            notifyItemRangeInserted(offsetStart, data.size());

            if (footer != null && showFooter) {
                int footerNow = sizeConsideringHeaderOffset();
                notifyItemMoved(offsetStart, footerNow);

                if (DEBUG) Log.d(TAG, "addInfoItemList() footer from " + offsetStart + " to " + footerNow);
            }
        }
    }

    public void addInfoItem(InfoItem data) {
        if (data != null) {
            if (DEBUG) {
                Log.d(TAG, "addInfoItem() before > infoItemList.size() = " + infoItemList.size() + ", thread = " + Thread.currentThread());
            }

            int positionInserted = sizeConsideringHeaderOffset();
            infoItemList.add(data);

            if (DEBUG) {
                Log.d(TAG, "addInfoItem() after > position = " + positionInserted + ", infoItemList.size() = " + infoItemList.size() + ", header = " + header + ", footer = " + footer + ", showFooter = " + showFooter);
            }
            notifyItemInserted(positionInserted);

            if (footer != null && showFooter) {
                int footerNow = sizeConsideringHeaderOffset();
                notifyItemMoved(positionInserted, footerNow);

                if (DEBUG) Log.d(TAG, "addInfoItem() footer from " + positionInserted + " to " + footerNow);
            }
        }
    }

    public void clearStreamItemList() {
        if (infoItemList.isEmpty()) {
            return;
        }
        infoItemList.clear();
        notifyDataSetChanged();
    }

    public void setHeader(View header) {
        boolean changed = header != this.header;
        this.header = header;
        if (changed) notifyDataSetChanged();
    }

    public void setFooter(View view) {
        this.footer = view;
    }

    public void showFooter(boolean show) {
        if (DEBUG) Log.d(TAG, "showFooter() called with: show = [" + show + "]");
        if (show == showFooter) return;

        showFooter = show;
        if (show) notifyItemInserted(sizeConsideringHeaderOffset());
        else notifyItemRemoved(sizeConsideringHeaderOffset());
    }


    private int sizeConsideringHeaderOffset() {
        int i = infoItemList.size() + (header != null ? 1 : 0);
        if (DEBUG) Log.d(TAG, "sizeConsideringHeaderOffset() called → " + i);
        return i;
    }

    public ArrayList<InfoItem> getItemsList() {
        return infoItemList;
    }

    @Override
    public int getItemCount() {
        int count = infoItemList.size();
        if (header != null) count++;
        if (footer != null && showFooter) count++;

        if (DEBUG) {
            Log.d(TAG, "getItemCount() called, count = " + count + ", infoItemList.size() = " + infoItemList.size() + ", header = " + header + ", footer = " + footer + ", showFooter = " + showFooter);
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (DEBUG) Log.d(TAG, "getItemViewType() called with: position = [" + position + "]");

        if (header != null && position == 0) {
            return HEADER_TYPE;
        } else if (header != null) {
            position--;
        }
        if (footer != null && position == infoItemList.size() && showFooter) {
            return FOOTER_TYPE;
        }
        final InfoItem item = infoItemList.get(position);
        switch (item.getInfoType()) {
            case STREAM:
                return useMiniVariant ? MINI_STREAM_HOLDER_TYPE : STREAM_HOLDER_TYPE;
            case CHANNEL:
                return useMiniVariant ? MINI_CHANNEL_HOLDER_TYPE : CHANNEL_HOLDER_TYPE;
            case PLAYLIST:
                return useMiniVariant ? MINI_PLAYLIST_HOLDER_TYPE : PLAYLIST_HOLDER_TYPE;
            case COMMENT:
                return useMiniVariant ? MINI_COMMENT_HOLDER_TYPE : COMMENT_HOLDER_TYPE;
            default:
                Log.e(TAG, "Trollolo");
                return -1;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        if (DEBUG)
            Log.d(TAG, "onCreateViewHolder() called with: parent = [" + parent + "], type = [" + type + "]");
        switch (type) {
            case HEADER_TYPE:
                return new HFHolder(header);
            case FOOTER_TYPE:
                return new HFHolder(footer);
            case MINI_STREAM_HOLDER_TYPE:
                return new StreamMiniInfoItemHolder(infoItemBuilder, parent);
            case STREAM_HOLDER_TYPE:
                return new StreamInfoItemHolder(infoItemBuilder, parent);
            case MINI_CHANNEL_HOLDER_TYPE:
                return new ChannelMiniInfoItemHolder(infoItemBuilder, parent);
            case CHANNEL_HOLDER_TYPE:
                return new ChannelInfoItemHolder(infoItemBuilder, parent);
            case MINI_PLAYLIST_HOLDER_TYPE:
                return new PlaylistMiniInfoItemHolder(infoItemBuilder, parent);
            case PLAYLIST_HOLDER_TYPE:
                return new PlaylistInfoItemHolder(infoItemBuilder, parent);
            case MINI_COMMENT_HOLDER_TYPE:
                return new CommentsMiniInfoItemHolder(infoItemBuilder, parent);
            case COMMENT_HOLDER_TYPE:
                return new CommentsInfoItemHolder(infoItemBuilder, parent);
            default:
                Log.e(TAG, "Trollolo");
                return new FallbackViewHolder(new View(parent.getContext()));
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (DEBUG) Log.d(TAG, "onBindViewHolder() called with: holder = [" + holder.getClass().getSimpleName() + "], position = [" + position + "]");
        if (holder instanceof InfoItemHolder) {
            // If header isn't null, offset the items by -1
            if (header != null) position--;

            ((InfoItemHolder) holder).updateFromItem(infoItemList.get(position));
        } else if (holder instanceof HFHolder && position == 0 && header != null) {
            ((HFHolder) holder).view = header;
        } else if (holder instanceof HFHolder && position == sizeConsideringHeaderOffset() && footer != null && showFooter) {
            ((HFHolder) holder).view = footer;
        }
    }
}
