package org.schabi.newpipe.player.helper

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.player.datasource.NonUriHlsDataSourceFactory
import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource
import java.io.File

class PlayerDataSource(context: Context,
                       transferListener: TransferListener) {
    private val progressiveLoadIntervalBytes: Int

    // Generic Data Source Factories (without or with cache)
    private val cachelessDataSourceFactory: DataSource.Factory
    private val cacheDataSourceFactory: CacheFactory

    // YouTube-specific Data Source Factories (with cache)
    // They use YoutubeHttpDataSource.Factory, with different parameters each
    private val ytHlsCacheDataSourceFactory: CacheFactory
    private val ytDashCacheDataSourceFactory: CacheFactory
    private val ytProgressiveDashCacheDataSourceFactory: CacheFactory

    init {
        progressiveLoadIntervalBytes = PlayerHelper.getProgressiveLoadIntervalBytes(context)

        // make sure the static cache was created: needed by CacheFactories below
        instantiateCacheIfNeeded(context)

        // generic data source factories use DefaultHttpDataSource.Factory
        cachelessDataSourceFactory = DefaultDataSource.Factory(context,
                DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.Companion.USER_AGENT))
                .setTransferListener(transferListener)
        cacheDataSourceFactory = CacheFactory(context, transferListener, cache,
                DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.Companion.USER_AGENT))

        // YouTube-specific data source factories use getYoutubeHttpDataSourceFactory()
        ytHlsCacheDataSourceFactory = CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, false))
        ytDashCacheDataSourceFactory = CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(true, true))
        ytProgressiveDashCacheDataSourceFactory = CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, true))

        // set the maximum size to manifest creators
        YoutubeProgressiveDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE)
        YoutubeOtfDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE)
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().setMaximumSize(
                MAX_MANIFEST_CACHE_SIZE)
    }

    val liveSsMediaSourceFactory: SsMediaSource.Factory
        //region Live media source factories
        get() {
            return sSMediaSourceFactory.setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS.toLong())
        }
    val liveHlsMediaSourceFactory: HlsMediaSource.Factory
        get() {
            return HlsMediaSource.Factory(cachelessDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setPlaylistTrackerFactory(HlsPlaylistTracker.Factory({ dataSourceFactory: HlsDataSourceFactory?, loadErrorHandlingPolicy: LoadErrorHandlingPolicy?, playlistParserFactory: HlsPlaylistParserFactory? ->
                        DefaultHlsPlaylistTracker((dataSourceFactory)!!, (loadErrorHandlingPolicy)!!,
                                (playlistParserFactory)!!,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT)
                    }))
        }
    val liveDashMediaSourceFactory: DashMediaSource.Factory
        get() {
            return DashMediaSource.Factory(
                    getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                    cachelessDataSourceFactory)
        }

    //endregion
    //region Generic media source factories
    fun getHlsMediaSourceFactory(
            hlsDataSourceFactoryBuilder: NonUriHlsDataSourceFactory.Builder?): HlsMediaSource.Factory {
        if (hlsDataSourceFactoryBuilder != null) {
            hlsDataSourceFactoryBuilder.setDataSourceFactory(cacheDataSourceFactory)
            return HlsMediaSource.Factory(hlsDataSourceFactoryBuilder.build())
        }
        return HlsMediaSource.Factory(cacheDataSourceFactory)
    }

    val dashMediaSourceFactory: DashMediaSource.Factory
        get() {
            return DashMediaSource.Factory(
                    getDefaultDashChunkSourceFactory(cacheDataSourceFactory),
                    cacheDataSourceFactory)
        }
    val progressiveMediaSourceFactory: ProgressiveMediaSource.Factory
        get() {
            return ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes)
        }
    val sSMediaSourceFactory: SsMediaSource.Factory
        get() {
            return SsMediaSource.Factory(
                    DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
                    cachelessDataSourceFactory)
        }
    val singleSampleMediaSourceFactory: SingleSampleMediaSource.Factory
        get() {
            return SingleSampleMediaSource.Factory(cacheDataSourceFactory)
        }
    val youtubeHlsMediaSourceFactory: HlsMediaSource.Factory
        //endregion
        get() {
            return HlsMediaSource.Factory(ytHlsCacheDataSourceFactory)
        }
    val youtubeDashMediaSourceFactory: DashMediaSource.Factory
        get() {
            return DashMediaSource.Factory(
                    getDefaultDashChunkSourceFactory(ytDashCacheDataSourceFactory),
                    ytDashCacheDataSourceFactory)
        }
    val youtubeProgressiveMediaSourceFactory: ProgressiveMediaSource.Factory
        get() {
            return ProgressiveMediaSource.Factory(ytProgressiveDashCacheDataSourceFactory)
                    .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes)
        }

    companion object {
        val TAG: String = PlayerDataSource::class.java.getSimpleName()
        val LIVE_STREAM_EDGE_GAP_MILLIS: Int = 10000

        /**
         * An approximately 4.3 times greater value than the
         * [default][DefaultHlsPlaylistTracker.DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT]
         * to ensure that (very) low latency livestreams which got stuck for a moment don't crash too
         * early.
         */
        private val PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT: Double = 15.0

        /**
         * The maximum number of generated manifests per cache, in
         * [YoutubeProgressiveDashManifestCreator], [YoutubeOtfDashManifestCreator] and
         * [YoutubePostLiveStreamDvrDashManifestCreator].
         */
        private val MAX_MANIFEST_CACHE_SIZE: Int = 500

        /**
         * The folder name in which the ExoPlayer cache will be written.
         */
        private val CACHE_FOLDER_NAME: String = "exoplayer"

        /**
         * The [SimpleCache] instance which will be used to build
         * [com.google.android.exoplayer2.upstream.cache.CacheDataSource]s instances (with
         * [CacheFactory]).
         */
        private var cache: SimpleCache? = null

        //endregion
        //region Static methods
        private fun getDefaultDashChunkSourceFactory(
                dataSourceFactory: DataSource.Factory): DefaultDashChunkSource.Factory {
            return DefaultDashChunkSource.Factory(dataSourceFactory)
        }

        private fun getYoutubeHttpDataSourceFactory(
                rangeParameterEnabled: Boolean,
                rnParameterEnabled: Boolean): YoutubeHttpDataSource.Factory? {
            return YoutubeHttpDataSource.Factory()
                    .setRangeParameterEnabled(rangeParameterEnabled)
                    .setRnParameterEnabled(rnParameterEnabled)
        }

        private fun instantiateCacheIfNeeded(context: Context) {
            if (cache == null) {
                val cacheDir: File = File(context.getExternalCacheDir(), CACHE_FOLDER_NAME)
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, "instantiateCacheIfNeeded: cacheDir = " + cacheDir.getAbsolutePath())
                }
                if (!cacheDir.exists() && !cacheDir.mkdir()) {
                    Log.w(TAG, "instantiateCacheIfNeeded: could not create cache dir")
                }
                val evictor: LeastRecentlyUsedCacheEvictor = LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize())
                cache = SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
            }
        } //endregion
    }
}
