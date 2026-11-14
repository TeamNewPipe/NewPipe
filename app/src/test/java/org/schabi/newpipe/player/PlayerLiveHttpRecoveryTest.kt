package org.schabi.newpipe.player

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import java.io.IOException
import java.time.Duration
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.InfoCache

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerLiveHttpRecoveryTest {
    @Test
    fun repeatedLiveAudioSegment403RefreshesUrlsBeforeBufferDrains() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        val cache = mock(InfoCache::class.java)
        mockStatic(InfoCache::class.java).use { cacheClass ->
            cacheClass.`when`<InfoCache> { InfoCache.getInstance() }.thenReturn(cache)
            fixture.controller.onLiveMediaSegmentRejected(
                fixture.engine,
                C.TRACK_TYPE_AUDIO,
                6_216_000
            )
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            fixture.controller.onLiveMediaSegmentRejected(
                fixture.engine,
                C.TRACK_TYPE_AUDIO,
                6_216_000
            )
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            verify(fixture.player, never()).reloadPlayQueueManager()
            fixture.controller.onLiveMediaSegmentRejected(
                fixture.engine,
                C.TRACK_TYPE_AUDIO,
                6_216_000
            )
            shadowOf(Looper.getMainLooper()).idle()

            verify(cache).removeInfo(fixture.item.serviceId, fixture.item.url, InfoCache.Type.STREAM)
            verify(fixture.player).setRecovery()
            verify(fixture.player).reloadPlayQueueManager()
            verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
            verify(fixture.queue, never()).unsetRecovery(0)
        }
        fixture.controller.resetRecovery()
    }

    @Test
    fun earlySegmentRecoveryIgnoresPausedAndReplacedPlayback() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        `when`(fixture.player.playWhenReady).thenReturn(false)
        repeat(3) {
            fixture.controller.onLiveMediaSegmentRejected(
                fixture.engine,
                C.TRACK_TYPE_VIDEO,
                6_216_000
            )
        }
        `when`(fixture.player.playWhenReady).thenReturn(true)
        val oldEngine = mock(ExoPlayer::class.java)
        repeat(3) {
            fixture.controller.onLiveMediaSegmentRejected(
                oldEngine,
                C.TRACK_TYPE_VIDEO,
                6_216_000
            )
        }
        shadowOf(Looper.getMainLooper()).idle()
        verify(fixture.player, never()).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun exhaustedRefreshRetriesSwitchToHlsAtLivePosition() {
        val fixture = Fixture()
        val method = PlayerErrorController::class.java.getDeclaredMethod(
            "tryRecoverFromYouTubeMediaUrlFailure",
            PlaybackException::class.java
        ).apply { isAccessible = true }

        repeat(3) {
            assertTrue(method.invoke(fixture.controller, httpError(403)) as Boolean)
        }
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())

        assertTrue(method.invoke(fixture.controller, httpError(403)) as Boolean)

        verify(fixture.resolver).preferHlsForLiveStream(fixture.info.url)
        verify(fixture.queue).unsetRecovery(0)
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.player, never()).changeState(Player.STATE_PAUSED)
        verify(fixture.queue, never()).error()
        fixture.controller.resetRecovery()
    }

    @Test
    fun fallbackRunsOnlyOnceEvenIfDashIsSelectedAgain() {
        val fixture = Fixture()

        assertTrue(fixture.recover())
        fixture.hlsPreferred = false
        assertFalse(fixture.recover())

        verify(fixture.player, times(1)).reloadPlayQueueManager()
    }

    @Test
    fun alreadySelectedHlsIsNotRestartedByDashFallback() {
        val fixture = Fixture()
        fixture.hlsPreferred = true

        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun otherHttpStatusesDoNotUse403Fallback() {
        val fixture = Fixture()

        assertFalse(fixture.recover(404))
        assertFalse(fixture.recover(410))
        assertFalse(fixture.recover(500))
        assertTrue(fixture.recover())
    }

    @Test
    fun missingAlternativeManifestDoesNotUseFallback() {
        val fixture = Fixture()
        fixture.info.hlsUrl = ""

        assertFalse(fixture.recover())
        fixture.info.hlsUrl = "https://example.com/live.m3u8"
        fixture.info.dashMpdUrl = ""
        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun directVideoStreamsDoNotUseLiveManifestFallback() {
        val fixture = Fixture()
        fixture.info.streamType = StreamType.VIDEO_STREAM
        fixture.info.videoStreams = listOf(mock(VideoStream::class.java))

        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun otherServicesAndAudioPlaybackDoNotUseFallback() {
        val fixture = Fixture()
        `when`(fixture.item.serviceId).thenReturn(ServiceList.SoundCloud.serviceId)
        assertFalse(fixture.recover())

        `when`(fixture.item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
        // Background presentation can be audio-only while still using the video resolver.
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        `when`(fixture.player.videoPlayerSelected()).thenReturn(false)
        assertFalse(fixture.recover())
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun backgroundLiveStallFallsBackAfterTenSecondsOnlyOnce() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(9_999))
        verify(fixture.player, never()).reloadPlayQueueManager()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        verify(fixture.resolver).preferHlsForLiveStream(fixture.info.url)
        verify(fixture.queue).unsetRecovery(0)
        verify(fixture.player).reloadPlayQueueManager()

        fixture.hlsPreferred = false
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(1)).reloadPlayQueueManager()
    }

    @Test
    fun streamThatStartsInBackgroundCanRecoverFromALaterStall() {
        val fixture = Fixture()
        `when`(fixture.player.isAudioOnly).thenReturn(true)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
    }

    @Test
    fun pauseCancelsStallAndResumeRearmsAlreadyBufferingPlayer() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.player.playWhenReady).thenReturn(false)
        fixture.controller.onPlayWhenReadyChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()

        `when`(fixture.player.playWhenReady).thenReturn(true)
        fixture.controller.onPlayWhenReadyChanged(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
    }

    @Test
    fun initialBufferingAndShortStallsDoNotDiscardDash() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun staleRecoveryCannotRestartAnotherQueueItemOrResetPlayer() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.item.url).thenReturn("https://www.youtube.com/watch?v=other")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()

        `when`(fixture.item.url).thenReturn(fixture.info.url)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        fixture.controller.resetRecovery()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun releasedEngineCannotRunPendingRecovery() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.player.exoPlayerIsNull()).thenReturn(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun sustainedHlsStallRefreshesCachedUrlsAndPreservesPositionOnlyOnce() {
        val fixture = Fixture()
        fixture.hlsPreferred = true
        val cache = mock(InfoCache::class.java)
        mockStatic(InfoCache::class.java).use { cacheClass ->
            cacheClass.`when`<InfoCache> { InfoCache.getInstance() }.thenReturn(cache)
            fixture.state(Media3Player.STATE_READY)
            fixture.state(Media3Player.STATE_BUFFERING)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(9_999))
            verify(fixture.player, never()).reloadPlayQueueManager()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
            verify(cache).removeInfo(fixture.item.serviceId, fixture.item.url, InfoCache.Type.STREAM)
            verify(fixture.player).setRecovery()
            verify(fixture.queue, never()).unsetRecovery(0)
            verify(fixture.player).reloadPlayQueueManager()
            verify(fixture.engine, never()).play()
            verify(fixture.resolver, never()).clearLiveHlsFallback()

            fixture.state(Media3Player.STATE_IDLE)
            fixture.state(Media3Player.STATE_BUFFERING)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            fixture.state(Media3Player.STATE_READY)
            fixture.state(Media3Player.STATE_BUFFERING)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            verify(fixture.player, times(1)).reloadPlayQueueManager()
        }
    }

    @Test
    fun stalledHlsFallbackCanRefreshWithoutFirstReachingReady() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
        assertTrue(fixture.hlsPreferred)

        fixture.state(Media3Player.STATE_IDLE)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(2)).reloadPlayQueueManager()
        verify(fixture.player).setRecovery()
    }

    @Test
    fun fallbackRefreshRearmsWhenMetadataArrivesAfterBufferingEvent() {
        val fixture = Fixture()
        assertTrue(fixture.recover())
        fixture.state(Media3Player.STATE_IDLE)
        `when`(fixture.player.currentStreamInfo).thenReturn(Optional.empty())
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(1)).reloadPlayQueueManager()

        `when`(fixture.player.currentStreamInfo).thenReturn(Optional.of(fixture.info))
        val listener = PlayerMedia3ListenerController(
            fixture.player,
            mock(PlayerAudioController::class.java),
            mock(PlayerMetadataController::class.java),
            mock(PlaybackParametersController::class.java),
            mock(SponsorBlockPlaybackController::class.java),
            fixture.controller,
            mock(SleepTimerPlaybackController::class.java)
        )
        listener.onEvents(fixture.engine)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        listener.onEvents(fixture.engine)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        verify(fixture.player, times(2)).reloadPlayQueueManager()
    }

    @Test
    fun httpFallbackCanAlsoRefreshIfHlsNeverBecomesReady() {
        val fixture = Fixture()
        assertTrue(fixture.recover())
        fixture.state(Media3Player.STATE_IDLE)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(2)).reloadPlayQueueManager()
        verify(fixture.player).setRecovery()
    }

    @Test
    fun hlsOnlyLiveStreamCanRecoverButShortHlsStallsDoNotRefresh() {
        val fixture = Fixture()
        fixture.info.dashMpdUrl = ""
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        verify(fixture.player, never()).reloadPlayQueueManager()

        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.resolver).preferHlsForLiveStream(fixture.info.url)
    }

    @Test
    fun pausedOrReplacedHlsStreamCannotRunPendingRefresh() {
        val fixture = Fixture()
        fixture.hlsPreferred = true
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        `when`(fixture.player.playWhenReady).thenReturn(false)
        fixture.controller.onPlayWhenReadyChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        verify(fixture.player, never()).reloadPlayQueueManager()

        `when`(fixture.player.playWhenReady).thenReturn(true)
        fixture.controller.onPlayWhenReadyChanged(true)
        `when`(fixture.item.url).thenReturn("https://www.youtube.com/watch?v=other")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun archivedManifestDoesNotUseLiveHlsStallRecovery() {
        val fixture = Fixture()
        fixture.hlsPreferred = true
        fixture.info.streamType = StreamType.VIDEO_STREAM
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        verify(fixture.player, never()).reloadPlayQueueManager()
    }

    @Test
    fun fullLiveRetriesDashWithoutDiscardingRewind() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))

        verify(fixture.player).setRecovery()
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)

        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)
        verify(fixture.player, times(2)).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun fullLiveDashRefreshIsCancelledWhenPlaybackRecovers() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, never()).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun fullLiveCanRefreshAgainAfterStablePlaybackWithoutLosingRewind() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player, times(2)).reloadPlayQueueManager()
        verify(fixture.player, times(2)).setRecovery()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        fixture.controller.resetRecovery()
    }

    @Test
    fun briefReadyFlickerKeepsDvrWithoutResettingTheRetryBudget() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        fixture.state(Media3Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.player, times(2)).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun progressingBufferDoesNotReloadOrDiscardRewindUntilProgressStops() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        for (step in 1..4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
            `when`(fixture.engine.bufferedPosition).thenReturn(step * 1_000L)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
        verify(fixture.player, never()).reloadPlayQueueManager()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        verify(fixture.player, never()).reloadPlayQueueManager()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.player).setRecovery()
        verify(fixture.queue, never()).unsetRecovery(0)
        fixture.controller.resetRecovery()
    }

    @Test
    fun initialFullLiveStallRefreshesDashAfterThirtySeconds() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29))
        verify(fixture.player, never()).reloadPlayQueueManager()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.player).setRecovery()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)
        fixture.controller.resetRecovery()
    }

    @Test
    fun initialBufferProgressExtendsStartupGracePeriod() {
        val fixture = Fixture()
        fixture.state(Media3Player.STATE_BUFFERING)
        repeat(4) { step ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(19))
            `when`(fixture.engine.bufferedPosition).thenReturn((step + 1) * 1_000L)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }
        verify(fixture.player, never()).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun initialStallWaitsForMatchingMetadataAndStopsWhenPaused() {
        val fixture = Fixture()
        `when`(fixture.item.url).thenReturn("https://www.youtube.com/watch?v=anotherLive")
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(40))
        verify(fixture.player, never()).reloadPlayQueueManager()
        `when`(fixture.item.url).thenReturn(fixture.info.url)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        `when`(fixture.player.playWhenReady).thenReturn(false)
        fixture.controller.onPlayWhenReadyChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(40))
        verify(fixture.player, never()).reloadPlayQueueManager()
        fixture.controller.resetRecovery()
    }

    @Test
    fun onlyAnEstablishedDvrWindowRestrictsFutureSourceFallback() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        fixture.state(Media3Player.STATE_READY)
        verify(fixture.resolver, never()).requireDvrForLiveStream(anyString())
        `when`(fixture.engine.isCurrentMediaItemSeekable).thenReturn(true)
        `when`(fixture.engine.duration).thenReturn(14_400_000)
        fixture.state(Media3Player.STATE_READY)
        verify(fixture.resolver).requireDvrForLiveStream(fixture.info.url)
        fixture.controller.resetRecovery()
        verify(fixture.resolver).clearLiveDvrRequirement()
    }

    @Test
    fun protectedDvrWithoutARefreshedDashUrlNeverArmsHlsFallback() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        `when`(fixture.resolver.isDvrRequiredForLiveStream(fixture.info.url)).thenReturn(true)
        fixture.info.dashMpdUrl = ""
        fixture.state(Media3Player.STATE_READY)
        fixture.state(Media3Player.STATE_BUFFERING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        verify(fixture.player).reloadPlayQueueManager()
        verify(fixture.player).setRecovery()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)
        fixture.controller.resetRecovery()
    }

    @Test
    fun fullLiveStopsAfterBoundedRetriesInsteadOfDroppingRewind() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        `when`(fixture.player.context).thenReturn(RuntimeEnvironment.getApplication())
        fixture.state(Media3Player.STATE_READY)
        repeat(4) {
            fixture.state(Media3Player.STATE_BUFFERING)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        }
        verify(fixture.player, times(3)).reloadPlayQueueManager()
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)
        verify(fixture.engine).pause()
        verify(fixture.player).changeState(Player.STATE_PAUSED)
        fixture.controller.resetRecovery()
    }

    @Test
    fun fullLiveHttpFailureCannotSilentlyChooseShortHlsWindow() {
        val fixture = Fixture()
        `when`(fixture.player.isNormalLive()).thenReturn(false)
        assertFalse(fixture.recover())
        verify(fixture.resolver, never()).preferHlsForLiveStream(anyString())
        verify(fixture.queue, never()).unsetRecovery(0)
    }

    @Test
    fun healthyLiveMediaLoadsRenewExhaustedUrlBudget() {
        val fixture = Fixture()
        val guard = fixture.urlGuard()
        repeat(3) { assertTrue(guard.acquireAttempt(fixture.key()) != null) }
        assertTrue(guard.acquireAttempt(fixture.key()) == null)
        `when`(fixture.engine.isPlaying).thenReturn(true)
        `when`(fixture.engine.currentPosition).thenReturn(1_000L)
        fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 1_000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        `when`(fixture.engine.currentPosition).thenReturn(31_000L)
        fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 31_000)
        repeat(3) { assertTrue(guard.acquireAttempt(fixture.key()) != null) }
        assertTrue(guard.acquireAttempt(fixture.key()) == null)
        fixture.controller.resetRecovery()
    }

    @Test
    fun slidingLiveWindowRenewsBudgetWhileRelativePositionStaysConstant() {
        val fixture = Fixture()
        val guard = fixture.urlGuard()
        repeat(3) { guard.acquireAttempt(fixture.key()) }
        val timeline = mock(Timeline::class.java)
        val window = Timeline.Window()
        `when`(timeline.windowCount).thenReturn(1)
        `when`(timeline.getWindow(eq(0), any(Timeline.Window::class.java))).thenReturn(window)
        `when`(fixture.engine.currentTimeline).thenReturn(timeline)
        `when`(fixture.engine.isPlaying).thenReturn(true)
        `when`(fixture.engine.currentPosition).thenReturn(25_000L)
        fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 25_000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        window.positionInFirstPeriodUs = 30_000_000L
        fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 55_000)
        assertTrue(guard.acquireAttempt(fixture.key()) != null)
        fixture.controller.resetRecovery()
    }

    @Test
    fun pausedBufferingAndSeekedPlaybackDoNotRenewUrlBudget() {
        val fixture = Fixture()
        val guard = fixture.urlGuard()
        repeat(3) { guard.acquireAttempt(fixture.key()) }
        `when`(fixture.engine.isPlaying).thenReturn(true)
        fixture.state(Media3Player.STATE_READY)
        for (interrupt in listOf<() -> Unit>(
            { fixture.controller.onPlayWhenReadyChanged(false) },
            { fixture.state(Media3Player.STATE_BUFFERING) },
            { fixture.controller.onPositionDiscontinuity() },
            { fixture.controller.onLiveMediaSegmentRejected(fixture.engine, C.TRACK_TYPE_VIDEO, 1_000) }
        )) {
            fixture.state(Media3Player.STATE_READY)
            `when`(fixture.engine.currentPosition).thenReturn(1_000L)
            fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 1_000)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            interrupt()
            fixture.state(Media3Player.STATE_READY)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
            `when`(fixture.engine.currentPosition).thenReturn(41_000L)
            fixture.controller.onLiveMediaSegmentLoaded(fixture.engine, C.TRACK_TYPE_VIDEO, 41_000)
            assertTrue(guard.acquireAttempt(fixture.key()) == null)
        }
        fixture.controller.resetRecovery()
    }

    private class Fixture {
        val player = mock(Player::class.java)
        val engine = mock(ExoPlayer::class.java)
        val queue = mock(PlayQueue::class.java)
        val item = mock(PlayQueueItem::class.java)
        val resolver = mock(VideoPlaybackResolver::class.java)
        val info = StreamInfo(
            ServiceList.YouTube.serviceId,
            "live",
            "https://www.youtube.com/watch?v=f0JQ1XrlAoQ",
            "Live stream"
        ).apply {
            streamType = StreamType.LIVE_STREAM
            dashMpdUrl = "https://example.com/live.mpd"
            hlsUrl = "https://example.com/live.m3u8"
        }
        var hlsPreferred = false
        val controller = PlayerErrorController(
            player,
            mock(PlayerEventDispatcher::class.java),
            resolver
        )

        init {
            `when`(engine.currentTimeline).thenReturn(Timeline.EMPTY)
            `when`(player.exoPlayer).thenReturn(engine)
            `when`(player.getExoPlayer()).thenReturn(engine)
            `when`(player.playWhenReady).thenReturn(true)
            `when`(player.isNormalLive()).thenReturn(true)
            `when`(player.playQueue).thenReturn(queue)
            `when`(queue.item).thenReturn(item)
            `when`(item.serviceId).thenReturn(ServiceList.YouTube.serviceId)
            `when`(item.url).thenReturn(info.url)
            `when`(player.currentStreamInfo).thenReturn(Optional.of(info))
            `when`(player.videoPlayerSelected()).thenReturn(true)
            `when`(resolver.isHlsPreferredForLiveStream(info.url)).thenAnswer { hlsPreferred }
            doAnswer {
                hlsPreferred = true
                null
            }.`when`(resolver).preferHlsForLiveStream(info.url)
        }

        fun key(): String = "${item.serviceId}:${item.url}"

        fun urlGuard(): PlayerHttpErrorRecovery.RecoveryGuard = PlayerErrorController::class.java
            .getDeclaredField("recoveryGuard").apply { isAccessible = true }
            .get(controller) as PlayerHttpErrorRecovery.RecoveryGuard

        fun state(state: Int) {
            `when`(engine.playbackState).thenReturn(state)
            controller.onPlaybackStateChanged(state)
        }

        fun recover(status: Int = 403): Boolean = controller.tryRecoverFromLiveDashHttpFailure(httpError(status), item)
    }

    companion object {
        private fun httpError(status: Int): PlaybackException = PlaybackException(
            "Source error",
            HttpDataSource.InvalidResponseCodeException(
                status,
                "HTTP error",
                IOException("Rejected segment"),
                emptyMap(),
                DataSpec.Builder().setUri("https://example.com/segment").build(),
                byteArrayOf()
            ),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
    }
}
