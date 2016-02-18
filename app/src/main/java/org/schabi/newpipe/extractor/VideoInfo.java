package org.schabi.newpipe.extractor;

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
    public static VideoInfo getVideoInfo(StreamExtractor extractor, Downloader downloader)
            throws ExctractionException, IOException {
        VideoInfo videoInfo = new VideoInfo();

        videoInfo = extractImportantData(videoInfo, extractor, downloader);
        videoInfo = extractStreams(videoInfo, extractor, downloader);
        videoInfo = extractOptionalData(videoInfo, extractor, downloader);

        return videoInfo;
    }

    private static VideoInfo extractImportantData(
            VideoInfo videoInfo, StreamExtractor extractor, Downloader downloader)
            throws ExctractionException, IOException {
        /* ---- importand data, withoug the video can't be displayed goes here: ---- */
        // if one of these is not available an exception is ment to be thrown directly into the frontend.

        VideoUrlIdHandler uiconv = extractor.getUrlIdConverter();

        videoInfo.webpage_url = extractor.getPageUrl();
        videoInfo.id = uiconv.getVideoId(extractor.getPageUrl());
        videoInfo.title = extractor.getTitle();

        if((videoInfo.webpage_url == null || videoInfo.webpage_url.isEmpty())
                || (videoInfo.id == null || videoInfo.id.isEmpty())
                || (videoInfo.title == null /* videoInfo.title can be empty of course */));

        return videoInfo;
    }

    private static VideoInfo extractStreams(
            VideoInfo videoInfo, StreamExtractor extractor, Downloader downloader)
            throws ExctractionException, IOException {
        /* ---- stream extraction goes here ---- */
        // At least one type of stream has to be available,
        // otherwise an exception will be thrown directly into the frontend.

        try {
            videoInfo.dashMpdUrl = extractor.getDashMpdUrl();
        } catch(Exception e) {
            videoInfo.addException(new ExctractionException("Couldn't get Dash manifest", e));
        }

        /*  Load and extract audio */
        try {
            videoInfo.audio_streams = extractor.getAudioStreams();
        } catch(Exception e) {
            videoInfo.addException(new ExctractionException("Couldn't get audio streams", e));
        }
        // also try to get streams from the dashMpd
        if(videoInfo.dashMpdUrl != null && !videoInfo.dashMpdUrl.isEmpty()) {
            if(videoInfo.audio_streams == null) {
                videoInfo.audio_streams = new Vector<AudioStream>();
            }
            //todo: make this quick and dirty solution a real fallback
            // same as the quick and dirty aboth
            try {
                videoInfo.audio_streams.addAll(
                        DashMpdParser.getAudioStreams(videoInfo.dashMpdUrl, downloader));
            } catch(Exception e) {
                videoInfo.addException(
                        new ExctractionException("Couldn't get audio streams from dash mpd", e));
            }
        }
        /* Extract video stream url*/
        try {
            videoInfo.video_streams = extractor.getVideoStreams();
        } catch (Exception e) {
            videoInfo.addException(
                    new ExctractionException("Couldn't get video streams", e));
        }
        /* Extract video only stream url*/
        try {
            videoInfo.video_only_streams = extractor.getVideoOnlyStreams();
        } catch(Exception e) {
            videoInfo.addException(
                    new ExctractionException("Couldn't get video only streams", e));
        }

        // either dash_mpd audio_only or video has to be available, otherwise we didn't get a stream,
        // and therefore failed. (Since video_only_streams are just optional they don't caunt).
        if((videoInfo.video_streams == null || videoInfo.video_streams.isEmpty())
                && (videoInfo.audio_streams == null || videoInfo.audio_streams.isEmpty())
                && (videoInfo.dashMpdUrl == null || videoInfo.dashMpdUrl.isEmpty())) {
            throw new ExctractionException("Could not get any stream. See error variable to get further details.");
        }

        return videoInfo;
    }

    private static VideoInfo extractOptionalData(
            VideoInfo videoInfo, StreamExtractor extractor, Downloader downloader) {
        /*  ---- optional data goes here: ---- */
        // If one of these failes, the frontend neets to handle that they are not available.
        // Exceptions are therfore not thrown into the frontend, but stored into the error List,
        // so the frontend can afterwads check where errors happend.

        try {
            videoInfo.thumbnail_url = extractor.getThumbnailUrl();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.duration = extractor.getLength();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.uploader = extractor.getUploader();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.description = extractor.getDescription();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.view_count = extractor.getViews();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.upload_date = extractor.getUploadDate();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.uploader_thumbnail_url = extractor.getUploaderThumbnailUrl();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.start_position = extractor.getTimeStamp();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.average_rating = extractor.getAverageRating();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.like_count = extractor.getLikeCount();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.dislike_count = extractor.getDislikeCount();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.next_video = extractor.getNextVideo();
        } catch(Exception e) {
            videoInfo.addException(e);
        }
        try {
            videoInfo.related_videos = extractor.getRelatedVideos();
        } catch(Exception e) {
            videoInfo.addException(e);
        }

        return videoInfo;
    }

    public String uploader_thumbnail_url = "";
    public String description = "";

    public List<VideoStream> video_streams = null;
    public List<AudioStream> audio_streams = null;
    public List<VideoStream> video_only_streams = null;
    // video streams provided by the dash mpd do not need to be provided as VideoStream.
    // Later on this will also aplly to audio streams. Since dash mpd is standarized,
    // crawling such a file is not service dependent. Therefore getting audio only streams by yust
    // providing the dash mpd fille will be possible in the future.
    public String dashMpdUrl = "";
    public int duration = -1;

    public int age_limit = 0;
    public int like_count = -1;
    public int dislike_count = -1;
    public String average_rating = "";
    public VideoPreviewInfo next_video = null;
    public List<VideoPreviewInfo> related_videos = null;
    //in seconds. some metadata is not passed using a VideoInfo object!
    public int start_position = 0;
    //todo: public int service_id = -1;

    public List<Exception> errors = new Vector<>();

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

        // reveals wether two streams are the same, but have diferent urls
        public boolean equalStats(VideoStream cmp) {
            return format == cmp.format
                    && resolution == cmp.resolution;
        }

        // revelas wether two streams are equal
        public boolean equals(VideoStream cmp) {
            return equalStats(cmp)
                    && url == cmp.url;
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

        // reveals wether two streams are the same, but have diferent urls
        public boolean equalStats(AudioStream cmp) {
            return format == cmp.format
                    && bandwidth == cmp.bandwidth
                    && sampling_rate == cmp.sampling_rate;
        }

        // revelas wether two streams are equal
        public boolean equals(AudioStream cmp) {
            return equalStats(cmp)
                    && url == cmp.url;
        }
    }

    public void addException(Exception e) {
        errors.add(e);
    }
}