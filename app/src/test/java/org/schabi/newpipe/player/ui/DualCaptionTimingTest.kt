package org.schabi.newpipe.player.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.widget.RelativeLayout
import androidx.media3.common.FlagSet
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import java.time.Duration
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.SubtitleTiming

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DualCaptionTimingTest {
    private lateinit var context: Context
    private lateinit var player: Player
    private lateinit var content: RelativeLayout
    private lateinit var clock: PlaybackClock
    private lateinit var controller: DualCaptionController
    private lateinit var currentInfo: StreamInfo
    private var parsed = TimedSubtitle()
    private val timing = SubtitleTiming()
    private var loads = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putString("dual_captions_secondary_language", "fr").commit()
        currentInfo = info("first")
        clock = PlaybackClock()
        player = mock(Player::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        `when`(player.exoPlayer).thenAnswer { clock.engine }
        `when`(player.currentStreamInfo).thenAnswer { Optional.of(currentInfo) }
        `when`(player.captionPreference).thenReturn("en")
        `when`(player.subtitleTiming).thenReturn(timing)
        content = RelativeLayout(context)
        val primary = SubtitleView(context)
        content.addView(primary)
        controller = DualCaptionController(
            player,
            content,
            primary,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        ) {
            loads++
            parsed
        }
    }

    @After
    fun tearDown() {
        controller.release()
    }

    @Test
    fun shortCuesStartAndClearBetweenTheUnchangedOneSecondProgressTicks() {
        assertText()
        advance(199)
        assertText()
        advance(1)
        assertText("First")
        advance(99)
        assertText("First")
        advance(1)
        assertText()
        advance(500)
        assertText("Second")
        advance(100)
        assertText()

        assertEquals(listOf(0L, 200_000L, 300_000L, 800_000L, 900_000L), parsed.queries.distinct())
        assertEquals(1000, Player.PROGRESS_LOOP_INTERVAL_MILLIS)
        assertEquals(1, loads)
    }

    @Test
    fun speedChangesRescheduleBothStartsAndEndsAtTheCurrentPlaybackClock() {
        clock.speed(2f)
        advance(99)
        assertText()
        advance(1)
        assertText("First")
        clock.speed(0.5f)
        advance(199)
        assertText("First")
        advance(1)
        assertText()
        assertEquals(300_000L, parsed.queries.last())
        assertEquals(1, loads)
    }

    @Test
    fun pauseCancelsBoundariesAndOffsetEditsRefreshImmediatelyWithoutProgressOrSeek() {
        advance(250)
        clock.pause()
        assertText("First")
        val pausedQueries = parsed.queries.size
        advance(2000)
        assertEquals(pausedQueries, parsed.queries.size)
        assertText("First")

        timing.setOffset(100)
        assertText()
        assertEquals(150_000L, parsed.queries.last())
        timing.setOffset(-25)
        assertText("First")
        assertEquals(275_000L, parsed.queries.last())
        timing.setOffset(0)
        clock.play()
        advance(49)
        assertText("First")
        advance(1)
        assertText()
        assertEquals(1, loads)
    }

    @Test
    fun forwardAndBackwardSeeksRefreshWhilePausedAndResumeFromTheNewBoundary() {
        clock.pause()
        clock.seek(220)
        assertText("First")
        clock.seek(500)
        assertText()
        clock.seek(850)
        assertText("Second")
        clock.seek(50)
        assertText()
        val pausedQueries = parsed.queries.size
        advance(1500)
        assertEquals(pausedQueries, parsed.queries.size)
        clock.play()
        advance(150)
        assertText("First")
    }

    @Test
    fun bufferingStopsTheClockAndTrackEventsKeepTheLoadedSelection() {
        advance(250)
        clock.pause(Media3Player.EVENT_PLAYBACK_STATE_CHANGED)
        val bufferedQueries = parsed.queries.size
        advance(1000)
        assertEquals(bufferedQueries, parsed.queries.size)
        assertText("First")
        clock.emit(Media3Player.EVENT_TRACKS_CHANGED)
        assertText("First")
        clock.play()
        advance(50)
        assertText()
        assertEquals(1, loads)
    }

    @Test
    fun mediaReplacementClearsOldCuesAndRestoresCachedMetadataAtTheSettledEvent() {
        advance(250)
        val previous = parsed
        parsed = TimedSubtitle("Replacement")
        currentInfo = info("next")
        clock.transition()
        assertText()
        val previousQueries = previous.queries.size
        clock.emit(Media3Player.EVENT_MEDIA_ITEM_TRANSITION)
        assertText("Replacement")
        advance(50)
        assertText()
        assertEquals(previousQueries, previous.queries.size)
        assertEquals(2, loads)
    }

    @Test
    fun repeatingTheSameItemReschedulesWithoutDiscardingItsLoadedCaptions() {
        advance(850)
        assertText("Second")
        clock.transition(Media3Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        clock.seek(0)
        clock.emit(Media3Player.EVENT_MEDIA_ITEM_TRANSITION)
        assertText()
        advance(200)
        assertText("First")
        assertEquals(1, loads)
    }

    @Test
    fun engineReplacementAndReleaseRemoveListenersAndRejectStaleCallbacks() {
        val previousClock = clock
        val previousListener = previousClock.listeners.single()
        controller.detachPlayer()
        assertTrue(previousClock.listeners.isEmpty())
        val queriesBeforeDetach = parsed.queries.size
        advance(1000)
        assertEquals(queriesBeforeDetach, parsed.queries.size)
        assertText()

        clock = PlaybackClock(positionMs = 850, playing = false)
        controller.attachPlayer()
        assertText("Second")
        val replacementQueries = parsed.queries.size
        previousListener.onMediaItemTransition(null, Media3Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        previousListener.onEvents(previousClock.engine, events(Media3Player.EVENT_POSITION_DISCONTINUITY))
        assertText("Second")
        assertEquals(replacementQueries, parsed.queries.size)

        controller.release()
        assertTrue(clock.listeners.isEmpty())
        assertEquals(1, content.childCount)
        timing.setOffset(100)
        advance(1000)
        assertEquals(replacementQueries, parsed.queries.size)
    }

    @Test
    fun clearCancelsPendingCueBoundariesUntilMetadataIsAvailableAgain() {
        controller.clear()
        val queriesBeforeClear = parsed.queries.size
        advance(250)
        assertText()
        assertEquals(queriesBeforeClear, parsed.queries.size)
        controller.onMetadata(currentInfo)
        assertText("First")
        advance(50)
        assertText()
    }

    private fun advance(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    private fun assertText(vararg expected: String) {
        val view = content.getChildAt(1) as SubtitleView
        val cues = SubtitleView::class.java.getDeclaredField("cues").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val displayed = cues.get(view) as List<Cue>
        assertEquals(expected.toList(), displayed.map { it.text.toString() })
    }

    private fun info(id: String) = StreamInfo(0, id, "https://example.invalid/$id", id).apply {
        subtitles = listOf(
            SubtitlesStream.Builder().setContent("WEBVTT", false).setLanguageCode("fr")
                .setMediaFormat(MediaFormat.VTT).setAutoGenerated(false).build()
        )
    }

    private class TimedSubtitle(firstText: String = "First") : Subtitle {
        val queries = mutableListOf<Long>()
        private val events = longArrayOf(200_000, 300_000, 800_000, 900_000)
        private val first = Cue.Builder().setText(firstText).build()
        private val second = Cue.Builder().setText("Second").build()

        override fun getNextEventTimeIndex(timeUs: Long): Int = events.indexOfFirst { it > timeUs }
        override fun getEventTimeCount(): Int = events.size
        override fun getEventTime(index: Int): Long = events[index]
        override fun getCues(timeUs: Long): List<Cue> {
            queries += timeUs
            return when (timeUs) {
                in events[0] until events[1] -> listOf(first)
                in events[2] until events[3] -> listOf(second)
                else -> emptyList()
            }
        }
    }

    private class PlaybackClock(positionMs: Long = 0, private var playing: Boolean = true) {
        val engine = mock(ExoPlayer::class.java)
        val listeners = mutableSetOf<Media3Player.Listener>()
        private var basePositionMs = positionMs
        private var baseTimeMs = SystemClock.uptimeMillis()
        private var playbackSpeed = 1f
        private val position: Long
            get() = basePositionMs + if (playing) {
                ((SystemClock.uptimeMillis() - baseTimeMs) * playbackSpeed).toLong()
            } else {
                0L
            }

        init {
            `when`(engine.currentPosition).thenAnswer { position }
            `when`(engine.isPlaying).thenAnswer { playing }
            `when`(engine.playbackParameters).thenAnswer { PlaybackParameters(playbackSpeed) }
            `when`(engine.currentCues).thenReturn(CueGroup(emptyList(), 0))
            doAnswer {
                listeners += it.getArgument<Media3Player.Listener>(0)
                null
            }.`when`(engine).addListener(any(Media3Player.Listener::class.java))
            doAnswer {
                listeners -= it.getArgument<Media3Player.Listener>(0)
                null
            }.`when`(engine).removeListener(any(Media3Player.Listener::class.java))
        }

        fun speed(value: Float) {
            checkpoint()
            playbackSpeed = value
            emit(Media3Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
        }

        fun pause(event: Int = Media3Player.EVENT_IS_PLAYING_CHANGED) {
            checkpoint()
            playing = false
            emit(event)
        }

        fun play() {
            checkpoint()
            playing = true
            emit(Media3Player.EVENT_IS_PLAYING_CHANGED)
        }

        fun seek(value: Long) {
            basePositionMs = value
            baseTimeMs = SystemClock.uptimeMillis()
            emit(Media3Player.EVENT_POSITION_DISCONTINUITY)
        }

        fun transition(reason: Int = Media3Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            listeners.toList().forEach { it.onMediaItemTransition(null, reason) }
        }

        fun emit(event: Int) {
            listeners.toList().forEach { it.onEvents(engine, events(event)) }
        }

        private fun checkpoint() {
            basePositionMs = position
            baseTimeMs = SystemClock.uptimeMillis()
        }
    }

    private companion object {
        fun events(event: Int) = Media3Player.Events(FlagSet.Builder().add(event).build())
    }
}
