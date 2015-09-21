package org.schabi.newpipe;

/**
 * Created by Christian Schabesberger on 26.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoInfo.java is part of NewPipe.
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

import android.graphics.Bitmap;

import java.util.Vector;

public class VideoInfo {

    public static final String F_MPEG_4 = "MPEG-4";
    public static final String F_3GPP = "3GPP";
    public static final String F_WEBM = "WebM";
    public static final String F_M4A = "m4a";

    public static final int VIDEO_AVAILABLE = 0x00;
    public static final int VIDEO_UNAVAILABLE = 0x01;
    public static final int VIDEO_UNAVAILABLE_GEMA = 0x02;

    public static class VideoStream {
        public VideoStream(String url, String format, String res) {
            this.url = url; this.format = format; resolution = res;
        }
        public String url = "";     //url of the stream
        public String format = "";
        public String resolution = "";
    }

    public static class AudioStream {
        public AudioStream(String url, String format) {
            this.url = url; this.format = format;
        }
        public String url = "";
        public String format = "";
    }

    public String id = "";
    public String uploader = "";
    public String upload_date = "";
    public String uploader_thumbnail_url = "";
    public Bitmap uploader_thumbnail = null;
    public String title = "";
    public String thumbnail_url = "";
    public Bitmap thumbnail = null;
    public String description = "";
    public int duration = -1;
    public int age_limit = 0;
    public String webpage_url = "";
    public String view_count = "";
    public String like_count = "";
    public String dislike_count = "";
    public String average_rating = "";
    public VideoStream[] videoStreams = null;
    public AudioStream[] audioStreams = null;
    public VideoInfoItem nextVideo = null;
    public Vector<VideoInfoItem> relatedVideos = null;
    public int videoAvailableStatus = VIDEO_AVAILABLE;
}