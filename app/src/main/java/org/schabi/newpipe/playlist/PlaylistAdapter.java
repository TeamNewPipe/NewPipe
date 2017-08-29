package org.schabi.newpipe.playlist;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.StreamInfoItemHolder;

import java.util.ArrayList;
import java.util.List;

/**
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

public class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = PlaylistAdapter.class.toString();

    private final PlaylistItemBuilder playlistItemBuilder;
    private final List<PlaylistItem> playlistItems;
    private boolean showFooter = false;
    private View header = null;
    private View footer = null;

    public class HFHolder extends RecyclerView.ViewHolder {
        public HFHolder(View v) {
            super(v);
            view = v;
        }
        public View view;
    }

    public void showFooter(boolean show) {
        showFooter = show;
        notifyDataSetChanged();
    }

    public PlaylistAdapter(List<PlaylistItem> data) {
        playlistItemBuilder = new PlaylistItemBuilder();
        playlistItems = data;
    }

    public void setSelectedListener(PlaylistItemBuilder.OnSelectedListener listener) {
        playlistItemBuilder.setOnSelectedListener(listener);
    }

    public void addInfoItemList(List<PlaylistItem> data) {
        if(data != null) {
            playlistItems.addAll(data);
            notifyDataSetChanged();
        }
    }

    public void addInfoItem(PlaylistItem data) {
        if (data != null) {
            playlistItems.add(data);
            notifyDataSetChanged();
        }
    }

    public void clearStreamItemList() {
        if(playlistItems.isEmpty()) {
            return;
        }
        playlistItems.clear();
        notifyDataSetChanged();
    }

    public void setHeader(View header) {
        this.header = header;
        notifyDataSetChanged();
    }

    public void setFooter(View footer) {
        this.footer = footer;
        notifyDataSetChanged();
    }

    public List<PlaylistItem> getItemsList() {
        return playlistItems;
    }

    @Override
    public int getItemCount() {
        int count = playlistItems.size();
        if(header != null) count++;
        if(footer != null && showFooter) count++;
        return count;
    }

    // don't ask why we have to do that this way... it's android accept it -.-
    @Override
    public int getItemViewType(int position) {
        if(header != null && position == 0) {
            return 0;
        } else if(header != null) {
            position--;
        }
        if(footer != null && position == playlistItems.size() && showFooter) {
            return 1;
        }
        return 2;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        switch(type) {
            case 0:
                return new HFHolder(header);
            case 1:
                return new HFHolder(footer);
            case 2:
                return new StreamInfoItemHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.playlist_stream_item, parent, false));
            default:
                Log.e(TAG, "Trollolo");
                return null;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if(holder instanceof PlaylistItemHolder) {
            if(header != null) {
                i--;
            }
            playlistItemBuilder.buildStreamInfoItem((PlaylistItemHolder) holder, playlistItems.get(i));
        } else if(holder instanceof HFHolder && i == 0 && header != null) {
            ((HFHolder) holder).view = header;
        } else if(holder instanceof HFHolder && i == playlistItems.size() && footer != null && showFooter) {
            ((HFHolder) holder).view = footer;
        }
    }
}
