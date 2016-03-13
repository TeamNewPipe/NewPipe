package org.schabi.newpipe.extractor;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 26.08.15.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfo.java is part of NewPipe.
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
public class StreamInfo extends AbstractVideoInfo {

    public static class StreamExctractException extends ExtractionException {
        StreamExctractException(String message) {
            super(message);
        }
    }

    public StreamInfo() {}

    /**Creates a new StreamInfo object from an existing AbstractVideoInfo.
     * All the shared properties are copied to the new StreamInfo.*/
    @SuppressWarnings("WeakerAccess")
    public StreamInfo(AbstractVideoInfo avi) {
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
        if(avi instanceof StreamPreviewInfo) {
            //shitty String to convert code
            /*
            String dur = ((StreamPreviewInfo)avi).duration;
            int minutes = Integer.parseInt(dur.substring(0, dur.indexOf(":")));
            int seconds = Integer.parseInt(dur.substring(dur.indexOf(":")+1, dur.length()));
            */
            this.duration = ((StreamPreviewInfo)avi).duration;
        }
    }

    public void addException(Exception e) {
        errors.add(e);
    }

    /**Fills out the video info fields which are common to all services.
     * Probably needs to be overridden by subclasses*/
    public static StreamInfo getVideoInfo(StreamExtractor extractor, Downloader downloader)
            throws ExtractionException, IOException {
        StreamInfo streamInfo = new StreamInfo();

        streamInfo = extractImportantData(streamInfo, extractor, downloader);
        streamInfo = extractStreams(streamInfo, extractor, downloader);
        streamInfo = extractOptionalData(streamInfo, extractor, downloader);

        return streamInfo;
    }

    private static StreamInfo extractImportantData(
            StreamInfo streamInfo, StreamExtractor extractor, Downloader downloader)
            throws ExtractionException, IOException {
        /* ---- importand data, withoug the video can't be displayed goes here: ---- */
        // if one of these is not available an exception is ment to be thrown directly into the frontend.

        StreamUrlIdHandler uiconv = extractor.getUrlIdConverter();

        streamInfo.service_id = extractor.getServiceId();
        streamInfo.webpage_url = extractor.getPageUrl();
        streamInfo.stream_type = extractor.getStreamType();
        streamInfo.id = uiconv.getVideoId(extractor.getPageUrl());
        streamInfo.title = extractor.getTitle();
        streamInfo.age_limit = extractor.getAgeLimit();

        if((streamInfo.stream_type == StreamType.NONE)
                || (streamInfo.webpage_url == null || streamInfo.webpage_url.isEmpty())
                || (streamInfo.id == null || streamInfo.id.isEmpty())
                || (streamInfo.title == null /* streamInfo.title can be empty of course */)
                || (streamInfo.age_limit == -1)) {
            throw new ExtractionException("Some importand stream information was not given.");
        }

        return streamInfo;
    }

    private static StreamInfo extractStreams(
            StreamInfo streamInfo, StreamExtractor extractor, Downloader downloader)
            throws ExtractionException, IOException {
        /* ---- stream extraction goes here ---- */
        // At least one type of stream has to be available,
        // otherwise an exception will be thrown directly into the frontend.

        try {
            streamInfo.dashMpdUrl = extractor.getDashMpdUrl();
        } catch(Exception e) {
            streamInfo.addException(new ExtractionException("Couldn't get Dash manifest", e));
        }

        /*  Load and extract audio */
        try {
            streamInfo.audio_streams = extractor.getAudioStreams();
        } catch(Exception e) {
            streamInfo.addException(new ExtractionException("Couldn't get audio streams", e));
        }
        // also try to get streams from the dashMpd
        if(streamInfo.dashMpdUrl != null && !streamInfo.dashMpdUrl.isEmpty()) {
            if(streamInfo.audio_streams == null) {
                streamInfo.audio_streams = new Vector<>();
            }
            //todo: make this quick and dirty solution a real fallback
            // same as the quick and dirty aboth
            try {
                streamInfo.audio_streams.addAll(
                        DashMpdParser.getAudioStreams(streamInfo.dashMpdUrl, downloader));
            } catch(Exception e) {
                streamInfo.addException(
                        new ExtractionException("Couldn't get audio streams from dash mpd", e));
            }
        }
        /* Extract video stream url*/
        try {
            streamInfo.video_streams = extractor.getVideoStreams();
        } catch (Exception e) {
            streamInfo.addException(
                    new ExtractionException("Couldn't get video streams", e));
        }
        /* Extract video only stream url*/
        try {
            streamInfo.video_only_streams = extractor.getVideoOnlyStreams();
        } catch(Exception e) {
            streamInfo.addException(
                    new ExtractionException("Couldn't get video only streams", e));
        }

        // either dash_mpd audio_only or video has to be available, otherwise we didn't get a stream,
        // and therefore failed. (Since video_only_streams are just optional they don't caunt).
        if((streamInfo.video_streams == null || streamInfo.video_streams.isEmpty())
                && (streamInfo.audio_streams == null || streamInfo.audio_streams.isEmpty())
                && (streamInfo.dashMpdUrl == null || streamInfo.dashMpdUrl.isEmpty())) {
            throw new StreamExctractException(
                    "Could not get any stream. See error variable to get further details.");
        }

        return streamInfo;
    }

    private static StreamInfo extractOptionalData(
            StreamInfo streamInfo, StreamExtractor extractor, Downloader downloader) {
        /*  ---- optional data goes here: ---- */
        // If one of these failes, the frontend neets to handle that they are not available.
        // Exceptions are therfore not thrown into the frontend, but stored into the error List,
        // so the frontend can afterwads check where errors happend.

        try {
            streamInfo.thumbnail_url = extractor.getThumbnailUrl();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.duration = extractor.getLength();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.uploader = extractor.getUploader();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.description = extractor.getDescription();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.view_count = extractor.getViewCount();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.upload_date = extractor.getUploadDate();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.uploader_thumbnail_url = extractor.getUploaderThumbnailUrl();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.start_position = extractor.getTimeStamp();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.average_rating = extractor.getAverageRating();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.like_count = extractor.getLikeCount();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.dislike_count = extractor.getDislikeCount();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.next_video = extractor.getNextVideo();
        } catch(Exception e) {
            streamInfo.addException(e);
        }
        try {
            streamInfo.related_videos = extractor.getRelatedVideos();
        } catch(Exception e) {
            streamInfo.addException(e);
        }

        return streamInfo;
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

    public int age_limit = -1;
    public int like_count = -1;
    public int dislike_count = -1;
    public String average_rating = "";
    public StreamPreviewInfo next_video = null;
    public List<StreamPreviewInfo> related_videos = null;
    //in seconds. some metadata is not passed using a StreamInfo object!
    public int start_position = 0;

    public List<Exception> errors = new Vector<>();
}