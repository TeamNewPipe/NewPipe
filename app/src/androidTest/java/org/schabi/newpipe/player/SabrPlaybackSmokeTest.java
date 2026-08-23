package org.schabi.newpipe.player.datasource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.App;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRequestDumper;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrResponseDecoder;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrRequest;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.PlaybackStartupTrace;
import org.schabi.newpipe.player.SabrBackoffCoordinator;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.QualityResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nonnull;

/**
 * Online smoke test for the production Extractor -> SABR MediaSource -> Media3 pipeline.
 *
 * <p>Run only this test with:</p>
 * <pre>
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=\
 * org.schabi.newpipe.player.SabrPlaybackSmokeTest \
 *   -Pandroid.testInstrumentationRunnerArguments.url=\
 * https://www.youtube.com/watch?v=G-eNlqqkn1w
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class SabrPlaybackSmokeTest {
    private static final int SMOKE_AUDIO_ITAG = 140;
    private static final int SMOKE_VIDEO_ITAG = 248;
    private static final int PROTO_WIRE_VARINT = 0;
    private static final int PROTO_WIRE_LENGTH_DELIMITED = 2;
    private static final String DEFAULT_URL =
            "https://www.youtube.com/watch?v=G-eNlqqkn1w";
    private static final String RICKROLL_URL =
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final int DEFAULT_MAX_VIDEO_HEIGHT = 720;
    private static final long DEFAULT_SEEK_POSITION_MS = (49 * 60 + 55) * 1000L;
    private static final long DEFAULT_LINEAR_PLAYBACK_MS = 3_000;
    private static final long DEFAULT_POST_SEEK_PLAYBACK_MS = 30_000;
    private static final long PREPARE_TIMEOUT_SECONDS = 150;
    private static final long PLAYBACK_TIMEOUT_SECONDS = 75;

    @Test
    public void extractorToMedia3PlaysAndSeeks() throws Exception {
        runSmokeCase(SmokeCase.playback());
    }

    @Test
    public void extractorToSabrFetchesAudioAfter65Seconds() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue("The target process must use PipePlay's App initialization",
                context instanceof App);

        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String url = arguments.getString("url", DEFAULT_URL);
        NewPipe.setYoutubePlayerClient("mweb");

        // Keep this as a real StreamExtractor run: all SABR metadata, including visitorData,
        // serverAbrStreamingUrl, ustreamer config and formats, must come from the player response.
        final StreamInfo streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url);
        final AudioStream audioStream = streamInfo.getAudioStreams().stream()
                .filter(SabrPlaybackSmokeTest::isSabr)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Extractor returned no SABR audio stream for " + url));
        assertTrue("SABR audio stream has no YoutubeSabrInfo",
                audioStream.getDeliveryMethodInfo() instanceof YoutubeSabrInfo);
        final YoutubeSabrInfo sabrInfo =
                (YoutubeSabrInfo) audioStream.getDeliveryMethodInfo();
        final String trackId = audioStream.getAudioTrackId();
        final YoutubeSabrInfo.Format audioFormat = sabrInfo.getFormats().stream()
                .filter(YoutubeSabrInfo.Format::isAudio)
                .filter(format -> format.getItag() == audioStream.getItagItem().id)
                .filter(format -> trackId == null
                        || trackId.equals(format.getAudioTrackId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Could not map extracted SABR audio stream to its format: itag="
                                + audioStream.getItagItem().id + " track=" + trackId));

        final File spoolDirectory = new File(context.getCacheDir(),
                "sabr-audio-probe-" + System.nanoTime());
        final YoutubeSabrSession session = new YoutubeSabrSession(sabrInfo, spoolDirectory);
        session.setPoToken(sabrInfo.getPoToken());

        final long requestedStartMs = 65_000L;
        final long requiredMediaMs = 2_000L;
        final AtomicBoolean found = new AtomicBoolean();
        final AtomicReference<String> observed = new AtomicReference<>("");
        try {
            for (int attempt = 1; attempt <= 8 && !found.get(); attempt++) {
                final YoutubeSabrSession.RequestResult result = session.requestOnce(
                        YoutubeSabrRequest.playback(requestedStartMs, 1.0f,
                                Collections.singletonList(YoutubeSabrRequest.Track.of(
                                        audioFormat, null, 0))),
                        segment -> {
                            try {
                                if (segment.getHeader().isInitSegment()) return;
                                final long startMs = segment.getHeader().getStartMs();
                                final long durationMs = segment.getHeader().getDurationMs();
                                final long usableMs = startMs < 0 || durationMs < 0 ? -1
                                        : startMs + durationMs
                                        - Math.max(startMs, requestedStartMs);
                                observed.set("itag=" + segment.getHeader().getItag()
                                        + " sequence=" + segment.getHeader().getSequenceNumber()
                                        + " startMs=" + startMs
                                        + " durationMs=" + durationMs
                                        + " usableAfter65sMs=" + usableMs);
                                if (usableMs > requiredMediaMs) found.set(true);
                            } finally {
                                segment.delete();
                            }
                        });
                final long backoffMs = Math.max(result.getBackoffMs(),
                        session.getBackoffRemainingMs());
                if (!found.get() && backoffMs > 0) {
                    SystemClock.sleep(Math.min(backoffMs + 10, 30_000));
                }
            }
        } catch (final Exception error) {
            printSabrAudioProbeTrace("failure", session);
            throw error;
        }

        printSabrAudioProbeTrace("complete", session);
        System.out.println("SABR_AUDIO_PROBE url=" + url
                + " videoId=" + streamInfo.getId()
                + " itag=" + audioFormat.getItag()
                + " observed={" + observed.get() + '}');
        assertTrue("Did not receive more than 2 seconds of SABR audio at/after 65 seconds; "
                        + "lastSegment={" + observed.get() + "} trace="
                        + session.getDiagnosticTrace(),
                found.get());
    }

    private static void printSabrAudioProbeTrace(
            @Nonnull final String outcome,
            @Nonnull final YoutubeSabrSession session) {
        final String trace = session.getDiagnosticTrace();
        final String[] events = trace.isEmpty() ? new String[0] : trace.split(" \\| ");
        for (int index = 0; index < events.length; index++) {
            System.out.println("SABR_AUDIO_PROBE_TRACE outcome=" + outcome
                    + " event=" + (index + 1) + " " + events[index]);
        }
    }

    @Test
    public void anonymousSequentialAudioCrossesSabrProtectionBoundaries() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String playlistUrl = arguments.getString("anonymousPlaylistUrl", "");
        assumeTrue("Set anonymousPlaylistUrl to run the sequential anonymous SABR probe",
                !playlistUrl.isEmpty());

        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue("The target process must use PipePlay's App initialization",
                context instanceof App);
        final int videoCount = Integer.parseInt(arguments.getString(
                "anonymousVideoCount", "10"));
        final long playbackMs = Long.parseLong(arguments.getString(
                "anonymousPlaybackMs", "130000"));
        assertTrue("The probe must cross the 60s SABR protection boundary",
                playbackMs > 60_000);

        ServiceList.YouTube.setTokens("");
        NewPipe.setYoutubePlayerClient("mweb");

        final PlaylistInfo playlist = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl);
        assertTrue("Playlist has fewer items than requested: requested=" + videoCount
                        + " actual=" + playlist.getRelatedItems().size(),
                playlist.getRelatedItems().size() >= videoCount);

        for (int index = 0; index < videoCount; index++) {
            final StreamInfoItem item = playlist.getRelatedItems().get(index);
            final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, item.getUrl());
            assertTrue("Extractor returned no SABR audio stream for item=" + index
                            + " video=" + info.getId(),
                    info.getAudioStreams().stream().anyMatch(SabrPlaybackSmokeTest::isSabr));
            runAnonymousAudioWindow(context, info, index, playbackMs);
        }
    }

    @Test
    public void playbackIntoSponsorBlockSkipsToDuration() throws Exception {
        runSmokeCase(SmokeCase.sponsorBlockPlayback());
    }

    @Test
    public void seekIntoSponsorBlockSkipsToDuration() throws Exception {
        runSmokeCase(SmokeCase.sponsorBlockSeek());
    }

    @Test
    public void demandRepositionsAfterNonTargetMediaBatch() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.setPlayerTimeMs(20_000);
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 5_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_VIDEO_ITAG, 4, 15_000, 5_000)
                    .segment(3, SMOKE_VIDEO_ITAG, 5, 20_000, 5_000)
                    .segment(4, SMOKE_VIDEO_ITAG, 6, 25_000, 5_000)
                    .segment(5, SMOKE_VIDEO_ITAG, 7, 30_000, 5_000)
                    .segment(6, SMOKE_VIDEO_ITAG, 8, 35_000, 5_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(7, SMOKE_VIDEO_ITAG, 3, 10_000, 5_000)
                    .bytes());

            harness.openMediaSegment(
                    SabrSegmentKey.media(harness.videoFormat, 3), 5_000);

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("A non-target media batch did not reach the target response: " + trace,
                    trace.contains("response n=2"));
            assertTrue("Expected initial, non-target, and repositioned target requests",
                    harness.downloader.requestBodies.size() >= 3);
            final String repositionedRequest = SabrRequestDumper.summarize(
                    harness.downloader.requestBodies.get(2));
            assertTrue("Repositioned demand did not report the target as the next segment: "
                            + repositionedRequest,
                    repositionedRequest.contains("seq=1-2"));
            assertTrue("Repositioned demand kept the ahead-of-hole player time: "
                            + repositionedRequest,
                    repositionedRequest.contains("playerTimeMs=10000"));
        }
    }

    @Test
    public void companionOnlyResponseTriggersDemandRecovery() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 5_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_AUDIO_ITAG, 1, 0, 5_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(3, SMOKE_VIDEO_ITAG, 3, 10_000, 5_000)
                    .bytes());

            harness.openMediaSegment(
                    SabrSegmentKey.media(harness.videoFormat, 3), 5_000);

            assertEquals("Companion-only response did not trigger another SABR request",
                    3, harness.downloader.requestBodies.size());
            assertEquals("Demand returned the wrong segment bytes",
                    4, harness.getLastSegmentData().length);
        }
    }

    @Test
    public void repeatedNonTargetMediaBatchesFailWithinDemandBudget() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 5_000)
                    .bytes());
            for (int response = 0; response < 3; response++) {
                harness.downloader.enqueue(new UmpFixture()
                        .segment(2 + response, SMOKE_VIDEO_ITAG,
                                4 + response, 15_000 + response * 5_000L, 5_000)
                        .bytes());
            }

            harness.openMediaSegmentExpectFailure(
                    SabrSegmentKey.media(harness.videoFormat, 3), 5_000);

            assertEquals("Demand did not stop after the queued non-target responses",
                    5, harness.downloader.requestBodies.size());
        }
    }

    @Test
    public void demandHonorsFullServerBackoff() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY, nextRequestPolicy(3_000))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final long elapsedMs = harness.openMediaSegment(
                    SabrSegmentKey.media(harness.videoFormat, 2), 6_000);

            final List<Long> requestTimesMs = harness.downloader.requestTimesSnapshot();
            assertTrue("Expected initial, policy-only, and target requests: " + requestTimesMs,
                    requestTimesMs.size() >= 3);
            final long retryDelayMs = requestTimesMs.get(2) - requestTimesMs.get(1);
            assertTrue("Demand retry ignored the server backoff entirely: delayMs="
                            + retryDelayMs,
                    retryDelayMs >= 2_800);
            assertTrue("Demand retry did not honor the full server backoff: elapsedMs="
                            + elapsedMs, elapsedMs < 5_000);
        }
    }

    @Test
    public void demandBackoffRemainsCancelableWithoutEarlyRequest() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000).bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY, nextRequestPolicy(3_000))
                    .bytes());
            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            final SabrSegmentDataSource dataSource = new SabrSegmentDataSource(
                    harness.holder.spec, harness.holder.bridge);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final Thread loader = new Thread(() -> {
                try {
                    dataSource.open(new DataSpec(harness.segmentUri(request)));
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            }, "SabrSmokeCancelableBackoff");
            loader.start();
            boolean completed;
            try {
                final long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (harness.holder.session.getBackoffRemainingMs() == 0
                        && System.nanoTime() < deadlineNs) {
                    Thread.sleep(25);
                }
                assertTrue("Demand did not enter the server backoff: "
                                + harness.holder.session.getDiagnosticTrace(),
                        harness.holder.session.getBackoffRemainingMs() > 0);
                harness.holder.bridge.stop();
                completed = done.await(1_500, TimeUnit.MILLISECONDS);
                Thread.sleep(250);
            } finally {
                dataSource.close();
                loader.interrupt();
                done.await(2, TimeUnit.SECONDS);
            }
            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Backoff kept the stale loader blocked: " + trace, completed);
            assertTrue("Backoff cancellation should surface as a recoverable load failure: "
                            + failure.get(), failure.get() instanceof IOException);
            assertEquals("Cancellation sent a request before the server deadline: " + trace,
                    2, harness.downloader.requestTimesSnapshot().size());
            assertTrue("Backoff cancellation failed the shared session: " + trace,
                    !trace.contains("terminal_failure"));
        }
    }

    @Test
    public void demandBackoffPublishesStandaloneNotificationWhileBuffering() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        final SabrBackoffCoordinator coordinator = SabrBackoffCoordinator.getInstance();
        coordinator.setPlayerBuffering(context, true);
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY, nextRequestPolicy(3_000))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final CountDownLatch completed = new CountDownLatch(1);
            final Thread demand = new Thread(() -> {
                try {
                    harness.openMediaSegment(
                            SabrSegmentKey.media(harness.videoFormat, 2), 5_000);
                } catch (final Throwable error) {
                    failure.set(error);
                } finally {
                    completed.countDown();
                }
            }, "SabrBackoffNotificationSmoke");
            demand.start();

            final StatusBarNotification notification = awaitBackoffNotification(context, true);
            assertNotNull("SABR demand backoff did not publish its standalone notification",
                    notification);
            assertTrue("Demand completed before the backoff notification was observed",
                    completed.getCount() > 0);
            assertTrue("SABR demand did not recover after the server backoff",
                    completed.await(5, TimeUnit.SECONDS));
            assertNull("SABR demand failed after the server backoff", failure.get());
            assertNull("Backoff notification remained after the demanded segment recovered",
                    awaitBackoffNotification(context, false));
        } finally {
            coordinator.setPlayerBuffering(context, false);
        }
    }

    @Test
    public void rejectedAttestationFailsWithoutEnteringBackoff() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.STREAM_PROTECTION_STATUS,
                            streamProtection(3, 20))
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY,
                            nextRequestPolicy(59_000))
                    .bytes());

            final long startMs = System.currentTimeMillis();
            harness.openMediaSegmentExpectFailure(
                    SabrSegmentKey.media(harness.videoFormat, 2), 5_000);
            final long elapsedMs = System.currentTimeMillis() - startMs;

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Rejected attestation response was not exercised: " + trace,
                    trace.contains("protection=3/20"));
            assertTrue("Rejected attestation incorrectly entered the 59 second backoff: elapsedMs="
                            + elapsedMs + " trace=" + trace,
                    elapsedMs < 2_000);
            assertTrue("Rejected attestation triggered another SABR request: "
                            + harness.downloader.requestTimesSnapshot(),
                    harness.downloader.requestTimesSnapshot().size() <= 2);
        }
    }

    @Test
    public void pendingAttestationDoesNotReloadOrFail() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.STREAM_PROTECTION_STATUS,
                            streamProtection(2, 20))
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY,
                            nextRequestPolicy(2_000))
                    .bytes());

            final YoutubeSabrSession.RequestResult result =
                    harness.holder.session.pumpOnceStreamingForDemand(
                            new Localization("en", "US"),
                            SabrSegmentKey.media(harness.videoFormat, 1));

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Pending attestation response was not exercised: " + trace,
                    trace.contains("protection=2/20"));
            assertTrue("Pending attestation did not return through normal response handling",
                    !result.isDeferred());
            assertEquals("Pending attestation unexpectedly returned media", 0,
                    result.getSegmentCount());
            assertEquals("Pending attestation triggered an implicit retry", 1,
                    harness.downloader.requestTimesSnapshot().size());
        }
    }

    @Test
    public void nearEdgeServerBackoffsDoNotTriggerLocalRecovery() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            for (int i = 0; i < 6; i++) {
                harness.downloader.enqueue(new UmpFixture()
                        .part(SabrResponseDecoder.NEXT_REQUEST_POLICY,
                                nextRequestPolicy(2_000))
                        .bytes());
            }
            harness.downloader.enqueue(new UmpFixture()
                    .segment(8, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final long elapsedMs = harness.openMediaSegment(
                    SabrSegmentKey.media(harness.videoFormat, 2), 15_000);

            assertTrue("Demand did not preserve the repeated server backoffs: elapsedMs="
                            + elapsedMs,
                    elapsedMs >= 11_500);
            assertEquals("Repeated pacing responses triggered an extra recovery request",
                    8, harness.downloader.requestBodies.size());
        }
    }

    @Test
    public void staleReaderDemandStopsWithoutFailingSession() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            final CountDownLatch responseStarted = new CountDownLatch(1);
            final CountDownLatch releaseResponse = new CountDownLatch(1);
            harness.downloader.enqueue(() -> {
                responseStarted.countDown();
                try {
                    if (!releaseResponse.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release stale demand response");
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting to release stale demand response", e);
                }
                return new ByteArrayInputStream(new UmpFixture()
                        .segment(2, SMOKE_VIDEO_ITAG, 3, 60_000, 5_000)
                        .bytes());
            });

            final SabrSegmentKey request =
                    SabrSegmentKey.media(harness.videoFormat, 2);
            final SabrSegmentDataSource dataSource = new SabrSegmentDataSource(
                    harness.holder.spec, harness.holder.bridge);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final Thread loader = new Thread(() -> {
                try {
                    dataSource.open(new DataSpec(harness.segmentUri(request)));
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            }, "SabrSmokeStaleDemand");
            loader.start();

            boolean completed;
            try {
                assertTrue("Demand request did not reach the controlled response",
                        responseStarted.await(5, TimeUnit.SECONDS));
                harness.advanceReaderGeneration();
                releaseResponse.countDown();
                completed = done.await(1_500, TimeUnit.MILLISECONDS);
            } finally {
                releaseResponse.countDown();
                dataSource.close();
                loader.interrupt();
                done.await(2, TimeUnit.SECONDS);
            }

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Stale reader demand kept waiting until the no-progress watchdog: " + trace,
                    completed);
            assertTrue("Stale reader demand should end as a recoverable load cancellation: "
                            + failure.get(),
                    failure.get() instanceof IOException);
            assertTrue("Stale reader demand failed the shared SABR session: " + trace,
                    !trace.contains("terminal_failure"));
            assertTrue("Media-bearing stale demand changed the session after cancellation: " + trace,
                    !trace.contains("pump_demand_target_miss itag=" + SMOKE_VIDEO_ITAG + " seq=2")
                            && !trace.contains("pump_demand_reposition itag="
                            + SMOKE_VIDEO_ITAG + " seq=2")
                            && !trace.contains("pump_demand_failed itag="
                            + SMOKE_VIDEO_ITAG + " seq=2"));
        }
    }

    @Test
    public void interruptedUmpReadStopsDemandWithoutFailingSession() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(() -> new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new InterruptedIOException("Interrupted while reading UMP stream");
                }
            });
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final SabrSegmentKey request =
                    SabrSegmentKey.media(harness.videoFormat, 2);
            final SabrSegmentDataSource dataSource = new SabrSegmentDataSource(
                    harness.holder.spec, harness.holder.bridge);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final Thread loader = new Thread(() -> {
                try {
                    dataSource.open(new DataSpec(harness.segmentUri(request)));
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    done.countDown();
                }
            }, "SabrSmokeInterruptedDemand");
            loader.start();

            final boolean completed;
            try {
                completed = done.await(1_500, TimeUnit.MILLISECONDS);
            } finally {
                dataSource.close();
                loader.interrupt();
                done.await(2, TimeUnit.SECONDS);
            }

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Interrupted UMP read was retried until the watchdog: " + trace,
                    completed);
            assertTrue("Interrupted UMP read should surface as a recoverable load failure: "
                            + failure.get(),
                    failure.get() instanceof IOException);
            assertTrue("Interrupted UMP read failed the shared SABR session: " + trace,
                    !trace.contains("terminal_failure"));
            harness.openMediaSegment(request, 1_500);
        }
    }

    @Test
    public void demandIncompleteMediaResponseRetriesThroughPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .media(2)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(3, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            harness.openMediaSegment(request, 5_000);

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Incomplete media response was not exercised: " + trace,
                    trace.contains("missing-media-end:2"));
            assertEquals("Demand retry returned unexpected target bytes: " + trace,
                    4, harness.getLastSegmentData().length);
        }
    }

    @Test
    public void demandRecoverableIntegrityShapesRetryThroughPump() throws Exception {
        verifyDemandIntegrityRetry("length-mismatch:2", new UmpFixture()
                .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000, 4)
                .media(2, new byte[]{10, 11})
                .mediaEnd(2));
        verifyDemandIntegrityRetry("missing-media:2", new UmpFixture()
                .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                .mediaEnd(2));
        verifyDemandIntegrityRetry("media-without-header:2", new UmpFixture()
                .media(2)
                .mediaEnd(2));
        verifyDemandIntegrityRetry("media-end-without-header:2", new UmpFixture()
                .mediaEnd(2));
    }

    @Test
    public void malformedControlPartDoesNotDropMediaInPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 1);
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY, new byte[]{0x0f})
                    .segment(1, SMOKE_VIDEO_ITAG, 1)
                    .bytes());

            assertEquals(1, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertNotNull("Malformed control part caused media to be dropped: " + trace,
                    harness.holder.session.getCachedSegment(request));
            assertTrue("Malformed control part was not exercised: " + trace,
                    trace.contains("malformedParts=[35:1:"));
        }
    }

    @Test
    public void malformedMediaHeaderRetriesThroughDemandPump() throws Exception {
        verifyDemandIntegrityRetry("media-without-header:2", new UmpFixture()
                .part(SabrResponseDecoder.MEDIA_HEADER, new byte[]{0x0f})
                .media(2)
                .mediaEnd(2));
    }

    @Test
    public void duplicateMediaHeaderFailsThroughDemandPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .mediaHeader(2, SMOKE_VIDEO_ITAG, 3, 35_000, 5_000)
                    .bytes());

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            harness.openMediaSegmentExpectFailure(request, 5_000);

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Duplicate media header was not exercised: " + trace,
                    trace.contains("duplicate-media-header:2"));
        }
    }

    @Test
    public void demandPendingAttestationHonorsServerBackoff() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.STREAM_PROTECTION_STATUS, streamProtection(2, 7))
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY, nextRequestPolicy(3_000))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final long elapsedMs = harness.openMediaSegment(
                    SabrSegmentKey.media(harness.videoFormat, 2), 6_000);

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Pending attestation response was not exercised: " + trace,
                    trace.contains("protection=2/7"));
            final List<Long> requestTimesMs = harness.downloader.requestTimesSnapshot();
            assertTrue("Expected initial, protected, and target requests: " + requestTimesMs,
                    requestTimesMs.size() >= 3);
            final long retryDelayMs = requestTimesMs.get(2) - requestTimesMs.get(1);
            assertTrue("Pending attestation next request ignored backoff: delayMs=" + retryDelayMs
                            + " trace=" + trace,
                    retryDelayMs >= 2_800);
            assertTrue("Pending attestation did not honor the server backoff: elapsedMs="
                            + elapsedMs, elapsedMs < 5_000);
        }
    }

    @Test
    public void requestPolicyLiveAndInitializationMetadataUpdateSessionState()
            throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.NEXT_REQUEST_POLICY,
                            nextRequestPolicy(2_000, playbackCookie(), "smoke-video"))
                    .part(SabrResponseDecoder.LIVE_METADATA,
                            liveMetadata(40, 200_000, true))
                    .part(SabrResponseDecoder.FORMAT_INITIALIZATION_METADATA,
                            initializationMetadata(SMOKE_VIDEO_ITAG, 60, 300_000, "video/webm"))
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            final String trace = harness.holder.session.getDiagnosticTrace();
            final long remainingMs = harness.holder.session.getBackoffRemainingMs();
            assertTrue("Policy backoff was not applied: " + remainingMs,
                    remainingMs > 0 && remainingMs <= 2_000);
            final Method getRawPlaybackCookie = YoutubeSabrSession.class
                    .getDeclaredMethod("getRawPlaybackCookie");
            getRawPlaybackCookie.setAccessible(true);
            assertNotNull("Playback cookie was not applied: " + trace,
                    getRawPlaybackCookie.invoke(harness.holder.session.delegate));
            assertTrue("Live metadata was not applied: " + trace,
                    harness.holder.session.delegate.isLive());
            assertTrue("Post-live DVR flag was not applied: " + trace,
                    harness.holder.session.delegate.isPostLiveDvr());
        }
    }

    @Test
    public void timelinePreparationKeepsPositionWithoutSelectingTracks() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            final long initialPositionMs = 65_000L;
            final byte[] audioInitialization = mp4Sidx(5_000, 5_000, 5_000);
            final byte[] videoInitialization = mp4Sidx(5_000, 5_000, 5_000);
            final byte[] media = new byte[]{4, 5, 6, 7};
            final Field initializationData = SabrSourceSpec.class
                    .getDeclaredField("initializationData");
            initializationData.setAccessible(true);
            ((Map<?, ?>) initializationData.get(harness.holder.spec)).clear();
            harness.holder.setBridgeTimeline("audioTimeline", null);
            harness.holder.setBridgeTimeline("videoTimeline", null);

            harness.downloader.enqueue(new UmpFixture()
                    .mediaHeader(1, SMOKE_AUDIO_ITAG, 0, 0, 0,
                            audioInitialization.length, 0, true)
                    .media(1, audioInitialization)
                    .mediaEnd(1)
                    .mediaHeader(2, SMOKE_VIDEO_ITAG, 0, 0, 0,
                            videoInitialization.length, 0, true)
                    .media(2, videoInitialization)
                    .mediaEnd(2)
                    .mediaHeader(3, SMOKE_VIDEO_ITAG, 2, initialPositionMs, 5_000,
                            media.length)
                    .media(3, media)
                    .mediaEnd(3)
                    .bytes());
            harness.holder.bridge.prepareTimelines(initialPositionMs);

            assertTrue(harness.holder.bridge.hasTimelines());
            assertEquals(1, harness.downloader.requestBodies.size());
            final String request = SabrRequestDumper.summarize(
                    harness.downloader.requestBodies.get(0));
            assertTrue("Timeline preparation advertised selected tracks: " + request,
                    request.contains("selected=[]"));
            assertTrue("Timeline preparation advertised a buffered range: " + request,
                    request.contains("ranges=[]"));
            assertTrue("Timeline preparation omitted the playback position: " + request,
                    request.contains("topPlayerTimeMs=" + initialPositionMs));
            assertTrue("Timeline preparation omitted the preferred video format: " + request,
                    request.contains("prefVideo=[itag:" + SMOKE_VIDEO_ITAG));

            harness.openSegment(SabrSegmentKey.initialization(harness.videoFormat), 5_000);
            assertArrayEquals("Prepared initialization data changed",
                    videoInitialization, harness.getLastSegmentData());
            harness.openSegment(SabrSegmentKey.media(harness.videoFormat, 2), 5_000);
            assertArrayEquals("Preparation media was not served from the bridge cache",
                    media, harness.getLastSegmentData());
            assertEquals("Opening cached preparation media sent another request",
                    1, harness.downloader.requestBodies.size());
        }
    }

    @Test
    public void redirectUpdatesFollowUpSabrStreamingUrl() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_REDIRECT,
                            redirect("https://redirect.googlevideo.com/sabr"))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1)
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));
            assertEquals(1, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            assertTrue("First SABR request did not use original URL: "
                            + harness.downloader.requestedUrls,
                    harness.downloader.requestedUrls.get(0).contains("https://sabr.test"));
            assertTrue("Follow-up SABR request did not use redirect URL: "
                            + harness.downloader.requestedUrls,
                    harness.downloader.requestedUrls.get(1)
                            .contains("https://redirect.googlevideo.com/sabr"));
        }
    }

    @Test
    public void sabrErrorFailsThroughPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_ERROR, sabrError("blocked", 403))
                    .bytes());

            try {
                harness.holder.session.pumpOnceStreaming(new Localization("en", "US"));
            } catch (final Exception expected) {
                final String trace = harness.holder.session.getDiagnosticTrace();
                assertTrue("SABR error details were not decoded: " + trace,
                        trace.contains("type=blocked, code=403"));
                return;
            }
            throw new AssertionError("SABR error response did not fail the pump");
        }
    }

    @Test
    public void reloadPlayerResponseFailsBoundedThroughPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.RELOAD_PLAYER_RESPONSE,
                            reloadPlayerResponse("reload-token"))
                    .bytes());

            try {
                harness.holder.session.pumpOnceStreaming(new Localization("en", "US"));
            } catch (final Exception expected) {
                final String trace = harness.holder.session.getDiagnosticTrace();
                assertTrue("Reload response did not mark no-media reload state: " + trace,
                        trace.contains("reload=true"));
                return;
            }
            throw new AssertionError("SABR reload response unexpectedly succeeded");
        }
    }

    @Test
    public void unknownAndGenericControlsRemainDiagnosticsInPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(99, proto().u64(1, 7).bytes())
                    .part(SabrResponseDecoder.CONFIG, proto().u64(2, 9).bytes())
                    .part(SabrResponseDecoder.REQUEST_IDENTIFIER,
                            requestIdentifier("request-token"))
                    .part(SabrResponseDecoder.SNACKBAR_MESSAGE, snackbar(12))
                    .part(SabrResponseDecoder.REQUEST_CANCELLATION_POLICY, cancellationPolicy())
                    .part(SabrResponseDecoder.PREWARM_CONNECTION, prewarmConnection())
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Unknown part was not retained for diagnostics: " + trace,
                    trace.contains("unknownParts=[99]"));
            assertTrue("CONFIG control was not summarized: " + trace,
                    trace.contains("30=[2=9]"));
            assertTrue("Request identifier was not summarized: " + trace,
                    trace.contains("52=[1=bytes(13)]"));
            assertTrue("Snackbar was not summarized: " + trace,
                    trace.contains("67=[1=12]"));
            assertTrue("Cancellation policy was not summarized: " + trace,
                    trace.contains("53=[1=1"));
            assertTrue("Prewarm connection was not summarized: " + trace,
                    trace.contains("65=[1=bytes(7)]"));
        }
    }

    @Test
    public void advancedControlsRemainDiagnosticsInPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_SEEK, sabrSeek(45_000, 1000, 2))
                    .part(SabrResponseDecoder.PLAYBACK_START_POLICY, playbackStartPolicy())
                    .part(SabrResponseDecoder.FORMAT_SELECTION_CONFIG, formatSelectionConfig())
                    .part(SabrResponseDecoder.SELECTABLE_FORMATS, selectableFormats())
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Advanced control parts were not retained: " + trace,
                    trace.contains("parts=[45:9, 47:20, 37:22, 51:66]"));
            assertTrue("Advanced controls were not retained: " + trace,
                    trace.contains("controls={45=") && trace.contains("47=")
                            && trace.contains("37=") && trace.contains("51="));
        }
    }

    @Test
    public void onesieControlsRemainDiagnosticsInPump() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.ONESIE_HEADER, onesieHeader(0, 1, false))
                    .part(SabrResponseDecoder.ONESIE_DATA, onesieInnertubeResponse())
                    .part(SabrResponseDecoder.ONESIE_HEADER, onesieHeader(25, 2, true))
                    .part(SabrResponseDecoder.ONESIE_ENCRYPTED_MEDIA, new byte[]{1, 2, 3})
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Onesie parts were not retained: " + trace,
                    trace.contains("parts=[10:74, 11:26, 10:83, 12:3]"));
            assertTrue("Onesie controls were not retained: " + trace,
                    trace.contains("controls={10=") && trace.contains("11=")
                            && trace.contains("12="));
        }
    }

    @Test
    public void contextKeepExistingAndDiscardUpdateSessionState() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_CONTEXT_UPDATE,
                            contextUpdate(30, new byte[]{1}, true, 1))
                    .part(SabrResponseDecoder.SABR_CONTEXT_UPDATE,
                            contextUpdate(30, new byte[]{2}, false, 2))
                    .part(SabrResponseDecoder.SABR_CONTEXT_UPDATE,
                            contextUpdate(40, new byte[]{3}, true, 1))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_CONTEXT_SENDING_POLICY,
                            contextPolicy(new int[0], new int[0], new int[]{40}))
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));
            assertTrue("Context 30 should be active after first update",
                    activeContextTypes(harness).contains(30));
            assertTrue("KEEP_EXISTING should not make context 30 unsent",
                    !unsentContextTypes(harness).contains(30));

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            assertTrue("Context 40 was not discarded",
                    !activeContextTypes(harness).contains(40)
                            && !unsentContextTypes(harness).contains(40));
        }
    }

    @Test
    public void compressedMediaSegmentCachesDecompressedBytesThroughDemandPump()
            throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            final byte[] raw = new byte[]{40, 41, 42, 43, 44};
            final byte[] gzipped = gzip(raw);
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000,
                            gzipped.length, 1)
                    .media(2, gzipped)
                    .mediaEnd(2)
                    .bytes());

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            harness.openMediaSegment(request, 5_000);

            assertArrayEquals("Demand path did not return decompressed media bytes",
                    raw, harness.getLastSegmentData());
        }
    }

    @Test
    public void recoverableCompressedAndOverflowMediaRetryThroughDemandPump()
            throws Exception {
        verifyDemandIntegrityRetry("Could not decompress gzip SABR media segment",
                new UmpFixture()
                        .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000,
                                4, 1)
                        .media(2, new byte[]{1, 2, 3, 4})
                        .mediaEnd(2));
        verifyDemandIntegrityRetry("SABR media length overflow: headerId=2",
                new UmpFixture()
                        .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000, 1)
                        .media(2, new byte[]{1, 2})
                        .mediaEnd(2));
    }

    @Test
    public void terminalMediaCollectorErrorsFailThroughDemandPump() throws Exception {
        verifyDemandIntegrityFailure("SABR media segment too large: headerId=2",
                new UmpFixture()
                        .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000,
                                (long) Integer.MAX_VALUE + 1L)
                        .mediaEnd(2));
        verifyDemandIntegrityFailure("Unsupported SABR media compression: 99",
                new UmpFixture()
                        .mediaHeader(2, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000,
                                4, 99)
                        .media(2)
                        .mediaEnd(2));
    }

    @Test
    public void generatedLargeMediaPartStaysOffHeap() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String mediaBytesArgument = arguments.getString("sabrStressMediaBytes");
        assumeTrue("Set sabrStressMediaBytes to run the SABR heap pressure regression test",
                mediaBytesArgument != null);
        final int mediaBytes = Integer.parseInt(mediaBytesArgument);
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new GeneratedLargeMediaResponse(
                    2, SMOKE_VIDEO_ITAG, 1, 0, 5_000, mediaBytes));

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 1);
            final long beforeUsed = usedHeapBytes();
            harness.holder.session.pumpOnceStreaming(new Localization("en", "US"));

            final long peakCached = harness.holder.session.getPeakCachedBytes();
            final SabrMediaSegment segment = harness.holder.session.getCachedSegment(request);
            assertNotNull("Large SABR media segment was not cached", segment);
            System.out.println("SABR_OOM_REGRESSION mediaBytes=" + mediaBytes
                    + " beforeUsed=" + beforeUsed
                    + " afterUsed=" + usedHeapBytes()
                    + " peakCachedBytes=" + peakCached
                    + " diskBacked=" + segment.isDiskBacked()
                    + " trace=" + harness.holder.session.getDiagnosticTrace());
            assertEquals("Large SABR segment cache accounting changed", mediaBytes, peakCached);
            assertTrue("Large SABR media segment must be disk-backed", segment.isDiskBacked());
        }
    }

    @Test
    public void generatedSabrCachePressureStaysOffHeap()
            throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String segmentBytesArgument = arguments.getString("sabrStressSegmentBytes");
        assumeTrue("Set sabrStressSegmentBytes to run the accessibility OOM regression test",
                segmentBytesArgument != null);
        final int segmentBytes = Integer.parseInt(segmentBytesArgument);
        final int segmentCount = Integer.parseInt(arguments.getString(
                "sabrStressSegmentCount", "7"));
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            for (int i = 1; i <= segmentCount; i++) {
                harness.downloader.enqueue(new GeneratedLargeMediaResponse(
                        i, SMOKE_VIDEO_ITAG, i, (i - 1L) * 5_000L, 5_000L,
                        segmentBytes));
            }

            final long beforeUsed = usedHeapBytes();
            for (int i = 1; i <= segmentCount; i++) {
                harness.holder.session.pumpOnceStreaming(new Localization("en", "US"));
                final SabrMediaSegment segment = harness.holder.session.getCachedSegment(
                        SabrSegmentKey.media(harness.videoFormat, i));
                assertNotNull("Generated SABR segment was not cached: " + i, segment);
                assertTrue("Generated SABR media segment must be disk-backed: " + i,
                        segment.isDiskBacked());
                System.out.println("SABR_ACCESSIBILITY_OOM_REGRESSION cachedSegment=" + i
                        + " used=" + usedHeapBytes()
                        + " peakCachedBytes=" + harness.holder.session.getPeakCachedBytes());
            }

            final AtomicReference<Throwable> allocationFailure = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    AccessibilityEvent.obtain().recycle();
                } catch (final Throwable e) {
                    allocationFailure.set(e);
                }
            });

            final Throwable thrown = allocationFailure.get();
            final long expectedCachedBytes = (long) segmentBytes * segmentCount;
            System.out.println("SABR_ACCESSIBILITY_OOM_REGRESSION beforeUsed=" + beforeUsed
                    + " afterUsed=" + usedHeapBytes()
                    + " segmentBytes=" + segmentBytes
                    + " segmentCount=" + segmentCount
                    + " peakCachedBytes=" + harness.holder.session.getPeakCachedBytes()
                    + " allocationFailure=" + (thrown == null ? "" : messageChain(thrown)));
            assertNull("Accessibility small allocation failed after SABR cache pressure",
                    thrown);
            assertEquals("SABR cache accounting did not include generated media",
                    expectedCachedBytes, harness.holder.session.getPeakCachedBytes());
        }
    }

    @Test
    public void contextUpdateAndSendingPolicyUpdateSessionState() throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_CONTEXT_UPDATE,
                            contextUpdate(10, new byte[]{1}, true, 1))
                    .part(SabrResponseDecoder.SABR_CONTEXT_UPDATE,
                            contextUpdate(20, new byte[]{2}, false, 1))
                    .bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .part(SabrResponseDecoder.SABR_CONTEXT_SENDING_POLICY,
                            contextPolicy(new int[]{20}, new int[]{10}, new int[0]))
                    .bytes());

            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));
            assertEquals(0, harness.holder.session.pumpOnceStreaming(new Localization("en", "US")));

            assertTrue("Context 20 was not activated by sending policy",
                    activeContextTypes(harness).contains(20));
            assertTrue("Context 10 was not made unsent by sending policy",
                    unsentContextTypes(harness).contains(10));
        }
    }

    private static void runSmokeCase(final SmokeCase smokeCase) throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue("The target process must use PipePlay's App initialization",
                context instanceof App);

        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String cookieFile = arguments.getString("cookieFile", "");
        if (!cookieFile.isEmpty()) {
            ServiceList.YouTube.setTokens(readTextFile(new File(cookieFile)).trim());
        }
        final String url = arguments.getString("url",
                smokeCase.isSponsorBlockCase() ? RICKROLL_URL : DEFAULT_URL);
        final String client = arguments.getString("youtubeClient", "mweb");
        NewPipe.setYoutubePlayerClient(client);

        // This is intentionally live extraction: the test should detect upstream protocol changes.
        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
        assertTrue("Extractor returned no SABR video stream for client=" + client,
                info.getVideoStreams().stream().anyMatch(SabrPlaybackSmokeTest::isSabr)
                        || info.getVideoOnlyStreams().stream()
                        .anyMatch(SabrPlaybackSmokeTest::isSabr));

        final int maxVideoHeight = Integer.parseInt(arguments.getString("maxVideoHeight",
                String.valueOf(DEFAULT_MAX_VIDEO_HEIGHT)));
        final String targetCodec = arguments.getString("targetCodec", "");
        final long tailStartPositionMs;
        if (smokeCase.isSponsorBlockCase()) {
            final long extractedDurationMs = info.getDuration() * 1000L;
            assertTrue("Video is too short for the SponsorBlock tail test: "
                    + extractedDurationMs, extractedDurationMs > 30_000);
            tailStartPositionMs = extractedDurationMs - 30_000;
        } else {
            tailStartPositionMs = C.TIME_UNSET;
        }
        final PlayerDataSource dataSource = new PlayerDataSource(context,
                DownloaderImpl.USER_AGENT, new DefaultBandwidthMeter.Builder(context).build());
        final VideoPlaybackResolver resolver = new VideoPlaybackResolver(context, dataSource,
                new BoundedQualityResolver(maxVideoHeight, targetCodec));
        final MediaSource mediaSource = resolver.resolve(info,
                tailStartPositionMs == C.TIME_UNSET ? 0 : tailStartPositionMs);
        assertNotNull("VideoPlaybackResolver returned no MediaSource", mediaSource);
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch firstVideoFrame = new CountDownLatch(1);
        final CountDownLatch audioStarted = new CountDownLatch(1);
        final CountDownLatch ended = new CountDownLatch(1);
        final AtomicReference<CountDownLatch> seekProcessed =
                new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<PlaybackException> playerError = new AtomicReference<>();
        final AtomicReference<Long> seekPositionReported = new AtomicReference<>();
        final AtomicBoolean endedEarly = new AtomicBoolean();
        final AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        final AtomicReference<SurfaceTexture> textureRef = new AtomicReference<>();
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final SurfaceTexture texture = new SurfaceTexture(0);
            final Surface surface = new Surface(texture);
            final DefaultRenderersFactory renderersFactory =
                    new DefaultRenderersFactory(context);
            renderersFactory.setEnableDecoderFallback(true);
            final ExoPlayer player = new ExoPlayer.Builder(context, renderersFactory)
                    .setTrackSelector(new DefaultTrackSelector(context))
                    .setLoadControl(new LoadController())
                    .build();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(final int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        ready.countDown();
                    } else if (playbackState == Player.STATE_ENDED) {
                        endedEarly.set(true);
                        ended.countDown();
                    }
                }

                @Override
                public void onPlayerError(final PlaybackException error) {
                    playerError.compareAndSet(null, error);
                    ready.countDown();
                    firstVideoFrame.countDown();
                    audioStarted.countDown();
                }

                @Override
                public void onPositionDiscontinuity(final Player.PositionInfo oldPosition,
                                                    final Player.PositionInfo newPosition,
                                                    final int reason) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        seekPositionReported.set(newPosition.positionMs);
                        seekProcessed.get().countDown();
                    }
                }
            });
            player.addAnalyticsListener(new AnalyticsListener() {
                @Override
                public void onRenderedFirstFrame(final EventTime eventTime,
                                                 final Object output,
                                                 final long renderTimeMs) {
                    firstVideoFrame.countDown();
                }

                @Override
                public void onAudioPositionAdvancing(final EventTime eventTime,
                                                     final long playoutStartSystemTimeMs) {
                    audioStarted.countDown();
                }
            });
            player.setVideoSurface(surface);
            player.setVolume(0f);
            player.setMediaSource(mediaSource);
            if (tailStartPositionMs != C.TIME_UNSET) {
                player.seekTo(tailStartPositionMs);
            }
            player.prepare();
            player.play();
            textureRef.set(texture);
            surfaceRef.set(surface);
            playerRef.set(player);
        });

        try {
            assertTrue("Player did not reach READY within " + PREPARE_TIMEOUT_SECONDS + "s",
                    ready.await(PREPARE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("Player failed while preparing", playerError.get());
            assertTrue("MediaCodec did not render a video frame",
                    firstVideoFrame.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("Player failed while starting video", playerError.get());
            assertTrue("Audio output did not start",
                    audioStarted.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("Player failed while starting audio", playerError.get());
            if (smokeCase.isSponsorBlockCase()) {
                verifySponsorBlockSkipToEnd(playerRef.get(), smokeCase, tailStartPositionMs,
                        seekProcessed, seekPositionReported, playerError, ended);
                return;
            }

            final long linearPlaybackMs = Long.parseLong(arguments.getString(
                    "linearPlaybackMs", String.valueOf(DEFAULT_LINEAR_PLAYBACK_MS)));
            final long initialPositionMs = positionOf(playerRef.get());
            waitForPositionWithSabrProgress(playerRef.get(), info.getId(),
                    initialPositionMs + linearPlaybackMs, PLAYBACK_TIMEOUT_SECONDS, playerError);
            assertNull("Player failed during linear playback", playerError.get());

            final long postSeekPlaybackMs = Long.parseLong(arguments.getString(
                    "postSeekPlaybackMs", String.valueOf(DEFAULT_POST_SEEK_PLAYBACK_MS)));
            final long durationMs = durationOf(playerRef.get());
            final long seekPositionMs = seekPositionMs(arguments, durationMs,
                    postSeekPlaybackMs);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> playerRef.get().seekTo(seekPositionMs));
            assertTrue("Player did not report processing the seek",
                    seekProcessed.get().await(10, TimeUnit.SECONDS));
            assertNotNull("Seek discontinuity did not report a new position",
                    seekPositionReported.get());
            assertTrue("Seek landed outside the expected position: requested=" + seekPositionMs
                            + " reported=" + seekPositionReported.get(),
                    Math.abs(seekPositionReported.get() - seekPositionMs) <= 1_000);
            waitForPositionWithSabrProgress(playerRef.get(), info.getId(),
                    seekPositionMs + postSeekPlaybackMs, PLAYBACK_TIMEOUT_SECONDS, playerError);
            assertNull("Player failed after seek", playerError.get());
            assertTrue("Content ended before playback and seek checks completed",
                    !endedEarly.get() || durationMs < 8_000);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                final ExoPlayer player = playerRef.get();
                if (player != null) {
                    player.release();
                }
                final Surface surface = surfaceRef.get();
                if (surface != null) {
                    surface.release();
                }
                final SurfaceTexture texture = textureRef.get();
                if (texture != null) {
                    texture.release();
                }
            });
        }
    }

    private static String readTextFile(final File file) throws IOException {
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

    private static boolean isSabr(final VideoStream stream) {
        return stream.getDeliveryMethod() == DeliveryMethod.SABR;
    }

    private static boolean isSabr(final AudioStream stream) {
        return stream.getDeliveryMethod() == DeliveryMethod.SABR;
    }

    private static void runAnonymousAudioWindow(final Context context,
                                                final StreamInfo info,
                                                final int index,
                                                final long playbackMs) throws Exception {
        final PlayerDataSource dataSource = new PlayerDataSource(context,
                DownloaderImpl.USER_AGENT, new DefaultBandwidthMeter.Builder(context).build());
        final MediaSource mediaSource = new AudioPlaybackResolver(context, dataSource).resolve(info);
        assertNotNull("Audio resolver returned no MediaSource for item=" + index
                + " video=" + info.getId(), mediaSource);
        assertTrue("Audio resolver did not return a SABR MediaSource for item=" + index
                + " video=" + info.getId(), mediaSource instanceof SabrDashMediaSource);
        final SabrDashMediaSource sabrMediaSource = (SabrDashMediaSource) mediaSource;

        final AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        final AtomicReference<PlaybackException> playerError = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final ExoPlayer player = new ExoPlayer.Builder(context)
                    .setLoadControl(new LoadController())
                    .build();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(final int state) {
                    if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                        ready.countDown();
                    }
                }

                @Override
                public void onPlayerError(final PlaybackException error) {
                    playerError.compareAndSet(null, error);
                    ready.countDown();
                }
            });
            player.setVolume(0f);
            player.setMediaSource(mediaSource);
            player.prepare();
            player.play();
            playerRef.set(player);
        });

        try {
            assertTrue("Anonymous audio item did not become ready: index=" + index
                            + " video=" + info.getId(),
                    ready.await(PREPARE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("Anonymous audio item failed during startup: index=" + index
                    + " video=" + info.getId(), playerError.get());
            final long durationMs = durationOf(playerRef.get());
            assertTrue("Anonymous probe item is too short to cross 60s: index=" + index
                    + " video=" + info.getId() + " durationMs=" + durationMs,
                    durationMs > 61_000);
            final long targetMs = Math.min(playbackMs, durationMs - 1_000);
            waitForPositionWithSabrProgress(playerRef.get(), info.getId(), targetMs,
                    TimeUnit.MILLISECONDS.toSeconds(targetMs) + PLAYBACK_TIMEOUT_SECONDS,
                    playerError);
            assertNull("Anonymous audio item failed during playback: index=" + index
                            + " video=" + info.getId(),
                    playerError.get());
            assertTrue("Anonymous audio item did not reach target: index=" + index
                            + " video=" + info.getId() + " targetMs=" + targetMs
                            + " positionMs=" + positionOf(playerRef.get()),
                    positionOf(playerRef.get()) >= targetMs);
            System.out.println("SABR_ANONYMOUS_SEQUENCE index=" + index
                    + " video=" + info.getId()
                    + " positionMs=" + positionOf(playerRef.get())
                    + " maxStreamProtectionStatus="
                    + sabrMediaSource.getMaxStreamProtectionStatus());
        } catch (final Exception | AssertionError failure) {
            System.out.println("SABR_ANONYMOUS_SEQUENCE_FAILURE index=" + index
                    + " video=" + info.getId()
                    + " positionMs=" + (playerRef.get() == null ? -1
                    : positionOf(playerRef.get())));
            throw failure;
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (playerRef.get() != null) {
                    playerRef.get().release();
                }
            });
        }
    }

    private static int backoffNotificationId() {
        try {
            final Field field = SabrBackoffCoordinator.class.getDeclaredField("NOTIFICATION_ID");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static StatusBarNotification awaitBackoffNotification(
            final Context context, final boolean expected) {
        for (int attempt = 0; attempt < 100; attempt++) {
            final StatusBarNotification notification = findBackoffNotification(context);
            if ((notification != null) == expected) {
                return notification;
            }
            SystemClock.sleep(20L);
        }
        return findBackoffNotification(context);
    }

    private static StatusBarNotification findBackoffNotification(final Context context) {
        final NotificationManager manager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        for (final StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getId() == backoffNotificationId()) {
                return notification;
            }
        }
        return null;
    }

    private static void verifySponsorBlockSkipToEnd(
            final ExoPlayer player,
            final SmokeCase smokeCase,
            final long tailStartPositionMs,
            final AtomicReference<CountDownLatch> seekProcessed,
            final AtomicReference<Long> seekPositionReported,
            final AtomicReference<PlaybackException> playerError,
            final CountDownLatch ended) throws Exception {
        final long durationMs = durationOf(player);
        assertTrue("Cannot run SponsorBlock tail test when duration is unset",
                durationMs != C.TIME_UNSET);
        assertTrue("Video is too short for the SponsorBlock tail test: " + durationMs,
                durationMs > 30_000);
        assertTrue("Extractor and player durations disagree: extracted tail start="
                        + tailStartPositionMs + " player duration=" + durationMs,
                Math.abs(tailStartPositionMs - (durationMs - 30_000)) <= 1_000);
        final long sponsorStartMs = durationMs - 20_000;

        assertTrue("Playback did not start near duration - 30s: requested="
                        + tailStartPositionMs + " current=" + positionOf(player),
                positionOf(player) >= tailStartPositionMs - 1_000
                        && positionOf(player) < sponsorStartMs);
        if (smokeCase.kind == SmokeCase.Kind.SPONSOR_BLOCK_PLAYBACK) {
            waitForPosition(player, sponsorStartMs, PLAYBACK_TIMEOUT_SECONDS);
        } else {
            waitForPosition(player, tailStartPositionMs + 5_000, PLAYBACK_TIMEOUT_SECONDS);
            seekAndAssertPosition(player, sponsorStartMs, "SponsorBlock start",
                    seekProcessed, seekPositionReported);
        }

        seekAndAssertPosition(player, durationMs, "SponsorBlock end",
                seekProcessed, seekPositionReported);
        assertTrue("Player did not reach ENDED after SponsorBlock skipped to duration",
                ended.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNull("Player failed after SponsorBlock skipped to duration", playerError.get());
    }

    private static void seekAndAssertPosition(
            final ExoPlayer player,
            final long positionMs,
            final String description,
            final AtomicReference<CountDownLatch> seekProcessed,
            final AtomicReference<Long> seekPositionReported) throws Exception {
        seekProcessed.set(new CountDownLatch(1));
        seekPositionReported.set(null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> player.seekTo(positionMs));
        assertTrue("Player did not report processing the " + description + " seek",
                seekProcessed.get().await(10, TimeUnit.SECONDS));
        assertNotNull(description + " seek did not report a new position",
                seekPositionReported.get());
        assertTrue(description + " seek landed outside the expected position: requested="
                        + positionMs
                        + " reported=" + seekPositionReported.get(),
                Math.abs(seekPositionReported.get() - positionMs) <= 1_000);
    }

    private static long positionOf(final ExoPlayer player) {
        final AtomicReference<Long> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> result.set(player.getCurrentPosition()));
        return result.get();
    }

    private static long durationOf(final ExoPlayer player) {
        final AtomicReference<Long> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> result.set(player.getDuration()));
        return result.get();
    }

    private static long seekPositionMs(final Bundle arguments, final long durationMs,
                                       final long requiredTailMs) {
        assertTrue("Cannot use fixed seek position when duration is unset",
                durationMs != C.TIME_UNSET);
        final long seekPositionMs = Long.parseLong(arguments.getString("seekPositionMs",
                String.valueOf(DEFAULT_SEEK_POSITION_MS)));
        assertTrue("seekPositionMs must be positive: " + seekPositionMs, seekPositionMs > 0);
        assertTrue("Video is too short for seek target: duration=" + durationMs
                        + " target=" + seekPositionMs + " requiredTail=" + requiredTailMs,
                durationMs > seekPositionMs + requiredTailMs);
        return seekPositionMs;
    }

    private static void waitForPosition(final ExoPlayer player, final long targetMs,
                                        final long timeoutSeconds) throws Exception {
        final long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadlineNs) {
            if (positionOf(player) >= targetMs) {
                return;
            }
            Thread.sleep(250);
        }
        assertEquals("Playback position did not reach target", targetMs, positionOf(player));
    }

    private static void waitForPositionWithSabrProgress(final ExoPlayer player,
                                                        final String videoId,
                                                        final long targetMs,
                                                        final long timeoutSeconds,
                                                        final AtomicReference<PlaybackException>
                                                                playerError) throws Exception {
        final long deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadlineNs) {
            if (playerError.get() != null) {
                return;
            }
            final long positionMs = positionOf(player);
            if (positionMs >= targetMs) {
                return;
            }
            Thread.sleep(250);
        }
        assertEquals("Playback position did not reach target", targetMs, positionOf(player));
    }

    private static void verifyDemandIntegrityRetry(final String expectedIssue,
                                                   final UmpFixture brokenResponse)
            throws Exception {
        final String expectedTrace = expectedIssue.startsWith("length-mismatch:")
                ? "SABR media length mismatch: headerId="
                        + expectedIssue.substring("length-mismatch:".length())
                : expectedIssue.startsWith("missing-media:")
                ? "SABR media length mismatch: headerId="
                        + expectedIssue.substring("missing-media:".length())
                : expectedIssue;
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(brokenResponse.bytes());
            harness.downloader.enqueue(new UmpFixture()
                    .segment(3, SMOKE_VIDEO_ITAG, 2, 30_000, 5_000)
                    .bytes());

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            harness.openMediaSegment(request, 5_000);

            final String trace = harness.holder.session.getDiagnosticTrace();
            assertTrue("Integrity issue was not exercised: expected=" + expectedIssue
                    + " trace=" + trace, trace.contains(expectedTrace));
            assertEquals("Demand retry returned unexpected target bytes after " + expectedIssue
                            + ": " + trace,
                    4, harness.getLastSegmentData().length);
        }
    }

    private static void verifyDemandIntegrityFailure(final String expectedTrace,
                                                     final UmpFixture brokenResponse)
            throws Exception {
        try (SabrSmokeHarness harness = SabrSmokeHarness.create()) {
            harness.downloader.enqueue(new UmpFixture()
                    .segment(1, SMOKE_VIDEO_ITAG, 1, 0, 30_000)
                    .bytes());
            harness.downloader.enqueue(brokenResponse.bytes());

            final SabrSegmentKey request = SabrSegmentKey.media(harness.videoFormat, 2);
            harness.openMediaSegmentExpectFailure(request, 5_000);

            final String trace = waitForTrace(harness, expectedTrace, 2_000);
            assertTrue("Terminal integrity issue was not exercised: expected=" + expectedTrace
                    + " trace=" + trace, trace.contains(expectedTrace));
        }
    }

    private static String waitForTrace(final SabrSmokeHarness harness,
                                       final String expected,
                                       final long timeoutMs) throws Exception {
        final long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        String trace = harness.holder.session.getDiagnosticTrace();
        while (!trace.contains(expected) && System.nanoTime() < deadlineNs) {
            Thread.sleep(25);
            trace = harness.holder.session.getDiagnosticTrace();
        }
        return trace;
    }

    private static long usedHeapBytes() {
        final Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String messageChain(final Throwable throwable) {
        final StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(current.getClass().getSimpleName())
                    .append(':')
                    .append(current.getMessage());
            current = current.getCause();
        }
        return builder.toString();
    }

    private static YoutubeSabrInfo.Format smokeFormat(final int itag, final boolean audio)
            throws Exception {
        return smokeFormat(itag, audio, null, -1, -1);
    }

    private static YoutubeSabrInfo.Format smokeFormat(final int itag,
                                                 final boolean audio,
                                                 final String initializationUrl,
                                                 final long initRangeStart,
                                                 final long initRangeEnd)
            throws Exception {
        final ItagItem parsedFormat = ItagItem.getItag(itag);
        parsedFormat.setWidth(audio ? -1 : 1920);
        parsedFormat.setHeight(audio ? -1 : 1080);
        parsedFormat.setBitrate(audio ? 128_000 : 2_000_000);
        parsedFormat.setContentLength(100_000L);
        parsedFormat.setApproxDurationMs(300_000L);
        return YoutubeSabrInfo.Format.fromParsedFormat(parsedFormat, 123456L,
                audio ? "audio-xtags" : "video-xtags",
                audio ? "audio/mp4" : "video/mp4",
                audio ? "audio-track" : null,
                audio ? "English original" : null,
                false, initializationUrl, initRangeStart, initRangeEnd);
    }

    private static byte[] mp4Sidx(final int... durationsMs) {
        final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(32 + durationsMs.length * 12)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(buffer.capacity());
        buffer.put(new byte[]{'s', 'i', 'd', 'x'});
        buffer.putInt(0); // version + flags
        buffer.putInt(1); // reference ID
        buffer.putInt(1_000); // timescale
        buffer.putInt(0); // earliest presentation time
        buffer.putInt(0); // first offset
        buffer.putShort((short) 0);
        buffer.putShort((short) durationsMs.length);
        for (final int durationMs : durationsMs) {
            buffer.putInt(1); // referenced size
            buffer.putInt(durationMs);
            buffer.putInt(0); // SAP flags
        }
        return buffer.array();
    }

    private static YoutubeSabrInfo smokeInfo(final YoutubeSabrInfo.Format audioFormat,
                                             final YoutubeSabrInfo.Format videoFormat)
            throws Exception {
        final Constructor<YoutubeSabrInfo> constructor =
                YoutubeSabrInfo.class.getDeclaredConstructor(
                        String.class, String.class, String.class, String.class, String.class,
                        String.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance("smoke-video", "cpn",
                "2.20250122.04.00", "visitor", "https://sabr.test",
                base64(new byte[]{1, 2, 3, 4}), Arrays.asList(audioFormat, videoFormat));
    }

    private static byte[] nextRequestPolicy(final int backoffMs) {
        return nextRequestPolicy(backoffMs, null, null);
    }

    private static byte[] nextRequestPolicy(final int backoffMs,
                                            final byte[] playbackCookie,
                                            final String videoId) {
        final Proto proto = proto()
                .u64(1, 3_000)
                .u64(2, 4_000)
                .u64(3, 1_000)
                .u64(4, backoffMs)
                .u64(5, 500)
                .u64(6, 600);
        if (playbackCookie != null) {
            proto.message(7, playbackCookie);
        }
        if (videoId != null) {
            proto.string(8, videoId);
        }
        return proto.bytes();
    }

    private static byte[] streamProtection(final int status, final int maxRetries) {
        return proto()
                .u64(1, status)
                .u64(2, maxRetries)
                .bytes();
    }

    private static byte[] playbackCookie() {
        return proto()
                .u64(1, 1080)
                .u64(2, 1)
                .message(7, formatId(SMOKE_VIDEO_ITAG))
                .message(8, formatId(SMOKE_AUDIO_ITAG))
                .bytes();
    }

    private static byte[] formatId(final int itag) {
        return proto().u64(1, itag).u64(2, 123456).bytes();
    }

    private static byte[] liveMetadata(final long headSeq,
                                       final long headTimeMs,
                                       final boolean postLiveDvr) {
        return proto()
                .string(1, "broadcast")
                .u64(3, headSeq)
                .u64(4, headTimeMs)
                .u64(5, headTimeMs + 1000)
                .string(6, "smoke-video")
                .u64(8, postLiveDvr ? 1 : 0)
                .u64(12, 0)
                .u64(13, 1000)
                .u64(14, headTimeMs)
                .u64(15, 1000)
                .bytes();
    }

    private static byte[] initializationMetadata(final int itag,
                                                 final long endSegment,
                                                 final long endTimeMs,
                                                 final String mimeType) {
        return proto()
                .message(2, formatId(itag))
                .u64(3, endTimeMs)
                .u64(4, endSegment)
                .string(5, mimeType)
                .bytes();
    }

    private static byte[] redirect(final String url) {
        return proto().string(1, url).bytes();
    }

    private static byte[] sabrError(final String type, final int code) {
        return proto().string(1, type).u64(2, code).bytes();
    }

    private static byte[] reloadPlayerResponse(final String token) {
        return proto()
                .message(1, proto()
                        .message(1, proto()
                                .string(1, token)
                                .bytes())
                        .bytes())
                .bytes();
    }

    private static byte[] sabrSeek(final long mediaTime,
                                   final int timescale,
                                   final int source) {
        return proto()
                .u64(1, mediaTime)
                .u64(2, timescale)
                .u64(3, source)
                .bytes();
    }

    private static byte[] playbackStartPolicy() {
        return proto()
                .message(1, proto().u64(1, 100_000).u64(2, 1_500).bytes())
                .message(2, proto().u64(1, 120_000).u64(2, 2_500).bytes())
                .u64(5, 9)
                .bytes();
    }

    private static byte[] formatSelectionConfig() {
        return proto()
                .packedU64(2, SMOKE_VIDEO_ITAG, SMOKE_AUDIO_ITAG)
                .string(3, "smoke-video")
                .u64(4, 1080)
                .bytes();
    }

    private static byte[] selectableFormats() {
        return proto()
                .message(1, formatIdWithXtags(SMOKE_VIDEO_ITAG, "vxtags"))
                .message(2, formatIdWithXtags(SMOKE_AUDIO_ITAG, "axtags"))
                .message(4, proto().message(1, formatIdWithXtags(399, "wv")).bytes())
                .message(5, proto().message(1, formatIdWithXtags(251, "wa")).bytes())
                .u64(9, 1)
                .bytes();
    }

    private static byte[] onesieHeader(final int type,
                                       final long sequence,
                                       final boolean encrypted) {
        final Proto crypto = proto().u64(6, 0);
        if (encrypted) {
            crypto.message(4, new byte[]{1, 2, 3});
            crypto.message(5, new byte[]{4, 5});
        }
        return proto()
                .u64(1, type)
                .string(2, "smoke-video")
                .string(3, String.valueOf(SMOKE_VIDEO_ITAG))
                .message(4, crypto.bytes())
                .u64(5, 123456)
                .u64(7, 11)
                .message(11, proto().u64(1, SMOKE_VIDEO_ITAG).bytes())
                .string(15, "onesie-xtags")
                .u64(18, sequence)
                .message(23, proto().string(2, "smoke-video").bytes())
                .message(34, proto().u64(1, SMOKE_AUDIO_ITAG).bytes())
                .bytes();
    }

    private static byte[] onesieInnertubeResponse() {
        return proto()
                .u64(1, 1)
                .u64(2, 200)
                .message(3, proto().string(1, "x-smoke").string(2, "ok").bytes())
                .message(4, new byte[]{1, 2, 3, 4})
                .bytes();
    }

    private static byte[] requestIdentifier(final String token) {
        return proto().string(1, token).bytes();
    }

    private static byte[] snackbar(final int id) {
        return proto().u64(1, id).bytes();
    }

    private static byte[] cancellationPolicy() {
        return proto()
                .u64(1, 1)
                .message(2, proto().u64(1, 2).u64(2, 3).u64(3, 1500).bytes())
                .u64(3, 4)
                .bytes();
    }

    private static byte[] prewarmConnection() {
        return proto()
                .message(1, proto().string(1, "cdn").u64(2, 1).bytes())
                .bytes();
    }

    private static byte[] contextUpdate(final int type,
                                        final byte[] value,
                                        final boolean sendByDefault,
                                        final int writePolicy) {
        return proto()
                .u64(1, type)
                .u64(2, 1)
                .message(3, value)
                .u64(4, sendByDefault ? 1 : 0)
                .u64(5, writePolicy)
                .bytes();
    }

    private static byte[] contextPolicy(final int[] start,
                                        final int[] stop,
                                        final int[] discard) {
        final Proto proto = proto();
        for (final int value : start) {
            proto.u64(1, value);
        }
        for (final int value : stop) {
            proto.u64(2, value);
        }
        for (final int value : discard) {
            proto.u64(3, value);
        }
        return proto.bytes();
    }

    private static byte[] gzip(final byte[] data) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(data);
        }
        return output.toByteArray();
    }

    private static List<Integer> activeContextTypes(final SabrSmokeHarness harness)
            throws Exception {
        final Method getActiveSabrContexts = YoutubeSabrSession.class
                .getDeclaredMethod("getActiveSabrContexts");
        getActiveSabrContexts.setAccessible(true);
        @SuppressWarnings("unchecked") final Map<Integer, byte[]> contexts =
                (Map<Integer, byte[]>) getActiveSabrContexts.invoke(
                        harness.holder.session.delegate);
        return new ArrayList<>(contexts.keySet());
    }

    private static List<Integer> unsentContextTypes(final SabrSmokeHarness harness)
            throws Exception {
        final Method getUnsentSabrContextTypes = YoutubeSabrSession.class
                .getDeclaredMethod("getUnsentSabrContextTypes");
        getUnsentSabrContextTypes.setAccessible(true);
        @SuppressWarnings("unchecked") final Collection<Integer> types =
                (Collection<Integer>) getUnsentSabrContextTypes.invoke(
                        harness.holder.session.delegate);
        return new ArrayList<>(types);
    }

    private static Proto proto() {
        return new Proto();
    }

    private static String base64(final byte[] bytes) {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private static byte[] formatIdWithXtags(final int itag, final String xtags) {
        return proto()
                .u64(1, itag)
                .u64(2, 123456)
                .string(3, xtags)
                .bytes();
    }

    private static final class SmokeCase {
        private enum Kind {
            PLAYBACK,
            SPONSOR_BLOCK_PLAYBACK,
            SPONSOR_BLOCK_SEEK
        }

        private final Kind kind;

        private SmokeCase(final Kind kind) {
            this.kind = kind;
        }

        private static SmokeCase playback() {
            return new SmokeCase(Kind.PLAYBACK);
        }

        private static SmokeCase sponsorBlockPlayback() {
            return new SmokeCase(Kind.SPONSOR_BLOCK_PLAYBACK);
        }

        private static SmokeCase sponsorBlockSeek() {
            return new SmokeCase(Kind.SPONSOR_BLOCK_SEEK);
        }

        private boolean isSponsorBlockCase() {
            return kind == Kind.SPONSOR_BLOCK_PLAYBACK || kind == Kind.SPONSOR_BLOCK_SEEK;
        }
    }

    /** Test-side composition of the current session, bridge and source specification. */
    private static final class SmokeHolder {
        private final SabrSourceSpec spec;
        private final SabrMediaBridge bridge;
        private final SmokeSession session;

        private SmokeHolder(final Context context,
                            final String videoId,
                            final YoutubeSabrInfo info,
                            final YoutubeSabrSession delegate,
                            final YoutubeSabrInfo.Format audioFormat,
                            final YoutubeSabrInfo.Format videoFormat) throws Exception {
            final byte[] audioInitialization = mp4Sidx(5_000, 5_000, 5_000, 5_000,
                    5_000, 5_000, 5_000, 5_000);
            final byte[] videoInitialization = mp4Sidx(5_000, 5_000, 5_000, 5_000,
                    5_000, 5_000, 5_000, 5_000);
            final org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline
                    audioTimeline = org.schabi.newpipe.extractor.services.youtube.sabr
                    .YoutubeSabrFormatTimeline.parse(audioFormat, audioInitialization);
            final org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline
                    videoTimeline = org.schabi.newpipe.extractor.services.youtube.sabr
                    .YoutubeSabrFormatTimeline.parse(videoFormat, videoInitialization);
            spec = new SabrSourceSpec(videoId, info, audioFormat,
                    Collections.singletonList(audioFormat), videoFormat,
                    audioInitialization, videoInitialization, audioTimeline, videoTimeline,
                    Collections.emptyList());
            delegate.setPoToken(new byte[]{1, 2, 3, 4});
            bridge = new SabrMediaBridge(context, delegate, spec);
            setBridgeTimeline("audioTimeline", audioTimeline);
            setBridgeTimeline("videoTimeline", videoTimeline);
            session = new SmokeSession(delegate, bridge, spec, audioFormat, videoFormat,
                    audioTimeline, videoTimeline);
        }

        private void setBridgeTimeline(final String fieldName,
                                       final org.schabi.newpipe.extractor.services.youtube.sabr
                                               .YoutubeSabrFormatTimeline timeline) throws Exception {
            final Field field = SabrMediaBridge.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(bridge, timeline);
        }

        private void setActiveTracks(final Object owner,
                                     final boolean video,
                                     final boolean audio) {
            session.videoActive = video;
            session.audioActive = audio;
        }

        private void setPlayerTimeMs(final long value) { session.playerTimeMs = value; }
        private void advanceReaderGeneration(final Object owner) { session.clearCache(); }
        private void stop(final String reason) { bridge.stop(); session.clearCache(); }
    }

    private static final class SmokeSession {
        private final YoutubeSabrSession delegate;
        private final SabrMediaBridge bridge;
        private final SabrSourceSpec spec;
        private final YoutubeSabrInfo.Format audioFormat;
        private final YoutubeSabrInfo.Format videoFormat;
        private final org.schabi.newpipe.extractor.services.youtube.sabr
                .YoutubeSabrFormatTimeline audioTimeline;
        private final org.schabi.newpipe.extractor.services.youtube.sabr
                .YoutubeSabrFormatTimeline videoTimeline;
        private final Map<SabrSegmentKey, SabrMediaSegment> segments = new ConcurrentHashMap<>();
        private final SmokeState state;
        private volatile boolean audioActive;
        private volatile boolean videoActive = true;
        private volatile long playerTimeMs;
        private volatile long peakCachedBytes;

        private SmokeSession(final YoutubeSabrSession delegate,
                             final SabrMediaBridge bridge,
                             final SabrSourceSpec spec,
                             final YoutubeSabrInfo.Format audioFormat,
                             final YoutubeSabrInfo.Format videoFormat,
                             final org.schabi.newpipe.extractor.services.youtube.sabr
                                     .YoutubeSabrFormatTimeline audioTimeline,
                             final org.schabi.newpipe.extractor.services.youtube.sabr
                                     .YoutubeSabrFormatTimeline videoTimeline) {
            this.delegate = delegate;
            this.bridge = bridge;
            this.spec = spec;
            this.audioFormat = audioFormat;
            this.videoFormat = videoFormat;
            this.audioTimeline = audioTimeline;
            this.videoTimeline = videoTimeline;
            state = new SmokeState(delegate, audioTimeline, videoTimeline);
        }

        private int pumpOnceStreaming(final Localization localization) throws Exception {
            return requestOnce(null).getSegmentCount();
        }

        private YoutubeSabrSession.RequestResult pumpOnceStreamingForDemand(
                final Localization localization, final SabrSegmentKey request) throws Exception {
            playerTimeMs = Math.max(0, (request.getFormat().isAudio() ? audioTimeline : videoTimeline)
                    .getStartMs(request.getSequenceNumber()));
            return requestOnce(request);
        }

        private YoutubeSabrSession.RequestResult requestOnce(final SabrSegmentKey demand)
                throws Exception {
            final boolean demandAudio = demand != null && demand.getFormat().isAudio();
            final boolean useAudio = audioActive || demandAudio;
            final boolean useVideo = videoActive || demand != null && !demandAudio;
            final List<YoutubeSabrRequest.Track> tracks = new ArrayList<>(2);
            if (useAudio) {
                tracks.add(YoutubeSabrRequest.Track.of(audioFormat, audioTimeline,
                        state.maxSegment(audioFormat)));
            }
            if (useVideo) {
                tracks.add(YoutubeSabrRequest.Track.of(videoFormat, videoTimeline,
                        state.maxSegment(videoFormat)));
            }
            return delegate.requestOnce(
                    YoutubeSabrRequest.playback(playerTimeMs, 1.0f, tracks), this::accept);
        }

        private void accept(final SabrMediaSegment segment) {
            if (segment.getHeader().isInitSegment()) return;
            final YoutubeSabrInfo.Format format = segment.getHeader().getItag()
                    == audioFormat.getItag() ? audioFormat : videoFormat;
            final SabrSegmentKey key = SabrSegmentKey.media(format,
                    segment.getHeader().getSequenceNumber());
            final SabrMediaSegment previous = segments.put(key, segment);
            if (previous != null && previous != segment) previous.delete();
            state.observe(format, segment);
            peakCachedBytes = Math.max(peakCachedBytes, getCachedBytes());
        }

        private SabrMediaSegment getCachedSegment(final SabrSegmentKey request) {
            return segments.get(request);
        }

        private void clearCache() {
            for (final SabrMediaSegment segment : segments.values()) segment.delete();
            segments.clear();
        }

        private SmokeState getStreamState() { return state; }
        private String getDiagnosticTrace() { return delegate.getDiagnosticTrace(); }
        private long getBackoffRemainingMs() { return delegate.getBackoffRemainingMs(); }
        private int getMaxStreamProtectionStatus() {
            return delegate.getMaxStreamProtectionStatus();
        }
        private long getPeakCachedBytes() { return peakCachedBytes; }
        private long getCachedBytes() {
            long result = 0;
            for (final SabrMediaSegment segment : segments.values()) result += segment.getLength();
            return result;
        }
    }

    private static final class SmokeState {
        private final YoutubeSabrSession session;
        private org.schabi.newpipe.extractor.services.youtube.sabr
                .YoutubeSabrFormatTimeline audioTimeline;
        private org.schabi.newpipe.extractor.services.youtube.sabr
                .YoutubeSabrFormatTimeline videoTimeline;
        private final Map<Integer, Integer> maxSegments = new ConcurrentHashMap<>();
        private byte[] playbackCookie;
        private final List<SmokeContext> contexts = new ArrayList<>();

        private SmokeState(final YoutubeSabrSession session,
                           final org.schabi.newpipe.extractor.services.youtube.sabr
                                   .YoutubeSabrFormatTimeline audioTimeline,
                           final org.schabi.newpipe.extractor.services.youtube.sabr
                                   .YoutubeSabrFormatTimeline videoTimeline) {
            this.session = session;
            this.audioTimeline = audioTimeline;
            this.videoTimeline = videoTimeline;
        }

        private void observe(final YoutubeSabrInfo.Format format,
                             final SabrMediaSegment segment) {
            maxSegments.merge(format.getItag(), segment.getHeader().getSequenceNumber(), Math::max);
        }

        private int maxSegment(final YoutubeSabrInfo.Format format) {
            return maxSegments.getOrDefault(format.getItag(), 0);
        }

        private void reset(final YoutubeSabrInfo.Format format) {
            maxSegments.remove(format.getItag());
        }

        private void setTimelines(
                final org.schabi.newpipe.extractor.services.youtube.sabr
                        .YoutubeSabrFormatTimeline audio,
                final org.schabi.newpipe.extractor.services.youtube.sabr
                        .YoutubeSabrFormatTimeline video) {
            if (audio != null) audioTimeline = audio;
            if (video != null) videoTimeline = video;
        }

        private void setVideoOnlyRequestMode() { }
        private boolean hasSegmentIndex(final YoutubeSabrInfo.Format format) { return true; }
        private int getMaxSegment(final YoutubeSabrInfo.Format format) {
            return maxSegment(format);
        }
        private long getMinBufferedEndMs() {
            return Math.min(endMs(audioTimeline, maxSegments.getOrDefault(SMOKE_AUDIO_ITAG, 0)),
                    endMs(videoTimeline, maxSegments.getOrDefault(SMOKE_VIDEO_ITAG, 0)));
        }
        private SmokePolicy getNextRequestPolicy() {
            final long backoff = session.getBackoffRemainingMs();
            return backoff <= 0 ? null : new SmokePolicy((int) backoff);
        }
        private byte[] getPlaybackCookie() { return playbackCookie; }
        private boolean isLive() { return session.isLive(); }
        private boolean isPostLiveDvr() { return session.isPostLiveDvr(); }
        private int getEndSegment(final YoutubeSabrInfo.Format format) {
            return format.isAudio() ? audioTimeline.getEndSequence() : videoTimeline.getEndSequence();
        }
        private long getSegmentStartMs(final YoutubeSabrInfo.Format format,
                                       final int sequence) {
            return format.isAudio() ? audioTimeline.getStartMs(sequence)
                    : videoTimeline.getStartMs(sequence);
        }
        private int getSegmentNumberAtOrAfterTimeMs(final YoutubeSabrInfo.Format format,
                                                    final long timeMs) {
            return format.isAudio() ? audioTimeline.getSequenceAt(timeMs)
                    : videoTimeline.getSequenceAt(timeMs);
        }
        private Collection<SmokeContext> getActiveSabrContexts() { return contexts; }
        private Collection<Integer> getUnsentSabrContextTypes() {
            return Collections.emptyList();
        }
        private static long endMs(
                final org.schabi.newpipe.extractor.services.youtube.sabr
                        .YoutubeSabrFormatTimeline timeline,
                final int sequence) {
            return sequence <= 0 ? 0 : timeline.getEndMs(sequence);
        }
    }

    private static final class SmokePolicy {
        private final int backoffTimeMs;
        private SmokePolicy(final int backoffTimeMs) { this.backoffTimeMs = backoffTimeMs; }
        private int getBackoffTimeMs() { return backoffTimeMs; }
    }

    private static final class SmokeContext {
        private final int type;
        private SmokeContext(final int type) { this.type = type; }
        private int getType() { return type; }
    }

    private static final class SabrSmokeHarness implements AutoCloseable {
        private final Downloader previousDownloader;
        private final Localization previousLocalization;
        private final ContentCountry previousContentCountry;
        private final FakeSabrDownloader downloader;
        private final SmokeHolder holder;
        private final YoutubeSabrInfo.Format videoFormat;
        private final Object readerOwner;
        private volatile byte[] lastSegmentData = new byte[0];

        private SabrSmokeHarness(final Downloader previousDownloader,
                                 final Localization previousLocalization,
                                 final ContentCountry previousContentCountry,
                                 final FakeSabrDownloader downloader,
                                 final SmokeHolder holder,
                                 final YoutubeSabrInfo.Format videoFormat,
                                 final Object readerOwner) {
            this.previousDownloader = previousDownloader;
            this.previousLocalization = previousLocalization;
            this.previousContentCountry = previousContentCountry;
            this.downloader = downloader;
            this.holder = holder;
            this.videoFormat = videoFormat;
            this.readerOwner = readerOwner;
        }

        private static SabrSmokeHarness create() throws Exception {
            return create(smokeFormat(SMOKE_AUDIO_ITAG, true),
                    smokeFormat(SMOKE_VIDEO_ITAG, false));
        }

        private static SabrSmokeHarness create(final YoutubeSabrInfo.Format audioFormat,
                                               final YoutubeSabrInfo.Format videoFormat)
                throws Exception {
            final Downloader previousDownloader = NewPipe.getDownloader();
            final Localization previousLocalization = NewPipe.getPreferredLocalization();
            final ContentCountry previousContentCountry = NewPipe.getPreferredContentCountry();
            final FakeSabrDownloader downloader = new FakeSabrDownloader();
            NewPipe.init(downloader, Localization.DEFAULT, ContentCountry.DEFAULT);
            final YoutubeSabrInfo info = smokeInfo(audioFormat, videoFormat);
            final File spoolDirectory = new File(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                    "sabr-smoke-" + System.nanoTime());
            final YoutubeSabrSession session =
                    new YoutubeSabrSession(info, spoolDirectory);
            final Constructor<SmokeHolder> constructor =
                    SmokeHolder.class.getDeclaredConstructor(Context.class,
                            String.class, YoutubeSabrInfo.class, YoutubeSabrSession.class,
                            YoutubeSabrInfo.Format.class, YoutubeSabrInfo.Format.class);
            constructor.setAccessible(true);
            final SmokeHolder holder = constructor.newInstance(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    "smoke-video", info, session, audioFormat, videoFormat);
            final Object readerOwner = new Object();
            final Method setActiveTracks = SmokeHolder.class.getDeclaredMethod(
                    "setActiveTracks", Object.class, boolean.class, boolean.class);
            setActiveTracks.setAccessible(true);
            setActiveTracks.invoke(holder, readerOwner, true, false);
            return new SabrSmokeHarness(previousDownloader, previousLocalization,
                    previousContentCountry, downloader, holder, videoFormat, readerOwner);
        }

        private void setPlayerTimeMs(final long playerTimeMs) throws Exception {
            final Method setPlayerTimeMs = SmokeHolder.class.getDeclaredMethod(
                    "setPlayerTimeMs", long.class);
            setPlayerTimeMs.setAccessible(true);
            setPlayerTimeMs.invoke(holder, playerTimeMs);
        }

        private void advanceReaderGeneration() throws Exception {
            final Method advanceReaderGeneration = SmokeHolder.class
                    .getDeclaredMethod("advanceReaderGeneration", Object.class);
            advanceReaderGeneration.setAccessible(true);
            advanceReaderGeneration.invoke(holder, readerOwner);
        }

        private long openMediaSegment(final SabrSegmentKey request,
                                      final long timeoutMs) throws Exception {
            return openSegment(request, timeoutMs);
        }

        private long openSegment(final SabrSegmentKey request,
                                 final long timeoutMs) throws Exception {
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicReference<byte[]> result = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final long startMs = System.currentTimeMillis();
            final Thread thread = new Thread(() -> {
                final SabrSegmentDataSource dataSource = new SabrSegmentDataSource(
                        holder.spec, holder.bridge);
                try {
                    dataSource.open(new DataSpec(segmentUri(request)));
                    final byte[] buffer = new byte[8_192];
                    final ByteArrayOutputStream output = new ByteArrayOutputStream();
                    int read;
                    while ((read = dataSource.read(buffer, 0, buffer.length))
                            != C.RESULT_END_OF_INPUT) {
                        output.write(buffer, 0, read);
                    }
                    result.set(output.toByteArray());
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    dataSource.close();
                    done.countDown();
                }
            }, "SabrSmokeDemandOpen");
            thread.start();
            assertTrue("SABR smoke demand open timed out, trace="
                            + holder.session.getDiagnosticTrace(),
                    done.await(timeoutMs, TimeUnit.MILLISECONDS));
            if (failure.get() != null) {
                throw new AssertionError("SABR smoke demand open failed, trace="
                        + holder.session.getDiagnosticTrace(), failure.get());
            }
            lastSegmentData = result.get();
            return System.currentTimeMillis() - startMs;
        }

        private byte[] getLastSegmentData() {
            return lastSegmentData.clone();
        }

        private Uri segmentUri(final SabrSegmentKey request) {
            return Uri.parse("sabr://" + holder.spec.getFormatKey(request.getFormat()) + '/'
                    + (request.isInitialization()
                    ? "init" : String.valueOf(request.getSequenceNumber())));
        }

        private void openMediaSegmentExpectFailure(final SabrSegmentKey request,
                                                   final long timeoutMs) throws Exception {
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final CountDownLatch done = new CountDownLatch(1);
            final Thread thread = new Thread(() -> {
                final SabrSegmentDataSource dataSource = new SabrSegmentDataSource(
                        holder.spec, holder.bridge);
                try {
                    dataSource.open(new DataSpec(Uri.parse("sabr://"
                            + holder.spec.getFormatKey(request.getFormat()) + '/'
                            + request.getSequenceNumber())));
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    dataSource.close();
                    done.countDown();
                }
            }, "SabrSmokeDemandOpenFailure");
            thread.start();
            assertTrue("SABR smoke demand failure did not complete, trace="
                            + holder.session.getDiagnosticTrace(),
                    done.await(timeoutMs, TimeUnit.MILLISECONDS));
            assertNotNull("SABR smoke demand unexpectedly succeeded, trace="
                    + holder.session.getDiagnosticTrace(), failure.get());
        }

        @Override
        public void close() throws Exception {
            final Method stop = SmokeHolder.class.getDeclaredMethod(
                    "stop", String.class);
            stop.setAccessible(true);
            stop.invoke(holder, "smoke_harness_close");
            NewPipe.init(previousDownloader, previousLocalization, previousContentCountry);
        }
    }

    private static final class FakeSabrDownloader extends Downloader {
        private final LinkedBlockingQueue<QueuedStreamingBody> responses =
                new LinkedBlockingQueue<>();
        private final List<String> requestedUrls = new ArrayList<>();
        private final List<byte[]> requestBodies = new ArrayList<>();
        private final List<Long> requestTimesMs = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> streamingTimeoutsMs =
                Collections.synchronizedList(new ArrayList<>());
        private final Map<String, byte[]> streamingGetBodies = new ConcurrentHashMap<>();
        private final Map<String, Integer> streamingGetCodes = new ConcurrentHashMap<>();

        private void enqueue(final byte[] body) {
            responses.add(() -> new ByteArrayInputStream(body));
        }

        private void enqueue(final QueuedStreamingBody body) {
            responses.add(body);
        }

        private void enqueueGet(final String url, final int responseCode, final byte[] body) {
            streamingGetCodes.put(url, responseCode);
            streamingGetBodies.put(url, body.clone());
        }

        private List<Long> requestTimesSnapshot() {
            synchronized (requestTimesMs) {
                return new ArrayList<>(requestTimesMs);
            }
        }

        @Override
        public Response execute(final Request request) throws IOException {
            requestedUrls.add(request.url());
            throw new IOException("Unexpected buffered request in SABR smoke: "
                    + request.httpMethod() + " " + request.url());
        }

        @Override
        public StreamingResponse getStreaming(final String url,
                                              final Map<String, List<String>> headers,
                                              final Localization localization,
                                              final long timeoutMs)
                throws IOException, ReCaptchaException {
            streamingTimeoutsMs.add(timeoutMs);
            requestedUrls.add(url);
            final byte[] body = streamingGetBodies.get(url);
            final Integer responseCode = streamingGetCodes.get(url);
            if (body == null || responseCode == null) {
                throw new IOException("No queued SABR smoke GET response for " + url);
            }
            return new StreamingResponse(responseCode, Collections.emptyMap(),
                    new ByteArrayInputStream(body));
        }

        @Override
        public StreamingResponse postStreaming(final String url,
                                               final Map<String, List<String>> headers,
                                               final byte[] dataToSend,
                                               final Localization localization)
                throws IOException {
            requestedUrls.add(url);
            requestBodies.add(dataToSend.clone());
            requestTimesMs.add(System.currentTimeMillis());
            final QueuedStreamingBody body = responses.poll();
            if (body == null) {
                throw new IOException("No queued SABR smoke response for " + url);
            }
            final Map<String, List<String>> responseHeaders = new HashMap<>();
            responseHeaders.put("Content-Type",
                    Collections.singletonList("application/vnd.yt-ump"));
            return new StreamingResponse(200, responseHeaders, body.open());
        }

        @Override
        public CancellableCall executeAsync(final Request request,
                                            final AsyncCallback callback)
                throws IOException, ReCaptchaException {
            throw new IOException("Unexpected async request in SABR smoke: "
                    + request.httpMethod() + " " + request.url());
        }
    }

    @FunctionalInterface
    private interface QueuedStreamingBody {
        InputStream open() throws IOException;
    }

    private static final class GatedMediaResponse implements QueuedStreamingBody {
        private final byte[] prefix;
        private final byte[] suffix;
        private final IOException failureAfterGate;
        private final CountDownLatch gateReached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private GatedMediaResponse(final int headerId,
                                   final int itag,
                                   final int sequence,
                                   final long startMs,
                                   final long durationMs,
                                   final byte[] firstMediaBytes,
                                   final byte[] remainingMediaBytes,
                                   final int compressionAlgorithm,
                                   final boolean initialization,
                                   final IOException failureAfterGate) {
            final byte[] mediaHeader = new UmpFixture()
                    .mediaHeader(headerId, itag, sequence, startMs, durationMs,
                            firstMediaBytes.length + remainingMediaBytes.length,
                            compressionAlgorithm, initialization)
                    .bytes();
            this.prefix = concatBytes(mediaHeader,
                    umpPartPrefix(SabrResponseDecoder.MEDIA,
                            firstMediaBytes.length + remainingMediaBytes.length + 1),
                    new byte[]{(byte) headerId}, firstMediaBytes);
            this.suffix = concatBytes(remainingMediaBytes,
                    new UmpFixture().mediaEnd(headerId).bytes());
            this.failureAfterGate = failureAfterGate;
        }

        private boolean awaitGate(final long timeoutMs) throws InterruptedException {
            return gateReached.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private void release() {
            released.countDown();
        }

        @Override
        public InputStream open() {
            return new InputStream() {
                private int prefixOffset;
                private int suffixOffset;

                @Override
                public int read() throws IOException {
                    final byte[] one = new byte[1];
                    final int read = read(one, 0, 1);
                    return read < 0 ? -1 : one[0] & 0xff;
                }

                @Override
                public int read(final byte[] buffer, final int offset, final int length)
                        throws IOException {
                    if (length == 0) {
                        return 0;
                    }
                    if (prefixOffset < prefix.length) {
                        final int count = Math.min(length, prefix.length - prefixOffset);
                        System.arraycopy(prefix, prefixOffset, buffer, offset, count);
                        prefixOffset += count;
                        if (prefixOffset == prefix.length) {
                            gateReached.countDown();
                        }
                        // Return the available prefix now instead of blocking this read for release.
                        return count;
                    }
                    gateReached.countDown();
                    awaitRelease();
                    if (failureAfterGate != null) {
                        throw failureAfterGate;
                    }
                    if (suffixOffset >= suffix.length) {
                        return -1;
                    }
                    final int count = Math.min(length, suffix.length - suffixOffset);
                    System.arraycopy(suffix, suffixOffset, buffer, offset, count);
                    suffixOffset += count;
                    return count;
                }

                private void awaitRelease() throws InterruptedIOException {
                    try {
                        released.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException(
                                "Interrupted awaiting gated SABR media release");
                    }
                }
            };
        }
    }

    private static final class AsyncSegmentReader {
        private final SmokeHolder holder;
        private final Object readerOwner;
        private final SabrSegmentKey request;
        private final int firstBytesTarget;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<SabrSegmentDataSource> dataSource = new AtomicReference<>();
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch firstBytesRead = new CountDownLatch(1);
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicBoolean eofObserved = new AtomicBoolean();
        private Thread thread;

        private AsyncSegmentReader(final SmokeHolder holder,
                                   final Object readerOwner,
                                   final SabrSegmentKey request,
                                   final int firstBytesTarget) {
            this.holder = holder;
            this.readerOwner = readerOwner;
            this.request = request;
            this.firstBytesTarget = firstBytesTarget;
        }

        private void start() {
            thread = new Thread(() -> {
                final SabrSegmentDataSource currentDataSource = new SabrSegmentDataSource(
                    holder.spec, holder.bridge);
                dataSource.set(currentDataSource);
                try {
                    currentDataSource.open(new DataSpec(Uri.parse("sabr://"
                            + holder.spec.getFormatKey(request.getFormat()) + '/'
                            + (request.isInitialization()
                            ? "init" : String.valueOf(request.getSequenceNumber())))));
                    opened.countDown();
                    final byte[] buffer = new byte[64];
                    while (true) {
                        final int read = currentDataSource.read(buffer, 0, buffer.length);
                        if (read == C.RESULT_END_OF_INPUT) {
                            eofObserved.set(true);
                            break;
                        }
                        if (read > 0) {
                            synchronized (output) {
                                output.write(buffer, 0, read);
                                if (output.size() >= firstBytesTarget) {
                                    firstBytesRead.countDown();
                                }
                            }
                        }
                    }
                } catch (final Throwable e) {
                    failure.set(e);
                } finally {
                    currentDataSource.close();
                    dataSource.compareAndSet(currentDataSource, null);
                    done.countDown();
                }
            }, "SabrSmokeAsyncSegmentRead");
            thread.setDaemon(true);
            thread.start();
        }

        private boolean awaitOpened(final long timeoutMs) throws InterruptedException {
            return opened.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private boolean awaitFirstBytes(final long timeoutMs) throws InterruptedException {
            return firstBytesRead.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private boolean awaitDone(final long timeoutMs) throws InterruptedException {
            return done.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        private void closeDataSource() {
            final SabrSegmentDataSource current = dataSource.get();
            if (current != null) {
                current.close();
            }
        }

        private byte[] bytesSnapshot() {
            synchronized (output) {
                return output.toByteArray();
            }
        }

        private Throwable getFailure() {
            return failure.get();
        }

        private boolean isEofObserved() {
            return eofObserved.get();
        }
    }

    private static final class GeneratedLargeMediaResponse implements QueuedStreamingBody {
        private final int headerId;
        private final int itag;
        private final int sequence;
        private final long startMs;
        private final long durationMs;
        private final int mediaBytes;

        private GeneratedLargeMediaResponse(final int headerId,
                                            final int itag,
                                            final int sequence,
                                            final long startMs,
                                            final long durationMs,
                                            final int mediaBytes) {
            this.headerId = headerId;
            this.itag = itag;
            this.sequence = sequence;
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.mediaBytes = mediaBytes;
        }

        @Override
        public InputStream open() {
            final byte[] mediaHeader = proto()
                    .u64(1, headerId)
                    .u64(3, itag)
                    .u64(4, 123456)
                    .u64(7, 0)
                    .u64(8, 0)
                    .u64(9, sequence)
                    .u64(11, Math.max(0, startMs))
                    .u64(12, Math.max(0, durationMs))
                    .u64(14, Math.max(0, mediaBytes))
                    .bytes();
            final byte[] headerPartPrefix = umpPartPrefix(
                    SabrResponseDecoder.MEDIA_HEADER, mediaHeader.length);
            final byte[] mediaPartPrefix = umpPartPrefix(
                    SabrResponseDecoder.MEDIA, mediaBytes + 1);
            final byte[] mediaEndPart = new UmpFixture().mediaEnd(headerId).bytes();
            return new GeneratedLargeMediaInputStream(
                    headerPartPrefix, mediaHeader, mediaPartPrefix,
                    (byte) headerId, mediaBytes, mediaEndPart);
        }
    }

    private static final class GeneratedLargeMediaInputStream extends InputStream {
        private final byte[] headerPartPrefix;
        private final byte[] mediaHeader;
        private final byte[] mediaPartPrefix;
        private final byte headerId;
        private final int mediaBytes;
        private final byte[] mediaEndPart;
        private int phase;
        private int offset;
        private int generatedMediaBytes;
        private boolean mediaHeaderIdSent;

        private GeneratedLargeMediaInputStream(final byte[] headerPartPrefix,
                                               final byte[] mediaHeader,
                                               final byte[] mediaPartPrefix,
                                               final byte headerId,
                                               final int mediaBytes,
                                               final byte[] mediaEndPart) {
            this.headerPartPrefix = headerPartPrefix;
            this.mediaHeader = mediaHeader;
            this.mediaPartPrefix = mediaPartPrefix;
            this.headerId = headerId;
            this.mediaBytes = mediaBytes;
            this.mediaEndPart = mediaEndPart;
        }

        @Override
        public int read() {
            final byte[] one = new byte[1];
            final int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(final byte[] buffer, final int off, final int len) {
            if (len <= 0) {
                return 0;
            }
            int written = 0;
            while (written < len) {
                final int value = nextByte();
                if (value < 0) {
                    return written == 0 ? -1 : written;
                }
                buffer[off + written] = (byte) value;
                written++;
            }
            return written;
        }

        private int nextByte() {
            while (true) {
                switch (phase) {
                    case 0:
                        return byteFrom(headerPartPrefix);
                    case 1:
                        return byteFrom(mediaHeader);
                    case 2:
                        return byteFrom(mediaPartPrefix);
                    case 3:
                        if (!mediaHeaderIdSent) {
                            mediaHeaderIdSent = true;
                            return headerId & 0xff;
                        }
                        if (generatedMediaBytes < mediaBytes) {
                            generatedMediaBytes++;
                            return 0;
                        }
                        phase++;
                        offset = 0;
                        break;
                    case 4:
                        return byteFrom(mediaEndPart);
                    default:
                        return -1;
                }
            }
        }

        private int byteFrom(final byte[] bytes) {
            if (offset < bytes.length) {
                return bytes[offset++] & 0xff;
            }
            phase++;
            offset = 0;
            return nextByte();
        }
    }

    private static final class UmpFixture {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private UmpFixture segment(final int headerId, final int itag, final int sequence) {
            return mediaHeader(headerId, itag, sequence).media(headerId).mediaEnd(headerId);
        }

        private UmpFixture segment(final int headerId,
                                   final int itag,
                                   final int sequence,
                                   final long startMs,
                                   final long durationMs) {
            return mediaHeader(headerId, itag, sequence, startMs, durationMs)
                    .media(headerId)
                    .mediaEnd(headerId);
        }

        private UmpFixture mediaHeader(final int headerId, final int itag, final int sequence) {
            return mediaHeader(headerId, itag, sequence, (sequence - 1) * 5_000L, 5_000L);
        }

        private UmpFixture mediaHeader(final int headerId,
                                       final int itag,
                                       final int sequence,
                                       final long startMs,
                                       final long durationMs) {
            return mediaHeader(headerId, itag, sequence, startMs, durationMs, 4);
        }

        private UmpFixture mediaHeader(final int headerId,
                                       final int itag,
                                       final int sequence,
                                       final long startMs,
                                       final long durationMs,
                                       final long contentLength) {
            return mediaHeader(headerId, itag, sequence, startMs, durationMs, contentLength, 0);
        }

        private UmpFixture mediaHeader(final int headerId,
                                       final int itag,
                                       final int sequence,
                                       final long startMs,
                                       final long durationMs,
                                       final long contentLength,
                                       final int compressionAlgorithm) {
            return mediaHeader(headerId, itag, sequence, startMs, durationMs, contentLength,
                    compressionAlgorithm, false);
        }

        private UmpFixture mediaHeader(final int headerId,
                                       final int itag,
                                       final int sequence,
                                       final long startMs,
                                       final long durationMs,
                                       final long contentLength,
                                       final int compressionAlgorithm,
                                       final boolean initialization) {
            final Proto header = proto()
                    .u64(1, headerId)
                    .u64(3, itag)
                    .u64(4, 123456)
                    .u64(7, compressionAlgorithm)
                    .u64(8, initialization ? 1 : 0)
                    .u64(9, sequence)
                    .u64(11, Math.max(0, startMs))
                    .u64(12, Math.max(0, durationMs))
                    .u64(14, Math.max(0, contentLength));
            return part(SabrResponseDecoder.MEDIA_HEADER, header.bytes());
        }

        private UmpFixture media(final int headerId) {
            return media(headerId, new byte[]{10, 11, 12, 13});
        }

        private UmpFixture media(final int headerId, final byte[] payload) {
            final byte[] part = new byte[payload.length + 1];
            part[0] = (byte) headerId;
            System.arraycopy(payload, 0, part, 1, payload.length);
            return part(SabrResponseDecoder.MEDIA, part);
        }

        private UmpFixture mediaEnd(final int headerId) {
            return part(SabrResponseDecoder.MEDIA_END, new byte[]{(byte) headerId});
        }

        private UmpFixture part(final int type, final byte[] payload) {
            writeVarint(output, type);
            writeVarint(output, payload.length);
            output.write(payload, 0, payload.length);
            return this;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }

    private static final class Proto {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Proto u64(final int field, final long value) {
            writeVarint(output, ((long) field << 3) | PROTO_WIRE_VARINT);
            writeVarint(output, value);
            return this;
        }

        private Proto string(final int field, final String value) {
            return message(field, value.getBytes(StandardCharsets.UTF_8));
        }

        private Proto message(final int field, final byte[] value) {
            writeVarint(output, ((long) field << 3) | PROTO_WIRE_LENGTH_DELIMITED);
            writeVarint(output, value.length);
            output.write(value, 0, value.length);
            return this;
        }

        private Proto packedU64(final int field, final long... values) {
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            for (final long value : values) {
                writeVarint(packed, value);
            }
            return message(field, packed.toByteArray());
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }

    private static void writeVarint(final ByteArrayOutputStream output, final long value) {
        long remaining = value;
        while ((remaining & ~0x7fL) != 0) {
            output.write((int) ((remaining & 0x7f) | 0x80));
            remaining >>>= 7;
        }
        output.write((int) remaining);
    }

    private static byte[] umpPartPrefix(final int type, final int size) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeUmpInt(output, type);
        writeUmpInt(output, size);
        return output.toByteArray();
    }

    private static byte[] concatBytes(final byte[]... values) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final byte[] value : values) {
            output.write(value, 0, value.length);
        }
        return output.toByteArray();
    }

    private static byte[] filledBytes(final int length, final int seed) {
        final byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (seed + i);
        }
        return bytes;
    }

    private static void writeUmpInt(final ByteArrayOutputStream output, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("UMP integer must be non-negative");
        }
        if (value < 128) {
            output.write(value);
            return;
        }
        output.write(240);
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static final class BoundedQualityResolver implements QualityResolver {
        private final int maxHeight;
        private final String targetCodec;

        private BoundedQualityResolver(final int maxHeight) {
            this(maxHeight, "");
        }

        private BoundedQualityResolver(final int maxHeight, final String targetCodec) {
            this.maxHeight = maxHeight;
            this.targetCodec = targetCodec == null ? "" : targetCodec.toLowerCase(Locale.ROOT);
        }

        @Override
        public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
            int lowestIndex = -1;
            int lowestHeight = Integer.MAX_VALUE;
            int preferredIndex = -1;
            int preferredHeight = -1;
            for (int i = 0; i < sortedVideos.size(); i++) {
                final VideoStream stream = sortedVideos.get(i);
                if (!isSabr(stream)) {
                    continue;
                }
                final String codec = stream.getCodec() == null
                        ? "" : stream.getCodec().toLowerCase(Locale.ROOT);
                if (!targetCodec.isEmpty() && !codec.isEmpty() && !codec.contains(targetCodec)) {
                    continue;
                }
                final int height = stream.getHeight();
                if (height > 0 && height < lowestHeight) {
                    lowestHeight = height;
                    lowestIndex = i;
                }
                if (height > preferredHeight && height <= maxHeight) {
                    preferredHeight = height;
                    preferredIndex = i;
                }
            }
            if (lowestIndex < 0) {
                throw new AssertionError("Resolver has no selectable SABR video stream");
            }
            return preferredIndex >= 0 ? preferredIndex : lowestIndex;
        }

        @Override
        public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                              final String selectedResolution,
                                              final String selectedCodec) {
            return getDefaultResolutionIndex(sortedVideos);
        }

        @Override
        public int getCurrentAudioQualityIndex(final List<AudioStream> audioStreams) {
            return 0;
        }
    }
}
