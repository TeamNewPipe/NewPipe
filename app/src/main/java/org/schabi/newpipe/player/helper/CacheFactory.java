package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource;

/* package-private */ final class CacheFactory implements DataSource.Factory {
    private static final int CACHE_FLAGS = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR;

    private final long maxFileSize;
    private final Context context;
    private final String userAgent;
    private final TransferListener transferListener;
    private final DataSource.Factory upstreamDataSourceFactory;
    private final SimpleCache simpleCache;

    public static class Builder {
        private final Context context;
        private final String userAgent;
        private final TransferListener transferListener;
        private DataSource.Factory upstreamDataSourceFactory;
        private SimpleCache simpleCache;

        Builder(@NonNull final Context context,
                @NonNull final String userAgent,
                @NonNull final TransferListener transferListener) {
            this.context = context;
            this.userAgent = userAgent;
            this.transferListener = transferListener;
        }

        public void setUpstreamDataSourceFactory(
                @Nullable final DataSource.Factory upstreamDataSourceFactory) {
            this.upstreamDataSourceFactory = upstreamDataSourceFactory;
        }

        public void setSimpleCache(@NonNull final SimpleCache simpleCache) {
            this.simpleCache = simpleCache;
        }

        public CacheFactory build() {
            if (simpleCache == null) {
                throw new IllegalStateException("No SimpleCache instance has been specified. "
                        + "Please specify one with setSimpleCache");
            }
            return new CacheFactory(context, userAgent, transferListener, simpleCache,
                    upstreamDataSourceFactory);
        }
    }

    private CacheFactory(@NonNull final Context context,
                         @NonNull final String userAgent,
                         @NonNull final TransferListener transferListener,
                         @NonNull final SimpleCache simpleCache,
                         @Nullable final DataSource.Factory upstreamDataSourceFactory) {
        this.context = context;
        this.userAgent = userAgent;
        this.transferListener = transferListener;
        this.simpleCache = simpleCache;
        this.upstreamDataSourceFactory = upstreamDataSourceFactory;

        maxFileSize = PlayerHelper.getPreferredFileSize();
    }

    @NonNull
    @Override
    public DataSource createDataSource() {

        final DataSource.Factory upstreamDataSourceFactoryToUse;
        if (upstreamDataSourceFactory == null) {
            upstreamDataSourceFactoryToUse = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent);
        } else {
            if (upstreamDataSourceFactory instanceof DefaultHttpDataSource.Factory) {
                upstreamDataSourceFactoryToUse =
                        ((DefaultHttpDataSource.Factory) upstreamDataSourceFactory)
                                .setUserAgent(userAgent);
            } else if (upstreamDataSourceFactory instanceof YoutubeHttpDataSource.Factory) {
                upstreamDataSourceFactoryToUse =
                        ((YoutubeHttpDataSource.Factory) upstreamDataSourceFactory)
                                .setUserAgentForNonMobileStreams(userAgent);
            } else {
                upstreamDataSourceFactoryToUse = upstreamDataSourceFactory;
            }
        }

        final DefaultDataSource dataSource = new DefaultDataSource.Factory(context,
                upstreamDataSourceFactoryToUse)
                .setTransferListener(transferListener)
                .createDataSource();

        final FileDataSource fileSource = new FileDataSource();
        final CacheDataSink dataSink = new CacheDataSink(simpleCache, maxFileSize);
        return new CacheDataSource(simpleCache, dataSource, fileSource, dataSink, CACHE_FLAGS,
                null);
    }
}
