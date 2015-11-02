package org.schabi.newpipe;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Christian Schabesberger on 26.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoInfoItem.java is part of NewPipe.
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

public class VideoInfoItem implements Parcelable {
    public String id = "";
    public String title = "";
    public String uploader = "";
    public String duration = "";
    public String thumbnail_url = "";
    public Bitmap thumbnail = null;
    public String webpage_url = "";
    public String upload_date = "";
    public String view_count = "";

    protected VideoInfoItem(Parcel in) {
        id = in.readString();
        title = in.readString();
        uploader = in.readString();
        duration = in.readString();
        thumbnail_url = in.readString();
        thumbnail = (Bitmap) in.readValue(Bitmap.class.getClassLoader());
        webpage_url = in.readString();
        upload_date = in.readString();
        view_count = in.readString();
    }

    public VideoInfoItem() {

    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(uploader);
        dest.writeString(duration);
        dest.writeString(thumbnail_url);
        dest.writeValue(thumbnail);
        dest.writeString(webpage_url);
        dest.writeString(upload_date);
        dest.writeString(view_count);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<VideoInfoItem> CREATOR = new Parcelable.Creator<VideoInfoItem>() {
        @Override
        public VideoInfoItem createFromParcel(Parcel in) {
            return new VideoInfoItem(in);
        }

        @Override
        public VideoInfoItem[] newArray(int size) {
            return new VideoInfoItem[size];
        }
    };
}