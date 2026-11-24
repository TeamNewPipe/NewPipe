package org.schabi.newpipe.player.helper

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache

class CacheFactory @JvmOverloads constructor(
    private val context: Context,
    private val transferListener: TransferListener?,
    private val cache: SimpleCache,
    private val upstreamDataSourceFactory: DataSource.Factory,
    private val cacheKeyFactory: CacheKeyFactory = CacheKeyFactory.DEFAULT
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val dataSource = DefaultDataSource.Factory(context, upstreamDataSourceFactory)
            .setTransferListener(transferListener)
            .createDataSource()
        val fileSource = FileDataSource()
        val dataSink = CacheDataSink(cache, PlayerHelper.getPreferredFileSize())

        return CacheDataSource(
            cache,
            dataSource,
            fileSource,
            dataSink,
            CACHE_FLAGS,
            null,
            cacheKeyFactory
        )
    }

    companion object {
        private const val CACHE_FLAGS = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
    }
}
