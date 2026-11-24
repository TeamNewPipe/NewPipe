package org.schabi.newpipe.extractor.stream;

/*
 * Created by Christian Schabesberger on 10.08.18.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
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

import org.schabi.newpipe.extractor.Extractor;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemExtractor;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.StaffInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.utils.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Scrapes information from a video/audio streaming service (eg, YouTube).
 */
public abstract class StreamExtractor extends Extractor {

    public static final int NO_AGE_LIMIT = 0;
    public static final long UNKNOWN_SUBSCRIBER_COUNT = -1;

    public StreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    /**
     * The original textual date provided by the service. Should be used as a fallback if
     * {@link #getUploadDate()} isn't provided by the service, or it fails for some reason.
     *
     * <p>If the stream is a live stream, {@code null} should be returned.</p>
     *
     * @return The original textual date provided by the service, or {@code null}.
     * @throws ParsingException if there is an error in the extraction
     * @see #getUploadDate()
     */
    @Nullable
    public String getTextualUploadDate() throws ParsingException {
        return null;
    }

    /**
     * A more general {@code Calendar} instance set to the date provided by the service.<br>
     * Implementations usually will just parse the date returned from the {@link
     * #getTextualUploadDate()}.
     *
     * <p>If the stream is a live stream, {@code null} should be returned.</p>
     *
     * @return The date this item was uploaded, or {@code null}.
     * @throws ParsingException if there is an error in the extraction
     *                          or the extracted date couldn't be parsed.
     * @see #getTextualUploadDate()
     */
    @Nullable
    public DateWrapper getUploadDate() throws ParsingException {
        return null;
    }

    /**
     * This will return the url to the thumbnail of the stream. Try to return the medium resolution
     * here.
     *
     * @return The url of the thumbnail.
     */
    @Nonnull
    public String getThumbnailUrl() throws ParsingException {
        final List<Image> thumbnails = getThumbnails();
        return thumbnails.isEmpty() ? null : thumbnails.get(0).getUrl();
    }

    /**
     * This will return the thumbnails of the stream.
     *
     * @return the thumbnails of the stream
     */
    @Nonnull
    public List<Image> getThumbnails() throws ParsingException {
        String thumbnailUrl = getThumbnailUrl();
        if (!thumbnailUrl.isEmpty()) {
            Image image = new Image(thumbnailUrl, -1, -1, Image.ResolutionLevel.MEDIUM);
            List<Image> list = new java.util.ArrayList<>();
            list.add(image);
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * This is the stream description.
     *
     * @return The description of the stream/video or {@link Description#EMPTY_DESCRIPTION} if the
     * description is empty.
     */
    @Nonnull
    public Description getDescription() throws ParsingException {
        return Description.EMPTY_DESCRIPTION;
    }

    /**
     * Get the age limit.
     *
     * @return The age which limits the content or {@value NO_AGE_LIMIT} if there is no limit
     * @throws ParsingException if an error occurs while parsing
     */
    public int getAgeLimit() throws ParsingException {
        return NO_AGE_LIMIT;
    }

    /**
     * This should return the length of a video in seconds.
     *
     * @return The length of the stream in seconds or 0 when it has no length (e.g. a livestream).
     */
    public long getLength() throws ParsingException {
        return 0;
    }

    /**
     * If the url you are currently handling contains a time stamp/seek, you can return the
     * position it represents here.
     * If the url has no time stamp simply return zero.
     *
     * @return the timestamp in seconds or 0 when there is no timestamp
     */
    public long getTimeStamp() throws ParsingException {
        return 0;
    }

    /**
     * The count of how many people have watched the video/listened to the audio stream.
     * If the current stream has no view count or its not available simply return -1
     *
     * @return amount of views or -1 if not available.
     */
    public long getViewCount() throws ParsingException {
        return -1;
    }

    /**
     * The amount of likes a video/audio stream got.
     * If the current stream has no likes or its not available simply return -1
     *
     * @return the amount of likes the stream got or -1 if not available.
     */
    public long getLikeCount() throws ParsingException {
        return -1;
    }

    /**
     * The amount of dislikes a video/audio stream got.
     * If the current stream has no dislikes or its not available simply return -1
     *
     * @return the amount of likes the stream got or -1 if not available.
     */
    public long getDislikeCount() throws ParsingException {
        return -1;
    }

    /**
     * The Url to the page of the creator/uploader of the stream. This must not be a homepage,
     * but the page offered by the service the extractor handles. This url will be handled by the
     * {@link ChannelExtractor}, so be sure to implement that one before you return a value here,
     * otherwise NewPipe will crash if one selects this url.
     *
     * @return the url to the page of the creator/uploader of the stream or an empty string
     */
    @Nonnull
    public abstract String getUploaderUrl() throws ParsingException;

    /**
     * The name of the creator/uploader of the stream.
     * If the name is not available you can simply return an empty string.
     *
     * @return the name of the creator/uploader of the stream or an empty tring
     */
    @Nonnull
    public abstract String getUploaderName() throws ParsingException;

    /**
     * Whether the uploader has been verified by the service's provider.
     * If there is no verification implemented, return <code>false</code>.
     *
     * @return whether the uploader has been verified by the service's provider
     */
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    /**
     * The subscriber count of the uploader.
     * If the subscriber count is not implemented, or is unavailable, return <code>-1</code>.
     *
     * @return the subscriber count of the uploader or {@value UNKNOWN_SUBSCRIBER_COUNT} if not
     * available
     */
    public long getUploaderSubscriberCount() throws ParsingException {
        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    /**
     * The url to the image file/profile picture/avatar of the creator/uploader of the stream.
     * If the url is not available you can return an empty String.
     *
     * @return The url of the image file of the uploader or an empty String
     */
    @Nonnull
    public String getUploaderAvatarUrl() throws ParsingException {
        return "";
    }

    /**
     * The image files/profile pictures/avatars of the creator/uploader of the stream.
     *
     * <p>
     * If they are not available in the stream on specific cases, you must return an empty list for
     * these ones, like it is made by default.
     * </p>
     *
     * @return the avatars of the sub-channel of the stream or an empty list (default)
     */
    @Nonnull
    public List<Image> getUploaderAvatars() throws ParsingException {
        String avatarUrl = getUploaderAvatarUrl();
        if (!avatarUrl.isEmpty()) {
            Image image = new Image(avatarUrl, -1, -1, Image.ResolutionLevel.MEDIUM);
            List<Image> list = new java.util.ArrayList<>();
            list.add(image);
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * The Url to the page of the sub-channel of the stream. This must not be a homepage,
     * but the page offered by the service the extractor handles. This url will be handled by the
     * {@link ChannelExtractor}, so be sure to implement that one before you return a value here,
     * otherwise NewPipe will crash if one selects this url.
     *
     * @return the url to the page of the sub-channel of the stream or an empty String
     */
    @Nonnull
    public String getSubChannelUrl() throws ParsingException {
        return "";
    }

    /**
     * The name of the sub-channel of the stream.
     * If the name is not available you can simply return an empty string.
     *
     * @return the name of the sub-channel of the stream or an empty String
     */
    @Nonnull
    public String getSubChannelName() throws ParsingException {
        return "";
    }

    /**
     * The avatars of the sub-channel of the stream.
     *
     * <p>
     * If they are not available in the stream on specific cases, you must return an empty list for
     * these ones, like it is made by default.
     * </p>
     *
     * <p>
     * If the concept of sub-channels doesn't apply to the stream's service, keep the default
     * implementation.
     * </p>
     *
     * @return the avatars of the sub-channel of the stream or an empty list (default)
     */
    @Nonnull
    public List<Image> getSubChannelAvatars() throws ParsingException {
        String subChannelUrl = getSubChannelUrl();
        if (!subChannelUrl.isEmpty()) {
            Image image = new Image(subChannelUrl, -1, -1, Image.ResolutionLevel.MEDIUM);
            List<Image> list = new ArrayList<>();
            list.add(image);
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * The url to the image file/profile picture/avatar of the sub-channel of the stream.
     * If the url is not available you can return an empty String.
     *
     * @return The url of the image file of the sub-channel or an empty String
     */
    @Nonnull
    public String getSubChannelAvatarUrl() throws ParsingException {
        return "";
    }

    /**
     * Get the dash mpd url. If you don't know what a dash MPD is you can read about it
     * <a href="https://www.brendanlong.com/the-structure-of-an-mpeg-dash-mpd.html">here</a>.
     *
     * @return the url as a string or an empty string or an empty string if not available
     * @throws ParsingException if an error occurs while reading
     */
    @Nonnull
    public String getDashMpdUrl() throws ParsingException {
        return "";
    }

    /**
     * I am not sure if this is in use, and how this is used. However the frontend is missing
     * support for HLS streams. Prove me if I am wrong. Please open an
     * <a href="https://github.com/teamnewpipe/newpipe/issues">issue</a>,
     * or fix this description if you know whats up with this.
     *
     * @return The Url to the hls stream or an empty string if not available.
     */
    @Nonnull
    public String getHlsUrl() throws ParsingException {
        return "";
    }

    /** Headers required for HLS/DASH manifests and their segments. */
    @Nullable
    public Map<String, String> getManifestRequestHeaders() throws ParsingException {
        return null;
    }

    /**
     * This should return a list of available {@link AudioStream}s.
     * You can also return null or an empty list, however be aware that if you don't return anything
     * in getVideoStreams(), getVideoOnlyStreams() and getDashMpdUrl() either the Collector will
     * handle this as a failed extraction procedure.
     *
     * @return a list of audio only streams in the format of AudioStream
     */
    public abstract List<AudioStream> getAudioStreams() throws IOException, ExtractionException;

    /**
     * This should return a list of available {@link VideoStream}s.
     * Be aware this is the list of video streams which do contain an audio stream.
     * You can also return null or an empty list, however be aware that if you don't return anything
     * in getAudioStreams(), getVideoOnlyStreams() and getDashMpdUrl() either the Collector will
     * handle this as a failed extraction procedure.
     *
     * @return a list of combined video and streams in the format of AudioStream
     */
    public abstract List<VideoStream> getVideoStreams() throws IOException, ExtractionException;

    /**
     * This should return a list of available {@link VideoStream}s.
     * Be aware this is the list of video streams which do NOT contain an audio stream.
     * You can also return null or an empty list, however be aware that if you don't return anything
     * in getAudioStreams(), getVideoStreams() and getDashMpdUrl() either the Collector will handle
     * this as a failed extraction procedure.
     *
     * @return a list of video and streams in the format of AudioStream
     */
    public abstract List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException;

    /**
     * This will return a list of available {@link SubtitlesStream}s.
     * If no subtitles are available an empty list can be returned.
     *
     * @return a list of available subtitles or an empty list
     */
    @Nonnull
    public List<SubtitlesStream> getSubtitlesDefault() throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    /**
     * This will return a list of available {@link SubtitlesStream}s given by a specific type.
     * If no subtitles in that specific format are available an empty list can be returned.
     *
     * @param format the media format by which the subtitles should be filtered
     * @return a list of available subtitles or an empty list
     */
    @Nonnull
    public List<SubtitlesStream> getSubtitles(final MediaFormat format)
            throws IOException, ExtractionException {
        return Collections.emptyList();
    }

    /** Whether the source explicitly describes spherical rather than flat video. */
    public boolean isSpherical() throws ParsingException {
        return false;
    }

    /**
     * Get the {@link StreamType}.
     *
     * @return the type of the stream
     */
    public abstract StreamType getStreamType() throws ParsingException;

    /**
     * Should return a list of streams related to the current handled. Many services show suggested
     * streams. If you don't like suggested streams you should implement them anyway since they can
     * be disabled by the user later in the frontend. The first related stream might be what was
     * previously known as a next stream.
     * If related streams aren't available simply return {@code null}.
     *
     * @return a list of InfoItems showing the related videos/streams
     */
    @Nullable
    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor>
    getRelatedItems() throws IOException, ExtractionException {
        return null;
    }

    /**
     * @return The result of {@link #getRelatedItems()} if it is a
     * {@link StreamInfoItemsCollector}, <code>null</code> otherwise
     * @deprecated Use {@link #getRelatedItems()}. May be removed in a future version.
     */
    @Deprecated
    @Nullable
    public StreamInfoItemsCollector getRelatedStreams() throws IOException, ExtractionException {
        final InfoItemsCollector<?, ?> collector = getRelatedItems();
        if (collector instanceof StreamInfoItemsCollector) {
            return (StreamInfoItemsCollector) collector;
        } else {
            return null;
        }
    }

    /**
     * Should return a list of Frameset object that contains preview of stream frames
     *
     * @return list of preview frames or empty list if frames preview is not supported or not found
     * for specified stream
     */
    @Nonnull
    public List<Frameset> getFrames() throws ExtractionException {
        return Collections.emptyList();
    }

    /**
     * Should analyse the webpage's document and extracts any error message there might be.
     *
     * @return Error message; <code>null</code> if there is no error message.
     */
    public String getErrorMessage() {
        return null;
    }

    //////////////////////////////////////////////////////////////////
    ///  Helper
    //////////////////////////////////////////////////////////////////

    /**
     * Override this function if the format of timestamp in the url is not the same format as that
     * from youtube.
     *
     * @return the time stamp/seek for the video in seconds
     */
    protected long getTimestampSeconds(final String regexPattern) throws ParsingException {
        final String timestamp;
        try {
            timestamp = Parser.matchGroup1(regexPattern, getOriginalUrl());
        } catch (final Parser.RegexException e) {
            // catch this instantly since a url does not necessarily have a timestamp

            // -2 because the testing system will consequently know that the regex failed
            // not good, I know
            return -2;
        }

        if (!timestamp.isEmpty()) {
            try {
                String secondsString = "";
                String minutesString = "";
                String hoursString = "";
                try {
                    secondsString = Parser.matchGroup1("(\\d+)s", timestamp);
                    minutesString = Parser.matchGroup1("(\\d+)m", timestamp);
                    hoursString = Parser.matchGroup1("(\\d+)h", timestamp);
                } catch (final Exception e) {
                    // it could be that time is given in another method
                    if (secondsString.isEmpty() && minutesString.isEmpty()) {
                        // if nothing was obtained, treat as unlabelled seconds
                        secondsString = Parser.matchGroup1("t=(\\d+)", timestamp);
                    }
                }

                final int seconds = secondsString.isEmpty() ? 0 : Integer.parseInt(secondsString);
                final int minutes = minutesString.isEmpty() ? 0 : Integer.parseInt(minutesString);
                final int hours = hoursString.isEmpty() ? 0 : Integer.parseInt(hoursString);

                return seconds + (60L * minutes) + (3600L * hours);
            } catch (final ParsingException e) {
                throw new ParsingException("Could not get timestamp.", e);
            }
        } else {
            return 0;
        }
    }

    /**
     * The host of the stream (Eg. peertube.cpy.re).
     * If the host is not available, or if the service doesn't use
     * a federated system, but a centralised system,
     * you can simply return an empty string.
     *
     * @return the host of the stream or an empty string.
     */
    @Nonnull
    public String getHost() throws ParsingException {
        return "";
    }

    /**
     * The privacy of the stream (Eg. Public, Private, Unlisted…).
     *
     * @return the privacy of the stream.
     */
    public Privacy getPrivacy() throws ParsingException {
        return Privacy.PUBLIC;
    }

    /**
     * The name of the category of the stream.
     * If the category is not available you can simply return an empty string.
     *
     * @return the category of the stream or an empty string.
     */
    @Nonnull
    public String getCategory() throws ParsingException {
        return "";
    }

    /**
     * The name of the licence of the stream.
     * If the licence is not available you can simply return an empty string.
     *
     * @return the licence of the stream or an empty String.
     */
    @Nonnull
    public String getLicence() throws ParsingException {
        return "";
    }

    /**
     * The locale language of the stream.
     * If the language is not available you can simply return null.
     * If the language is provided by a language code, you can return
     * new Locale(language_code);
     *
     * @return the locale language of the stream or <code>null</code>.
     */
    @Nullable
    public Locale getLanguageInfo() throws ParsingException {
        return null;
    }

    /**
     * The list of tags of the stream.
     * If the tag list is not available you can simply return an empty list.
     *
     * @return the list of tags of the stream or Collections.emptyList().
     */
    @Nonnull
    public List<String> getTags() throws ParsingException {
        return Collections.emptyList();
    }

    /**
     * The support information of the stream.
     * see: https://framatube.org/videos/watch/ee408ec8-07cd-4e35-b884-fb681a4b9d37
     * (support button).
     * If the support information are not available,
     * you can simply return an empty String.
     *
     * @return the support information of the stream or an empty string.
     */
    @Nonnull
    public String getSupportInfo() throws ParsingException {
        return "";
    }

    /**
     * The list of stream segments by timestamps for the stream.
     * If the segment list is not available you can simply return an empty list.
     *
     * @return The list of segments of the stream or an empty list.
     */
    @Nonnull
    public List<StreamSegment> getStreamSegments() throws ParsingException {
        return Collections.emptyList();
    }

    /** Optional provider replay intensity. Missing data must not prevent playback. */
    @Nonnull
    public List<ReplayHeatmapSample> getReplayHeatmap() throws ParsingException {
        return Collections.emptyList();
    }

    /**
     * Meta information about the stream.
     * <p>
     * This can be information about the stream creator (e.g. if the creator is a public
     * broadcaster) or further information on the topic (e.g. hints that the video might contain
     * conspiracy theories or contains information about a current health situation like the
     * Covid-19 pandemic).
     * </p>
     * The meta information often contains links to external sources like Wikipedia or the WHO.
     *
     * @return The meta info of the stream or an empty list if not provided.
     */
    @Nonnull
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }

    /**
     * get the list of StaffInfoItem, if the stream can be co-authored
     * (with more than one author/staff).
     *
     * @return list of staffs, empty if stream is not a Cooperation Stream
     */
    @Nonnull
    public List<StaffInfoItem> getStaffs() {
        return Collections.emptyList();
    }

    /**
     * Detailed statistic data about the stream
     * @return map of statistic data, empty if not available
     */
    @Nonnull
    public Map<String, String> getStats() {
        return Collections.emptyMap();
    }

    public boolean isSupportComments() throws ParsingException {
        return true;
    }

    public boolean isSupportRelatedItems() throws ParsingException {
        return true;
    }

    /** Hint used before the provider's bullet comments or live chat have been requested. */
    public boolean isSupportBulletComments() throws ParsingException {
        return true;
    }

    public boolean isRoundPlayStream() {
        return false;
    }

    /**
     * Whether metadata is available but playback has not started yet.
     */
    public boolean isUpcoming() {
        return false;
    }

    /**
     * Provider-reported schedule state. Older providers only report a positive upcoming flag.
     */
    @Nullable
    public Boolean getUpcomingStatus() {
        return isUpcoming() ? true : null;
    }

    /*Live start time in millisecond. Just use if current status can't be fetched from the segment*/
    public long getStartAt() throws ParsingException {
        return -1;
    }

    public InfoItemsCollector<? extends InfoItem, ? extends InfoItemExtractor>
    getPartitions() throws ParsingException {
        return null;
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
    public boolean isShortFormContent() throws ParsingException {
        return false;
    }

    /**
     * Whether the stream is only available to channel members.
     *
     * @return whether the stream is only available to channel members.
     * @throws ParsingException if there is an error in the extraction
     */
    public boolean requiresMembership() throws ParsingException {
        return false;
    }

    public enum Privacy {
        PUBLIC,
        UNLISTED,
        PRIVATE,
        INTERNAL,
        OTHER
    }
}
