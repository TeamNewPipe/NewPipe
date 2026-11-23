package org.schabi.newpipe.player.ui

import android.app.Application
import android.content.Context
import android.widget.RelativeLayout
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import java.util.Optional
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.SubtitleTiming
import org.schabi.newpipe.player.helper.PlayerHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DualCaptionControllerTest {
    private lateinit var context: Context
    private lateinit var player: Player
    private lateinit var content: RelativeLayout
    private lateinit var primary: SubtitleView
    private var primaryCues = emptyList<Cue>()
    private lateinit var french: SubtitlesStream
    private lateinit var spanish: SubtitlesStream
    private var primaryLanguage: String? = null
    private var currentInfo: StreamInfo? = null
    private val timing = SubtitleTiming()
    private val primaryCue = Cue.Builder().setText("Primary caption").build()
    private val controllers = mutableListOf<DualCaptionController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putString("dual_captions_secondary_language", "fr").commit()
        french = subtitle("fr")
        spanish = subtitle("es")
        primaryLanguage = PlayerHelper.captionLanguageOf(context, subtitle("en"))
        currentInfo = StreamInfo(0, "video", "https://www.youtube.com/watch?v=test", "Video").apply {
            subtitles = listOf(french, spanish)
        }
        player = mock(Player::class.java)
        `when`(player.currentStreamInfo).thenAnswer { Optional.ofNullable(currentInfo) }
        `when`(player.captionPreference).thenAnswer { primaryLanguage }
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        `when`(player.subtitleTiming).thenReturn(timing)
        content = RelativeLayout(context)
        primary = spy(SubtitleView(context))
        doAnswer { invocation ->
            primaryCues = invocation.getArgument<List<Cue>?>(0).orEmpty()
            invocation.callRealMethod()
        }.`when`(primary).setCues(any())
        content.addView(primary)
    }

    @After
    fun tearDown() {
        controllers.forEach(DualCaptionController::release)
    }

    @Test
    fun changingPrimaryLanguageSuppressesAndRestoresTheSelectedSecondary() {
        val parsed = RecordingSubtitle()
        var loads = 0
        val controller = controller {
            loads++
            parsed
        }
        controller.renderPrimary(listOf(primaryCue))
        assertPrimaryMovedUp()
        assertEquals(1, loads)

        primaryLanguage = PlayerHelper.captionLanguageOf(context, french)
        controller.onPrimaryCaptionChanged()
        assertPrimaryInOriginalPosition()
        val queriesBeforeSuppression = parsed.queries.size
        controller.onProgress(2_000)
        assertEquals(queriesBeforeSuppression, parsed.queries.size)

        primaryLanguage = PlayerHelper.captionLanguageOf(context, subtitle("en"))
        controller.onPrimaryCaptionChanged()
        assertPrimaryMovedUp()
        assertEquals(2, loads)
        repeat(3) { controller.onPrimaryCaptionChanged() }
        assertEquals("Unrelated track events must not reload captions", 2, loads)
    }

    @Test
    fun primaryNoneKeepsSecondaryButSecondaryOffRestoresPrimaryPlacement() {
        val parsed = RecordingSubtitle()
        var loads = 0
        val controller = controller {
            loads++
            parsed
        }
        primaryLanguage = null
        controller.onPrimaryCaptionChanged()
        controller.renderPrimary(emptyList())
        controller.onProgress(3_000)
        assertTrue(primaryCues.isEmpty())
        assertEquals(3_000_000L, parsed.queries.last())
        assertEquals(1, loads)

        primaryLanguage = PlayerHelper.captionLanguageOf(context, subtitle("en"))
        controller.onPrimaryCaptionChanged()
        controller.renderPrimary(listOf(primaryCue))
        controller.selectLanguage(null)
        assertPrimaryInOriginalPosition()
        val queriesBeforeOff = parsed.queries.size
        controller.onProgress(4_000)
        assertEquals(queriesBeforeOff, parsed.queries.size)

        controller.selectLanguage("es")
        assertPrimaryMovedUp()
        assertEquals(2, loads)
    }

    @Test
    fun primaryAutoGeneratedVariantDoesNotDuplicateTheSameLanguage() {
        primaryLanguage = PlayerHelper.captionLanguageOf(context, subtitle("fr", auto = true))
        var loads = 0
        val controller = controller {
            loads++
            RecordingSubtitle()
        }
        controller.renderPrimary(listOf(primaryCue))
        assertPrimaryInOriginalPosition()
        assertEquals(0, loads)
        primaryLanguage = null
        controller.onPrimaryCaptionChanged()
        assertEquals(1, loads)
    }

    @Test
    fun changingSecondaryLanguageIgnoresThePreviousLateCompletion() {
        val pending = mutableListOf<PendingLoad>()
        val controller = controller { stream -> pendingLoad(stream, pending) }
        controller.renderPrimary(listOf(primaryCue))
        assertPrimaryInOriginalPosition()
        assertEquals("fr", pending.single().stream.languageTag)

        controller.selectLanguage("es")
        val spanishParsed = RecordingSubtitle()
        pending[1].complete(spanishParsed)
        assertPrimaryMovedUp()

        val lateFrench = RecordingSubtitle()
        pending[0].complete(lateFrench)
        controller.onProgress(5_000)
        assertTrue(lateFrench.queries.isEmpty())
        assertEquals(5_000_000L, spanishParsed.queries.last())
        assertPrimaryMovedUp()
    }

    @Test
    fun matchingPrimaryCancelsPendingSecondaryAndCanLaterRestoreIt() {
        val pending = mutableListOf<PendingLoad>()
        val controller = controller { stream -> pendingLoad(stream, pending) }
        controller.renderPrimary(listOf(primaryCue))
        primaryLanguage = "fr"
        controller.onPrimaryCaptionChanged()
        val stale = RecordingSubtitle()
        pending.single().complete(stale)
        assertTrue(stale.queries.isEmpty())
        assertPrimaryInOriginalPosition()

        primaryLanguage = "en"
        controller.onPrimaryCaptionChanged()
        val restored = RecordingSubtitle()
        pending[1].complete(restored)
        controller.onProgress(1_000)
        assertEquals(1_000_000L, restored.queries.last())
        assertPrimaryMovedUp()
    }

    @Test
    fun releasingUiWhileLoadingPreventsLateCuesFromReturning() {
        val pending = mutableListOf<PendingLoad>()
        val controller = controller { stream -> pendingLoad(stream, pending) }
        controller.release()
        assertEquals(1, content.childCount)
        val stale = RecordingSubtitle()
        pending.single().complete(stale)
        controller.onProgress(1_000)
        assertTrue(stale.queries.isEmpty())
        assertTrue(primaryCues.isEmpty())
    }

    @Test
    fun newMediaClearsCaptionsAndRejectsThePreviousPendingLoad() {
        val pending = mutableListOf<PendingLoad>()
        val controller = controller { stream -> pendingLoad(stream, pending) }
        val next = StreamInfo(0, "next", "https://www.youtube.com/watch?v=next", "Next").apply {
            subtitles = emptyList()
        }
        controller.onMetadata(next)
        val stale = RecordingSubtitle()
        pending.single().complete(stale)
        controller.renderPrimary(listOf(primaryCue))
        controller.onProgress(1_000)
        assertTrue(stale.queries.isEmpty())
        assertPrimaryInOriginalPosition()
    }

    @Test
    fun sessionTimingShiftsBothTracksWithoutReloadingTheSecondary() {
        val parsed = RecordingSubtitle()
        var loads = 0
        val controller = controller {
            loads++
            parsed
        }
        val primaryRenderer = mock(Renderer::class.java)
        val wrapped = timing.wrap(primaryRenderer)

        timing.setOffset(250)
        wrapped.render(1_000_000, 500)
        controller.onProgress(1_000)
        verify(primaryRenderer).render(750_000, 500)
        assertEquals(750_000L, parsed.queries.last())

        timing.setOffset(-250)
        wrapped.render(1_000_000, 600)
        controller.onProgress(1_000)
        verify(primaryRenderer).render(1_250_000, 600)
        assertEquals(1_250_000L, parsed.queries.last())

        timing.setOffset(250)
        controller.onProgress(0)
        assertEquals(-250_000L, parsed.queries.last())
        timing.onMediaItemTransition(null, 0)
        controller.onProgress(1_000)
        assertEquals(1_000_000L, parsed.queries.last())
        assertEquals(1, loads)
    }

    @Test
    fun replacementUiRestoresSameMediaWithoutAnotherMetadataEvent() {
        val engine = mock(ExoPlayer::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        `when`(player.exoPlayer).thenReturn(engine)
        `when`(engine.currentPosition).thenReturn(6_000L)
        `when`(engine.currentCues).thenReturn(CueGroup(listOf(primaryCue), 6_000_000L))
        var loads = 0
        val first = controller {
            loads++
            RecordingSubtitle()
        }
        assertPrimaryMovedUp()
        first.release()
        assertEquals(1, content.childCount)

        val replacementTrack = RecordingSubtitle()
        controller {
            loads++
            replacementTrack
        }
        assertEquals(2, loads)
        assertEquals(2, content.childCount)
        assertEquals(6_000_000L, replacementTrack.queries.last())
        assertPrimaryMovedUp()
    }

    private fun controller(load: suspend (SubtitlesStream) -> Subtitle): DualCaptionController = DualCaptionController(
        player,
        content,
        primary,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        load
    ).also(controllers::add)

    private fun assertPrimaryInOriginalPosition() {
        assertEquals(primaryCue.line, primaryCues.single().line, 0f)
    }

    private fun assertPrimaryMovedUp() {
        assertEquals(0.09f, primaryCues.single().line, 0f)
    }

    private fun subtitle(language: String, auto: Boolean = false): SubtitlesStream = SubtitlesStream.Builder()
        .setContent("WEBVTT", false)
        .setLanguageCode(language)
        .setMediaFormat(MediaFormat.VTT)
        .setAutoGenerated(auto)
        .build()

    // Deliberately uncancellable, like a blocking download finishing after its UI was replaced.
    private suspend fun pendingLoad(stream: SubtitlesStream, pending: MutableList<PendingLoad>): Subtitle = suspendCoroutine { pending += PendingLoad(stream, it) }

    private class PendingLoad(val stream: SubtitlesStream, private val continuation: Continuation<Subtitle>) {
        fun complete(subtitle: Subtitle) = continuation.resume(subtitle)
    }

    private class RecordingSubtitle : Subtitle {
        val queries = mutableListOf<Long>()
        private val cue = Cue.Builder().setText("Secondary caption").build()

        override fun getNextEventTimeIndex(timeUs: Long): Int = if (timeUs < 0) 0 else -1
        override fun getEventTimeCount(): Int = 1
        override fun getEventTime(index: Int): Long = 0
        override fun getCues(timeUs: Long): List<Cue> {
            queries += timeUs
            return if (timeUs < 0) emptyList() else listOf(cue)
        }
    }
}
