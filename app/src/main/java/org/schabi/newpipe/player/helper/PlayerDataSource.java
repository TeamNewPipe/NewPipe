package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.MediaParserExtractorAdapter;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.chunk.MediaParserChunkExtractor;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.MediaParserHlsMediaChunkExtractor;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;

public class PlayerDataSource {
    private static final int MANIFEST_MINIMUM_RETRY = 5;
    private static final int EXTRACTOR_MINIMUM_RETRY = Integer.MAX_VALUE;
    public static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    private final DataSource.Factory cacheDataSourceFactory;
    private final DataSource.Factory cachelessDataSourceFactory;

    public PlayerDataSource(@NonNull final Context context, @NonNull final String userAgent,
                            @NonNull final TransferListener transferListener) {
        cacheDataSourceFactory = new CacheFactory(context, userAgent, transferListener);
        cachelessDataSourceFactory
                = new DefaultDataSourceFactory(context, userAgent, transferListener);
    }

    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
                cachelessDataSourceFactory
        )
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY))
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        final HlsMediaSource.Factory factory =
                new HlsMediaSource.Factory(cachelessDataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .setLoadErrorHandlingPolicy(
                                new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            factory.setExtractorFactory(MediaParserHlsMediaChunkExtractor.FACTORY);
        }

        return factory;
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                cachelessDataSourceFactory
        )
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
    }

    private DefaultDashChunkSource.Factory getDefaultDashChunkSourceFactory(
            final DataSource.Factory dataSourceFactory
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new DefaultDashChunkSource.Factory(
                    MediaParserChunkExtractor.FACTORY,
                    dataSourceFactory,
                    1
            );
        }

        return new DefaultDashChunkSource.Factory(dataSourceFactory);
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory() {
        final HlsMediaSource.Factory factory = new HlsMediaSource.Factory(cacheDataSourceFactory);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return factory;
        }

        // *** >= Android 11 / R / API 30 ***
        return factory.setExtractorFactory(MediaParserHlsMediaChunkExtractor.FACTORY);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cacheDataSourceFactory),
                cacheDataSourceFactory
        );
    }

    public ProgressiveMediaSource.Factory getExtractorMediaSourceFactory() {
        final ProgressiveMediaSource.Factory factory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            factory = new ProgressiveMediaSource.Factory(
                    cacheDataSourceFactory,
                    MediaParserExtractorAdapter.FACTORY
            );
        } else {
            factory = new ProgressiveMediaSource.Factory(cacheDataSourceFactory);
        }

        return factory.setLoadErrorHandlingPolicy(
                new DefaultLoadErrorHandlingPolicy(EXTRACTOR_MINIMUM_RETRY));
    }

    public SingleSampleMediaSource.Factory getSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(cacheDataSourceFactory);
    }
}
