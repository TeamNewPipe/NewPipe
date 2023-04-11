package org.schabi.newpipe.info_list;

import android.content.Context;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.OnClickGesture;

/*
 * Created by Christian Schabesberger on 26.09.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoItemBuilder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class InfoItemBuilder {
    private final Context context;

    private OnClickGesture<StreamInfoItem> onStreamSelectedListener;
    private OnClickGesture<ChannelInfoItem> onChannelSelectedListener;
    private OnClickGesture<PlaylistInfoItem> onPlaylistSelectedListener;
    private OnClickGesture<CommentsInfoItem> onCommentsSelectedListener;

    private ListInfo<?> sourceListInfo; // the list-info the info-items from this list belong to

    public InfoItemBuilder(final Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public OnClickGesture<StreamInfoItem> getOnStreamSelectedListener() {
        return onStreamSelectedListener;
    }

    public void setOnStreamSelectedListener(final OnClickGesture<StreamInfoItem> listener) {
        this.onStreamSelectedListener = listener;
    }

    public OnClickGesture<ChannelInfoItem> getOnChannelSelectedListener() {
        return onChannelSelectedListener;
    }

    public void setOnChannelSelectedListener(final OnClickGesture<ChannelInfoItem> listener) {
        this.onChannelSelectedListener = listener;
    }

    public OnClickGesture<PlaylistInfoItem> getOnPlaylistSelectedListener() {
        return onPlaylistSelectedListener;
    }

    public void setOnPlaylistSelectedListener(final OnClickGesture<PlaylistInfoItem> listener) {
        this.onPlaylistSelectedListener = listener;
    }

    public OnClickGesture<CommentsInfoItem> getOnCommentsSelectedListener() {
        return onCommentsSelectedListener;
    }

    public void setOnCommentsSelectedListener(
            final OnClickGesture<CommentsInfoItem> onCommentsSelectedListener) {
        this.onCommentsSelectedListener = onCommentsSelectedListener;
    }

    public Info getSourceListInfo() {
        return sourceListInfo;
    }

    public void setSourceListInfo(final ListInfo<?> sourceListInfo) {
        this.sourceListInfo = sourceListInfo;
    }
}
