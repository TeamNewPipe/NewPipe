package org.schabi.newpipe.crawler;

/**
 * Created by Christian Schabesberger on 10.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * StreamExtractor.java is part of NewPipe.
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

import java.util.List;

/**Scrapes information from a video streaming service (eg, YouTube).*/


@SuppressWarnings("ALL")
public interface StreamExtractor {

    public class ExctractorInitException extends CrawlingException {
        public ExctractorInitException() {}
        public ExctractorInitException(String message) {
            super(message);
        }
        public ExctractorInitException(Throwable cause) {
            super(cause);
        }
        public ExctractorInitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public class ContentNotAvailableException extends ParsingException {
        public ContentNotAvailableException() {}
        public ContentNotAvailableException(String message) {
            super(message);
        }
        public ContentNotAvailableException(Throwable cause) {
            super(cause);
        }
        public ContentNotAvailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public abstract int getTimeStamp() throws ParsingException;
    public abstract String getTitle() throws ParsingException;
    public abstract String getDescription() throws ParsingException;
    public abstract String getUploader() throws ParsingException;
    public abstract int getLength() throws ParsingException;
    public abstract long getViews() throws ParsingException;
    public abstract String getUploadDate() throws ParsingException;
    public abstract String getThumbnailUrl() throws ParsingException;
    public abstract String getUploaderThumbnailUrl() throws ParsingException;
    public abstract List<VideoInfo.AudioStream> getAudioStreams() throws ParsingException;
    public abstract List<VideoInfo.VideoStream> getVideoStreams() throws ParsingException;
    public abstract List<VideoInfo.VideoStream> getVideoOnlyStreams() throws ParsingException;
    public abstract String getDashMpdUrl() throws ParsingException;
    public abstract int getAgeLimit() throws ParsingException;
    public abstract String getAverageRating() throws ParsingException;
    public abstract int getLikeCount() throws ParsingException;
    public abstract int getDislikeCount() throws ParsingException;
    public abstract VideoPreviewInfo getNextVideo() throws ParsingException;
    public abstract List<VideoPreviewInfo> getRelatedVideos() throws ParsingException;
    public abstract VideoUrlIdHandler getUrlIdConverter();
    public abstract String getPageUrl();
}
