package org.schabi.newpipe.playlist;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.ImageErrorLoadingListener;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.AbstractStreamInfo;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlayListInfoItem;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;
import org.schabi.newpipe.info_list.ChannelInfoItemHolder;
import org.schabi.newpipe.info_list.InfoItemHolder;
import org.schabi.newpipe.info_list.PlaylistInfoItemHolder;
import org.schabi.newpipe.info_list.StreamInfoItemHolder;

import java.util.Locale;

/**
 * Created by Christian Schabesberger on 26.09.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoItemBuilder.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class PlaylistItemBuilder {

    private static final String TAG = PlaylistItemBuilder.class.toString();

    public interface OnSelectedListener {
        void selected(int serviceId, String url, String title);
    }

    private OnSelectedListener onStreamInfoItemSelectedListener;

    public PlaylistItemBuilder() {}

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onStreamInfoItemSelectedListener = listener;
    }

    public View buildView(ViewGroup parent, final PlaylistItem item) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View itemView = inflater.inflate(R.layout.stream_item, parent, false);
        final PlaylistItemHolder holder = new PlaylistItemHolder(itemView);

        buildStreamInfoItem(holder, item);

        return itemView;
    }


    public void buildStreamInfoItem(PlaylistItemHolder holder, final PlaylistItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) holder.itemVideoTitleView.setText(item.getTitle());

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(getDurationString(item.getDuration()));
        } else {
            holder.itemDurationView.setVisibility(View.GONE);
        }

        holder.itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onStreamInfoItemSelectedListener != null) {
                    onStreamInfoItemSelectedListener.selected(item.getServiceId(), item.getUrl(), item.getTitle());
                }
            }
        });
    }


    public static String getDurationString(int duration) {
        if(duration < 0) {
            duration = 0;
        }
        String output;
        int days = duration / (24 * 60 * 60); /* greater than a day */
        duration %= (24 * 60 * 60);
        int hours = duration / (60 * 60); /* greater than an hour */
        duration %= (60 * 60);
        int minutes = duration / 60;
        int seconds = duration % 60;

        //handle days
        if (days > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds);
        } else if(hours > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            output = String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        return output;
    }
}
