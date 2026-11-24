package org.schabi.newpipe.player.helper;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.SingleSampleMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker;
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.SimpleCache;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeAdaptiveDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.player.datasource.NonUriHlsDataSourceFactory;
import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource;
import org.schabi.newpipe.player.datasource.InvidiousDataSource;

import java.io.File;
import java.util.Map;

public class PlayerDataSource {
    public static final String TAG = PlayerDataSource.class.getSimpleName();

    public static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    /**
     * An approximately 4.3 times greater value than the
     * {@link DefaultHlsPlaylistTracker#DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT default}
     * to ensure that (very) low latency livestreams which got stuck for a moment don't crash too
     * early.
     */
    private static final double PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15;

    private static final int YOUTUBE_CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int YOUTUBE_READ_TIMEOUT_MILLIS = 30_000;

    /**
     * The maximum number of generated manifests per cache, in
     * {@link YoutubeProgressiveDashManifestCreator}, {@link YoutubeOtfDashManifestCreator} and
     * {@link YoutubePostLiveStreamDvrDashManifestCreator}.
     */
    private static final int MAX_MANIFEST_CACHE_SIZE = 500;

    /**
     * The folder name in which the ExoPlayer cache will be written.
     */
    private static final String CACHE_FOLDER_NAME = "exoplayer";

    /**
     * The {@link SimpleCache} instance which will be used to build
     * {@link androidx.media3.datasource.cache.CacheDataSource}s instances (with
     * {@link CacheFactory}).
     */
    private static SimpleCache cache;


    private final int progressiveLoadIntervalBytes;
    private final Context context;
    private final org.schabi.newpipe.player.LivePlaybackMode livePlaybackMode;
    @Nullable
    private final TransferListener transferListener;

    // Generic Data Source Factories (without or with cache)
    private final DataSource.Factory cachelessDataSourceFactory;
    private final CacheFactory cacheDataSourceFactory;
    private final CacheFactory bilibiliCacheDataSourceFactory;

    // YouTube-specific Data Source Factories (with cache)
    // They use YoutubeHttpDataSource.Factory, with different parameters each
    private final CacheFactory ytHlsCacheDataSourceFactory;
    private final CacheFactory ytDashCacheDataSourceFactory;
    private final CacheFactory ytProgressiveDashCacheDataSourceFactory;


    public boolean isNormalLive(final String streamUrl) {
        return livePlaybackMode.isNormal(streamUrl);
    }

    public void setNormalLive(final String streamUrl, final boolean normal) {
        livePlaybackMode.setNormal(streamUrl, normal);
    }

    public PlayerDataSource(final Context context,
                            final TransferListener transferListener) {
        this.context = context.getApplicationContext();
        livePlaybackMode = new org.schabi.newpipe.player.LivePlaybackMode(context);
        this.transferListener = transferListener;
        progressiveLoadIntervalBytes = PlayerHelper.getProgressiveLoadIntervalBytes(context);

        // make sure the static cache was created: needed by CacheFactories below
        instantiateCacheIfNeeded(context);

        // generic data source factories use DefaultHttpDataSource.Factory
        cachelessDataSourceFactory = new DefaultDataSource.Factory(context,
                getGenericHttpDataSourceFactory())
                .setTransferListener(transferListener);
        cacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getGenericHttpDataSourceFactory());
        bilibiliCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getGenericHttpDataSourceFactory()
                        .setDefaultRequestProperties(getBilibiliPlaybackHeaders()));

        // YouTube-specific data source factories use getYoutubeHttpDataSourceFactory()
        ytHlsCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, false));
        ytDashCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(true, true));
        ytProgressiveDashCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, true));

        // set the maximum size to manifest creators
        YoutubeProgressiveDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE);
        YoutubeAdaptiveDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE);
        YoutubeOtfDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE);
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().setMaximumSize(
                MAX_MANIFEST_CACHE_SIZE);
    }


    //region Live media source factories
    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        return getSSMediaSourceFactory().setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        return getLiveHlsMediaSourceFactory(false);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory(final boolean restrictInstance) {
        return new HlsMediaSource.Factory(restrictInstance
                ? InvidiousDataSource.restrict(cachelessDataSourceFactory)
                        : cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setPlaylistTrackerFactory((dataSourceFactory, loadErrorHandlingPolicy,
                                            playlistParserFactory, cmcdConfiguration,
                                            downloadExecutorSupplier) ->
                        new DefaultHlsPlaylistTracker(dataSourceFactory, loadErrorHandlingPolicy,
                                playlistParserFactory, cmcdConfiguration,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT,
                                downloadExecutorSupplier));
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        return getLiveDashMediaSourceFactory(false);
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory(final boolean restrictInstance) {
        final DataSource.Factory factory = restrictInstance
                ? InvidiousDataSource.restrict(cachelessDataSourceFactory)
                        : cachelessDataSourceFactory;
        final DashMediaSource.Factory mediaSourceFactory = new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(factory), factory);
        if (restrictInstance) {
            mediaSourceFactory.setManifestParser(new InvidiousDashManifestParser());
        }
        return mediaSourceFactory;
    }

    public DashMediaSource.Factory getLiveYoutubeDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                cachelessDataSourceFactory)
                .setManifestParser(new YoutubeDashLiveManifestParser());
    }
    //endregion


    //region Generic media source factories
    /**
     * Creates a separate HTTP factory and profile cache namespace for a plugin source.
     *
     * @param headers request headers for this source
     * @param cacheable whether the source may use the current profile's media cache
     * @return a data source factory with the requested headers and cache policy
     */
    public DataSource.Factory getPluginDataSourceFactory(final Map<String, String> headers,
                                                         final boolean cacheable) {
        final HttpDataSource.Factory httpFactory = getGenericHttpDataSourceFactory()
                .setDefaultRequestProperties(headers);
        if (!cacheable) {
            return new DefaultDataSource.Factory(context, httpFactory)
                    .setTransferListener(transferListener);
        }
        final String prefix = "plugin:"
                + org.schabi.newpipe.profiles.ProfileManager.getActiveProfileId(context) + ":";
        return new CacheFactory(context, transferListener, cache, httpFactory,
                dataSpec -> prefix + CacheKeyFactory.DEFAULT.buildCacheKey(dataSpec));
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory(
            @Nullable final NonUriHlsDataSourceFactory.Builder hlsDataSourceFactoryBuilder) {
        return getHlsMediaSourceFactory(hlsDataSourceFactoryBuilder, false);
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory(
            @Nullable final NonUriHlsDataSourceFactory.Builder hlsDataSourceFactoryBuilder,
            final boolean restrictInstance) {
        final DataSource.Factory factory = restrictInstance
                ? InvidiousDataSource.restrict(cacheDataSourceFactory) : cacheDataSourceFactory;
        if (hlsDataSourceFactoryBuilder != null) {
            hlsDataSourceFactoryBuilder.setDataSourceFactory(factory);
            return new HlsMediaSource.Factory(hlsDataSourceFactoryBuilder.build());
        }

        return new HlsMediaSource.Factory(factory);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        return getDashMediaSourceFactory(false);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory(final boolean restrictInstance) {
        final DataSource.Factory factory = restrictInstance
                ? InvidiousDataSource.restrict(cacheDataSourceFactory) : cacheDataSourceFactory;
        final DashMediaSource.Factory mediaSourceFactory = new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(factory), factory);
        if (restrictInstance) {
            mediaSourceFactory.setManifestParser(new InvidiousDashManifestParser());
        }
        return mediaSourceFactory;
    }

    public HlsMediaSource.Factory getNiconicoHlsMediaSourceFactory(final String cookie) {
        // Each media source owns its cookie, including playlist, segment and AES key requests.
        // Do not mutate a shared HTTP factory: another queued video may have a different session.
        final HttpDataSource.Factory httpFactory = getGenericHttpDataSourceFactory()
                .setDefaultRequestProperties(Map.of(
                        "Cookie", cookie,
                        "Referer", "https://www.nicovideo.jp/",
                        "Origin", "https://www.nicovideo.jp"));
        return new HlsMediaSource.Factory(
                new CacheFactory(context, transferListener, cache, httpFactory));
    }

    public ProgressiveMediaSource.Factory getProgressiveMediaSourceFactory() {
        return getProgressiveMediaSourceFactory(false);
    }

    public ProgressiveMediaSource.Factory getProgressiveMediaSourceFactory(
            final boolean restrictInstance) {
        return new ProgressiveMediaSource.Factory(restrictInstance
                ? InvidiousDataSource.restrict(cacheDataSourceFactory) : cacheDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes);
    }

    public ProgressiveMediaSource.Factory getBilibiliProgressiveMediaSourceFactory() {
        return new ProgressiveMediaSource.Factory(bilibiliCacheDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes);
    }

    public SsMediaSource.Factory getSSMediaSourceFactory() {
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
                cachelessDataSourceFactory);
    }

    public SingleSampleMediaSource.Factory getSingleSampleMediaSourceFactory() {
        return getSingleSampleMediaSourceFactory(false);
    }

    public SingleSampleMediaSource.Factory getSingleSampleMediaSourceFactory(
            final boolean restrictInstance) {
        return new SingleSampleMediaSource.Factory(restrictInstance
                ? InvidiousDataSource.restrict(cacheDataSourceFactory) : cacheDataSourceFactory);
    }
    //endregion


    //region YouTube media source factories
    public HlsMediaSource.Factory getYoutubeHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(ytHlsCacheDataSourceFactory);
    }

    public DashMediaSource.Factory getYoutubeDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(ytDashCacheDataSourceFactory),
                ytDashCacheDataSourceFactory);
    }

    public ProgressiveMediaSource.Factory getYoutubeProgressiveMediaSourceFactory() {
        return new ProgressiveMediaSource.Factory(ytProgressiveDashCacheDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes);
    }
    //endregion


    //region Static methods
    private static DefaultDashChunkSource.Factory getDefaultDashChunkSourceFactory(
            final DataSource.Factory dataSourceFactory) {
        return new DefaultDashChunkSource.Factory(dataSourceFactory);
    }

    /**
     * Invalidates generated YouTube manifests after a media URL is rejected.
     *
     * <p>Refreshing {@code StreamInfo} alone is not sufficient for generated progressive, OTF,
     * or post-live DASH manifests: their cached XML can still reference the rejected redirected
     * CDN URL. Clearing these small in-memory caches ensures that the next recovery rebuilds the
     * manifest from the refreshed extractor response.</p>
     */
    public static void invalidateYoutubeManifestCaches() {
        YoutubeProgressiveDashManifestCreator.getCache().clear();
        YoutubeAdaptiveDashManifestCreator.getCache().clear();
        YoutubeOtfDashManifestCreator.getCache().clear();
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().clear();
    }

    static Map<String, String> getBilibiliPlaybackHeaders() {
        return Map.of(
                "Referer", BilibiliService.WWW_REFERER,
                "Accept-Language", "zh-CN,zh;q=0.9");
    }

    private static HttpDataSource.Factory getGenericHttpDataSourceFactory() {
        if (InvidiousBackend.isEnabled()) {
            return new YoutubeHttpDataSource.Factory()
                    .setRangeParameterEnabled(false)
                    .setRnParameterEnabled(false);
        }
        return new DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT);
    }

    private static YoutubeHttpDataSource.Factory getYoutubeHttpDataSourceFactory(
            final boolean rangeParameterEnabled,
            final boolean rnParameterEnabled) {
        return new YoutubeHttpDataSource.Factory()
                .setConnectTimeoutMs(YOUTUBE_CONNECT_TIMEOUT_MILLIS)
                .setReadTimeoutMs(YOUTUBE_READ_TIMEOUT_MILLIS)
                .setRangeParameterEnabled(rangeParameterEnabled)
                .setRnParameterEnabled(rnParameterEnabled);
    }

    private static void instantiateCacheIfNeeded(final Context context) {
        if (cache == null) {
            final File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
            if (DEBUG) {
                Log.d(TAG, "instantiateCacheIfNeeded: cacheDir = " + cacheDir.getAbsolutePath());
            }
            if (!cacheDir.exists() && !cacheDir.mkdir()) {
                Log.w(TAG, "instantiateCacheIfNeeded: could not create cache dir");
            }

            final LeastRecentlyUsedCacheEvictor evictor =
                    new LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize());
            cache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
        }
    }
    //endregion
}
