package org.schabi.newpipe;

import org.schabi.newpipe.services.AbstractVideoInfo;

import java.util.List;

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

/**Info object for opened videos, ie the video ready to play.*/
@SuppressWarnings("ALL")
public class VideoInfo extends AbstractVideoInfo {

    public String uploader_thumbnail_url = "";
    public String description = "";
    public VideoStream[] videoStreams = null;
    public AudioStream[] audioStreams = null;
    public int videoAvailableStatus = VIDEO_AVAILABLE;
    public int duration = -1;

    /*YouTube-specific fields
    todo: move these to a subclass*/
    public int age_limit = 0;
    public int like_count = -1;
    public int dislike_count = -1;
    public String average_rating = "";
    public VideoPreviewInfo nextVideo = null;
    public List<VideoPreviewInfo> relatedVideos = null;
    public int startPosition = -1;//in seconds. some metadata is not passed using a VideoInfo object!

    public static final int VIDEO_AVAILABLE = 0x00;
    public static final int VIDEO_UNAVAILABLE = 0x01;
    public static final int VIDEO_UNAVAILABLE_GEMA = 0x02;//German DRM organisation


    public VideoInfo() {}


    /**Creates a new VideoInfo object from an existing AbstractVideoInfo.
     * All the shared properties are copied to the new VideoInfo.*/
    @SuppressWarnings("WeakerAccess")
    public VideoInfo(AbstractVideoInfo avi) {
        this.id = avi.id;
        this.title = avi.title;
        this.uploader = avi.uploader;
        this.thumbnail_url = avi.thumbnail_url;
        this.thumbnail = avi.thumbnail;
        this.webpage_url = avi.webpage_url;
        this.upload_date = avi.upload_date;
        this.upload_date = avi.upload_date;
        this.view_count = avi.view_count;

        //todo: better than this
        if(avi instanceof VideoPreviewInfo) {//shitty String to convert code
            String dur = ((VideoPreviewInfo)avi).duration;
            int minutes = Integer.parseInt(dur.substring(0, dur.indexOf(":")));
            int seconds = Integer.parseInt(dur.substring(dur.indexOf(":")+1, dur.length()));
            this.duration = (minutes*60)+seconds;
        }
    }

    public static class VideoStream {
        public String url = "";     //url of the stream
        public int format = -1;
        public String resolution = "";

        public VideoStream(String url, int format, String res) {
            this.url = url; this.format = format; resolution = res;
        }
    }

    @SuppressWarnings("unused")
    public static class AudioStream {
        public String url = "";
        public int format = -1;
        public int bandwidth = -1;
        public int samplingRate = -1;

        public AudioStream(String url, int format, int bandwidth, int samplingRate) {
            this.url = url; this.format = format;
            this.bandwidth = bandwidth; this.samplingRate = samplingRate;
        }
    }
}