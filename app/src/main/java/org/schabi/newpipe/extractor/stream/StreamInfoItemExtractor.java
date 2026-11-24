package org.schabi.newpipe.extractor.stream;

import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import javax.annotation.Nullable;

/*
 * Created by Christian Schabesberger on 28.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemExtractor.java is part of NewPipe.
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

public interface StreamInfoItemExtractor extends InfoItemExtractor {
    default String getAlbumUrl() throws ParsingException {
        return null;
    }



    /**
     * Get the stream type
     *
     * @return the stream type
     * @throws ParsingException thrown if there is an error in the extraction
     */
    StreamType getStreamType() throws ParsingException;

    /**
     * Whether the provider identifies this item as scheduled and not yet available.
     * Return null when the source does not expose enough information to determine its state.
     */
    @Nullable
    default Boolean getUpcomingStatus() throws ParsingException {
        return null;
    }

    /**
     * Check if the stream is an ad.
     *
     * @return {@code true} if the stream is an ad.
     * @throws ParsingException thrown if there is an error in the extraction
     */
    default boolean isAd() throws ParsingException {
        return false;
    }

    /**
     * Get the stream duration in seconds
     *
     * @return the stream duration in seconds
     * @throws ParsingException thrown if there is an error in the extraction
     */
    long getDuration() throws ParsingException;

    /**
     * Parses the number of views
     *
     * @return the number of views or -1 for live streams
     * @throws ParsingException thrown if there is an error in the extraction
     */
    long getViewCount() throws ParsingException;

    /**
     * Get the uploader name
     *
     * @return the uploader name
     * @throws ParsingException if parsing fails
     */
    String getUploaderName() throws ParsingException;

    @Nullable
    default String getUploaderId() throws ParsingException {
        return null;
    }

    default String getUploaderUrl() throws ParsingException {
        return "";
    }

    /**
     * Get the uploader's avatar
     *
     * @return The uploader's avatar url or {@code null} if not provided by the service.
     * @throws ParsingException if there is an error in the extraction
     */
    @Nullable
    default String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    /**
     * Whether the uploader has been verified by the service's provider.
     * If there is no verification implemented, return <code>false</code>.
     *
     * @return whether the uploader has been verified by the service's provider
     * @throws ParsingException
     */
    default boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    /**
     * The original textual date provided by the service. Should be used as a fallback if
     * {@link #getUploadDate()} isn't provided by the service, or it fails for some reason.
     *
     * @return The original textual date provided by the service or {@code null} if not provided.
     * @throws ParsingException if there is an error in the extraction
     * @see #getUploadDate()
     */
    @Nullable
    String getTextualUploadDate() throws ParsingException;

    /**
     * Extracts the upload date and time of this item and parses it.
     * <p>
     * If the service doesn't provide an exact time, an approximation can be returned.
     * <br>
     * If the service doesn't provide any date at all, then {@code null} should be returned.
     * </p>
     *
     * @return The date and time (can be approximated) this item was uploaded or {@code null}.
     * @throws ParsingException if there is an error in the extraction
     *                          or the extracted date couldn't be parsed.
     * @see #getTextualUploadDate()
     */
    @Nullable
    DateWrapper getUploadDate() throws ParsingException;


    /**
     * Get the video's short description.
     *
     * @return The video's short description or {@code null} if not provided by the service.
     * @throws ParsingException if there is an error in the extraction
     */
    @Nullable
    default String getShortDescription() throws ParsingException {
        return null;
    }

    default boolean isRoundPlayStream() {
        return false;
    }

    /**
     * Whether the stream is a short-form content.
     *
     * <p>
     * Short-form contents are contents in the style of TikTok, YouTube Shorts, or Instagram Reels
     * videos.
     * </p>
     *
     * @return whether the stream is a short-form content
     */
    default boolean isShortFormContent() throws ParsingException {
        return false;
    }

    /**
     * Whether the stream is only available to channel members.
     *
     * @return whether the stream is only available to channel members.
     * @throws ParsingException if there is an error in the extraction
     */
    default boolean requiresMembership() throws ParsingException {
        return false;
    }

    default ContentAvailability getContentAvailability() throws ParsingException {
        return ContentAvailability.UNKNOWN;
    }
}
