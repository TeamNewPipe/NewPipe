package org.schabi.newpipe;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.StreamPreviewInfo;

/**
 * Created by Christian Schabesberger on 24.10.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * StreamInfoItemViewCreator.java is part of NewPipe.
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

public class StreamInfoItemViewCreator {

    private class ViewHolder {
        public ImageView itemThumbnailView;
        public TextView itemVideoTitleView,
                itemUploaderView,
                itemDurationView,
                itemUploadDateView,
                itemViewCountView;
    }

    private View rootView = null; //root view of the activty
    private Activity activity = null;
    private final LayoutInflater inflater;
    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder().cacheInMemory(true).build();

    public StreamInfoItemViewCreator(LayoutInflater inflater, Activity a, View rootView) {
        this.inflater = inflater;
        activity = a;
        this.rootView = rootView;
    }

    public View getViewFromVideoInfoItem(View convertView, ViewGroup parent, final StreamPreviewInfo info) {
        ViewHolder holder;

        // generate holder
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

        // fill holder with information
        holder.itemVideoTitleView.setText(info.title);
        if(info.uploader != null && !info.uploader.isEmpty()) {
            holder.itemUploaderView.setText(info.uploader);
        } else {
            holder.itemUploaderView.setVisibility(View.INVISIBLE);
        }
        if(info.duration > 0) {
            holder.itemDurationView.setText(getDurationString(info.duration));
        } else {
            if(info.stream_type == AbstractVideoInfo.StreamType.LIVE_STREAM) {
                holder.itemDurationView.setText(R.string.duration_live);
            } else {
                holder.itemDurationView.setVisibility(View.GONE);
            }
        }
        if(info.view_count >= 0) {
            holder.itemViewCountView.setText(shortViewCount(info.view_count));
        } else {
            holder.itemViewCountView.setVisibility(View.GONE);
        }
        if(info.upload_date != null && !info.upload_date.isEmpty()) {
            holder.itemUploadDateView.setText(info.upload_date + " • ");
        }

        holder.itemThumbnailView.setImageResource(R.drawable.dummy_thumbnail);
        if(info.thumbnail_url != null && !info.thumbnail_url.isEmpty()) {
            imageLoader.displayImage(info.thumbnail_url,
                    holder.itemThumbnailView,
                    displayImageOptions,
                    new ImageErrorLoadingListener(activity, rootView, info.service_id));
        }

        return convertView;
    }


    public View setupView(View convertView, final StreamPreviewInfo info) {
        convertView.setClickable(true);
        convertView.setFocusable(true);


        int[] attrs = new int[]{R.attr.selectableItemBackground};
        TypedArray typedArray = activity.obtainStyledAttributes(attrs);
        int backgroundResource = typedArray.getResourceId(0, 0);
        convertView.setBackgroundResource(backgroundResource);
        typedArray.recycle();

        convertView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Intent detailIntent = new Intent(activity, VideoItemDetailActivity.class);
                    detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, info.webpage_url);
                    detailIntent.putExtra(
                            VideoItemDetailFragment.STREAMING_SERVICE, info.service_id);
                    activity.startActivity(detailIntent);
                    return true;
                }
                return false;
            }
        });

        ImageView rthumb = (ImageView) convertView.findViewById(R.id.itemThumbnailView);
        imageLoader.displayImage(info.thumbnail_url, rthumb,
                displayImageOptions, new ImageErrorLoadingListener(activity, rootView, info.service_id));

        return convertView;
    }

    public static String shortViewCount(Long viewCount){
        if(viewCount >= 1000000000){
            return Long.toString(viewCount/1000000000)+"B views";
        }else if(viewCount>=1000000){
            return Long.toString(viewCount/1000000)+"M views";
        }else if(viewCount>=1000){
            return Long.toString(viewCount/1000)+"K views";
        }else {
            return Long.toString(viewCount)+" views";
        }
    }

    public static String getDurationString(int duration) {
        String output = "";
        int days = duration / (24 * 60 * 60); /* greater than a day */
        duration %= (24 * 60 * 60);
        int hours = duration / (60 * 60); /* greater than an hour */
        duration %= (60 * 60);
        int minutes = duration / 60;
        int seconds = duration % 60;

        //handle days
        if(days > 0) {
            output = Integer.toString(days) + ":";
        }
        // handle hours
        if(hours > 0 || !output.isEmpty()) {
            if(hours > 0) {
                if(hours >= 10 || output.isEmpty()) {
                    output += Integer.toString(hours);
                } else {
                    output += "0" + Integer.toString(hours);
                }
            } else {
                output += "00";
            }
            output += ":";
        }
        //handle minutes
        if(minutes > 0 || !output.isEmpty()) {
            if(minutes > 0) {
                if(minutes >= 10 || output.isEmpty()) {
                    output += Integer.toString(minutes);
                } else {
                    output += "0" + Integer.toString(minutes);
                }
            } else {
                output += "00";
            }
            output += ":";
        }

        //handle seconds
        if(output.isEmpty()) {
            output += "0:";
        }

        if(seconds >= 10) {
            output += Integer.toString(seconds);
        } else {
            output += "0" + Integer.toString(seconds);
        }

        return output;
    }
}
