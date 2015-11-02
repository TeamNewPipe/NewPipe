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
import android.util.Log;

import java.util.Vector;

public class VideoInfo {

    private static final String TAG = VideoInfo.class.toString();

    // format identifier
    public static final int I_MPEG_4 = 0x0;
    public static final int I_3GPP = 0x1;
    public static final int I_WEBM = 0x2;
    public static final int I_M4A = 0x3;
    public static final int I_WEBMA = 0x4;

    // format name
    public static final String F_MPEG_4 = "MPEG-4";
    public static final String F_3GPP = "3GPP";
    public static final String F_WEBM = "WebM";
    public static final String F_M4A = "m4a";
    public static final String F_WEBMA = "WebM";

    // file suffix
    public static final String C_MPEG_4 = "mp4";
    public static final String C_3GPP = "3gp";
    public static final String C_WEBM = "webm";
    public static final String C_M4A = "m4a";
    public static final String C_WEBMA = "webm";

    // mimeType
    public static final String M_MPEG_4 = "video/mp4";
    public static final String M_3GPP = "video/3gpp";
    public static final String M_WEBM = "video/webm";
    public static final String M_M4A = "audio/mp4";
    public static final String M_WEBMA = "audio/webm";

    public static final int VIDEO_AVAILABLE = 0x00;
    public static final int VIDEO_UNAVAILABLE = 0x01;
    public static final int VIDEO_UNAVAILABLE_GEMA = 0x02;

    public static String getNameById(int id) {
        switch(id) {
            case I_MPEG_4: return F_MPEG_4;
            case I_3GPP: return F_3GPP;
            case I_WEBM: return F_WEBM;
            case I_M4A: return F_M4A;
            case I_WEBMA: return F_WEBMA;
            default: Log.e(TAG, "format not known: " +
                    Integer.toString(id) + "call the programmer he messed it up.");
        }
        return "";
    }

    public static String getSuffixById(int id) {
        switch(id) {
            case I_MPEG_4: return C_MPEG_4;
            case I_3GPP: return C_3GPP;
            case I_WEBM: return C_WEBM;
            case I_M4A: return C_M4A;
            case I_WEBMA: return C_WEBMA;
            default: Log.e(TAG, "format not known: " +
                    Integer.toString(id) + "call the programmer he messed it up.");
        }
        return "";
    }

    public static String getMimeById(int id) {
        switch(id) {
            case I_MPEG_4: return M_MPEG_4;
            case I_3GPP: return M_3GPP;
            case I_WEBM: return M_WEBM;
            case I_M4A: return M_M4A;
            case I_WEBMA: return M_WEBMA;
            default: Log.e(TAG, "format not known: " +
                    Integer.toString(id) + "call the programmer he messed it up.");
        }
        return "";
    }

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
    public VideoInfoItem[] relatedVideos = null;
    public int videoAvailableStatus = VIDEO_AVAILABLE;
}