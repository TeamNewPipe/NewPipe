package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.niconico.NicoWebSocketClient;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.services.niconico.extractors.NiconicoDMCPayloadBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;

import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource;

public class PlayerDataSource {

    public static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    /**
     * An approximately 4.3 times greater value than the
     * {@link DefaultHlsPlaylistTracker#DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT default}
     * to ensure that (very) low latency livestreams which got stuck for a moment don't crash too
     * early.
     */
    private static final double PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15;

    /**
     * The maximum number of generated manifests per cache, in
     * {@link YoutubeProgressiveDashManifestCreator}, {@link YoutubeOtfDashManifestCreator} and
     * {@link YoutubePostLiveStreamDvrDashManifestCreator}.
     */
    private static final int MAXIMUM_SIZE_CACHED_GENERATED_MANIFESTS_PER_CACHE = 500;

    private final int continueLoadingCheckIntervalBytes;
    private final CacheFactory.Builder cacheDataSourceFactoryBuilder;
    private final DataSource.Factory cachelessDataSourceFactory;
    private final DataSource.Factory biliCachelessDataSourceFactory;
    private final TransferListener transferListener;
    private final Context context;
    private final String userAgent;

    private NicoWebSocketClient nicoWebSocketClient;

    /** Clear only downloaded media while retaining fetched extractor responses in memory. */
    public static void clearMediaCacheForBenchmark() throws IOException {
        CacheFactory.clearMediaCache();
    }

    public PlayerDataSource(@NonNull final Context context,
                            @NonNull final String userAgent,
                            @NonNull final TransferListener transferListener) {
        continueLoadingCheckIntervalBytes = PlayerHelper.getProgressiveLoadIntervalBytes(context);
        cacheDataSourceFactoryBuilder = new CacheFactory.Builder(context, userAgent,
                transferListener);
        cachelessDataSourceFactory = new DefaultDataSource.Factory(context,
                new DefaultHttpDataSource.Factory().setUserAgent(userAgent).setDefaultRequestProperties(Map.of("Referer", "https://www.bilibili.com")))
                .setTransferListener(transferListener);

        this.context = context;
        this.transferListener = transferListener;
        this.userAgent = userAgent;

        YoutubeProgressiveDashManifestCreator.getCache().setMaximumSize(
                MAXIMUM_SIZE_CACHED_GENERATED_MANIFESTS_PER_CACHE);
        YoutubeOtfDashManifestCreator.getCache().setMaximumSize(
                MAXIMUM_SIZE_CACHED_GENERATED_MANIFESTS_PER_CACHE);
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().setMaximumSize(
                MAXIMUM_SIZE_CACHED_GENERATED_MANIFESTS_PER_CACHE);

        biliCachelessDataSourceFactory = new PurifiedDataSource.Factory(context,
                new PurifiedHttpDataSource.Factory().setUserAgent(userAgent)
                        .setDefaultRequestProperties(Map.of("Referer", "https://www.bilibili.com")))
                .setTransferListener(transferListener);
    }

    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        return getSSMediaSourceFactory().setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setPlaylistTrackerFactory((dataSourceFactory, loadErrorHandlingPolicy,
                                            playlistParserFactory) ->
                        new DefaultHlsPlaylistTracker(dataSourceFactory, loadErrorHandlingPolicy,
                                playlistParserFactory,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT));
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                cachelessDataSourceFactory);
    }

    public DashMediaSource.Factory getLiveYoutubeDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                cachelessDataSourceFactory)
                .setManifestParser(new YoutubeDashLiveManifestParser());
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory(
            @Nullable final HlsPlaylistParserFactory hlsPlaylistParserFactory) {
        final HlsMediaSource.Factory factory = new HlsMediaSource.Factory(
                cacheDataSourceFactoryBuilder.build());
        if (hlsPlaylistParserFactory != null) {
            factory.setPlaylistParserFactory(hlsPlaylistParserFactory);
        }
        return factory;
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cacheDataSourceFactoryBuilder.build()),
                cacheDataSourceFactoryBuilder.build());
    }

    /** Wraps a custom playback source in the same disk cache used by other media sources. */
    @NonNull
    public DataSource.Factory getCacheDataSourceFactory(
            @NonNull final DataSource.Factory upstreamDataSourceFactory,
            @NonNull final CacheKeyFactory cacheKeyFactory) {
        final CacheFactory.Builder builder = new CacheFactory.Builder(
                context, userAgent, transferListener);
        builder.setUpstreamDataSourceFactory(upstreamDataSourceFactory);
        builder.setCacheKeyFactory(cacheKeyFactory);
        return builder.build();
    }

    public ProgressiveMediaSource.Factory getProgressiveMediaSourceFactory() {
        return new ProgressiveMediaSource.Factory(cachelessDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(continueLoadingCheckIntervalBytes);
    }

    public SsMediaSource.Factory getSSMediaSourceFactory() {
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
                cachelessDataSourceFactory);
    }

    public SingleSampleMediaSource.Factory getSingleSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(cacheDataSourceFactoryBuilder.build());
    }

    @NonNull
    private DefaultDashChunkSource.Factory getDefaultDashChunkSourceFactory(
            final DataSource.Factory dataSourceFactory) {
        return new DefaultDashChunkSource.Factory(dataSourceFactory);
    }

    // YoutubeMediaSourceFactories
    public DashMediaSource.Factory getYoutubeDashMediaSourceFactory() {
        cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(
                getYoutubeHttpDataSourceFactory(true, true));
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cacheDataSourceFactoryBuilder.build()),
                cacheDataSourceFactoryBuilder.build());
    }

    public HlsMediaSource.Factory getYoutubeHlsMediaSourceFactory() {
        cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(
                getYoutubeHttpDataSourceFactory(false, false));
        final int payloadReaderFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
        return new HlsMediaSource.Factory(cacheDataSourceFactoryBuilder.build())
                .setExtractorFactory(new DefaultHlsExtractorFactory(payloadReaderFlags, true));
    }

    public ProgressiveMediaSource.Factory getYoutubeProgressiveMediaSourceFactory() {
        cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(
                getYoutubeHttpDataSourceFactory(false, true));
        return new ProgressiveMediaSource.Factory(cacheDataSourceFactoryBuilder.build())
                .setContinueLoadingCheckIntervalBytes(continueLoadingCheckIntervalBytes);
    }

    @NonNull
    private YoutubeHttpDataSource.Factory getYoutubeHttpDataSourceFactory(
            final boolean rangeParameterEnabled,
            final boolean rnParameterEnabled) {
        return new YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(rangeParameterEnabled)
                .setRnParameterEnabled(rnParameterEnabled);
    }

    // NicoNicoMediaSourceFactories
    public String getNicoLiveUrl(String url) throws ParsingException, IOException, ReCaptchaException, JsonParserException {
        DownloaderImpl downloader = DownloaderImpl.getInstance();
        Document liveResponse = Jsoup.parse(downloader.get(url).responseBody());
        String result = JsonParser.object().from(liveResponse
                        .select("script#embedded-data").attr("data-props"))
                .getObject("site").getObject("relive").getString("webSocketUrl");
        disconnectWebSocketClients();
        nicoWebSocketClient = new NicoWebSocketClient(URI.create(result), NiconicoService.getWebSocketHeaders());
        NicoWebSocketClient.WrappedWebSocketClient webSocketClient = nicoWebSocketClient.getWebSocketClient();
        webSocketClient.connect();
        long startTime = System.nanoTime();
        do {
            String liveUrl = nicoWebSocketClient.getUrl();
            if (liveUrl != null) {
                return liveUrl;
            }
        } while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) <= 10);
        webSocketClient.close();
        throw new RuntimeException("Failed to get live url"); // TODO: throw other kind of Exception
    }

    public MediaSource.Factory getNicoMediaSourceFactory(String cookie) {
        cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(new PurifiedDataSource.Factory(context,
                new PurifiedHttpDataSource.Factory()
                        .setDefaultRequestProperties(Map.of("Cookie", cookie)))
                .setTransferListener(transferListener));

        return new HlsMediaSource.Factory(cacheDataSourceFactoryBuilder.build());
    }

    public HlsMediaSource.Factory getNicoLiveHlsMediaSourceFactory(String liveUrl) {
        DataSource.Factory newFactory = new ResolvingDataSource.Factory(new NiconicoLiveDataSource
                .Factory(context, new NiconicoLiveHttpDataSource.Factory(liveUrl)
                .setDefaultRequestProperties(Map.of("Referer", "https://live.nicovideo.jp",
                        "Origin", "https://live.nicovideo.jp",
                        "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36"
                ))
                .setTransferListener(transferListener)), dataSpec -> {
            try {
                if(dataSpec.uri.toString().contains("live.nicovideo.jp/watch")){
                    return dataSpec.withUri(Uri.parse(getNicoLiveUrl(String.valueOf(dataSpec.uri))));
                }
                return dataSpec;
            } catch (ParsingException | ReCaptchaException | JsonParserException e) {
                throw new RuntimeException(e);
            }
        });
        return new HlsMediaSource.Factory(newFactory)
                .setAllowChunklessPreparation(true)
                .setPlaylistTrackerFactory((dataSourceFactory, loadErrorHandlingPolicy,
                                            playlistParserFactory) ->
                        new DefaultHlsPlaylistTracker(dataSourceFactory, loadErrorHandlingPolicy,
                                playlistParserFactory,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT)).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy());
    }

    // BiliBiliMediaSourceFactories
    public MediaSource.Factory getBiliMediaSourceFactory(String url){
        DataSource.Factory factory;
        if(url.contains("live.bilibili.com")){
            factory = biliCachelessDataSourceFactory;
        } else {
            cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(biliCachelessDataSourceFactory);
            factory = cacheDataSourceFactoryBuilder.build();
        }
        return new ProgressiveMediaSource.Factory(factory)
                .setContinueLoadingCheckIntervalBytes(continueLoadingCheckIntervalBytes);
    }

    public DashMediaSource.Factory getBiliDashMediaSourceFactory(){
        cacheDataSourceFactoryBuilder.setUpstreamDataSourceFactory(biliCachelessDataSourceFactory);
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cacheDataSourceFactoryBuilder.build()),
                cacheDataSourceFactoryBuilder.build());
    }

    public void disconnectWebSocketClients() {
        try {
            nicoWebSocketClient.disconnect();
        } catch (Exception ignore) {
        }
    }
}
