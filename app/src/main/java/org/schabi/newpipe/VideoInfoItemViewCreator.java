package org.schabi.newpipe;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.extractor.VideoPreviewInfo;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Created by Christian Schabesberger on 24.10.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoInfoItemViewCreator.java is part of NewPipe.
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

class VideoInfoItemViewCreator {
    private final LayoutInflater inflater;
    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder().cacheInMemory(true).build();

    public VideoInfoItemViewCreator(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public View getViewFromVideoInfoItem(View convertView, ViewGroup parent, VideoPreviewInfo info, Context context) {
        ViewHolder holder;
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.video_item, parent, false);
            holder = new ViewHolder();
            holder.itemThumbnailView = (ImageView) convertView.findViewById(R.id.itemThumbnailView);
            holder.itemVideoTitleView = (TextView) convertView.findViewById(R.id.itemVideoTitleView);
            holder.itemUploaderView = (TextView) convertView.findViewById(R.id.itemUploaderView);
            holder.itemDurationView = (TextView) convertView.findViewById(R.id.itemDurationView);
            holder.itemUploadDateView = (TextView) convertView.findViewById(R.id.itemUploadDateView);
            holder.itemViewCountView = (TextView) convertView.findViewById(R.id.itemViewCountView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if(info.thumbnail == null) {
            holder.itemThumbnailView.setImageResource(R.drawable.dummy_thumbnail);
        } else {
            holder.itemThumbnailView.setImageBitmap(info.thumbnail);
        }
        holder.itemVideoTitleView.setText(info.title);
        holder.itemUploaderView.setText(info.uploader);
        holder.itemDurationView.setText(info.duration);
        holder.itemViewCountView.setText(shortViewCount(info.view_count));
        if(!info.upload_date.isEmpty()) {
            holder.itemUploadDateView.setText(info.upload_date+" • ");
        }

        imageLoader.displayImage(info.thumbnail_url, holder.itemThumbnailView, displayImageOptions);

        return convertView;
    }

    private class ViewHolder {
        public ImageView itemThumbnailView;
        public TextView itemVideoTitleView, itemUploaderView, itemDurationView, itemUploadDateView, itemViewCountView;
    }

    private String shortViewCount(Long view_count){
        if(view_count >= 1000000000){
            return Long.toString(view_count/1000000000)+"B views";
        }else if(view_count>=1000000){
            return Long.toString(view_count/1000000)+"M views";
        }else if(view_count>=1000){
            return Long.toString(view_count/1000)+"K views";
        }else {
            return Long.toString(view_count)+" views";
        }
    }

}
