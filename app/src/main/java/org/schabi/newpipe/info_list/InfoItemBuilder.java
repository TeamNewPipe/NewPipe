package org.schabi.newpipe.info_list;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.InfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder;
import org.schabi.newpipe.util.OnClickGesture;

/*
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
    private static final String TAG = InfoItemBuilder.class.toString();

    private final Context context;
    private final ImageLoader imageLoader = ImageLoader.getInstance();

    private OnClickGesture<StreamInfoItem> onStreamSelectedListener;
    private OnClickGesture<ChannelInfoItem> onChannelSelectedListener;
    private OnClickGesture<PlaylistInfoItem> onPlaylistSelectedListener;
    private OnClickGesture<CommentsInfoItem> onCommentsSelectedListener;

    public InfoItemBuilder(Context context) {
        this.context = context;
    }

    public View buildView(@NonNull ViewGroup parent, @NonNull final InfoItem infoItem) {
        return buildView(parent, infoItem, false);
    }

    public View buildView(@NonNull ViewGroup parent, @NonNull final InfoItem infoItem, boolean useMiniVariant) {
        InfoItemHolder holder = holderFromInfoType(parent, infoItem.getInfoType(), useMiniVariant);
        holder.updateFromItem(infoItem);
        return holder.itemView;
    }

    private InfoItemHolder holderFromInfoType(@NonNull ViewGroup parent, @NonNull InfoItem.InfoType infoType, boolean useMiniVariant) {
        switch (infoType) {
            case STREAM:
                return useMiniVariant ? new StreamMiniInfoItemHolder(this, parent) : new StreamInfoItemHolder(this, parent);
            case CHANNEL:
                return useMiniVariant ? new ChannelMiniInfoItemHolder(this, parent) : new ChannelInfoItemHolder(this, parent);
            case PLAYLIST:
                return useMiniVariant ? new PlaylistMiniInfoItemHolder(this, parent) : new PlaylistInfoItemHolder(this, parent);
            case COMMENT:
                return useMiniVariant ? new CommentsMiniInfoItemHolder(this, parent) : new CommentsInfoItemHolder(this, parent);
            default:
                Log.e(TAG, "Trollolo");
                throw new RuntimeException("InfoType not expected = " + infoType.name());
        }
    }

    public Context getContext() {
        return context;
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }

    public OnClickGesture<StreamInfoItem> getOnStreamSelectedListener() {
        return onStreamSelectedListener;
    }

    public void setOnStreamSelectedListener(OnClickGesture<StreamInfoItem> listener) {
        this.onStreamSelectedListener = listener;
    }

    public OnClickGesture<ChannelInfoItem> getOnChannelSelectedListener() {
        return onChannelSelectedListener;
    }

    public void setOnChannelSelectedListener(OnClickGesture<ChannelInfoItem> listener) {
        this.onChannelSelectedListener = listener;
    }

    public OnClickGesture<PlaylistInfoItem> getOnPlaylistSelectedListener() {
        return onPlaylistSelectedListener;
    }

    public void setOnPlaylistSelectedListener(OnClickGesture<PlaylistInfoItem> listener) {
        this.onPlaylistSelectedListener = listener;
    }

    public OnClickGesture<CommentsInfoItem> getOnCommentsSelectedListener() {
        return onCommentsSelectedListener;
    }

    public void setOnCommentsSelectedListener(OnClickGesture<CommentsInfoItem> onCommentsSelectedListener) {
        this.onCommentsSelectedListener = onCommentsSelectedListener;
    }

}
