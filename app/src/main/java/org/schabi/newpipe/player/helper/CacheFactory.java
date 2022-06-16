package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource;

import java.io.File;

/* package-private */ final class CacheFactory implements DataSource.Factory {
    private static final String TAG = CacheFactory.class.getSimpleName();

    private static final String CACHE_FOLDER_NAME = "exoplayer";
    private static final int CACHE_FLAGS = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR;
    private static SimpleCache cache;

    private final long maxFileSize;
    private final Context context;
    private final String userAgent;
    private final TransferListener transferListener;
    private final DataSource.Factory upstreamDataSourceFactory;

    public static class Builder {
        private final Context context;
        private final String userAgent;
        private final TransferListener transferListener;
        private DataSource.Factory upstreamDataSourceFactory;

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

        public CacheFactory build() {
            return new CacheFactory(context, userAgent, transferListener,
                    upstreamDataSourceFactory);
        }
    }

    private CacheFactory(@NonNull final Context context,
                         @NonNull final String userAgent,
                         @NonNull final TransferListener transferListener,
                         @Nullable final DataSource.Factory upstreamDataSourceFactory) {
        this.context = context;
        this.userAgent = userAgent;
        this.transferListener = transferListener;
        this.upstreamDataSourceFactory = upstreamDataSourceFactory;

        final File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
        if (!cacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdir();
        }

        if (cache == null) {
            final LeastRecentlyUsedCacheEvictor evictor
                    = new LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize());
            cache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
            Log.d(TAG, "initExoPlayerCache: cacheDir = " + cacheDir.getAbsolutePath());
        }

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
        final CacheDataSink dataSink = new CacheDataSink(cache, maxFileSize);
        return new CacheDataSource(cache, dataSource, fileSource, dataSink, CACHE_FLAGS, null);
    }
}
