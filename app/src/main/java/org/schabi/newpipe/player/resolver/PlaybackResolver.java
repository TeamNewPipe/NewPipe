package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.extractor.stream.AudioStream.UNKNOWN_BITRATE;
import static org.schabi.newpipe.extractor.stream.VideoStream.RESOLUTION_UNKNOWN;
import static org.schabi.newpipe.player.helper.PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.datasource.NonUriHlsDataSourceFactory;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * This interface is just a shorthand for {@link Resolver} with {@link StreamInfo} as source and
 * {@link MediaSource} as product. It contains many static methods that can be used by classes
 * implementing this interface, and nothing else.
 */
public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {
    String TAG = PlaybackResolver.class.getSimpleName();


    //region Cache key generation
    private static StringBuilder commonCacheKeyOf(final StreamInfo info,
                                                  final Stream stream,
                                                  final boolean resolutionOrBitrateUnknown) {
        // stream info service id
        final StringBuilder cacheKey = new StringBuilder(info.getServiceId());

        // stream info id
        cacheKey.append(" ");
        cacheKey.append(info.getId());

        // stream id (even if unknown)
        cacheKey.append(" ");
        cacheKey.append(stream.getId());

        // mediaFormat (if not null)
        final MediaFormat mediaFormat = stream.getFormat();
        if (mediaFormat != null) {
            cacheKey.append(" ");
            cacheKey.append(mediaFormat.getName());
        }

        // content (only if other information is missing)
        // If the media format and the resolution/bitrate are both missing, then we don't have
        // enough information to distinguish this stream from other streams.
        // So, only in that case, we use the content (i.e. url or manifest) to differentiate
        // between streams.
        // Note that if the content were used even when other information is present, then two
        // streams with the same stats but with different contents (e.g. because the url was
        // refreshed) will be considered different (i.e. with a different cacheKey), making the
        // cache useless.
        if (resolutionOrBitrateUnknown && mediaFormat == null) {
            cacheKey.append(" ");
            cacheKey.append(Objects.hash(stream.getContent(), stream.getManifestUrl()));
        }

        return cacheKey;
    }

    /**
     * Builds the cache key of a {@link VideoStream video stream}.
     *
     * <p>
     * A cache key is unique to the features of the provided video stream, and when possible
     * independent of <i>transient</i> parameters (such as the URL of the stream).
     * This ensures that there are no conflicts, but also that the cache is used as much as
     * possible: the same cache should be used for two streams which have the same features but
     * e.g. a different URL, since the URL might have been reloaded in the meantime, but the stream
     * actually referenced by the URL is still the same.
     * </p>
     *
     * @param info        the {@link StreamInfo stream info}, to distinguish between streams with
     *                    the same features but coming from different stream infos
     * @param videoStream the {@link VideoStream video stream} for which the cache key should be
     *                    created
     * @return a key to be used to store the cache of the provided {@link VideoStream video stream}
     */
    static String cacheKeyOf(final StreamInfo info, final VideoStream videoStream) {
        final boolean resolutionUnknown = videoStream.getResolution().equals(RESOLUTION_UNKNOWN);
        final StringBuilder cacheKey = commonCacheKeyOf(info, videoStream, resolutionUnknown);

        // resolution (if known)
        if (!resolutionUnknown) {
            cacheKey.append(" ");
            cacheKey.append(videoStream.getResolution());
        }

        // isVideoOnly
        cacheKey.append(" ");
        cacheKey.append(videoStream.isVideoOnly());

        return cacheKey.toString();
    }

    /**
     * Builds the cache key of an audio stream.
     *
     * <p>
     * A cache key is unique to the features of the provided {@link AudioStream audio stream}, and
     * when possible independent of <i>transient</i> parameters (such as the URL of the stream).
     * This ensures that there are no conflicts, but also that the cache is used as much as
     * possible: the same cache should be used for two streams which have the same features but
     * e.g. a different URL, since the URL might have been reloaded in the meantime, but the stream
     * actually referenced by the URL is still the same.
     * </p>
     *
     * @param info        the {@link StreamInfo stream info}, to distinguish between streams with
     *                    the same features but coming from different stream infos
     * @param audioStream the {@link AudioStream audio stream} for which the cache key should be
     *                    created
     * @return a key to be used to store the cache of the provided {@link AudioStream audio stream}
     */
    static String cacheKeyOf(final StreamInfo info, final AudioStream audioStream) {
        final boolean averageBitrateUnknown = audioStream.getAverageBitrate() == UNKNOWN_BITRATE;
        final StringBuilder cacheKey = commonCacheKeyOf(info, audioStream, averageBitrateUnknown);

        // averageBitrate (if known)
        if (!averageBitrateUnknown) {
            cacheKey.append(" ");
            cacheKey.append(audioStream.getAverageBitrate());
        }

        return cacheKey.toString();
    }

    /**
     * Use common base type {@link Stream} to handle {@link AudioStream} or {@link VideoStream}
     * transparently. For more info see {@link #cacheKeyOf(StreamInfo, AudioStream)} or
     * {@link #cacheKeyOf(StreamInfo, VideoStream)}.
     *
     * @param info   the {@link StreamInfo stream info}, to distinguish between streams with
     *               the same features but coming from different stream infos
     * @param stream the {@link Stream} ({@link AudioStream} or {@link VideoStream})
     *               for which the cache key should be created
     * @return a key to be used to store the cache of the provided {@link Stream}
     */
    static String cacheKeyOf(final StreamInfo info, final Stream stream) {
        if (stream instanceof AudioStream) {
            return cacheKeyOf(info, (AudioStream) stream);
        } else if (stream instanceof VideoStream) {
            return cacheKeyOf(info, (VideoStream) stream);
        }
        throw new RuntimeException("no audio or video stream. That should never happen");
    }
    //endregion


    //region Live media sources
    @Nullable
    static MediaSource maybeBuildLiveMediaSource(final PlayerDataSource dataSource,
                                                 final StreamInfo info) {
        if (!StreamTypeUtil.isLiveStream(info.getStreamType())) {
            return null;
        }

        try {
            final StreamInfoTag tag = StreamInfoTag.of(info);
            if (!info.getHlsUrl().isEmpty()) {
                return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.CONTENT_TYPE_HLS, tag);
            } else if (!info.getDashMpdUrl().isEmpty()) {
                return buildLiveMediaSource(
                        dataSource, info.getDashMpdUrl(), C.CONTENT_TYPE_DASH, tag);
            }
        } catch (final Exception e) {
            Log.w(TAG, "Error when generating live media source, falling back to standard sources",
                    e);
        }

        return null;
    }

    static MediaSource buildLiveMediaSource(final PlayerDataSource dataSource,
                                            final String sourceUrl,
                                            @C.ContentType final int type,
                                            final MediaItemTag metadata) throws ResolverException {
        final MediaSource.Factory factory;
        switch (type) {
            case C.CONTENT_TYPE_SS:
                factory = dataSource.getLiveSsMediaSourceFactory();
                break;
            case C.CONTENT_TYPE_DASH:
                factory = dataSource.getLiveDashMediaSourceFactory();
                break;
            case C.CONTENT_TYPE_HLS:
                factory = dataSource.getLiveHlsMediaSourceFactory();
                break;
            case C.CONTENT_TYPE_OTHER:
            case C.CONTENT_TYPE_RTSP:
            default:
                throw new ResolverException("Unsupported type: " + type);
        }

        return factory.createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(sourceUrl))
                        .setLiveConfiguration(
                                new MediaItem.LiveConfiguration.Builder()
                                        .setTargetOffsetMs(LIVE_STREAM_EDGE_GAP_MILLIS)
                                        .build())
                        .build());
    }
    //endregion


    //region Generic media sources
    static MediaSource buildMediaSource(final PlayerDataSource dataSource,
                                        final Stream stream,
                                        final StreamInfo streamInfo,
                                        final String cacheKey,
                                        final MediaItemTag metadata) throws ResolverException {
        if (streamInfo.getService() == ServiceList.YouTube) {
            return createYoutubeMediaSource(stream, streamInfo, dataSource, cacheKey, metadata);
        }

        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        switch (deliveryMethod) {
            case PROGRESSIVE_HTTP:
                return buildProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
            case DASH:
                return buildDashMediaSource(dataSource, stream, cacheKey, metadata);
            case HLS:
                return buildHlsMediaSource(dataSource, stream, cacheKey, metadata);
            case SS:
                return buildSSMediaSource(dataSource, stream, cacheKey, metadata);
            // Torrent streams are not supported by ExoPlayer
            default:
                throw new ResolverException("Unsupported delivery type: " + deliveryMethod);
        }
    }

    private static ProgressiveMediaSource buildProgressiveMediaSource(
            final PlayerDataSource dataSource,
            final Stream stream,
            final String cacheKey,
            final MediaItemTag metadata) throws ResolverException {
        if (!stream.isUrl()) {
            throw new ResolverException("Non URI progressive contents are not supported");
        }
        throwResolverExceptionIfUrlNullOrEmpty(stream.getContent());
        return dataSource.getProgressiveMediaSourceFactory().createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    private static DashMediaSource buildDashMediaSource(final PlayerDataSource dataSource,
                                                        final Stream stream,
                                                        final String cacheKey,
                                                        final MediaItemTag metadata)
            throws ResolverException {

        if (stream.isUrl()) {
            throwResolverExceptionIfUrlNullOrEmpty(stream.getContent());
            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }

        try {
            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    createDashManifest(stream.getContent(), stream),
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(manifestUrlToUri(stream.getManifestUrl()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } catch (final IOException e) {
            throw new ResolverException(
                    "Could not create a DASH media source/manifest from the manifest text", e);
        }
    }

    private static DashManifest createDashManifest(final String manifestContent,
                                                   final Stream stream) throws IOException {
        return new DashManifestParser().parse(manifestUrlToUri(stream.getManifestUrl()),
                new ByteArrayInputStream(manifestContent.getBytes(StandardCharsets.UTF_8)));
    }

    private static HlsMediaSource buildHlsMediaSource(final PlayerDataSource dataSource,
                                                      final Stream stream,
                                                      final String cacheKey,
                                                      final MediaItemTag metadata)
            throws ResolverException {
        if (stream.isUrl()) {
            throwResolverExceptionIfUrlNullOrEmpty(stream.getContent());
            return dataSource.getHlsMediaSourceFactory(null).createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }

        final NonUriHlsDataSourceFactory.Builder hlsDataSourceFactoryBuilder =
                new NonUriHlsDataSourceFactory.Builder();
        hlsDataSourceFactoryBuilder.setPlaylistString(stream.getContent());

        return dataSource.getHlsMediaSourceFactory(hlsDataSourceFactoryBuilder)
                .createMediaSource(new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(manifestUrlToUri(stream.getManifestUrl()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    private static SsMediaSource buildSSMediaSource(final PlayerDataSource dataSource,
                                                    final Stream stream,
                                                    final String cacheKey,
                                                    final MediaItemTag metadata)
            throws ResolverException {
        if (stream.isUrl()) {
            throwResolverExceptionIfUrlNullOrEmpty(stream.getContent());
            return dataSource.getSSMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }

        final Uri manifestUri = manifestUrlToUri(stream.getManifestUrl());

        final SsManifest smoothStreamingManifest;
        try {
            final ByteArrayInputStream smoothStreamingManifestInput = new ByteArrayInputStream(
                    stream.getContent().getBytes(StandardCharsets.UTF_8));
            smoothStreamingManifest = new SsManifestParser().parse(manifestUri,
                    smoothStreamingManifestInput);
        } catch (final IOException e) {
            throw new ResolverException("Error when parsing manual SS manifest", e);
        }

        return dataSource.getSSMediaSourceFactory().createMediaSource(
                smoothStreamingManifest,
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(manifestUri)
                        .setCustomCacheKey(cacheKey)
                        .build());
    }
    //endregion


    //region YouTube media sources
    private static MediaSource createYoutubeMediaSource(final Stream stream,
                                                        final StreamInfo streamInfo,
                                                        final PlayerDataSource dataSource,
                                                        final String cacheKey,
                                                        final MediaItemTag metadata)
            throws ResolverException {
        if (!(stream instanceof AudioStream || stream instanceof VideoStream)) {
            throw new ResolverException("Generation of YouTube DASH manifest for "
                    + stream.getClass().getSimpleName() + " is not supported");
        }

        final StreamType streamType = streamInfo.getStreamType();
        if (streamType == StreamType.VIDEO_STREAM) {
            return createYoutubeMediaSourceOfVideoStreamType(dataSource, stream, streamInfo,
                    cacheKey, metadata);
        } else if (streamType == StreamType.POST_LIVE_STREAM) {
            // If the content is not an URL, uses the DASH delivery method and if the stream type
            // of the stream is a post live stream, it means that the content is an ended
            // livestream so we need to generate the manifest corresponding to the content
            // (which is the last segment of the stream)

            try {
                final ItagItem itagItem = Objects.requireNonNull(stream.getItagItem());
                final String manifestString = YoutubePostLiveStreamDvrDashManifestCreator
                        .fromPostLiveStreamDvrStreamingUrl(stream.getContent(),
                                itagItem,
                                itagItem.getTargetDurationSec(),
                                streamInfo.getDuration());
                return buildYoutubeManualDashMediaSource(dataSource,
                        createDashManifest(manifestString, stream), stream, cacheKey,
                        metadata);
            } catch (final CreationException | IOException | NullPointerException e) {
                throw new ResolverException(
                        "Error when generating the DASH manifest of YouTube ended live stream", e);
            }
        } else {
            throw new ResolverException(
                    "DASH manifest generation of YouTube livestreams is not supported");
        }
    }

    private static MediaSource createYoutubeMediaSourceOfVideoStreamType(
            final PlayerDataSource dataSource,
            final Stream stream,
            final StreamInfo streamInfo,
            final String cacheKey,
            final MediaItemTag metadata) throws ResolverException {
        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        switch (deliveryMethod) {
            case PROGRESSIVE_HTTP:
                if ((stream instanceof VideoStream && ((VideoStream) stream).isVideoOnly())
                        || stream instanceof AudioStream) {
                    try {
                        final String manifestString = YoutubeProgressiveDashManifestCreator
                                .fromProgressiveStreamingUrl(stream.getContent(),
                                        Objects.requireNonNull(stream.getItagItem()),
                                        streamInfo.getDuration());
                        return buildYoutubeManualDashMediaSource(dataSource,
                                createDashManifest(manifestString, stream), stream, cacheKey,
                                metadata);
                    } catch (final CreationException | IOException | NullPointerException e) {
                        Log.w(TAG, "Error when generating or parsing DASH manifest of "
                                + "YouTube progressive stream, falling back to a "
                                + "ProgressiveMediaSource.", e);
                        return buildYoutubeProgressiveMediaSource(dataSource, stream, cacheKey,
                                metadata);
                    }
                } else {
                    // Legacy progressive streams, subtitles are handled by
                    // VideoPlaybackResolver
                    return buildYoutubeProgressiveMediaSource(dataSource, stream, cacheKey,
                            metadata);
                }
            case DASH:
                // If the content is not a URL, uses the DASH delivery method and if the stream
                // type of the stream is a video stream, it means the content is an OTF stream
                // so we need to generate the manifest corresponding to the content (which is
                // the base URL of the OTF stream).

                try {
                    final String manifestString = YoutubeOtfDashManifestCreator
                            .fromOtfStreamingUrl(stream.getContent(),
                                    Objects.requireNonNull(stream.getItagItem()),
                                    streamInfo.getDuration());
                    return buildYoutubeManualDashMediaSource(dataSource,
                            createDashManifest(manifestString, stream), stream, cacheKey,
                            metadata);
                } catch (final CreationException | IOException | NullPointerException e) {
                    Log.e(TAG,
                            "Error when generating the DASH manifest of YouTube OTF stream", e);
                    throw new ResolverException(
                            "Error when generating the DASH manifest of YouTube OTF stream", e);
                }
            case HLS:
                return dataSource.getYoutubeHlsMediaSourceFactory().createMediaSource(
                        new MediaItem.Builder()
                                .setTag(metadata)
                                .setUri(Uri.parse(stream.getContent()))
                                .setCustomCacheKey(cacheKey)
                                .build());
            default:
                throw new ResolverException("Unsupported delivery method for YouTube contents: "
                        + deliveryMethod);
        }
    }

    private static DashMediaSource buildYoutubeManualDashMediaSource(
            final PlayerDataSource dataSource,
            final DashManifest dashManifest,
            final Stream stream,
            final String cacheKey,
            final MediaItemTag metadata) {
        return dataSource.getYoutubeDashMediaSourceFactory().createMediaSource(dashManifest,
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    private static ProgressiveMediaSource buildYoutubeProgressiveMediaSource(
            final PlayerDataSource dataSource,
            final Stream stream,
            final String cacheKey,
            final MediaItemTag metadata) {
        return dataSource.getYoutubeProgressiveMediaSourceFactory()
                .createMediaSource(new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }
    //endregion


    //region Utils
    private static Uri manifestUrlToUri(final String manifestUrl) {
        return Uri.parse(Objects.requireNonNullElse(manifestUrl, ""));
    }

    private static void throwResolverExceptionIfUrlNullOrEmpty(@Nullable final String url)
            throws ResolverException {
        if (url == null) {
            throw new ResolverException("Null stream URL");
        } else if (url.isEmpty()) {
            throw new ResolverException("Empty stream URL");
        }
    }
    //endregion


    //region Resolver exception
    final class ResolverException extends Exception {
        public ResolverException(final String message) {
            super(message);
        }

        public ResolverException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
    //endregion
}
