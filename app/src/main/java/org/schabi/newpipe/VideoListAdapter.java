package org.schabi.newpipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

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

public class VideoListAdapter extends BaseAdapter {
    private static final String TAG = VideoListAdapter.class.toString();

    private Context context;
    private VideoInfoItemViewCreator viewCreator;
    private Vector<VideoInfoItem> videoList = new Vector<>();
    private Vector<Boolean> downloadedThumbnailList = new Vector<>();
    VideoItemListFragment videoListFragment;
    ListView listView;

    public VideoListAdapter(Context context, VideoItemListFragment videoListFragment) {
        viewCreator = new VideoInfoItemViewCreator(LayoutInflater.from(context));
        this.videoListFragment = videoListFragment;
        this.listView = videoListFragment.getListView();
        this.context = context;
    }

    public void addVideoList(Vector<VideoInfoItem> videos) {
        videoList.addAll(videos);
        for(int i = 0; i < videos.size(); i++) {
            downloadedThumbnailList.add(false);
        }
        notifyDataSetChanged();
    }

    public void clearVideoList() {
        videoList = new Vector<>();
        downloadedThumbnailList = new Vector<>();
        notifyDataSetChanged();
    }

    public Vector<VideoInfoItem> getVideoList() {
        return videoList;
    }

    public void updateDownloadedThumbnailList(int index, boolean val) {
        downloadedThumbnailList.set(index, val);
    }

    public Vector<Boolean> getDownloadedThumbnailList() {
        return downloadedThumbnailList;
    }

    public void setThumbnail(int index, Bitmap thumbnail) {
        videoList.get(index).thumbnail = thumbnail;
        downloadedThumbnailList.set(index, true);
        notifyDataSetChanged();
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
        convertView = viewCreator.getViewByVideoInfoItem(convertView, parent, videoList.get(position));

        if(listView.isItemChecked(position)) {
            convertView.setBackgroundColor(ContextCompat.getColor(context,R.color.primaryColorYoutube));
        } else {
            convertView.setBackgroundColor(0);
        }

        return convertView;
    }
}