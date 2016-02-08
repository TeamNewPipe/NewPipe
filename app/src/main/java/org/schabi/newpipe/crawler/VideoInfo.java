package org.schabi.newpipe.crawler;

import java.io.IOException;
import java.util.List;
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

/**Info object for opened videos, ie the video ready to play.*/
@SuppressWarnings("ALL")
public class VideoInfo extends AbstractVideoInfo {

    /**Fills out the video info fields which are common to all services.
     * Probably needs to be overridden by subclasses*/
    public static VideoInfo getVideoInfo(VideoExtractor extractor, Downloader downloader)
            throws CrawlingException, IOException {
        VideoInfo videoInfo = new VideoInfo();

        VideoUrlIdHandler uiconv = extractor.getUrlIdConverter();


        videoInfo.webpage_url = extractor.getPageUrl();
        videoInfo.title = extractor.getTitle();
        videoInfo.duration = extractor.getLength();
        videoInfo.uploader = extractor.getUploader();
        videoInfo.description = extractor.getDescription();
        videoInfo.view_count = extractor.getViews();
        videoInfo.upload_date = extractor.getUploadDate();
        videoInfo.thumbnail_url = extractor.getThumbnailUrl();
        videoInfo.id = uiconv.getVideoId(extractor.getPageUrl());
        videoInfo.dashMpdUrl = extractor.getDashMpdUrl();
        /** Load and extract audio*/
        videoInfo.audio_streams = extractor.getAudioStreams();
        if(videoInfo.dashMpdUrl != null && !videoInfo.dashMpdUrl.isEmpty()) {
            if(videoInfo.audio_streams == null) {
                videoInfo.audio_streams = new Vector<AudioStream>();
            }
            videoInfo.audio_streams.addAll(
                    DashMpdParser.getAudioStreams(videoInfo.dashMpdUrl, downloader));
        }
        /** Extract video stream url*/
        videoInfo.video_streams = extractor.getVideoStreams();
        videoInfo.uploader_thumbnail_url = extractor.getUploaderThumbnailUrl();
        videoInfo.start_position = extractor.getTimeStamp();
        videoInfo.average_rating = extractor.getAverageRating();
        videoInfo.like_count = extractor.getLikeCount();
        videoInfo.dislike_count = extractor.getDislikeCount();
        videoInfo.next_video = extractor.getNextVideo();
        videoInfo.related_videos = extractor.getRelatedVideos();

        //Bitmap thumbnail = null;
        //Bitmap uploader_thumbnail = null;
        //int videoAvailableStatus = VIDEO_AVAILABLE;
        return videoInfo;
    }


    public String uploader_thumbnail_url = "";
    public String description = "";
    /*todo: make this lists over vectors*/
    public List<VideoStream> video_streams = null;
    public List<AudioStream> audio_streams = null;
    // video streams provided by the dash mpd do not need to be provided as VideoStream.
    // Later on this will also aplly to audio streams. Since dash mpd is standarized,
    // crawling such a file is not service dependent. Therefore getting audio only streams by yust
    // providing the dash mpd fille will be possible in the future.
    public String dashMpdUrl = "";
    public int duration = -1;

    /*YouTube-specific fields
    todo: move these to a subclass*/
    public int age_limit = 0;
    public int like_count = -1;
    public int dislike_count = -1;
    public String average_rating = "";
    public VideoPreviewInfo next_video = null;
    public List<VideoPreviewInfo> related_videos = null;
    //in seconds. some metadata is not passed using a VideoInfo object!
    public int start_position = -1;
    //todo: public int service_id = -1;

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
        if(avi instanceof VideoPreviewInfo) {
            //shitty String to convert code
            String dur = ((VideoPreviewInfo)avi).duration;
            int minutes = Integer.parseInt(dur.substring(0, dur.indexOf(":")));
            int seconds = Integer.parseInt(dur.substring(dur.indexOf(":")+1, dur.length()));
            this.duration = (minutes*60)+seconds;
        }
    }

    public static class VideoStream {
        //url of the stream
        public String url = "";
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
        public int sampling_rate = -1;

        public AudioStream(String url, int format, int bandwidth, int samplingRate) {
            this.url = url; this.format = format;
            this.bandwidth = bandwidth; this.sampling_rate = samplingRate;
        }
    }
}