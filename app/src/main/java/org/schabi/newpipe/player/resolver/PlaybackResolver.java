package org.schabi.newpipe.player.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;

import org.schabi.newpipe.extractor.stream.DeliveryFormat;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {

    @Nullable
    default MediaSource maybeBuildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                  @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM || streamType == StreamType.LIVE_STREAM)) {
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
        final Uri uri = Uri.parse(sourceUrl);
        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_DASH:
                return dataSource.getLiveDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            case C.TYPE_HLS:
                return dataSource.getLiveHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    @NonNull
    default MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                         @NonNull final Stream sourceStream,
                                         @NonNull final String cacheKey,
                                         @NonNull final String overrideExtension,
                                         @NonNull final MediaSourceTag metadata) {
        final DeliveryFormat deliveryFormat = sourceStream.getDeliveryFormat();
        if (deliveryFormat instanceof DeliveryFormat.Direct) {
            final String url = ((DeliveryFormat.Direct) deliveryFormat).getUrl();
            return dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                    .createMediaSource(Uri.parse(url));

        } else if (deliveryFormat instanceof DeliveryFormat.HLS) {
            final String url = ((DeliveryFormat.HLS) deliveryFormat).getUrl();
            return dataSource.getHlsMediaSourceFactory().setTag(metadata)
                    .createMediaSource(Uri.parse(url));

        } else if (deliveryFormat instanceof DeliveryFormat.ManualDASH) {
            final DashManifest dashManifest;
            try {
                final DeliveryFormat.ManualDASH manualDash =
                        (DeliveryFormat.ManualDASH) deliveryFormat;
                final ByteArrayInputStream dashManifestInput = new ByteArrayInputStream(
                        manualDash.getManualDashManifest().getBytes("UTF-8"));

                dashManifest = new DashManifestParser().parse(Uri.parse(manualDash.getBaseUrl()),
                        dashManifestInput);
            } catch (IOException e) {
                throw new IllegalStateException("Error while parsing manual dash manifest", e);
            }

            return dataSource.getDashMediaSourceFactory().setTag(metadata)
                    .createMediaSource(dashManifest);
        } else {
            throw new IllegalArgumentException("Unsupported delivery format: " + deliveryFormat);
        }
    }
}
