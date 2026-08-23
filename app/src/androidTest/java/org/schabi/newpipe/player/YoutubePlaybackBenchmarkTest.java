package org.schabi.newpipe.player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.App;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.SharedWebViewRuntime;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.datasource.SabrSessionHelper;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.resolver.QualityResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Media-pipeline playback benchmark. Extraction happens once per instrumentation run and trials
 * directly create ExoPlayer, so firstFrameMs is resolver-to-frame, not detail-click-to-frame.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class YoutubePlaybackBenchmarkTest {
    private static final String DEFAULT_URL =
            "https://www.youtube.com/watch?v=G-eNlqqkn1w";
    private static final String DEFAULT_ANONYMOUS_PATHS =
            "sabr,visionos_generated_dash";
    private static final String DEFAULT_LOGGED_IN_PATHS =
            "sabr,tv_downgraded_generated_dash";
    private static final String PRODUCTION_LOAD_CONTROLLER = LoadController.class.getName();
    private static final int DEFAULT_PLAY_SECONDS = 60;
    private static final long DEFAULT_START_POSITION_MS = 2_995_000;
    private static final long START_TIMEOUT_MS = 150_000;
    private static final long PLAYBACK_STALL_BUDGET_MS = 60_000;
    private static final long PLAYBACK_POLL_MS = 1_000;

    @Test
    public void compareMwebSabrAndGeneratedDash() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue(context instanceof App);
        final Bundle args = InstrumentationRegistry.getArguments();
        final boolean warmWebViewRuntime = Boolean.parseBoolean(
                args.getString("warmWebViewRuntime", "false"));
        final boolean diagnosticDetails = Boolean.parseBoolean(
                args.getString("diagnosticDetails", "false"));
        if (warmWebViewRuntime) {
            SharedWebViewRuntime.get(context).ensureReady(120_000L, "benchmark WebView warmup");
        }
        final String cookieFile = args.getString("cookieFile", "");
        final String tokens = cookieFile.isEmpty()
                ? "" : readTextFile(new File(cookieFile)).trim();
        final boolean loggedIn = !tokens.isEmpty();
        ServiceList.YouTube.setTokens(loggedIn ? tokens : null);
        final String url = args.getString("url", DEFAULT_URL);
        final int repetitions = positive(args.getString("repetitions", "5"), "repetitions");
        final int warmups = Integer.parseInt(args.getString("warmups", "1"));
        final int playSeconds = positive(args.getString("playSeconds",
                String.valueOf(DEFAULT_PLAY_SECONDS)), "playSeconds");
        final long startPositionMs = Long.parseLong(args.getString("startPositionMs",
                String.valueOf(DEFAULT_START_POSITION_MS)));
        assertTrue("startPositionMs must be unset or non-negative: " + startPositionMs,
                startPositionMs >= -1);
        final long seekTargetMs = Long.parseLong(args.getString("seekTargetMs", "-1"));
        assertTrue("seekTargetMs must be unset or non-negative: " + seekTargetMs,
                seekTargetMs >= -1);
        final int maxHeight = positive(args.getString("maxVideoHeight", "1080"),
                "maxVideoHeight");
        final String targetCodec = args.getString("targetCodec", "avc")
                .toLowerCase(Locale.ROOT);
        final boolean replacePlayerCache = Boolean.parseBoolean(
                args.getString("replacePlayerCache", "false"));
        final String defaultPaths = loggedIn
                ? DEFAULT_LOGGED_IN_PATHS : DEFAULT_ANONYMOUS_PATHS;
        final String pathFilter = args.getString("paths", defaultPaths);
        final File playerCacheDirectory = new File(context.getFilesDir(),
                "youtube-playback-benchmark/player-responses");
        DownloaderImpl.getInstance().configureYoutubePlayerResponseCacheForBenchmark(
                playerCacheDirectory, replacePlayerCache);
        emit("PIPEPLAY_BENCHMARK_PLAYER_CACHE", new JSONObject()
                .put("directory", playerCacheDirectory.getAbsolutePath())
                .put("replace", replacePlayerCache));

        final List<Path> paths = filterPaths(Arrays.asList(
                new Path("sabr", "mweb", DeliveryMethod.SABR),
                new Path("tv_downgraded_generated_dash", "tv_downgraded",
                        DeliveryMethod.PROGRESSIVE_HTTP),
                new Path("visionos_generated_dash", "visionos",
                        DeliveryMethod.PROGRESSIVE_HTTP),
                new Path("android_vr_generated_dash", "android_vr",
                        DeliveryMethod.PROGRESSIVE_HTTP)), pathFilter);
        assertTrue("No benchmark paths selected by paths=" + pathFilter, !paths.isEmpty());
        emit("PIPEPLAY_BENCHMARK_CONFIG", new JSONObject()
                .put("url", url)
                .put("paths", new JSONArray(pathNames(paths)))
                .put("repetitions", repetitions)
                .put("warmups", warmups)
                .put("playSeconds", playSeconds)
                .put("startPositionMs", startPositionMs)
                .put("seekTargetMs", seekTargetMs)
                .put("maxVideoHeight", maxHeight)
                .put("targetCodec", targetCodec)
                .put("loadController", PRODUCTION_LOAD_CONTROLLER)
                .put("warmWebViewRuntime", warmWebViewRuntime)
                .put("diagnosticDetails", diagnosticDetails)
                .put("playerMediaCacheClearedEachTrial", true)
                .put("newSabrSessionEachTrial", true)
                .put("cachedExtractionAcrossTrials", true)
                .put("firstFrameMetricScope", "media_source_resolve_to_rendered_frame")
                .put("excludedFromFirstFrameMs", new JSONArray(Arrays.asList(
                        "detail_click", "player_service_start_or_bind", "play_queue_transfer",
                        "stream_info_cache_lookup_or_extraction"))));
        final Map<Path, CachedExtraction> extractions = new LinkedHashMap<>();
        for (final Path path : paths) {
            final long before = SystemClock.elapsedRealtimeNanos();
            NewPipe.setYoutubePlayerClient(path.client);
            final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
            final long extractionMs = elapsedMs(before);
            final SelectingQualityResolver selector = new SelectingQualityResolver(
                    path.sourceDelivery, maxHeight, targetCodec);
            final boolean selectable = selector.find(info) >= 0;
            emit("PIPEPLAY_BENCHMARK_FETCH", new JSONObject()
                    .put("path", path.name).put("client", path.client)
                    .put("extractionMs", extractionMs).put("fetchCount", 1)
                    .put("playerCacheReplace", replacePlayerCache)
                    .put("selectable", selectable));
            assertTrue("No selectable " + path.name + " stream", selectable);
            extractions.put(path, new CachedExtraction(info, extractionMs));
        }

        final List<Result> measured = new ArrayList<>();
        for (int round = -warmups; round < repetitions; round++) {
            final List<Path> roundOrder = new ArrayList<>(paths);
            Collections.rotate(roundOrder, Math.floorMod(round, roundOrder.size()));
            for (final Path path : roundOrder) {
                final boolean warmup = round < 0;
                final Result result = runTrial(context, path, extractions.get(path).info,
                        round, warmup, playSeconds, startPositionMs, seekTargetMs,
                        maxHeight, targetCodec, url, warmWebViewRuntime, diagnosticDetails);
                emit("PIPEPLAY_BENCHMARK_RESULT", result.toJson());
                emitTrialDetails(result);
                if (!warmup) {
                    measured.add(result);
                }
            }
        }
        verifyComparableFormats(measured, targetCodec);
        for (final Path path : paths) {
            emit("PIPEPLAY_BENCHMARK_SUMMARY", summarize(path, measured,
                    extractions.get(path).extractionMs));
        }
    }

    private static Result runTrial(final Context context, final Path path, final StreamInfo info,
                                   final int round, final boolean warmup, final int playSeconds,
                                   final long startPositionMs,
                                   final long seekTargetMs,
                                   final int maxHeight, final String targetCodec,
                                   final String url, final boolean warmWebViewRuntime,
                                   final boolean diagnosticDetails) throws Exception {
        NewPipe.setYoutubePlayerClient(path.client);
        final CountingTransferListener transfers = new CountingTransferListener(diagnosticDetails);
        final PlayerDataSource dataSource = new PlayerDataSource(context,
                DownloaderImpl.USER_AGENT, transfers);
        // Constructing PlayerDataSource opens any persistent cache from an earlier app run; clear it
        // afterwards so the first trial is cold too, while the in-memory StreamInfo remains intact.
        PlayerDataSource.clearMediaCacheForBenchmark();
        final long baselinePssKb = Debug.getPss();
        final long uidRxBefore = TrafficStats.getUidRxBytes(Process.myUid());
        final long cpuBefore = Process.getElapsedCpuTime();
        // Measure from the point where the real player starts resolving its MediaSource. The new
        // SABR session and its initial requests happen below; PO-token minting happened during the
        // cached extraction above and is intentionally outside this metric.
        final long prepareNs = SystemClock.elapsedRealtimeNanos();
        final SelectingQualityResolver selector = new SelectingQualityResolver(
                path.sourceDelivery, maxHeight, targetCodec);
        final AtomicReference<YoutubeSabrSession> sabrSessionRef = new AtomicReference<>();
        final AtomicReference<YoutubeSabrSession.TraceSnapshot> sabrTraceStart =
                new AtomicReference<>();
        if (path.sourceDelivery == DeliveryMethod.SABR) {
            SabrSessionHelper.setBenchmarkSessionObserver(session -> {
                session.setTraceEnabled(true);
                sabrSessionRef.set(session);
                sabrTraceStart.set(session.getTraceSnapshot());
            });
        }
        final long resolveStart = SystemClock.elapsedRealtimeNanos();
        final MediaSource source;
        try {
            source = new VideoPlaybackResolver(context, dataSource, selector).resolve(info,
                    Math.max(0, startPositionMs));
        } finally {
            SabrSessionHelper.setBenchmarkSessionObserver(null);
        }
        final long resolveMs = elapsedMs(resolveStart);
        assertNotNull("Resolver returned no source for " + path.name, source);
        assertNotNull("Resolver did not select a stream for " + path.name, selector.selected);
        if (path.sourceDelivery == DeliveryMethod.SABR) {
            assertNotNull("Resolver did not expose the newly created SABR session",
                    sabrSessionRef.get());
            assertNotNull("Resolver did not capture the initial SABR trace",
                    sabrTraceStart.get());
        }
        final AtomicReference<PlaybackException> error = new AtomicReference<>();
        final AtomicLong readyNs = new AtomicLong();
        final AtomicLong frameNs = new AtomicLong();
        final AtomicLong audioNs = new AtomicLong();
        final AtomicInteger droppedFrames = new AtomicInteger();
        final AtomicInteger rebufferCount = new AtomicInteger();
        final AtomicLong rebufferNs = new AtomicLong();
        final AtomicLong bufferingStartNs = new AtomicLong();
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean ended = new AtomicBoolean();
        final AtomicBoolean countRebuffers = new AtomicBoolean(true);
        final AtomicLong firstFramePositionMs = new AtomicLong(-1);
        final AtomicLong firstFrameBufferedPositionMs = new AtomicLong(-1);
        final AtomicInteger stateTransitionCount = new AtomicInteger();
        final AtomicInteger loadEventCount = new AtomicInteger();
        final AtomicReference<Format> actualVideoFormat = new AtomicReference<>();
        final AtomicReference<Format> actualAudioFormat = new AtomicReference<>();
        final List<String> stateTransitions =
                Collections.synchronizedList(new ArrayList<>());
        final List<String> loadEvents = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        final AtomicReference<SurfaceTexture> textureRef = new AtomicReference<>();
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>();
        final AtomicBoolean sampleMemory = new AtomicBoolean(true);
        final AtomicLong peakPssKb = new AtomicLong(Math.max(baselinePssKb, Debug.getPss()));
        final Thread memorySampler = new Thread(() -> {
            while (sampleMemory.get()) {
                peakPssKb.accumulateAndGet(Debug.getPss(), Math::max);
                SystemClock.sleep(100);
            }
        }, "PlaybackBenchmarkMemory");
        memorySampler.start();

        if (startPositionMs >= 0) {
            transfers.startSeekTrace();
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final SurfaceTexture texture = new SurfaceTexture(0);
            final Surface surface = new Surface(texture);
            final DefaultRenderersFactory renderers =
                    new DefaultRenderersFactory(context);
            renderers.setEnableDecoderFallback(true);
            final ExoPlayer player = new ExoPlayer.Builder(context, renderers)
                    .setTrackSelector(new DefaultTrackSelector(context))
                    .setLoadControl(new LoadController())
                    .build();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(final int state) {
                    final long now = SystemClock.elapsedRealtimeNanos();
                    stateTransitionCount.incrementAndGet();
                    if (diagnosticDetails) {
                        stateTransitions.add(playbackEvent(prepareNs, player,
                                "state=" + playerStateName(state)));
                    }
                    if (state == Player.STATE_ENDED) {
                        ended.set(true);
                    }
                    if (state == Player.STATE_READY) {
                        readyNs.compareAndSet(0, now);
                        final long buffering = bufferingStartNs.getAndSet(0);
                        if (buffering != 0) {
                            rebufferNs.addAndGet(now - buffering);
                        }
                    } else if (state == Player.STATE_BUFFERING && started.get()
                            && countRebuffers.get()
                            && bufferingStartNs.compareAndSet(0, now)) {
                        rebufferCount.incrementAndGet();
                    }
                }

                @Override
                public void onIsLoadingChanged(final boolean isLoading) {
                    if (diagnosticDetails) {
                        stateTransitions.add(playbackEvent(prepareNs, player,
                                "isLoading=" + isLoading));
                    }
                }

                @Override
                public void onPlayerError(final PlaybackException failure) {
                    if (diagnosticDetails) {
                        stateTransitions.add(playbackEvent(prepareNs, player,
                                "error=" + failure.getErrorCodeName()));
                    }
                    error.compareAndSet(null, failure);
                }
            });
            player.addAnalyticsListener(new AnalyticsListener() {
                @Override
                public void onRenderedFirstFrame(final EventTime eventTime, final Object output,
                                                 final long renderTimeMs) {
                    if (frameNs.compareAndSet(0, SystemClock.elapsedRealtimeNanos())) {
                        firstFramePositionMs.set(player.getCurrentPosition());
                        firstFrameBufferedPositionMs.set(player.getBufferedPosition());
                        if (diagnosticDetails) {
                            stateTransitions.add(playbackEvent(prepareNs, player,
                                    "event=first_frame"));
                        }
                        started.set(true);
                    }
                }

                @Override
                public void onAudioPositionAdvancing(final EventTime eventTime,
                                                     final long playoutStartSystemTimeMs) {
                    if (audioNs.compareAndSet(0, SystemClock.elapsedRealtimeNanos())) {
                        if (diagnosticDetails) {
                            stateTransitions.add(playbackEvent(prepareNs, player,
                                    "event=audio_advancing"));
                        }
                    }
                }

                @Override
                public void onDroppedVideoFrames(final EventTime eventTime, final int count,
                                                 final long elapsedMs) {
                    droppedFrames.addAndGet(count);
                }

                @Override
                public void onVideoInputFormatChanged(final EventTime eventTime,
                                                      final Format format,
                                                      @Nullable final DecoderReuseEvaluation
                                                              reuse) {
                    actualVideoFormat.set(format);
                }

                @Override
                public void onAudioInputFormatChanged(final EventTime eventTime,
                                                      final Format format,
                                                      @Nullable final DecoderReuseEvaluation
                                                              reuse) {
                    actualAudioFormat.set(format);
                }

                @Override
                public void onLoadStarted(final EventTime eventTime,
                                          final LoadEventInfo loadEventInfo,
                                          final MediaLoadData mediaLoadData) {
                    loadEventCount.incrementAndGet();
                    if (diagnosticDetails) {
                        loadEvents.add(loadEvent(prepareNs, "start", loadEventInfo,
                                mediaLoadData, ""));
                    }
                }

                @Override
                public void onLoadCompleted(final EventTime eventTime,
                                            final LoadEventInfo loadEventInfo,
                                            final MediaLoadData mediaLoadData) {
                    loadEventCount.incrementAndGet();
                    if (diagnosticDetails) {
                        loadEvents.add(loadEvent(prepareNs, "complete", loadEventInfo,
                                mediaLoadData, null));
                    }
                }

                @Override
                public void onLoadCanceled(final EventTime eventTime,
                                           final LoadEventInfo loadEventInfo,
                                           final MediaLoadData mediaLoadData) {
                    loadEventCount.incrementAndGet();
                    if (diagnosticDetails) {
                        loadEvents.add(loadEvent(prepareNs, "canceled", loadEventInfo,
                                mediaLoadData, null));
                    }
                }

                @Override
                public void onLoadError(final EventTime eventTime,
                                        final LoadEventInfo loadEventInfo,
                                        final MediaLoadData mediaLoadData,
                                        final IOException loadError,
                                        final boolean wasCanceled) {
                    loadEventCount.incrementAndGet();
                    if (diagnosticDetails) {
                        loadEvents.add(loadEvent(prepareNs, "error", loadEventInfo,
                                mediaLoadData, "canceled=" + wasCanceled
                                        + ",type=" + loadError.getClass().getSimpleName()));
                    }
                }
            });
            player.setVideoSurface(surface);
            player.setVolume(0f);
            player.setMediaSource(source);
            if (startPositionMs >= 0) {
                player.seekTo(startPositionMs);
            }
            player.prepare();
            player.play();
            textureRef.set(texture);
            surfaceRef.set(surface);
            playerRef.set(player);
        });

        // Historical output field: mid-start trials leave this at -1 and put startup details in the
        // seek-prefixed trace fields. Keep the name so existing benchmark parsers remain compatible.
        long seekRecoveryMs = -1;
        long linearPlaybackWallMs = -1;
        long finalPositionMs = -1;
        long finalBufferedPositionMs = -1;
        SabrStats sabrStats = SabrStats.EMPTY;
        SeekTrace seekTrace = SeekTrace.EMPTY;
        try {
            waitUntil(() -> frameNs.get() != 0 || ended.get() || error.get() != null,
                    START_TIMEOUT_MS);
            throwPlayerError(error.get());
            assertTrue("Playback ended before rendering the first frame", frameNs.get() != 0);
            final long playbackStartPositionMs = firstFramePositionMs.get();
            assertTrue("First frame did not report a valid playback position: "
                    + playbackStartPositionMs, playbackStartPositionMs >= 0);
            final long playbackTargetPositionMs = playbackStartPositionMs
                    + playSeconds * 1_000L;
            final long playbackTimeoutMs = playSeconds * 1_000L + PLAYBACK_STALL_BUDGET_MS;
            waitUntil(() -> position(playerRef.get(), info.getId())
                            >= playbackTargetPositionMs
                    || ended.get() || error.get() != null, playbackTimeoutMs, PLAYBACK_POLL_MS);
            throwPlayerError(error.get());
            countRebuffers.set(false);
            final long linearBuffer = bufferingStartNs.getAndSet(0);
            if (linearBuffer != 0) {
                rebufferNs.addAndGet(SystemClock.elapsedRealtimeNanos() - linearBuffer);
            }
            final long reachedPositionMs = position(playerRef.get(), info.getId());
            if (reachedPositionMs < playbackTargetPositionMs) {
                throw new AssertionError("Playback ended before completing the benchmark window: "
                        + "firstFramePositionMs=" + playbackStartPositionMs
                        + ", targetPositionMs=" + playbackTargetPositionMs
                        + ", reachedPositionMs=" + reachedPositionMs
                        + ", durationMs=" + duration(playerRef.get()));
            }
            linearPlaybackWallMs = toMs(SystemClock.elapsedRealtimeNanos() - frameNs.get());
            finalPositionMs = reachedPositionMs;
            finalBufferedPositionMs = bufferedPosition(playerRef.get());
            if (startPositionMs >= 0) {
                if (sabrSessionRef.get() != null) {
                    seekTrace = SeekTrace.fromSabr(sabrTraceStart.get(),
                            sabrSessionRef.get().getTraceSnapshot());
                } else {
                    seekTrace = transfers.finishSeekTrace();
                }
            } else {
                final long duration = duration(playerRef.get());
                final long target = seekTargetMs >= 0 ? seekTargetMs
                        : duration == C.TIME_UNSET ? 30_000
                        : Math.max(1_000, Math.min(30_000, duration / 2));
                transfers.startSeekTrace();
                final YoutubeSabrSession.TraceSnapshot sabrBefore = sabrSessionRef.get() == null
                        ? null : sabrSessionRef.get().getTraceSnapshot();
                final long seekStart = SystemClock.elapsedRealtimeNanos();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> playerRef.get().seekTo(target));
                waitUntil(() -> position(playerRef.get(), info.getId()) >= target + 1_000
                        || error.get() != null, START_TIMEOUT_MS);
                throwPlayerError(error.get());
                seekRecoveryMs = elapsedMs(seekStart);
                if (sabrSessionRef.get() != null) {
                    seekTrace = SeekTrace.fromSabr(sabrBefore,
                            sabrSessionRef.get().getTraceSnapshot());
                } else {
                    seekTrace = transfers.finishSeekTrace();
                }
            }
            if (sabrSessionRef.get() != null) {
                sabrStats = SabrStats.fromSession(sabrSessionRef.get());
            }
        } catch (final Exception | AssertionError failure) {
            try {
                emit("PIPEPLAY_BENCHMARK_FAILURE", new JSONObject()
                        .put("path", path.name)
                        .put("client", path.client)
                        .put("videoId", info.getId())
                        .put("round", round)
                        .put("warmup", warmup)
                        .put("requestedStartPositionMs", startPositionMs)
                        .put("type", failure.getClass().getSimpleName())
                        .put("message", safeDiagnosticMessage(failure.getMessage()))
                        .put("stateTransitionCount", stateTransitionCount.get())
                        .put("loadEventCount", loadEventCount.get()));
                if (diagnosticDetails) {
                    emitEvents("PIPEPLAY_BENCHMARK_STATE", path, info.getId(), round, warmup,
                            startPositionMs, "failure", snapshot(stateTransitions));
                    emitEvents("PIPEPLAY_BENCHMARK_LOAD", path, info.getId(), round, warmup,
                            startPositionMs, "failure", snapshot(loadEvents));
                }
            } catch (final Exception diagnosticFailure) {
                failure.addSuppressed(diagnosticFailure);
            }
            throw failure;
        } finally {
            final long openBuffer = bufferingStartNs.getAndSet(0);
            if (openBuffer != 0) {
                rebufferNs.addAndGet(SystemClock.elapsedRealtimeNanos() - openBuffer);
            }
            sampleMemory.set(false);
            memorySampler.join(2_000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (playerRef.get() != null) {
                    playerRef.get().release();
                }
                if (surfaceRef.get() != null) {
                    surfaceRef.get().release();
                }
                if (textureRef.get() != null) {
                    textureRef.get().release();
                }
            });
        }
        final long uidRxAfter = TrafficStats.getUidRxBytes(Process.myUid());
        final long uidRxBytes = uidRxBefore < 0 || uidRxAfter < 0 ? -1 : uidRxAfter - uidRxBefore;
        final long mediaBytes = sabrSessionRef.get() == null
                ? transfers.networkBytes.get() : sabrStats.responseBytes;
        return new Result(path, round, warmup, selector.selected, resolveMs,
                toMs(readyNs.get() - prepareNs), toMs(frameNs.get() - prepareNs),
                toMs(audioNs.get() - prepareNs), seekRecoveryMs, rebufferCount.get(),
                durationMs(rebufferNs.get()), droppedFrames.get(), mediaBytes, uidRxBytes,
                Process.getElapsedCpuTime() - cpuBefore, peakPssKb.get(), baselinePssKb,
                sabrStats, seekTrace, url, info.getId(), playSeconds, startPositionMs,
                seekTargetMs, maxHeight, targetCodec, linearPlaybackWallMs,
                firstFramePositionMs.get(), firstFrameBufferedPositionMs.get(), finalPositionMs,
                finalBufferedPositionMs, actualVideoFormat.get(), actualAudioFormat.get(),
                snapshot(stateTransitions), snapshot(loadEvents), warmWebViewRuntime,
                diagnosticDetails, stateTransitionCount.get(), loadEventCount.get());
    }

    private static String readTextFile(final File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject summarize(final Path path, final List<Result> all,
                                        final long extractionMs) throws Exception {
        final List<Result> values = new ArrayList<>();
        for (final Result result : all) {
            if (result.path == path) {
                values.add(result);
            }
        }
        return new JSONObject().put("path", path.name).put("client", path.client)
                .put("samples", values.size()).put("cachedExtractionMs", extractionMs)
                .put("firstFrameMetricScope", "media_source_resolve_to_rendered_frame")
                .put("firstFrameMsP50", percentile(values, r -> r.firstFrameMs, 0.50))
                .put("firstFrameMsP95", percentile(values, r -> r.firstFrameMs, 0.95))
                .put("seekRecoveryMsP50", percentile(values, r -> r.seekRecoveryMs, 0.50))
                .put("linearPlaybackWallMsP50", percentile(values,
                        r -> r.linearPlaybackWallMs, 0.50))
                .put("linearPlaybackWallMsP95", percentile(values,
                        r -> r.linearPlaybackWallMs, 0.95))
                .put("rebufferCountP50", percentile(values, r -> r.rebufferCount, 0.50))
                .put("rebufferCountP95", percentile(values, r -> r.rebufferCount, 0.95))
                .put("rebufferMsP50", percentile(values, r -> r.rebufferMs, 0.50))
                .put("rebufferMsP95", percentile(values, r -> r.rebufferMs, 0.95))
                .put("mediaBytesP50", percentile(values, r -> r.mediaBytes, 0.50))
                .put("seekNetworkBytesP50", percentile(values,
                        r -> r.seekTrace.networkBytes, 0.50))
                .put("seekSabrResponseBytesP50", percentile(values,
                        r -> r.seekTrace.sabrResponseBytes, 0.50))
                .put("seekSabrMediaPayloadBytesP50", percentile(values,
                        r -> r.seekTrace.sabrMediaPayloadBytes, 0.50))
                .put("seekSabrControlPayloadBytesP50", percentile(values,
                        r -> r.seekTrace.sabrControlPayloadBytes, 0.50))
                .put("seekSabrUmpOverheadBytesP50", percentile(values,
                        r -> r.seekTrace.sabrUmpOverheadBytes, 0.50))
                .put("sabrRequestCountP50", percentile(values,
                        r -> r.sabrStats.requestCount, 0.50))
                .put("sabrResponseBytesP50", percentile(values,
                        r -> r.sabrStats.responseBytes, 0.50))
                .put("cpuMsP50", percentile(values, r -> r.cpuMs, 0.50))
                .put("peakPssDeltaKbP50", percentile(values, r -> r.peakPssDeltaKb, 0.50));
    }

    private static void verifyComparableFormats(final List<Result> results,
                                                final String targetCodec) {
        for (final Result sabr : results) {
            if (!sabr.path.name.startsWith("sabr")) {
                continue;
            }
            final List<Result> controls = new ArrayList<>();
            for (final Result candidate : results) {
                if (candidate.round == sabr.round
                        && candidate.path.sourceDelivery == DeliveryMethod.PROGRESSIVE_HTTP) {
                    controls.add(candidate);
                }
            }
            for (final Result control : controls) {
                verifyComparableFormat(sabr, control, targetCodec);
            }
        }
    }

    private static void verifyComparableFormat(final Result sabr, final Result control,
                                               final String targetCodec) {
        final String label = control.path.name;
        assertNotNull("SABR did not report its actual video format in round " + sabr.round,
                sabr.actualVideoFormat);
        assertNotNull(label + " did not report its actual video format in round " + sabr.round,
                control.actualVideoFormat);
        assertNotNull("SABR did not report its actual audio format in round " + sabr.round,
                sabr.actualAudioFormat);
        assertNotNull(label + " did not report its actual audio format in round " + sabr.round,
                control.actualAudioFormat);
        assertTrue("Video heights differ in round " + sabr.round + ": SABR="
                        + sabr.actualVideoFormat.height + " " + label + "="
                        + control.actualVideoFormat.height,
                sabr.actualVideoFormat.height == control.actualVideoFormat.height);
        final String sabrCodec = String.valueOf(sabr.actualVideoFormat.codecs)
                .toLowerCase(Locale.ROOT);
        final String controlCodec = String.valueOf(control.actualVideoFormat.codecs)
                .toLowerCase(Locale.ROOT);
        assertTrue("Actual SABR codec does not match target " + targetCodec + ": " + sabrCodec,
                targetCodec.isEmpty() || sabrCodec.contains(targetCodec));
        assertTrue("Actual " + label + " codec does not match target " + targetCodec + ": "
                        + controlCodec,
                targetCodec.isEmpty() || controlCodec.contains(targetCodec));
        assertTrue("Video codecs differ in round " + sabr.round + ": SABR="
                        + sabrCodec + " " + label + "=" + controlCodec,
                sabrCodec.equals(controlCodec));
        assertTrue("Audio MIME types differ in round " + sabr.round + ": SABR="
                        + sabr.actualAudioFormat.sampleMimeType + " " + label + "="
                        + control.actualAudioFormat.sampleMimeType,
                java.util.Objects.equals(sabr.actualAudioFormat.sampleMimeType,
                        control.actualAudioFormat.sampleMimeType));
    }

    private interface Value { long get(Result result); }

    private static long percentile(final List<Result> values, final Value value,
                                   final double percentile) {
        final List<Long> sorted = new ArrayList<>();
        for (final Result result : values) sorted.add(value.get(result));
        sorted.sort(Comparator.naturalOrder());
        return sorted.get(Math.min(sorted.size() - 1,
                (int) Math.ceil(percentile * sorted.size()) - 1));
    }

    private static void emit(final String marker, final JSONObject json) throws Exception {
        json.put("record", marker.substring("PIPEPLAY_BENCHMARK_".length())
                .toLowerCase(Locale.ROOT));
        System.out.println(marker + " " + json);
    }

    private static void emitTrialDetails(final Result result) throws Exception {
        emitEvents("PIPEPLAY_BENCHMARK_STATE", result, "playback",
                result.stateTransitions);
        emitEvents("PIPEPLAY_BENCHMARK_LOAD", result, "media3", result.loadEvents);
        if (result.diagnosticDetails) {
            emitEvents("PIPEPLAY_BENCHMARK_SABR_RESPONSE", result, "measured",
                    result.seekTrace.sabrResponses);
        }
        emitEvents("PIPEPLAY_BENCHMARK_TRANSFER", result, "measured",
                result.seekTrace.transfers);
    }

    private static void emitEvents(final String marker, final Result result,
                                   final String phase, final List<String> events) throws Exception {
        emitEvents(marker, result.path, result.videoId, result.round, result.warmup,
                result.requestedStartPositionMs, phase, events);
    }

    private static void emitEvents(final String marker, final Path path, final String videoId,
                                   final int round, final boolean warmup,
                                   final long requestedStartPositionMs, final String phase,
                                   final List<String> events) throws Exception {
        for (int i = 0; i < events.size(); i++) {
            emit(marker, new JSONObject()
                    .put("path", path.name)
                    .put("client", path.client)
                    .put("videoId", videoId)
                    .put("round", round)
                    .put("warmup", warmup)
                    .put("requestedStartPositionMs", requestedStartPositionMs)
                    .put("phase", phase)
                    .put("index", i)
                    .put("event", events.get(i)));
        }
    }

    private static int positive(final String value, final String name) {
        final int parsed = Integer.parseInt(value);
        if (parsed <= 0) throw new IllegalArgumentException(name + " must be positive");
        return parsed;
    }

    private interface Condition { boolean test() throws Exception; }

    private static void waitUntil(final Condition condition, final long timeoutMs) throws Exception {
        waitUntil(condition, timeoutMs, 50);
    }

    private static void waitUntil(final Condition condition, final long timeoutMs,
                                  final long pollMs) throws Exception {
        final long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.test()) {
                return;
            }
            SystemClock.sleep(Math.min(pollMs,
                    Math.max(1, deadline - SystemClock.elapsedRealtime())));
        }
        assertTrue("Benchmark phase timed out after " + timeoutMs + "ms", condition.test());
    }

    private static void throwPlayerError(final PlaybackException error) {
        if (error != null) throw new AssertionError("Playback failed", error);
    }

    private static long position(final ExoPlayer player, final String videoId) {
        final AtomicLong value = new AtomicLong();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> value.set(player.getCurrentPosition()));
        return value.get();
    }

    private static long duration(final ExoPlayer player) {
        final AtomicLong value = new AtomicLong();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> value.set(player.getDuration()));
        return value.get();
    }

    private static long bufferedPosition(final ExoPlayer player) {
        final AtomicLong value = new AtomicLong();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> value.set(player.getBufferedPosition()));
        return value.get();
    }

    private static String playbackEvent(final long prepareNs, final ExoPlayer player,
                                        final String event) {
        final long positionMs = player.getCurrentPosition();
        final long bufferedPositionMs = player.getBufferedPosition();
        return "elapsedMs=" + elapsedMs(prepareNs)
                + ',' + event
                + ",positionMs=" + positionMs
                + ",bufferedPositionMs=" + bufferedPositionMs
                + ",bufferedDurationMs=" + Math.max(0, bufferedPositionMs - positionMs);
    }

    private static String playerStateName(final int state) {
        switch (state) {
            case Player.STATE_IDLE:
                return "IDLE";
            case Player.STATE_BUFFERING:
                return "BUFFERING";
            case Player.STATE_READY:
                return "READY";
            case Player.STATE_ENDED:
                return "ENDED";
            default:
                return "UNKNOWN_" + state;
        }
    }

    private static String loadEvent(final long prepareNs,
                                    final String event,
                                    final LoadEventInfo info,
                                    final MediaLoadData data,
                                    @Nullable final String detail) {
        final String resource = summarizeUri(info.uri);
        final StringBuilder value = new StringBuilder()
                .append("elapsedMs=").append(elapsedMs(prepareNs))
                .append(",event=").append(event)
                .append(",loadTaskId=").append(info.loadTaskId)
                .append(",resource=").append(resource)
                .append(",position=").append(info.dataSpec.position)
                .append(",length=").append(info.dataSpec.length)
                .append(",bytesLoaded=").append(info.bytesLoaded)
                .append(",loadDurationMs=").append(info.loadDurationMs)
                .append(",trackType=").append(trackTypeName(data.trackType))
                .append(",mediaStartMs=").append(data.mediaStartTimeMs)
                .append(",mediaEndMs=").append(data.mediaEndTimeMs);
        if (data.trackFormat != null) {
            value.append(",formatId=").append(data.trackFormat.id);
        }
        if (detail != null) {
            value.append(',').append(detail);
        }
        return value.toString();
    }

    private static String summarizeUri(final android.net.Uri uri) {
        return uri.buildUpon()
                .encodedQuery(null)
                .fragment(null)
                .build()
                .toString();
    }

    private static String safeDiagnosticMessage(@Nullable final String message) {
        if (message == null) {
            return "";
        }
        final String redacted = message.replaceAll("(?i)https?://\\S+", "<url>");
        return redacted.length() <= 240 ? redacted : redacted.substring(0, 240);
    }

    private static String trackTypeName(final int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_AUDIO:
                return "audio";
            case C.TRACK_TYPE_VIDEO:
                return "video";
            case C.TRACK_TYPE_TEXT:
                return "text";
            case C.TRACK_TYPE_METADATA:
                return "metadata";
            default:
                return "unknown_" + trackType;
        }
    }

    private static List<String> snapshot(final List<String> values) {
        synchronized (values) {
            return new ArrayList<>(values);
        }
    }

    private static List<String> pathNames(final List<Path> paths) {
        final List<String> names = new ArrayList<>();
        for (final Path path : paths) {
            names.add(path.name);
        }
        return names;
    }

    private static List<Path> filterPaths(final List<Path> paths, final String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return paths;
        }
        final List<String> selected = Arrays.asList(filter.toLowerCase(Locale.ROOT).split(","));
        final List<Path> filtered = new ArrayList<>();
        for (final Path path : paths) {
            if (selected.contains(path.name.toLowerCase(Locale.ROOT))) {
                filtered.add(path);
            }
        }
        return filtered;
    }

    private static long elapsedMs(final long startNs) {
        return toMs(SystemClock.elapsedRealtimeNanos() - startNs);
    }

    private static long toMs(final long ns) { return ns <= 0 ? -1 : ns / 1_000_000; }

    private static long durationMs(final long ns) { return Math.max(0, ns / 1_000_000); }

    private static final class CountingTransferListener implements TransferListener {
        private final boolean diagnosticDetails;
        private final AtomicLong networkBytes = new AtomicLong();
        private final AtomicBoolean traceSeek = new AtomicBoolean();
        private final AtomicLong seekNetworkBytes = new AtomicLong();
        private final List<String> seekTransfers = Collections.synchronizedList(new ArrayList<>());
        private CountingTransferListener(final boolean diagnosticDetails) {
            this.diagnosticDetails = diagnosticDetails;
        }
        @Override public void onTransferInitializing(@NonNull final DataSource source,
                @NonNull final DataSpec spec, final boolean network) { }
        @Override public void onTransferStart(@NonNull final DataSource source,
                @NonNull final DataSpec spec, final boolean network) { }
        @Override public void onBytesTransferred(@NonNull final DataSource source,
                @NonNull final DataSpec spec, final boolean network, final int bytes) {
            if (network) {
                networkBytes.addAndGet(bytes);
                if (traceSeek.get()) {
                    seekNetworkBytes.addAndGet(bytes);
                }
            }
        }
        @Override public void onTransferEnd(@NonNull final DataSource source,
                @NonNull final DataSpec spec, final boolean network) {
            if (diagnosticDetails && network && traceSeek.get()) {
                seekTransfers.add("resource=" + summarizeUri(spec.uri)
                        + ",position=" + spec.position
                        + ",length=" + spec.length);
            }
        }
        private void startSeekTrace() {
            seekNetworkBytes.set(0);
            seekTransfers.clear();
            traceSeek.set(true);
        }
        private SeekTrace finishSeekTrace() {
            traceSeek.set(false);
            synchronized (seekTransfers) {
                return SeekTrace.fromNetwork(seekNetworkBytes.get(),
                        new ArrayList<>(seekTransfers));
            }
        }
    }

    private static final class SelectingQualityResolver implements QualityResolver {
        private final DeliveryMethod delivery;
        private final int maxHeight;
        private final String codec;
        private VideoStream selected;
        private SelectingQualityResolver(final DeliveryMethod delivery, final int maxHeight,
                                         final String codec) {
            this.delivery = delivery; this.maxHeight = maxHeight; this.codec = codec;
        }
        private int find(final StreamInfo info) {
            final List<VideoStream> streams = new ArrayList<>(info.getVideoStreams());
            streams.addAll(info.getVideoOnlyStreams());
            return choose(streams);
        }
        private int choose(final List<VideoStream> streams) {
            int best = -1; int bestHeight = -1;
            for (int i = 0; i < streams.size(); i++) {
                final VideoStream stream = streams.get(i);
                final int height = effectiveHeight(stream);
                if (stream.getDeliveryMethod() != delivery || height > maxHeight) continue;
                final String streamCodec = stream.getCodec() == null ? ""
                        : stream.getCodec().toLowerCase(Locale.ROOT);
                if (!codec.isEmpty() && !streamCodec.contains(codec)) continue;
                if (height > bestHeight) { best = i; bestHeight = height; }
            }
            if (best >= 0) selected = streams.get(best);
            return best;
        }
        @Override public int getDefaultResolutionIndex(final List<VideoStream> streams) {
            final int result = choose(streams);
            if (result < 0) throw new AssertionError("No stream for " + delivery);
            return result;
        }
        @Override public int getOverrideResolutionIndex(final List<VideoStream> streams,
                                                        final String selectedResolution,
                                                        final String selectedCodec) {
            return getDefaultResolutionIndex(streams);
        }
        @Override public int getCurrentAudioQualityIndex(final List<AudioStream> streams) { return 0; }
        private static int effectiveHeight(final VideoStream stream) {
            if (stream.getHeight() > 0) return stream.getHeight();
            final String resolution = stream.getResolution();
            if (resolution == null) return 0;
            final java.util.regex.Matcher match = java.util.regex.Pattern
                    .compile("(?:x)?(\\d{3,4})p?(?:\\d{2})?$").matcher(resolution);
            return match.find() ? Integer.parseInt(match.group(1)) : 0;
        }
    }

    private static final class Path {
        private final String name; private final String client;
        private final DeliveryMethod sourceDelivery;
        private Path(final String name, final String client, final DeliveryMethod sourceDelivery) {
            this.name = name;
            this.client = client;
            this.sourceDelivery = sourceDelivery;
        }
    }
    private static final class CachedExtraction {
        private final StreamInfo info; private final long extractionMs;
        private CachedExtraction(final StreamInfo info, final long extractionMs) {
            this.info = info; this.extractionMs = extractionMs;
        }
    }
    private static final class Result {
        private final Path path; private final int round; private final boolean warmup;
        private final VideoStream stream; private final long resolveMs, readyMs, firstFrameMs,
                audioMs, seekRecoveryMs, rebufferMs, mediaBytes, uidRxBytes, cpuMs, peakPssKb,
                peakPssDeltaKb, linearPlaybackWallMs, firstFramePositionMs,
                firstFrameBufferedPositionMs, finalPositionMs, finalBufferedPositionMs;
        private final int rebufferCount, droppedFrames, stateTransitionCount, loadEventCount;
        private final boolean warmWebViewRuntime, diagnosticDetails;
        private final SabrStats sabrStats;
        private final SeekTrace seekTrace;
        private final String url, videoId, targetCodec;
        private final int playSeconds, maxVideoHeight;
        private final long requestedStartPositionMs, requestedSeekTargetMs;
        @Nullable private final Format actualVideoFormat;
        @Nullable private final Format actualAudioFormat;
        private final List<String> stateTransitions;
        private final List<String> loadEvents;
        private Result(final Path path, final int round, final boolean warmup,
                       final VideoStream stream, final long resolveMs, final long readyMs,
                       final long firstFrameMs, final long audioMs, final long seekRecoveryMs,
                       final int rebufferCount, final long rebufferMs, final int droppedFrames,
                       final long mediaBytes, final long uidRxBytes, final long cpuMs,
                       final long peakPssKb, final long baselinePssKb,
                       final SabrStats sabrStats, final SeekTrace seekTrace,
                       final String url, final String videoId, final int playSeconds,
                       final long requestedStartPositionMs, final long requestedSeekTargetMs,
                       final int maxVideoHeight, final String targetCodec,
                       final long linearPlaybackWallMs, final long firstFramePositionMs,
                       final long firstFrameBufferedPositionMs, final long finalPositionMs,
                       final long finalBufferedPositionMs,
                       @Nullable final Format actualVideoFormat,
                       @Nullable final Format actualAudioFormat,
                       final List<String> stateTransitions, final List<String> loadEvents,
                       final boolean warmWebViewRuntime, final boolean diagnosticDetails,
                       final int stateTransitionCount,
                       final int loadEventCount) {
            this.path=path; this.round=round; this.warmup=warmup; this.stream=stream;
            this.resolveMs=resolveMs; this.readyMs=readyMs; this.firstFrameMs=firstFrameMs;
            this.audioMs=audioMs; this.seekRecoveryMs=seekRecoveryMs;
            this.rebufferCount=rebufferCount; this.rebufferMs=rebufferMs;
            this.droppedFrames=droppedFrames; this.mediaBytes=mediaBytes;
            this.uidRxBytes=uidRxBytes; this.cpuMs=cpuMs; this.peakPssKb=peakPssKb;
            this.peakPssDeltaKb=Math.max(0, peakPssKb-baselinePssKb);
            this.sabrStats=sabrStats;
            this.seekTrace=seekTrace;
            this.url=url; this.videoId=videoId; this.playSeconds=playSeconds;
            this.requestedStartPositionMs=requestedStartPositionMs;
            this.requestedSeekTargetMs=requestedSeekTargetMs;
            this.maxVideoHeight=maxVideoHeight; this.targetCodec=targetCodec;
            this.linearPlaybackWallMs=linearPlaybackWallMs;
            this.firstFramePositionMs=firstFramePositionMs;
            this.firstFrameBufferedPositionMs=firstFrameBufferedPositionMs;
            this.finalPositionMs=finalPositionMs;
            this.finalBufferedPositionMs=finalBufferedPositionMs;
            this.actualVideoFormat=actualVideoFormat; this.actualAudioFormat=actualAudioFormat;
            this.stateTransitions=stateTransitions; this.loadEvents=loadEvents;
            this.warmWebViewRuntime=warmWebViewRuntime;
            this.diagnosticDetails=diagnosticDetails;
            this.stateTransitionCount=stateTransitionCount;
            this.loadEventCount=loadEventCount;
        }
        private JSONObject toJson() throws Exception {
            return new JSONObject().put("path",path.name).put("client",path.client)
                    .put("round",round).put("warmup",warmup)
                    .put("url",url).put("videoId",videoId)
                    .put("playSeconds",playSeconds)
                    .put("requestedStartPositionMs",requestedStartPositionMs)
                    .put("requestedSeekTargetMs",requestedSeekTargetMs)
                    .put("maxVideoHeight",maxVideoHeight).put("targetCodec",targetCodec)
                    .put("loadController",PRODUCTION_LOAD_CONTROLLER)
                    .put("warmWebViewRuntime",warmWebViewRuntime)
                    .put("diagnosticDetails",diagnosticDetails)
                    .put("firstFrameMetricScope", "media_source_resolve_to_rendered_frame")
                    .put("height",SelectingQualityResolver.effectiveHeight(stream))
                    .put("itag",stream.getItag()).put("codec",String.valueOf(stream.getCodec()))
                    .put("sourceDelivery",stream.getDeliveryMethod().name())
                    .put("resolveMs",resolveMs).put("readyMs",readyMs)
                    .put("firstFrameMs",firstFrameMs).put("audioMs",audioMs)
                    .put("linearPlaybackWallMs",linearPlaybackWallMs)
                    .put("firstFramePositionMs",firstFramePositionMs)
                    .put("firstFrameBufferedPositionMs",firstFrameBufferedPositionMs)
                    .put("finalPositionMs",finalPositionMs)
                    .put("finalBufferedPositionMs",finalBufferedPositionMs)
                    .put("actualVideoFormat",formatJson(actualVideoFormat))
                    .put("actualAudioFormat",formatJson(actualAudioFormat))
                    .put("seekRecoveryMs",seekRecoveryMs).put("rebufferCount",rebufferCount)
                    .put("rebufferMs",rebufferMs).put("droppedFrames",droppedFrames)
                    .put("mediaBytes",mediaBytes).put("uidRxBytes",uidRxBytes)
                    .put("cpuMs",cpuMs).put("peakPssKb",peakPssKb)
                    .put("peakPssDeltaKb",peakPssDeltaKb)
                    .put("sabrRequestCount",sabrStats.requestCount)
                    .put("sabrResponseBytes",sabrStats.responseBytes)
                    .put("seekNetworkBytes",seekTrace.networkBytes)
                    .put("seekSabrResponseBytes",seekTrace.sabrResponseBytes)
                    .put("seekSabrMediaPayloadBytes",seekTrace.sabrMediaPayloadBytes)
                    .put("seekSabrControlPayloadBytes",seekTrace.sabrControlPayloadBytes)
                    .put("seekSabrUmpOverheadBytes",seekTrace.sabrUmpOverheadBytes)
                    .put("seekSabrRequestCount",seekTrace.sabrRequestCount)
                    .put("seekSabrResponseCount",seekTrace.sabrResponses.size())
                    .put("seekTransferCount",seekTrace.transfers.size())
                    .put("stateTransitionCount",stateTransitionCount)
                    .put("loadEventCount",loadEventCount);
        }

        private static JSONObject formatJson(@Nullable final Format format) throws Exception {
            if (format == null) {
                return new JSONObject();
            }
            return new JSONObject()
                    .put("id", String.valueOf(format.id))
                    .put("sampleMimeType", String.valueOf(format.sampleMimeType))
                    .put("codecs", String.valueOf(format.codecs))
                    .put("bitrate", format.bitrate)
                    .put("width", format.width)
                    .put("height", format.height)
                    .put("channelCount", format.channelCount)
                    .put("sampleRate", format.sampleRate);
        }
    }

    private static final class SeekTrace {
        private static final SeekTrace EMPTY = new SeekTrace(-1, -1, -1, -1, -1,
                -1, Collections.emptyList(), Collections.emptyList());
        private final long networkBytes, sabrResponseBytes, sabrMediaPayloadBytes,
                sabrControlPayloadBytes, sabrUmpOverheadBytes, sabrRequestCount;
        private final List<String> transfers, sabrResponses;

        private SeekTrace(final long networkBytes,
                          final long sabrResponseBytes,
                          final long sabrMediaPayloadBytes,
                          final long sabrControlPayloadBytes,
                          final long sabrUmpOverheadBytes,
                          final long sabrRequestCount,
                          final List<String> transfers,
                          final List<String> sabrResponses) {
            this.networkBytes = networkBytes;
            this.sabrResponseBytes = sabrResponseBytes;
            this.sabrMediaPayloadBytes = sabrMediaPayloadBytes;
            this.sabrControlPayloadBytes = sabrControlPayloadBytes;
            this.sabrUmpOverheadBytes = sabrUmpOverheadBytes;
            this.sabrRequestCount = sabrRequestCount;
            this.transfers = transfers;
            this.sabrResponses = sabrResponses;
        }

        private static SeekTrace fromNetwork(final long networkBytes,
                                             final List<String> transfers) {
            return new SeekTrace(networkBytes, -1, -1, -1, -1, -1,
                    transfers, Collections.emptyList());
        }

        private static SeekTrace fromSabr(
                @Nullable final YoutubeSabrSession.TraceSnapshot before,
                @NonNull final YoutubeSabrSession.TraceSnapshot after) {
            if (before == null) {
                return EMPTY;
            }
            return new SeekTrace(-1,
                    delta(after.getResponseBytes(), before.getResponseBytes()),
                    delta(after.getMediaPayloadBytes(), before.getMediaPayloadBytes()),
                    delta(after.getControlPayloadBytes(), before.getControlPayloadBytes()),
                    delta(after.getUmpOverheadBytes(), before.getUmpOverheadBytes()),
                    delta(after.getRequestNumber(), before.getRequestNumber()),
                    Collections.emptyList(), suffix(after.getResponses(), before.getResponses()));
        }

        private static long delta(final long after, final long before) {
            return Math.max(0, after - before);
        }

        private static List<String> suffix(final List<String> after,
                                           final List<String> before) {
            final int start = Math.min(before.size(), after.size());
            return new ArrayList<>(after.subList(start, after.size()));
        }
    }

    private static final class SabrStats {
        private static final SabrStats EMPTY = new SabrStats(-1, -1);
        private final long responseBytes;
        private final long requestCount;

        private SabrStats(final long responseBytes, final long requestCount) {
            this.responseBytes = responseBytes;
            this.requestCount = requestCount;
        }

        private static SabrStats fromSession(final YoutubeSabrSession session) {
            return new SabrStats(session.getTotalResponseBytes(), session.getRequestNumber());
        }
    }
}
