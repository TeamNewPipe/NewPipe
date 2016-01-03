package org.schabi.newpipe.services;

/**
 * Created by Christian Schabesberger on 10.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoExtractor.java is part of NewPipe.
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

/**Scrapes information from a video streaming service (eg, YouTube).*/

@SuppressWarnings("ALL")
public abstract class VideoExtractor {
    protected final String pageUrl;
    protected VideoInfo videoInfo;

    @SuppressWarnings("WeakerAccess")
    public VideoExtractor(String url) {
        this.pageUrl = url;
    }

    /**Fills out the video info fields which are common to all services.
     * Probably needs to be overridden by subclasses*/
    public VideoInfo getVideoInfo()
    {
        if(videoInfo == null) {
            videoInfo = new VideoInfo();
        }

        if(videoInfo.webpage_url.isEmpty()) {
            videoInfo.webpage_url = pageUrl;
        }

        if(getErrorCode() == VideoInfo.NO_ERROR) {

            if (videoInfo.title.isEmpty()) {
                videoInfo.title = getTitle();
            }

            if (videoInfo.duration < 1) {
                videoInfo.duration = getLength();
            }


            if (videoInfo.uploader.isEmpty()) {
                videoInfo.uploader = getUploader();
            }

            if (videoInfo.description.isEmpty()) {
                videoInfo.description = getDescription();
            }

            if (videoInfo.view_count == -1) {
                videoInfo.view_count = getViews();
            }

            if (videoInfo.upload_date.isEmpty()) {
                videoInfo.upload_date = getUploadDate();
            }

            if (videoInfo.thumbnail_url.isEmpty()) {
                videoInfo.thumbnail_url = getThumbnailUrl();
            }

            if (videoInfo.id.isEmpty()) {
                videoInfo.id = getVideoId(pageUrl);
            }

            /** Load and extract audio*/
            if (videoInfo.audioStreams == null) {
                videoInfo.audioStreams = getAudioStreams();
            }
            /** Extract video stream url*/
            if (videoInfo.videoStreams == null) {
                videoInfo.videoStreams = getVideoStreams();
            }

            if (videoInfo.uploader_thumbnail_url.isEmpty()) {
                videoInfo.uploader_thumbnail_url = getUploaderThumbnailUrl();
            }

            if (videoInfo.startPosition < 0) {
                videoInfo.startPosition = getTimeStamp();
            }
        } else {
            videoInfo.errorCode = getErrorCode();
            videoInfo.errorMessage = getErrorMessage();
        }

        //Bitmap thumbnail = null;
        //Bitmap uploader_thumbnail = null;
        //int videoAvailableStatus = VIDEO_AVAILABLE;
        return videoInfo;
    }


    //todo: add licence field
    protected abstract int getErrorCode();
    protected abstract String getErrorMessage();
    protected abstract String getVideoUrl(String videoId);
    protected abstract String getVideoId(String siteUrl);
    protected abstract int getTimeStamp();
    protected abstract String getTitle();
    protected abstract String getDescription();
    protected abstract String getUploader();
    protected abstract int getLength();
    protected abstract long getViews();
    protected abstract String getUploadDate();
    protected abstract String getThumbnailUrl();
    protected abstract String getUploaderThumbnailUrl();
    protected abstract VideoInfo.AudioStream[] getAudioStreams();
    protected abstract VideoInfo.VideoStream[] getVideoStreams();
}
