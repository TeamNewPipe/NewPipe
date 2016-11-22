package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;

import java.util.List;
import java.util.Vector;


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

public class InfoListAdapter extends RecyclerView.Adapter<InfoItemHolder> implements ItemTouchHelperAdapter{

    private InfoItemBuilder infoItemBuilder;
    private List<StreamPreviewInfo> streamList = new Vector<>();
    private ItemListener itemListener = null;

    public interface ItemListener {
        void deletedItem(final StreamPreviewInfo deletedItem);
        boolean moveItem(final int fromPosition, final int toPosition);
    }

    public InfoListAdapter(Activity a, View rootView) {
        this.infoItemBuilder = new InfoItemBuilder(a, rootView);
    }

    public void setOnItemSelectedListener
            (InfoItemBuilder.OnItemSelectedListener onItemSelectedListener) {
        infoItemBuilder.setOnItemSelectedListener(onItemSelectedListener);
    }

    public void setOnPlayListActionListener
            (InfoItemBuilder.OnPlayListActionListener onPlaylistActionListener) {
        infoItemBuilder.setOnPlayListActionListener(onPlaylistActionListener);
    }

    public void setOnItemDeleteListener(ItemListener deletedListener) {
        this.itemListener = deletedListener;
    }

    public void addStreamItemList(List<StreamPreviewInfo> videos) {
        if(videos!= null) {
            streamList.addAll(videos);
            notifyDataSetChanged();
        }
    }

    public void clearSteamItemList() {
        streamList.clear();
        notifyDataSetChanged();
    }

    public List<StreamPreviewInfo> getStreamList() {
        return streamList;
    }

    @Override
    public int getItemCount() {
        return streamList.size();
    }

    @Override
    public InfoItemHolder onCreateViewHolder(ViewGroup parent, int i) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.video_item, parent, false);

        return new InfoItemHolder(itemView);
    }

    @Override
    public void onBindViewHolder(InfoItemHolder holder, int i) {
        infoItemBuilder.buildByHolder(holder, streamList.get(i));
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        if(itemListener != null) {
            return itemListener.moveItem(fromPosition, toPosition);
        }
        return true;
    }

    @Override
    public void onItemDismiss(int position) {
        StreamPreviewInfo info = streamList.get(position);
        streamList.remove(position);
        notifyItemRemoved(position);
        if(itemListener != null) {
            itemListener.deletedItem(info);
        }
    }
}
