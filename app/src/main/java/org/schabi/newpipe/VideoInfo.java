package org.schabi.newpipe;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.Date;
import java.util.Vector;

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


public class VideoInfo {
    public String id = "";
    public String title = "";
    public String uploader = "";
    public String thumbnail_url = "";
    public Bitmap thumbnail = null;
    public String webpage_url = "";
    public String upload_date = "";
    public long view_count = 0;

    public String uploader_thumbnail_url = "";
    public Bitmap uploader_thumbnail = null;
    public String description = "";
    public int duration = -1;
    public int age_limit = 0;
    public int like_count = 0;
    public int dislike_count = 0;
    public String average_rating = "";
    public VideoStream[] videoStreams = null;
    public AudioStream[] audioStreams = null;
    public VideoInfoItem nextVideo = null;
    public VideoInfoItem[] relatedVideos = null;
    public int videoAvailableStatus = VIDEO_AVAILABLE;

    private static final String TAG = VideoInfo.class.toString();

    public static final int VIDEO_AVAILABLE = 0x00;
    public static final int VIDEO_UNAVAILABLE = 0x01;
    public static final int VIDEO_UNAVAILABLE_GEMA = 0x02;//German DRM organisation

    public static class VideoStream {
        public VideoStream(String url, int format, String res) {
            this.url = url; this.format = format; resolution = res;
        }
        public String url = "";     //url of the stream
        public int format = -1;
        public String resolution = "";
    }

    public static class AudioStream {
        public AudioStream(String url, int format, int bandwidth, int samplingRate) {
            this.url = url; this.format = format;
            this.bandwidth = bandwidth; this.samplingRate = samplingRate;
        }
        public String url = "";
        public int format = -1;
        public int bandwidth = -1;
        public int samplingRate = -1;
    }
}