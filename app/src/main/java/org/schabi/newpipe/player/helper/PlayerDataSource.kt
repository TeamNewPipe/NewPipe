@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.player.datasource.NonUriHlsDataSourceFactory
import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource

class PlayerDataSource(context: Context, transferListener: TransferListener?) {

    private val progressiveLoadIntervalBytes: Int

    private val cachelessDataSourceFactory: DataSource.Factory
    private val cacheDataSourceFactory: CacheFactory

    private val ytHlsCacheDataSourceFactory: CacheFactory
    private val ytDashCacheDataSourceFactory: CacheFactory
    private val ytProgressiveDashCacheDataSourceFactory: CacheFactory

    init {
        progressiveLoadIntervalBytes = PlayerHelper.getProgressiveLoadIntervalBytes(context)

        instantiateCacheIfNeeded(context)

        cachelessDataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT)
        ).setTransferListener(transferListener)

        cacheDataSourceFactory = CacheFactory(
            context,
            transferListener,
            cache!!,
            DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT)
        )

        ytHlsCacheDataSourceFactory = CacheFactory(
            context,
            transferListener,
            cache!!,
            getYoutubeHttpDataSourceFactory(false, false)
        )
        ytDashCacheDataSourceFactory = CacheFactory(
            context,
            transferListener,
            cache!!,
            getYoutubeHttpDataSourceFactory(true, true)
        )
        ytProgressiveDashCacheDataSourceFactory = CacheFactory(
            context,
            transferListener,
            cache!!,
            getYoutubeHttpDataSourceFactory(false, true)
        )

        YoutubeProgressiveDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE)
        YoutubeOtfDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE)
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE)
    }

    val liveSsMediaSourceFactory: SsMediaSource.Factory
        get() = ssMediaSourceFactory.setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS.toLong())

    val liveHlsMediaSourceFactory: HlsMediaSource.Factory
        get() = HlsMediaSource.Factory(cachelessDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setPlaylistTrackerFactory { dataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory ->
                DefaultHlsPlaylistTracker(
                    dataSourceFactory,
                    loadErrorHandlingPolicy,
                    playlistParserFactory,
                    PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT
                )
            }

    val liveDashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(
            getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
            cachelessDataSourceFactory
        )

    val liveYoutubeDashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(
            getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
            cachelessDataSourceFactory
        ).setManifestParser(YoutubeDashLiveManifestParser())

    fun getHlsMediaSourceFactory(
        hlsDataSourceFactoryBuilder: NonUriHlsDataSourceFactory.Builder?
    ): HlsMediaSource.Factory {
        if (hlsDataSourceFactoryBuilder != null) {
            hlsDataSourceFactoryBuilder.setDataSourceFactory(cacheDataSourceFactory)
            return HlsMediaSource.Factory(hlsDataSourceFactoryBuilder.build())
        }

        return HlsMediaSource.Factory(cacheDataSourceFactory)
    }

    val dashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(
            getDefaultDashChunkSourceFactory(cacheDataSourceFactory),
            cacheDataSourceFactory
        )

    val progressiveMediaSourceFactory: ProgressiveMediaSource.Factory
        get() = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes)

    val ssMediaSourceFactory: SsMediaSource.Factory
        get() = SsMediaSource.Factory(
            DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
            cachelessDataSourceFactory
        )

    val singleSampleMediaSourceFactory: SingleSampleMediaSource.Factory
        get() = SingleSampleMediaSource.Factory(cacheDataSourceFactory)

    val youtubeHlsMediaSourceFactory: HlsMediaSource.Factory
        get() = HlsMediaSource.Factory(ytHlsCacheDataSourceFactory)

    val youtubeDashMediaSourceFactory: DashMediaSource.Factory
        get() = DashMediaSource.Factory(
            getDefaultDashChunkSourceFactory(ytDashCacheDataSourceFactory),
            ytDashCacheDataSourceFactory
        )

    val youtubeProgressiveMediaSourceFactory: ProgressiveMediaSource.Factory
        get() = ProgressiveMediaSource.Factory(ytProgressiveDashCacheDataSourceFactory)
            .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes)

    companion object {
        @JvmField
        val TAG = PlayerDataSource::class.java.simpleName

        const val LIVE_STREAM_EDGE_GAP_MILLIS = 10000

        private const val PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15.0

        private const val MAX_MANIFEST_CACHE_SIZE = 500

        private const val CACHE_FOLDER_NAME = "exoplayer"

        @JvmStatic
        var cache: SimpleCache? = null
            private set

        private fun getDefaultDashChunkSourceFactory(
            dataSourceFactory: DataSource.Factory
        ): DefaultDashChunkSource.Factory {
            return DefaultDashChunkSource.Factory(dataSourceFactory)
        }

        private fun getYoutubeHttpDataSourceFactory(
            rangeParameterEnabled: Boolean,
            rnParameterEnabled: Boolean
        ): YoutubeHttpDataSource.Factory {
            return YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(rangeParameterEnabled)
                .setRnParameterEnabled(rnParameterEnabled)
        }

        private fun instantiateCacheIfNeeded(context: Context) {
            if (cache == null) {
                val cacheDir = File(context.externalCacheDir, CACHE_FOLDER_NAME)
                if (DEBUG) {
                    Log.d(TAG, "instantiateCacheIfNeeded: cacheDir = " + cacheDir.absolutePath)
                }
                if (!cacheDir.exists() && !cacheDir.mkdir()) {
                    Log.w(TAG, "instantiateCacheIfNeeded: could not create cache dir")
                }

                val evictor = LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize())
                cache = SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
            }
        }
    }
}
