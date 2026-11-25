package org.schabi.newpipe.player.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.SubtitleTiming

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 35], application = Application::class)
class DualCaptionDialogTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var context: Context
    private lateinit var content: RelativeLayout
    private lateinit var controller: DualCaptionController
    private val loads = mutableListOf<String>()

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.LightTheme)
        activity.setup().visible()
        // The real video hierarchy is inflated from PlayerService, not an Activity context.
        context = ContextThemeWrapper(activity.get().applicationContext, R.style.LightTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
            .putString("dual_captions_secondary_language", "fr").commit()
        content = RelativeLayout(context)
        val primary = SubtitleView(context)
        content.addView(primary)
        activity.get().setContentView(content)
        content.layout(0, 0, 320, 180)
        val player = mock(Player::class.java)
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        `when`(player.currentStreamInfo).thenReturn(Optional.of(info("first")))
        `when`(player.captionPreference).thenReturn("en")
        `when`(player.subtitleTiming).thenReturn(SubtitleTiming())
        controller = DualCaptionController(
            player,
            content,
            primary,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        ) {
            loads += it.languageTag
            EmptySubtitle
        }
    }

    @After
    fun tearDown() {
        controller.release()
        activity.pause().stop().destroy()
    }

    @Test
    fun serviceContextPickerUsesItsAttachedPlayerTokenAndSelectsTheRequestedLanguage() {
        controller.showLanguagePicker()
        val dialog = picker()!!
        assertTrue(dialog.isShowing)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, dialog.window!!.attributes.type)
        assertSame(content.windowToken, dialog.window!!.attributes.token)
        assertEquals(3, dialog.listView.adapter.count)
        assertEquals(1, dialog.listView.checkedItemPosition)
        choose(dialog, 2)
        assertEquals("es", preference())
        assertEquals(listOf("fr", "es"), loads)
        assertFalse(dialog.isShowing)

        controller.showLanguagePicker()
        val reopened = picker()!!
        assertEquals(2, reopened.listView.checkedItemPosition)
        choose(reopened, 0)
        assertNull(preference())
    }

    @Test
    fun reopeningDismissesThePreviousWindowAndRejectsItsLateChoice() {
        controller.showLanguagePicker()
        val first = picker()!!
        controller.showLanguagePicker()
        val second = picker()!!
        assertNotSame(first, second)
        assertFalse(first.isShowing)
        assertTrue(second.isShowing)
        choose(first, 2)
        assertEquals("fr", preference())
        assertEquals(listOf("fr"), loads)
        assertTrue(second.isShowing)
        choose(second, 2)
        assertEquals("es", preference())
    }

    @Test
    fun mediaChangeAndReleaseDismissThePickerAndInvalidateItsCallbacks() {
        controller.showLanguagePicker()
        val previousMedia = picker()!!
        controller.onMetadata(info("next"))
        assertFalse(previousMedia.isShowing)
        choose(previousMedia, 2)
        assertEquals("fr", preference())
        controller.showLanguagePicker()
        val released = picker()!!
        controller.release()
        assertFalse(released.isShowing)
        choose(released, 2)
        assertEquals("fr", preference())
        controller.onMetadata(info("after-release"))
        controller.showLanguagePicker()
        assertNull(picker())
        assertEquals(listOf("fr", "fr"), loads)
    }

    @Test
    fun detachingThePlayerWindowDismissesAndPreventsAnotherPicker() {
        controller.showLanguagePicker()
        val dialog = picker()!!
        activity.get().setContentView(RelativeLayout(activity.get()))
        assertFalse(dialog.isShowing)
        choose(dialog, 2)
        assertEquals("fr", preference())
        controller.showLanguagePicker()
        assertNull(picker())
    }

    @Test
    fun clearingCaptionsClosesTheDialogWithoutChangingTheSavedLanguage() {
        controller.showLanguagePicker()
        val dialog = picker()!!
        controller.clear()
        assertFalse(dialog.isShowing)
        choose(dialog, 0)
        assertEquals("fr", preference())
        assertNull(picker())
    }

    private fun picker(): AlertDialog? = DualCaptionController::class.java.getDeclaredField("languagePicker")
        .apply { isAccessible = true }.get(controller) as AlertDialog?

    private fun preference(): String? = PreferenceManager.getDefaultSharedPreferences(context)
        .getString("dual_captions_secondary_language", null)

    private fun choose(dialog: AlertDialog, index: Int) {
        dialog.listView.performItemClick(null, index, index.toLong())
    }

    private fun info(id: String) = StreamInfo(0, id, "https://example.invalid/$id", id).apply {
        subtitles = listOf("fr", "es").map { language ->
            SubtitlesStream.Builder().setContent("WEBVTT", false).setLanguageCode(language)
                .setMediaFormat(MediaFormat.VTT).setAutoGenerated(false).build()
        }
    }

    private object EmptySubtitle : Subtitle {
        override fun getNextEventTimeIndex(timeUs: Long): Int = -1
        override fun getEventTimeCount(): Int = 0
        override fun getEventTime(index: Int): Long = error("Empty subtitle")
        override fun getCues(timeUs: Long): List<Cue> = emptyList()
    }
}
