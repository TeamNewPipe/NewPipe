package org.schabi.newpipe.extractor.stream;

/*
 * Created by Christian Schabesberger on 04.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * AudioStream.java is part of NewPipe Extractor.
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

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AudioStream extends Stream {
    private static final long serialVersionUID = -5432602896957400844L;

    public static final int UNKNOWN_BITRATE = -1;

    private final int averageBitrate;

    private String audioTrackId;
    private String audioTrackName;
    private String audioLocale;
    @Nullable
    private final AudioTrackType audioTrackType;

    // Fields for DASH
    private int itag = ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE;
    private int bitrate;
    private int initStart;
    private int initEnd;
    private int indexStart;
    private int indexEnd;
    private String quality;
    private String codec;
    @Nullable
    private ItagItem itagItem;

    /**
     * Class to build {@link AudioStream} objects.
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public static final class Builder {
        private String id;
        private String content;
        private boolean isUrl;
        private DeliveryMethod deliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP;
        @Nullable
        private MediaFormat mediaFormat;
        @Nullable
        private String manifestUrl;
        private int averageBitrate = UNKNOWN_BITRATE;
        @Nullable
        private ItagItem itagItem;
        private String quality;
        private String codec;
        private int bitrate;
        private int initStart;
        private int initEnd;
        private int indexStart;
        private int indexEnd;
        private String audioTrackId;
        private String audioTrackName;
        private String audioLocale;
        @Nullable
        private AudioTrackType audioTrackType;
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
         * Set the identifier of the {@link AudioStream}.
         *
         * <p>
         * It <b>must not be null</b> and should be non empty.
         * </p>
         *
         * <p>
         * If you are not able to get an identifier, use the static constant {@link
         * Stream#ID_UNKNOWN ID_UNKNOWN} of the {@link Stream} class.
         * </p>
         *
         * @param id the identifier of the {@link AudioStream}, which must not be null
         * @return this {@link Builder} instance
         */
        public Builder setId(@Nonnull final String id) {
            this.id = id;
            return this;
        }

        /**
         * Set the content of the {@link AudioStream}.
         *
         * <p>
         * It must not be null, and should be non empty.
         * </p>
         *
         * @param content the content of the {@link AudioStream}
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
         * Set the {@link MediaFormat} used by the {@link AudioStream}.
         *
         * <p>
         * It should be one of the audio {@link MediaFormat}s ({@link MediaFormat#M4A M4A},
         * {@link MediaFormat#WEBMA WEBMA}, {@link MediaFormat#MP3 MP3}, {@link MediaFormat#OPUS
         * OPUS}, {@link MediaFormat#OGG OGG}, or {@link MediaFormat#WEBMA_OPUS WEBMA_OPUS}) but
         * can be {@code null} if the media format could not be determined.
         * </p>
         *
         * <p>
         * The default value is {@code null}.
         * </p>
         *
         * @param mediaFormat the {@link MediaFormat} of the {@link AudioStream}, which can be null
         * @return this {@link Builder} instance
         */
        public Builder setMediaFormat(@Nullable final MediaFormat mediaFormat) {
            this.mediaFormat = mediaFormat;
            return this;
        }

        /**
         * Set the {@link DeliveryMethod} of the {@link AudioStream}.
         *
         * <p>
         * It must not be null.
         * </p>
         *
         * <p>
         * The default delivery method is {@link DeliveryMethod#PROGRESSIVE_HTTP}.
         * </p>
         *
         * @param deliveryMethod the {@link DeliveryMethod} of the {@link AudioStream}, which must
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
         * Set the average bitrate of the {@link AudioStream}.
         *
         * <p>
         * The default value is {@link #UNKNOWN_BITRATE}.
         * </p>
         *
         * @param averageBitrate the average bitrate of the {@link AudioStream}, which should
         *                       positive
         * @return this {@link Builder} instance
         */
        public Builder setAverageBitrate(final int averageBitrate) {
            this.averageBitrate = averageBitrate;
            return this;
        }

        /**
         * Set the {@link ItagItem} corresponding to the {@link AudioStream}.
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
         * @param itagItem the {@link ItagItem} of the {@link AudioStream}, which can be null
         * @return this {@link Builder} instance
         */
        public Builder setItagItem(@Nullable final ItagItem itagItem) {
            this.itagItem = itagItem;
            return this;
        }

        public Builder setQuality(String quality) {
            this.quality = quality;
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

        public Builder setAudioTrackId(String audioTrackId) {
            this.audioTrackId = audioTrackId;
            return this;
        }

        public Builder setAudioTrackName(String audioTrackName) {
            this.audioTrackName = audioTrackName;
            return this;
        }

        public Builder setAudioLocale(String audioLocale) {
            this.audioLocale = audioLocale;
            return this;
        }

        public Builder setAudioTrackType(@Nullable final AudioTrackType audioTrackType) {
            this.audioTrackType = audioTrackType;
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

        public Builder setCacheable(@Nullable final Boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        public Builder setDeliveryMethodInfo(@Nullable final Serializable deliveryMethodInfo) {
            this.deliveryMethodInfo = deliveryMethodInfo;
            return this;
        }

        /**
         * Build an {@link AudioStream} using the builder's current values.
         *
         * <p>
         * The identifier and the content (and so the {@code isUrl} boolean) properties must have
         * been set.
         * </p>
         *
         * @return a new {@link AudioStream} using the builder's current values
         * @throws IllegalStateException if {@code id}, {@code content} (and so {@code isUrl}) or
         * {@code deliveryMethod} have been not set, or have been set as {@code null}
         */
        @Nonnull
        public AudioStream build() {
            if (id == null) {
                throw new IllegalStateException(
                        "The identifier of the audio stream has been not set or is null. If you "
                                + "are not able to get an identifier, use the static constant "
                                + "ID_UNKNOWN of the Stream class.");
            }

            if (content == null) {
                throw new IllegalStateException("The content of the audio stream has been not set "
                        + "or is null. Please specify a non-null one with setContent.");
            }

            if (deliveryMethod == null) {
                throw new IllegalStateException(
                        "The delivery method of the audio stream has been set as null, which is "
                                + "not allowed. Pass a valid one instead with setDeliveryMethod.");
            }

            final AudioStream stream = new AudioStream(id, content, isUrl, mediaFormat,
                    deliveryMethod, averageBitrate, codec,
                    bitrate, initStart, initEnd, indexStart, indexEnd, manifestUrl, itagItem,
                    quality, audioTrackId, audioTrackName, audioLocale, audioTrackType,
                    availableAt, deliveryMethodInfo);
            stream.setRequestHeaders(requestHeaders);
            stream.setCacheable(cacheable);
            return stream;
        }
    }


    /**
     * Create a new audio stream.
     *
     * @param id             the identifier which uniquely identifies the stream, e.g. for YouTube
     *                       this would be the itag
     * @param content        the content or the URL of the stream, depending on whether isUrl is
     *                       true
     * @param isUrl          whether content is the URL or the actual content of e.g. a DASH
     *                       manifest
     * @param format         the {@link MediaFormat} used by the stream, which can be null
     * @param deliveryMethod the {@link DeliveryMethod} of the stream
     * @param averageBitrate the average bitrate of the stream (which can be unknown, see
     *                       {@link #UNKNOWN_BITRATE})
     * @param manifestUrl    the URL of the manifest this stream comes from (if applicable,
     *                       otherwise null)
     * @param itagItem       the {@link ItagItem} corresponding to the stream, which cannot be null
     * @param quality
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private AudioStream(@Nonnull final String id,
                        @Nonnull final String content,
                        final boolean isUrl,
                        @Nullable final MediaFormat format,
                        @Nonnull final DeliveryMethod deliveryMethod,
                        final int averageBitrate,
                        String codec,
                        final int bitrate,
                        final int initStart,
                        final int initEnd,
                        final int indexStart,
                        final int indexEnd,
                        @Nullable final String manifestUrl,
                        @Nullable final ItagItem itagItem, String quality,
                        @Nullable final String audioTrackId,
                        @Nullable final String audioTrackName,
                        @Nullable final String audioLocale,
                        @Nullable final AudioTrackType audioTrackType,
                        final long availableAt,
                        @Nullable final Serializable deliveryMethodInfo) {
        super(id, content, isUrl, format, deliveryMethod, manifestUrl, availableAt,
                deliveryMethodInfo);
        if (itagItem != null) {
            this.itagItem = itagItem;
            this.itag = itagItem.id;
            this.quality = itagItem.getQuality();
            this.bitrate = itagItem.getBitrate();
            this.initStart = itagItem.getInitStart();
            this.initEnd = itagItem.getInitEnd();
            this.indexStart = itagItem.getIndexStart();
            this.indexEnd = itagItem.getIndexEnd();
            this.codec = itagItem.getCodec();
        }
        this.averageBitrate = averageBitrate;
        if(quality != null) {
            this.quality = quality;
        }
        if(codec != null) {
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
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
        this.audioLocale = audioLocale;
        this.audioTrackType = audioTrackType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equalStats(final Stream cmp) {
        return super.equalStats(cmp) && cmp instanceof AudioStream
                && averageBitrate == ((AudioStream) cmp).averageBitrate
                && Objects.equals(audioTrackId, ((AudioStream) cmp).audioTrackId)
                && audioTrackType == ((AudioStream) cmp).audioTrackType;
    }

    /**
     * Get the average bitrate of the stream.
     *
     * @return the average bitrate or {@link #UNKNOWN_BITRATE} if it is unknown
     */
    public int getAverageBitrate() {
        return averageBitrate;
    }

    /**
     * Get the itag identifier of the stream.
     *
     * <p>
     * Always equals to {@link #ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE} for other streams than the
     * ones of the YouTube service.
     * </p>
     *
     * @return the number of the {@link ItagItem} passed in the constructor of the audio stream.
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
     * constructor of the stream.
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
     * Get the quality of the stream.
     *
     * @return the quality label set from the {@link ItagItem} passed in the constructor of the
     * stream.
     */
    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    /**
     * Get the codec of the stream.
     *
     * @return the codec set from the {@link ItagItem} passed in the constructor of the stream.
     */
    public String getCodec() {
        return codec;
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

    /**
     * Get the semantic type of this audio track.
     *
     * @return the track type, or {@code null} when the service did not provide one
     */
    @Nullable
    public AudioTrackType getAudioTrackType() {
        return audioTrackType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public ItagItem getItagItem() {
        return itagItem;
    }
}
