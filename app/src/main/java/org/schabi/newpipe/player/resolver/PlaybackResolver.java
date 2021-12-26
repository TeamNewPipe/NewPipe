package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {

    @Nullable
    default MediaSource maybeBuildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                  @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!StreamTypeUtil.isLiveStream(streamType)) {
            return null;
        }

        final MediaSourceTag tag = new MediaSourceTag(info);
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.TYPE_HLS, tag);
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getDashMpdUrl(), C.TYPE_DASH, tag);
        }

        return null;
    }

    @NonNull
    default MediaSource buildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                             @NonNull final String sourceUrl,
                                             @C.ContentType final int type,
                                             @NonNull final MediaSourceTag metadata) {
        final MediaSourceFactory factory;
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
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }

        return factory.createMediaSource(
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(sourceUrl))
                        .setLiveTargetOffsetMs(PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS)
                        .build()
        );
    }

    @NonNull
    default MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                         @NonNull final Stream stream,
                                         @NonNull final StreamInfo streamInfo,
                                         @NonNull final String cacheKey,
                                         @NonNull final MediaSourceTag metadata)
            throws IOException {
        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        if (deliveryMethod.equals(DeliveryMethod.PROGRESSIVE_HTTP)) {
            return buildProgressiveMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (deliveryMethod.equals(DeliveryMethod.HLS)) {
            return buildHlsMediaSource(dataSource, stream, cacheKey, metadata);
        } else if (deliveryMethod.equals(DeliveryMethod.DASH)) {
            return buildDashMediaSource(dataSource, stream, streamInfo, cacheKey, metadata);
        } else {
            throw new IllegalArgumentException("Unsupported delivery type" + deliveryMethod);
        }
    }

    default <T extends Stream> ProgressiveMediaSource buildProgressiveMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaSourceTag metadata) throws IOException {
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

    default <T extends Stream> DashMediaSource buildDashMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final StreamInfo streamInfo,
            @NonNull final String cacheKey,
            @NonNull final MediaSourceTag metadata) throws IOException {
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
            final String content;
            if (streamInfo.getService() == ServiceList.YouTube) {
                content = generateDashManifestOfYoutubeDashStream(streamInfo, stream);
            } else {
                // DASH manual streams from other stream services do not require manifest
                // generation
                content = stream.getContent();
            }
            return createDashMediaSource(dataSource, content, stream, cacheKey, metadata);
        }
    }

    default <T extends Stream> String generateDashManifestOfYoutubeDashStream(
            @NonNull final StreamInfo streamInfo,
            @NonNull final T stream) throws IOException {
        final String content;
        final StreamType streamType = streamInfo.getStreamType();

        if (!(stream instanceof AudioStream || stream instanceof VideoStream)) {
            throw new IOException("Try to generate a DASH manifest of a YouTube "
                    + stream.getClass() + " " + stream.getContent());
        }

        if (streamType == StreamType.VIDEO_STREAM) {
            // If the content is not an URL, uses the DASH delivery method and if the stream type
            // of the stream is a video stream, it means the content is an OTF stream so we need to
            // generate the manifest corresponding to the content (which is the base URL of the OTF
            // stream).

            try {
                content = YoutubeDashManifestCreator
                        .createDashManifestFromOtfStreamingUrl(stream.getContent(),
                                Objects.requireNonNull(stream.getItagItem()),
                                streamInfo.getDuration());
            } catch (final YoutubeDashManifestCreator.
                    YoutubeDashManifestCreationException | NullPointerException e) {
                throw new IOException("Error when generating the DASH manifest of "
                        + "YouTube OTF stream " + stream.getContent(), e);
            }
        } else if (streamType == StreamType.POST_LIVE_STREAM) {
            // If the content is not an URL, uses the DASH delivery method and if the stream type
            // of the stream is a post live stream, it means that the content is an ended
            // livestream so we need to generate the manifest corresponding to the content
            // (which is the last segment of the stream)

            try {
                final ItagItem itagItem = Objects.requireNonNull(stream.getItagItem());
                content = YoutubeDashManifestCreator
                        .createDashManifestFromPostLiveStreamDvrStreamingUrl(
                                stream.getContent(),
                                itagItem,
                                itagItem.getTargetDurationSec(),
                                streamInfo.getDuration());
            } catch (final YoutubeDashManifestCreator.
                    YoutubeDashManifestCreationException | NullPointerException e) {
                throw new IOException("Error when generating the DASH manifest of YouTube ended"
                        + "live stream " + stream.getContent(), e);
            }
        } else {
            throw new IllegalArgumentException("DASH manifest generation of YouTube livestreams is"
                    + "not supported");
        }
        return content;
    }

    default <T extends Stream> DashMediaSource createDashMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final String content,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaSourceTag metadata) throws IOException {
        final DashManifest dashManifest;
        try {
            final ByteArrayInputStream dashManifestInput = new ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8));
            String baseUrl = stream.getBaseUrl();
            if (baseUrl == null) {
                baseUrl = "";
            }

            dashManifest = new DashManifestParser().parse(Uri.parse(baseUrl),
                    dashManifestInput);
        } catch (final IOException e) {
            throw new IOException("Error when parsing manual DASH manifest", e);
        }

        return dataSource.getDashMediaSourceFactory().createMediaSource(dashManifest,
                new MediaItem.Builder()
                        .setTag(metadata)
                        .setUri(Uri.parse(stream.getContent()))
                        .setCustomCacheKey(cacheKey)
                        .build());
    }

    default <T extends Stream> HlsMediaSource buildHlsMediaSource(
            @NonNull final PlayerDataSource dataSource,
            @NonNull final T stream,
            @NonNull final String cacheKey,
            @NonNull final MediaSourceTag metadata) throws IOException {
        final boolean isUrlStream = stream.isUrl();
        if (isUrlStream && isNullOrEmpty(stream.getContent())) {
            throw new IOException("Try to generate a DASH media source from an empty string or "
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
            String baseUrl = stream.getBaseUrl();
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
                    new NewPipeHlsPlaylistParserFactory(hlsPlaylist))
                    .createMediaSource(new MediaItem.Builder()
                            .setTag(metadata)
                            .setUri(Uri.parse(stream.getContent()))
                            .setCustomCacheKey(cacheKey)
                            .build());
        }
    }
}
