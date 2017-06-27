package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;

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

public class InfoListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = InfoListAdapter.class.toString();

    private final InfoItemBuilder infoItemBuilder;
    private final List<InfoItem> infoItemList;
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

    public InfoListAdapter(Activity a) {
        infoItemBuilder = new InfoItemBuilder(a);
        infoItemList = new ArrayList<>();
    }

    public void setOnStreamInfoItemSelectedListener
            (InfoItemBuilder.OnInfoItemSelectedListener listener) {
        infoItemBuilder.setOnStreamInfoItemSelectedListener(listener);
    }

    public void setOnChannelInfoItemSelectedListener
            (InfoItemBuilder.OnInfoItemSelectedListener listener) {
        infoItemBuilder.setOnChannelInfoItemSelectedListener(listener);
    }

    public void addInfoItemList(List<InfoItem> data) {
        if(data != null) {
            infoItemList.addAll(data);
            notifyDataSetChanged();
        }
    }

    public void clearStreamItemList() {
        if(infoItemList.isEmpty()) {
            return;
        }
        infoItemList.clear();
        notifyDataSetChanged();
    }

    public void setHeader(View header) {
        this.header = header;
        notifyDataSetChanged();
    }

    public void setFooter(View view) {
        this.footer = view;
        notifyDataSetChanged();
    }

    public List<InfoItem> getItemsList() {
        return infoItemList;
    }

    @Override
    public int getItemCount() {
        int count = infoItemList.size();
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
        if(footer != null && position == infoItemList.size() && showFooter) {
            return 1;
        }
        switch(infoItemList.get(position).infoType()) {
            case STREAM:
                return 2;
            case CHANNEL:
                return 3;
            case PLAYLIST:
                return 4;
            default:
                Log.e(TAG, "Trollolo");
                return -1;
        }
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
                        .inflate(R.layout.stream_item, parent, false));
            case 3:
                return new ChannelInfoItemHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.channel_item, parent, false));
            case 4:
                Log.e(TAG, "Playlist is not yet implemented");
                return null;
            default:
                Log.e(TAG, "Trollolo");
                return null;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        //god damen f*** ANDROID SH**
        if(holder instanceof InfoItemHolder) {
            if(header != null) {
                i--;
            }
            infoItemBuilder.buildByHolder((InfoItemHolder) holder, infoItemList.get(i));
        } else if(holder instanceof HFHolder && i == 0 && header != null) {
            ((HFHolder) holder).view = header;
        } else if(holder instanceof HFHolder && i == infoItemList.size() && footer != null && showFooter) {
            ((HFHolder) holder).view = footer;
        }
    }
}
