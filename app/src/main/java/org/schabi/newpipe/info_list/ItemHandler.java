package org.schabi.newpipe.info_list;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.OnClickGesture;

import java.text.DateFormat;

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

public class ItemHandler {
    private final FragmentActivity activity;
    private final ImageLoader imageLoader = ImageLoader.getInstance();
    private final DateFormat dateFormat;

    @Nullable private OnClickGesture<StreamInfoItem> onStreamSelectedListener;
    @Nullable private OnClickGesture<ChannelInfoItem> onChannelSelectedListener;
    @Nullable private OnClickGesture<PlaylistInfoItem> onPlaylistSelectedListener;
    @Nullable private OnClickGesture<CommentsInfoItem> onCommentsSelectedListener;
    @Nullable private OnClickGesture<LocalItem> onLocalItemSelectedListener;

    public ItemHandler(final FragmentActivity activity, final DateFormat dateFormat) {
        this.activity = activity;
        this.dateFormat = dateFormat;
    }


    public FragmentActivity getActivity() {
        return activity;
    }

    public FragmentManager getFragmentManager() {
        return activity.getSupportFragmentManager();
    }

    public void displayImage(final String url, final ImageView view,
                             final DisplayImageOptions options) {
        imageLoader.displayImage(url, view, options);
    }

    public DateFormat getDateFormat() {
        return dateFormat;
    }


    @Nullable
    public OnClickGesture<StreamInfoItem> getOnStreamSelectedListener() {
        return onStreamSelectedListener;
    }

    public void setOnStreamSelectedListener(
            @Nullable final OnClickGesture<StreamInfoItem> listener) {
        this.onStreamSelectedListener = listener;
    }

    @Nullable
    public OnClickGesture<ChannelInfoItem> getOnChannelSelectedListener() {
        return onChannelSelectedListener;
    }

    public void setOnChannelSelectedListener(
            @Nullable final OnClickGesture<ChannelInfoItem> listener) {
        this.onChannelSelectedListener = listener;
    }

    @Nullable
    public OnClickGesture<PlaylistInfoItem> getOnPlaylistSelectedListener() {
        return onPlaylistSelectedListener;
    }

    public void setOnPlaylistSelectedListener(
            @Nullable final OnClickGesture<PlaylistInfoItem> listener) {
        this.onPlaylistSelectedListener = listener;
    }

    @Nullable
    public OnClickGesture<CommentsInfoItem> getOnCommentsSelectedListener() {
        return onCommentsSelectedListener;
    }

    public void setOnCommentsSelectedListener(
            @Nullable final OnClickGesture<CommentsInfoItem> onCommentsSelectedListener) {
        this.onCommentsSelectedListener = onCommentsSelectedListener;
    }

    @Nullable
    public OnClickGesture<LocalItem> getOnLocalItemSelectedListener() {
        return onLocalItemSelectedListener;
    }

    public void setOnLocalItemSelectedListener(
            @Nullable final OnClickGesture<LocalItem> onLocalItemSelectedListener) {
        this.onLocalItemSelectedListener = onLocalItemSelectedListener;
    }
}
