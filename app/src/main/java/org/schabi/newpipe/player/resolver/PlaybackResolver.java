package org.schabi.newpipe.player.resolver;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.util.StreamTypeUtil;

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
                                         @NonNull final String sourceUrl,
                                         @NonNull final String cacheKey,
                                         @NonNull final String overrideExtension,
                                         @NonNull final MediaSourceTag metadata) {
        final Uri uri = Uri.parse(sourceUrl);
        @C.ContentType final int type = TextUtils.isEmpty(overrideExtension)
                ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);

        final MediaSourceFactory factory;
        switch (type) {
            case C.TYPE_SS:
                factory = dataSource.getLiveSsMediaSourceFactory();
                break;
            case C.TYPE_DASH:
                factory = dataSource.getDashMediaSourceFactory();
                break;
            case C.TYPE_HLS:
                factory = dataSource.getHlsMediaSourceFactory();
                break;
            case C.TYPE_OTHER:
                factory = dataSource.getExtractorMediaSourceFactory();
                break;
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }

        return factory.createMediaSource(
                new MediaItem.Builder()
                    .setTag(metadata)
                    .setUri(uri)
                    .setCustomCacheKey(cacheKey)
                    .build()
        );
    }
}
