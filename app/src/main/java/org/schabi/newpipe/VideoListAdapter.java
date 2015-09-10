package org.schabi.newpipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Vector;

/**
 * Created by the-scrabi on 11.08.15.
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
    private LayoutInflater inflater;
    private Vector<VideoInfoItem> videoList = new Vector<>();
    private Vector<Boolean> downloadedThumbnailList = new Vector<>();
    VideoItemListFragment videoListFragment;
    ListView listView;

    public VideoListAdapter(Context context, VideoItemListFragment videoListFragment) {
        inflater = LayoutInflater.from(context);
        this.videoListFragment = videoListFragment;
        this.listView = videoListFragment.getListView();
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
        ViewHolder holder;
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.video_item, parent, false);
            holder = new ViewHolder();
            holder.itemThumbnailView = (ImageView) convertView.findViewById(R.id.itemThumbnailView);
            holder.itemVideoTitleView = (TextView) convertView.findViewById(R.id.itemVideoTitleView);
            holder.itemUploaderView = (TextView) convertView.findViewById(R.id.itemUploaderView);
            holder.itemDurationView = (TextView) convertView.findViewById(R.id.itemDurationView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Context context = parent.getContext();
        if(videoList.get(position).thumbnail == null) {
            holder.itemThumbnailView.setImageResource(R.drawable.dummi_thumbnail);
        } else {
            holder.itemThumbnailView.setImageBitmap(videoList.get(position).thumbnail);
        }
        holder.itemVideoTitleView.setText(videoList.get(position).title);
        holder.itemUploaderView.setText(videoList.get(position).uploader);
        holder.itemDurationView.setText(videoList.get(position).duration);

        if(listView.isItemChecked(position)) {
            convertView.setBackgroundColor(context.getResources().getColor(R.color.primaryColorYoutube));
        } else {
            convertView.setBackgroundColor(0);
        }

        return convertView;
    }

    private class ViewHolder {
        public ImageView itemThumbnailView;
        public TextView itemVideoTitleView, itemUploaderView, itemDurationView;
    }
}
