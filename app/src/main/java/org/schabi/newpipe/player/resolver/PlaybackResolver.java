package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.extractor.stream.AudioStream.UNKNOWN_BITRATE;
import static org.schabi.newpipe.extractor.stream.VideoStream.RESOLUTION_UNKNOWN;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
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
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
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
import org.schabi.newpipe.player.helper.NonUriHlsPlaylistParserFactory;
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
            Objects.hash(stream.getContent(), stream.getManifestUrl());
        }

        return cacheKey;
    }

    /**
     * Builds the cache key of a video stream. A cache key is unique to the features of the
     * provided video stream, and when possible independent of <i>transient</i> parameters (such as
     * the url of the stream). This ensures that there are no conflicts, but also that the cache is
     * used as much as possible: the same cache should be used for two streams which have the same
     * features but e.g. a different url, since the url might have been reloaded in the meantime,
     * but the stream actually referenced by the url is still the same.
     *
     * @param info the stream info, to distinguish between streams with the same features but coming
     *             from different stream infos
     * @param videoStream the video stream for which the cache key should be created
     * @return a key to be used to store the cache of the provided video stream
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
     * Builds the cache key of an audio stream. A cache key is unique to the features of the
     * provided audio stream, and when possible independent of <i>transient</i> parameters (such as
     * the url of the stream). This ensures that there are no conflicts, but also that the cache is
     * used as much as possible: the same cache should be used for two streams which have the same
     * features but e.g. a different url, since the url might have been reloaded in the meantime,
     * but the stream actually referenced by the url is still the same.
     *
     * @param info the stream info, to distinguish between streams with the same features but coming
     *             from different stream infos
     * @param audioStream the audio stream for which the cache key should be created
     * @return a key to be used to store the cache of the provided audio stream
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
    //endregion


    //region Live media sources
    @Nullable
    static MediaSource maybeBuildLiveMediaSource(final PlayerDataSource dataSource,
                                                 final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!StreamTypeUtil.isLiveStream(streamType)) {
            return null;
        }

        final StreamInfoTag tag = StreamInfoTag.of(info);
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.TYPE_HLS, tag);
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getDashMpdUrl(), C.TYPE_DASH, tag);
        }

        return null;
    }

    static MediaSource buildLiveMediaSource(final PlayerDataSource dataSource,
                                            final String sourceUrl,
                                            @C.ContentType final int type,
                                            final MediaItemTag metadata) {
        final MediaSource.Factory factory;
        switch (type) {
            case C.TYPE_SS:
                factory = dataSource.getLiveSsMediaSourceFactory();
                break;
            case C.TYPE_DASH:
                factory = dataSource.getLiveDashMediaSourceFactory();
                break;
            case C.TYPE_HLS:
                factory = dataSource.getLiveHlsMediaSourceFactory();
                break;
            case C.TYPE_OTHER: case C.TYPE_RTSP: default:
                throw new IllegalStateException("Unsupported type: " + type);
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
                                        final MediaItemTag metadata)
            throws IOException {
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
                throw new IllegalArgumentException("Unsupported delivery type: " + deliveryMethod);
        }
    }

    private static ProgressiveMediaSource buildProgressiveMediaSource(
            final PlayerDataSource dataSource,
            final Stream stream,
            final String cacheKey,
            final MediaItemTag metadata) throws IOException {
        final String url = stream.getContent();

        if (isNullOrEmpty(url)) {
            throw new IOException(
                    "Try to generate a progressive media source from an empty string or from a "
                            + "null object");
        } else {
            return dataSource.getProgressiveMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(url))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    private static DashMediaSource buildDashMediaSource(final PlayerDataSource dataSource,
                                                        final Stream stream,
                                                        final String cacheKey,
                                                        final MediaItemTag metadata)
            throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate a DASH media source from an empty string or "
                    + "from a null object");
        }

        if (isUrlStream) {
            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            return dataSource.getDashMediaSourceFactory().createMediaSource(
                    createDashManifest(stream.getContent(), stream),
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(uri)
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    private static DashManifest createDashManifest(final String manifestContent,
                                                   final Stream stream) throws IOException {
        try {
            final ByteArrayInputStream dashManifestInput = new ByteArrayInputStream(
                    manifestContent.getBytes(StandardCharsets.UTF_8));
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            return new DashManifestParser().parse(Uri.parse(baseUrl), dashManifestInput);
        } catch (final IOException e) {
            throw new IOException("Error when parsing manual DASH manifest", e);
        }
    }

    private static HlsMediaSource buildHlsMediaSource(final PlayerDataSource dataSource,
                                                      final Stream stream,
                                                      final String cacheKey,
                                                      final MediaItemTag metadata)
            throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate an HLS media source from an empty string or "
                    + "from a null object");
        }

        if (isUrlStream) {
            return dataSource.getHlsMediaSourceFactory(null).createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            final HlsPlaylist hlsPlaylist;
            try {
                final ByteArrayInputStream hlsManifestInput = new ByteArrayInputStream(
                        stream.getContent().getBytes(StandardCharsets.UTF_8));
                hlsPlaylist = new HlsPlaylistParser().parse(uri, hlsManifestInput);
            } catch (final IOException e) {
                throw new IOException("Error when parsing manual HLS manifest", e);
            }

            return dataSource.getHlsMediaSourceFactory(
                    new NonUriHlsPlaylistParserFactory(hlsPlaylist))
                    .createMediaSource(new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }

    private static SsMediaSource buildSSMediaSource(final PlayerDataSource dataSource,
                                                    final Stream stream,
                                                    final String cacheKey,
                                                    final MediaItemTag metadata)
            throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate an SmoothStreaming media source from an empty "
                    + "string or from a null object");
        }

        if (isUrlStream) {
            return dataSource.getSSMediaSourceFactory().createMediaSource(
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        } else {
            String baseUrl = stream.getManifestUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            final Uri uri = Uri.parse(baseUrl);

            final SsManifest smoothStreamingManifest;
            try {
                final ByteArrayInputStream smoothStreamingManifestInput = new ByteArrayInputStream(
                        stream.getContent().getBytes(StandardCharsets.UTF_8));
                smoothStreamingManifest = new SsManifestParser().parse(uri,
                        smoothStreamingManifestInput);
            } catch (final IOException e) {
                throw new IOException("Error when parsing manual SmoothStreaming manifest", e);
            }

            return dataSource.getSSMediaSourceFactory().createMediaSource(
                    smoothStreamingManifest,
                    new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(uri)
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }
    //endregion


    //region YouTube media sources
    private static MediaSource createYoutubeMediaSource(final Stream stream,
                                                        final StreamInfo streamInfo,
                                                        final PlayerDataSource dataSource,
                                                        final String cacheKey,
                                                        final MediaItemTag metadata)
            throws IOException {
        if (!(stream instanceof AudioStream || stream instanceof VideoStream)) {
            throw new IOException("Try to generate a DASH manifest of a YouTube "
                    + stream.getClass() + " " + stream.getContent());
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
            } catch (final CreationException | NullPointerException e) {
                Log.e(TAG, "Error when generating the DASH manifest of YouTube ended live stream",
                        e);
                throw new IOException("Error when generating the DASH manifest of YouTube ended "
                        + "live stream " + stream.getContent(), e);
            }
        } else {
            throw new IllegalArgumentException("DASH manifest generation of YouTube livestreams is "
                    + "not supported");
        }
    }

    private static MediaSource createYoutubeMediaSourceOfVideoStreamType(
            final PlayerDataSource dataSource,
            final Stream stream,
            final StreamInfo streamInfo,
            final String cacheKey,
            final MediaItemTag metadata) throws IOException {
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
                } catch (final CreationException | NullPointerException e) {
                    Log.e(TAG,
                            "Error when generating the DASH manifest of YouTube OTF stream", e);
                    throw new IOException(
                            "Error when generating the DASH manifest of YouTube OTF stream "
                                    + stream.getContent(), e);
                }
            case HLS:
                return dataSource.getYoutubeHlsMediaSourceFactory().createMediaSource(
                        new MediaItem.Builder()
                                .setTag(metadata)
                                .setUri(Uri.parse(stream.getContent()))
                                .setCustomCacheKey(cacheKey)
                                .build());
            default:
                throw new IOException("Unsupported delivery method for YouTube contents: "
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
}
