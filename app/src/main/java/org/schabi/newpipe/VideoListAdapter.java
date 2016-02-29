package org.schabi.newpipe;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import org.schabi.newpipe.extractor.StreamPreviewInfo;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 11.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoListAdapter.java is part of NewPipe.
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

class VideoListAdapter extends BaseAdapter {
    private final Context context;
    private final VideoInfoItemViewCreator viewCreator;
    private Vector<StreamPreviewInfo> videoList = new Vector<>();
    private final ListView listView;

    public VideoListAdapter(Context context, VideoItemListFragment videoListFragment) {
        viewCreator = new VideoInfoItemViewCreator(LayoutInflater.from(context));
        this.listView = videoListFragment.getListView();
        this.listView.setDivider(null);
        this.listView.setDividerHeight(0);
        this.context = context;
    }

    public void addVideoList(List<StreamPreviewInfo> videos) {
        videoList.addAll(videos);
        notifyDataSetChanged();
    }

    public void clearVideoList() {
        videoList = new Vector<>();
        notifyDataSetChanged();
    }

    public Vector<StreamPreviewInfo> getVideoList() {
        return videoList;
    }

    @Override
    public int getCount() {
        return videoList.size();
    }

    @Override
    public Object getItem(int position) {
        return videoList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = viewCreator.getViewFromVideoInfoItem(convertView, parent, videoList.get(position));

        if(listView.isItemChecked(position)) {
            convertView.setBackgroundColor(ContextCompat.getColor(context,R.color.light_youtube_primary_color));
        } else {
            convertView.setBackgroundColor(0);
        }

        return convertView;
    }
}