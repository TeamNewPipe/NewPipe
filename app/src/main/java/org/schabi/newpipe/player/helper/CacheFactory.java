package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

final class CacheFactory implements DataSource.Factory {
    private static final int CACHE_FLAGS = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR;

    private final Context context;
    private final TransferListener transferListener;
    private final DataSource.Factory upstreamDataSourceFactory;
    private final SimpleCache cache;

    CacheFactory(final Context context,
                 final TransferListener transferListener,
                 final SimpleCache cache,
                 final DataSource.Factory upstreamDataSourceFactory) {
        this.context = context;
        this.transferListener = transferListener;
        this.cache = cache;
        this.upstreamDataSourceFactory = upstreamDataSourceFactory;
    }

    @NonNull
    @Override
    public DataSource createDataSource() {
        final DefaultDataSource dataSource = new DefaultDataSource.Factory(context,
                upstreamDataSourceFactory)
                .setTransferListener(transferListener)
                .createDataSource();

        final FileDataSource fileSource = new FileDataSource();
        final CacheDataSink dataSink =
                new CacheDataSink(cache, PlayerHelper.getPreferredFileSize());
        return new CacheDataSource(cache, dataSource, fileSource, dataSink, CACHE_FLAGS, null);
    }
}
