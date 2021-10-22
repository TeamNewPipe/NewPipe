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
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY))
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                    .setExtractorFactory(MediaParserHlsMediaChunkExtractor.FACTORY)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
        } else {
            return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
        }
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                    MediaParserChunkExtractor.FACTORY,
                    cachelessDataSourceFactory, 1), cachelessDataSourceFactory)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
        } else {
            return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                    cachelessDataSourceFactory), cachelessDataSourceFactory)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
        }
    }

    public SsMediaSource.Factory getSsMediaSourceFactory() {
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                cacheDataSourceFactory), cacheDataSourceFactory);
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new HlsMediaSource.Factory(cacheDataSourceFactory)
                    .setExtractorFactory(MediaParserHlsMediaChunkExtractor.FACTORY);
        } else {
            return new HlsMediaSource.Factory(cacheDataSourceFactory);
        }
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                    MediaParserChunkExtractor.FACTORY,
                    cacheDataSourceFactory, 1), cacheDataSourceFactory);
        } else {
            return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                    cacheDataSourceFactory), cacheDataSourceFactory);
        }
    }

    public ProgressiveMediaSource.Factory getExtractorMediaSourceFactory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new ProgressiveMediaSource.Factory(cacheDataSourceFactory,
                    MediaParserExtractorAdapter.FACTORY)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(EXTRACTOR_MINIMUM_RETRY));
        } else {
            return new ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .setLoadErrorHandlingPolicy(
                            new DefaultLoadErrorHandlingPolicy(EXTRACTOR_MINIMUM_RETRY));
        }
    }

    public SingleSampleMediaSource.Factory getSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(cacheDataSourceFactory);
    }
}
