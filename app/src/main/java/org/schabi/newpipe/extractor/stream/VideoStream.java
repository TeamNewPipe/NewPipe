package org.schabi.newpipe.extractor.stream;

/*
 * Created by Christian Schabesberger on 04.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * VideoStream.java is part of NewPipe Extractor.
 *
 * NewPipe Extractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe Extractor. If not, see <https://www.gnu.org/licenses/>.
 */

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;

public final class VideoStream extends Stream {
    private static final long serialVersionUID = 7658657907775256506L;

    public static final String RESOLUTION_UNKNOWN = "";

    /** @deprecated Use {@link #getResolution()} instead. */
    @Deprecated
    public final String resolution;

    /** @deprecated Use {@link #isVideoOnly()} instead. */
    @Deprecated
    public final boolean isVideoOnly;

    // Fields for DASH
    private int itag = ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE;
    private int bitrate;
    private int initStart;
    private int initEnd;
    private int indexStart;
    private int indexEnd;
    private int width;
    private int height;

    private int fps;
    private String quality;
    private String codec = "";
    @Nullable private ItagItem itagItem;
    @Nullable private String audioTrackId;
    @Nullable private String audioTrackName;
    @Nullable private String audioLocale;

    /**
     * Class to build {@link VideoStream} objects.
     */
    public static final class Builder {
        private String id;
        private String content;
        private boolean isUrl;
        private DeliveryMethod deliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP;
        @Nullable
        private MediaFormat mediaFormat;
        @Nullable
        private String manifestUrl;
        // Use of the Boolean class instead of the primitive type needed for setter call check
        private Boolean isVideoOnly;
        private String resolution;
        private String codec;
        private int bitrate;
        private int initStart;
        private int initEnd;
        private int indexStart;
        private int indexEnd;
        private int width;
        private int height;
        private int fps;
        @Nullable
        private ItagItem itagItem;
        @Nullable
        private String audioTrackId;
        @Nullable
        private String audioTrackName;
        @Nullable
        private String audioLocale;
        private long availableAt = AVAILABLE_AT_UNKNOWN;
        @Nullable
        private Serializable deliveryMethodInfo;
        @Nullable
        private java.util.Map<String, String> requestHeaders;
        @Nullable
        private Boolean cacheable;

        /**
         * Create a new {@link Builder} instance with its default values.
         */
        public Builder() {
        }

        /**
         * Set the identifier of the {@link VideoStream}.
         *
         * <p>
         * It must not be null, and should be non empty.
         * </p>
         *
         * <p>
         * If you are not able to get an identifier, use the static constant {@link
         * Stream#ID_UNKNOWN ID_UNKNOWN} of the {@link Stream} class.
         * </p>
         *
         * @param id the identifier of the {@link VideoStream}, which must not be null
         * @return this {@link Builder} instance
         */
        public Builder setId(@Nonnull final String id) {
            this.id = id;
            return this;
        }

        /**
         * Set the content of the {@link VideoStream}.
         *
         * <p>
         * It must not be null, and should be non empty.
         * </p>
         *
         * @param content the content of the {@link VideoStream}
         * @param isUrl   whether the content is a URL
         * @return this {@link Builder} instance
         */
        public Builder setContent(@Nonnull final String content,
                                  final boolean isUrl) {
            this.content = content;
            this.isUrl = isUrl;
            return this;
        }

        /**
         * Set the {@link MediaFormat} used by the {@link VideoStream}.
         *
         * <p>
         * It should be one of the video {@link MediaFormat}s ({@link MediaFormat#MPEG_4 MPEG_4},
         * {@link MediaFormat#v3GPP v3GPP}, or {@link MediaFormat#WEBM WEBM}) but can be {@code
         * null} if the media format could not be determined.
         * </p>
         *
         * <p>
         * The default value is {@code null}.
         * </p>
         *
         * @param mediaFormat the {@link MediaFormat} of the {@link VideoStream}, which can be null
         * @return this {@link Builder} instance
         */
        public Builder setMediaFormat(@Nullable final MediaFormat mediaFormat) {
            this.mediaFormat = mediaFormat;
            return this;
        }

        /**
         * Set the {@link DeliveryMethod} of the {@link VideoStream}.
         *
         * <p>
         * It must not be null.
         * </p>
         *
         * <p>
         * The default delivery method is {@link DeliveryMethod#PROGRESSIVE_HTTP}.
         * </p>
         *
         * @param deliveryMethod the {@link DeliveryMethod} of the {@link VideoStream}, which must
         *                       not be null
         * @return this {@link Builder} instance
         */
        public Builder setDeliveryMethod(@Nonnull final DeliveryMethod deliveryMethod) {
            this.deliveryMethod = deliveryMethod;
            return this;
        }

        /**
         * Sets the URL of the manifest this stream comes from (if applicable, otherwise null).
         *
         * @param manifestUrl the URL of the manifest this stream comes from or {@code null}
         * @return this {@link Builder} instance
         */
        public Builder setManifestUrl(@Nullable final String manifestUrl) {
            this.manifestUrl = manifestUrl;
            return this;
        }

        /**
         * Set whether the {@link VideoStream} is video-only.
         *
         * <p>
         * This property must be set before building the {@link VideoStream}.
         * </p>
         *
         * @param isVideoOnly whether the {@link VideoStream} is video-only
         * @return this {@link Builder} instance
         */
        public Builder setIsVideoOnly(final boolean isVideoOnly) {
            this.isVideoOnly = isVideoOnly;
            return this;
        }

        /**
         * Set the resolution of the {@link VideoStream}.
         *
         * <p>
         * This resolution can be used by clients to know the quality of the video stream.
         * </p>
         *
         * <p>
         * If you are not able to know the resolution, you should use {@link #RESOLUTION_UNKNOWN}
         * as the resolution of the video stream.
         * </p>
         *
         * <p>
         * It must be set before building the builder and not null.
         * </p>
         *
         * @param resolution the resolution of the {@link VideoStream}
         * @return this {@link Builder} instance
         */
        public Builder setResolution(@Nonnull final String resolution) {
            this.resolution = resolution;
            return this;
        }

        /**
         * Set the {@link ItagItem} corresponding to the {@link VideoStream}.
         *
         * <p>
         * {@link ItagItem}s are YouTube specific objects, so they are only known for this service
         * and can be null.
         * </p>
         *
         * <p>
         * The default value is {@code null}.
         * </p>
         *
         * @param itagItem the {@link ItagItem} of the {@link VideoStream}, which can be null
         * @return this {@link Builder} instance
         */
        public Builder setItagItem(@Nullable final ItagItem itagItem) {
            this.itagItem = itagItem;
            return this;
        }

        public Builder setCodec(String codec) {
            this.codec = codec;
            return this;
        }

        public Builder setBitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public Builder setInitStart(int initStart) {
            this.initStart = initStart;
            return this;
        }

        public Builder setInitEnd(int initEnd) {
            this.initEnd = initEnd;
            return this;
        }

        public Builder setIndexStart(int indexStart) {
            this.indexStart = indexStart;
            return this;
        }

        public Builder setIndexEnd(int indexEnd) {
            this.indexEnd = indexEnd;
            return this;
        }

        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        public Builder setFps(int fps) {
            this.fps = fps;
            return this;
        }

        public Builder setAudioTrackId(@Nullable final String audioTrackId) {
            this.audioTrackId = audioTrackId;
            return this;
        }

        public Builder setAudioTrackName(@Nullable final String audioTrackName) {
            this.audioTrackName = audioTrackName;
            return this;
        }

        public Builder setAudioLocale(@Nullable final String audioLocale) {
            this.audioLocale = audioLocale;
            return this;
        }

        public Builder setAvailableAt(final long availableAt) {
            this.availableAt = availableAt;
            return this;
        }

        public Builder setRequestHeaders(@Nullable final java.util.Map<String, String> headers) {
            this.requestHeaders = headers;
            return this;
        }

        /** @see Stream#getCacheable() */
        public Builder setCacheable(@Nullable final Boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        public Builder setDeliveryMethodInfo(@Nullable final Serializable deliveryMethodInfo) {
            this.deliveryMethodInfo = deliveryMethodInfo;
            return this;
        }

        /**
         * Build a {@link VideoStream} using the builder's current values.
         *
         * <p>
         * The identifier, the content (and so the {@code isUrl} boolean), the {@code isVideoOnly}
         * and the {@code resolution} properties must have been set.
         * </p>
         *
         * @return a new {@link VideoStream} using the builder's current values
         * @throws IllegalStateException if {@code id}, {@code content} (and so {@code isUrl}),
         * {@code deliveryMethod}, {@code isVideoOnly} or {@code resolution} have been not set, or
         * have been set as {@code null}
         */
        @Nonnull
        public VideoStream build() {
            if (id == null) {
                throw new IllegalStateException(
                        "The identifier of the video stream has been not set or is null. If you "
                                + "are not able to get an identifier, use the static constant "
                                + "ID_UNKNOWN of the Stream class.");
            }

            if (content == null) {
                throw new IllegalStateException("The content of the video stream has been not set "
                        + "or is null. Please specify a non-null one with setContent.");
            }

            if (deliveryMethod == null) {
                throw new IllegalStateException(
                        "The delivery method of the video stream has been set as null, which is "
                                + "not allowed. Pass a valid one instead with setDeliveryMethod.");
            }

            if (isVideoOnly == null) {
                throw new IllegalStateException("The video stream has been not set as a "
                        + "video-only stream or as a video stream with embedded audio. Please "
                        + "specify this information with setIsVideoOnly.");
            }

            if (resolution == null) {
                throw new IllegalStateException(
                        "The resolution of the video stream has been not set. Please specify it "
                                + "with setResolution (use an empty string if you are not able to "
                                + "get it).");
            }

            final VideoStream stream = new VideoStream(id, content, isUrl, mediaFormat, deliveryMethod, resolution, codec,
                    bitrate, initStart, initEnd, indexStart, indexEnd, width, height, fps,
                    isVideoOnly, manifestUrl, itagItem, audioTrackId, audioTrackName, audioLocale,
                    availableAt, deliveryMethodInfo);
            stream.setRequestHeaders(requestHeaders);
            stream.setCacheable(cacheable);
            return stream;
        }
    }

    /**
     * Create a new video stream.
     *
     * @param id             the identifier which uniquely identifies the stream, e.g. for YouTube
     *                       this would be the itag
     * @param content        the content or the URL of the stream, depending on whether isUrl is
     *                       true
     * @param isUrl          whether content is the URL or the actual content of e.g. a DASH
     *                       manifest
     * @param format         the {@link MediaFormat} used by the stream, which can be null
     * @param deliveryMethod the {@link DeliveryMethod} of the stream
     * @param resolution     the resolution of the stream
     * @param isVideoOnly    whether the stream is video-only
     * @param itagItem       the {@link ItagItem} corresponding to the stream, which cannot be null
     * @param manifestUrl    the URL of the manifest this stream comes from (if applicable,
     *                       otherwise null)
     */
    private VideoStream(@Nonnull final String id,
                        @Nonnull final String content,
                        final boolean isUrl,
                        @Nullable final MediaFormat format,
                        @Nonnull final DeliveryMethod deliveryMethod,
                        @Nonnull final String resolution,
                        String codec,
                        final int bitrate,
                        final int initStart,
                        final int initEnd,
                        final int indexStart,
                        final int indexEnd,
                        final int width,
                        final int height,
                        final int fps,
                        final boolean isVideoOnly,
                        @Nullable final String manifestUrl,
                        @Nullable final ItagItem itagItem,
                        @Nullable final String audioTrackId,
                        @Nullable final String audioTrackName,
                        @Nullable final String audioLocale,
                        final long availableAt,
                        @Nullable final Serializable deliveryMethodInfo) {
        super(id, content, isUrl, format, deliveryMethod, manifestUrl, availableAt,
                deliveryMethodInfo);
        if (itagItem != null) {
            this.itagItem = itagItem;
            this.itag = itagItem.id;
            this.bitrate = itagItem.getBitrate();
            this.initStart = itagItem.getInitStart();
            this.initEnd = itagItem.getInitEnd();
            this.indexStart = itagItem.getIndexStart();
            this.indexEnd = itagItem.getIndexEnd();
            this.codec = itagItem.getCodec();
            this.height = itagItem.getHeight();
            this.width = itagItem.getWidth();
            this.quality = itagItem.getQuality();
            this.fps = itagItem.getFps();
        }
        if (codec != null) {
            this.codec = codec;
        }
        if (bitrate != 0) {
            this.bitrate = bitrate;
        }
        if (initEnd != 0 || indexEnd != 0) {
            this.initStart = initStart;
            this.initEnd = initEnd;
            this.indexStart = indexStart;
            this.indexEnd = indexEnd;
        }
        if (width != 0) {
            this.width = width;
        }
        if (height != 0) {
            this.height = height;
        }
        if (fps != 0) {
            this.fps = fps;
        }
        this.resolution = resolution;
        this.isVideoOnly = isVideoOnly;
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
        this.audioLocale = audioLocale;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equalStats(final Stream cmp) {
        return super.equalStats(cmp)
                && cmp instanceof VideoStream
                && resolution.equals(((VideoStream) cmp).resolution)
                && isVideoOnly == ((VideoStream) cmp).isVideoOnly
                && java.util.Objects.equals(audioTrackId,
                        ((VideoStream) cmp).audioTrackId);
    }

    /**
     * Get the video resolution.
     *
     * <p>
     * It can be unknown for some streams, like for HLS master playlists. In this case,
     * {@link #RESOLUTION_UNKNOWN} is returned by this method.
     * </p>
     *
     * @return the video resolution or {@link #RESOLUTION_UNKNOWN}
     */
    @Nonnull
    public String getResolution() {
        return resolution;
    }

    /**
     * Return whether the stream is video-only.
     *
     * <p>
     * Video-only streams have no audio.
     * </p>
     *
     * @return {@code true} if this stream is video-only, {@code false} otherwise
     */
    public boolean isVideoOnly() {
        return isVideoOnly;
    }

    /**
     * Get the itag identifier of the stream.
     *
     * <p>
     * Always equals to {@link #ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE} for other streams than the
     * ones of the YouTube service.
     * </p>
     *
     * @return the number of the {@link ItagItem} passed in the constructor of the video stream.
     */
    public int getItag() {
        return itag;
    }

    /**
     * Get the bitrate of the stream.
     *
     * @return the bitrate set from the {@link ItagItem} passed in the constructor of the stream.
     */
    public int getBitrate() {
        return bitrate;
    }

    /**
     * Get the initialization start of the stream.
     *
     * @return the initialization start value set from the {@link ItagItem} passed in the
     * constructor of the
     * stream.
     */
    public int getInitStart() {
        return initStart;
    }

    /**
     * Get the initialization end of the stream.
     *
     * @return the initialization end value set from the {@link ItagItem} passed in the constructor
     * of the stream.
     */
    public int getInitEnd() {
        return initEnd;
    }

    /**
     * Get the index start of the stream.
     *
     * @return the index start value set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public int getIndexStart() {
        return indexStart;
    }

    /**
     * Get the index end of the stream.
     *
     * @return the index end value set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public int getIndexEnd() {
        return indexEnd;
    }

    /**
     * Get the width of the video stream.
     *
     * @return the width set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the height of the video stream.
     *
     * @return the height set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Get the frames per second of the video stream.
     *
     * @return the frames per second set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public int getFps() {
        return fps;
    }

    /**
     * Get the quality of the stream.
     *
     * @return the quality label set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public String getQuality() {
        return quality;
    }

    /**
     * Get the codec of the stream.
     *
     * @return the codec set from the {@link ItagItem} passed in the constructor of the stream.
     */
    public String getCodec() {
        return codec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public ItagItem getItagItem() {
        return itagItem;
    }

    @Nullable
    public String getAudioTrackId() {
        return audioTrackId;
    }

    @Nullable
    public String getAudioTrackName() {
        return audioTrackName;
    }

    @Nullable
    public String getAudioLocale() {
        return audioLocale;
    }
}
