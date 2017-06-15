package org.schabi.newpipe.info_list;

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
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;

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

public class InfoItemBuilder {

    private final String viewsS;
    private final String videosS;
    private final String subsS;
    private final String subsPluralS;

    private final String thousand;
    private final String million;
    private final String billion;

    private static final String TAG = InfoItemBuilder.class.toString();

    public interface OnInfoItemSelectedListener {
        void selected(int serviceId, String url, String title);
    }

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private static final DisplayImageOptions DISPLAY_IMAGE_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .build();
    private static final DisplayImageOptions DISPLAY_STREAM_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
            .cloneFrom(DISPLAY_IMAGE_OPTIONS)
            .showImageOnFail(R.drawable.dummy_thumbnail)
            .showImageForEmptyUri(R.drawable.dummy_thumbnail)
            .showImageOnLoading(R.drawable.dummy_thumbnail)
            .build();

    private static final DisplayImageOptions DISPLAY_CHANNEL_THUMBNAIL_OPTIONS =
            new DisplayImageOptions.Builder()
            .cloneFrom(DISPLAY_IMAGE_OPTIONS)
            .showImageOnLoading(R.drawable.buddy_channel_item)
            .showImageForEmptyUri(R.drawable.buddy_channel_item)
            .showImageOnFail(R.drawable.buddy_channel_item)
            .build();
    private OnInfoItemSelectedListener onStreamInfoItemSelectedListener;
    private OnInfoItemSelectedListener onChannelInfoItemSelectedListener;

    public InfoItemBuilder(Context context) {
        viewsS = context.getString(R.string.views);
        videosS = context.getString(R.string.videos);
        subsS = context.getString(R.string.subscriber);
        subsPluralS = context.getString(R.string.subscriber_plural);
        thousand = context.getString(R.string.short_thousand);
        million = context.getString(R.string.short_million);
        billion = context.getString(R.string.short_billion);
    }

    public void setOnStreamInfoItemSelectedListener(
            OnInfoItemSelectedListener listener) {
        this.onStreamInfoItemSelectedListener = listener;
    }

    public void setOnChannelInfoItemSelectedListener(
            OnInfoItemSelectedListener listener) {
        this.onChannelInfoItemSelectedListener = listener;
    }

    public void buildByHolder(InfoItemHolder holder, final InfoItem i) {
        if (i.infoType() != holder.infoType())
            return;
        switch (i.infoType()) {
            case STREAM:
                buildStreamInfoItem((StreamInfoItemHolder) holder, (StreamInfoItem) i);
                break;
            case CHANNEL:
                buildChannelInfoItem((ChannelInfoItemHolder) holder, (ChannelInfoItem) i);
                break;
            case PLAYLIST:
                Log.e(TAG, "Not yet implemented");
                break;
            default:
                Log.e(TAG, "Trollolo");
        }
    }

    private String getStreamInfoDetailLine(final StreamInfoItem info) {
        String viewsAndDate = "";
        if(info.view_count >= 0) {
            viewsAndDate = shortViewCount(info.view_count);
        }
        if(!TextUtils.isEmpty(info.upload_date)) {
            if(viewsAndDate.isEmpty()) {
                viewsAndDate = info.upload_date;
            } else {
                viewsAndDate += " • " + info.upload_date;
            }
        }
        return viewsAndDate;
    }

    private void buildStreamInfoItem(StreamInfoItemHolder holder, final StreamInfoItem info) {
        if (info.infoType() != InfoItem.InfoType.STREAM) {
            Log.e("InfoItemBuilder", "Info type not yet supported");
        }
        // fill holder with information
        if (!TextUtils.isEmpty(info.title)) holder.itemVideoTitleView.setText(info.title);

        if (!TextUtils.isEmpty(info.uploader)) holder.itemUploaderView.setText(info.uploader);
        else holder.itemUploaderView.setVisibility(View.INVISIBLE);

        if (info.duration > 0) {
            holder.itemDurationView.setText(getDurationString(info.duration));
        } else {
            if (info.stream_type == AbstractStreamInfo.StreamType.LIVE_STREAM) {
                holder.itemDurationView.setText(R.string.duration_live);
            } else {
                holder.itemDurationView.setVisibility(View.GONE);
            }
        }

        holder.itemAdditionalDetails.setText(getStreamInfoDetailLine(info));

        // Default thumbnail is shown on error, while loading and if the url is empty
        imageLoader.displayImage(info.thumbnail_url,
                holder.itemThumbnailView,
                DISPLAY_STREAM_THUMBNAIL_OPTIONS,
                new ImageErrorLoadingListener(holder.itemRoot.getContext(), holder.itemRoot.getRootView(), info.service_id));


        holder.itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onStreamInfoItemSelectedListener != null) {
                    onStreamInfoItemSelectedListener.selected(info.service_id, info.webpage_url, info.getTitle());
                }
            }
        });
    }

    private String getChannelInfoDetailLine(final ChannelInfoItem info) {
        String details = "";
        if(info.subscriberCount >= 0) {
            details = shortSubscriber(info.subscriberCount);
        }
        if(info.videoAmount >= 0) {
            String formattedVideoAmount = info.videoAmount + " " + videosS;
            if(!details.isEmpty()) {
                details += " • " + formattedVideoAmount;
            } else {
                details = formattedVideoAmount;
            }
        }
        return details;
    }

    private void buildChannelInfoItem(ChannelInfoItemHolder holder, final ChannelInfoItem info) {
        if (!TextUtils.isEmpty(info.getTitle())) holder.itemChannelTitleView.setText(info.getTitle());
        holder.itemAdditionalDetailView.setText(getChannelInfoDetailLine(info));
        if (!TextUtils.isEmpty(info.description)) holder.itemChannelDescriptionView.setText(info.description);

        imageLoader.displayImage(info.thumbnailUrl,
                holder.itemThumbnailView,
                DISPLAY_CHANNEL_THUMBNAIL_OPTIONS,
                new ImageErrorLoadingListener(holder.itemRoot.getContext(), holder.itemRoot.getRootView(), info.serviceId));


        holder.itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onStreamInfoItemSelectedListener != null) {
                    onChannelInfoItemSelectedListener.selected(info.serviceId, info.getLink(), info.channelName);
                }
            }
        });
    }


    public String shortViewCount(Long viewCount) {
        if (viewCount >= 1000000000) {
            return Long.toString(viewCount / 1000000000) + billion + " " + viewsS;
        } else if (viewCount >= 1000000) {
            return Long.toString(viewCount / 1000000) + million + " " + viewsS;
        } else if (viewCount >= 1000) {
            return Long.toString(viewCount / 1000) + thousand + " " + viewsS;
        } else {
            return Long.toString(viewCount) + " " + viewsS;
        }
    }

    public String shortSubscriber(Long count) {
        String curSubString = count > 1 ? subsPluralS : subsS;

        if (count >= 1000000000) {
            return Long.toString(count / 1000000000) + billion + " " + curSubString;
        } else if (count >= 1000000) {
            return Long.toString(count / 1000000) + million + " " + curSubString;
        } else if (count >= 1000) {
            return Long.toString(count / 1000) + thousand + " " + curSubString;
        } else {
            return Long.toString(count) + " " + curSubString;
        }
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
