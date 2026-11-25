package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player

class DualCaptionLanguagePickerTest {
    @Test
    fun serviceOwnedMainAndPopupWindowsCanSelectReopenAndDetachTheirPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val previousLanguage = preferences.getString(PREFERENCE, null)
        val packageName = instrumentation.targetContext.packageName
        val previousOverlayMode = Regex("SYSTEM_ALERT_WINDOW: (\\w+)")
            .find(shell("appops get $packageName SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            assertTrue(Settings.canDrawOverlays(instrumentation.targetContext))
            preferences.edit().remove(PREFERENCE).commit()
            withCaptionPlayer { scenario, player ->
                var controller: DualCaptionController? = null
                var floating: RelativeLayout? = null
                val windows = player.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                try {
                    scenario.onActivity { activity ->
                        val host = FrameLayout(activity)
                        activity.setContentView(host)
                        val main = content(player)
                        host.addView(main, FrameLayout.LayoutParams(320, 180))
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val host = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
                        val main = host.getChildAt(0) as RelativeLayout
                        controller = createController(player, main)
                        controller!!.showLanguagePicker()
                        val first = picker(controller!!)!!
                        assertOwnedWindow(main, first)
                        controller!!.showLanguagePicker()
                        val reopened = picker(controller!!)!!
                        assertNotSame(first, reopened)
                        assertFalse(first.isShowing)
                        choose(first, 1)
                        assertNull(preferences.getString(PREFERENCE, null))
                        choose(reopened, 2)
                        assertEquals("es", preferences.getString(PREFERENCE, null))
                        assertFalse(reopened.isShowing)

                        controller!!.showLanguagePicker()
                        val removed = picker(controller!!)!!
                        host.removeView(main)
                        assertFalse(removed.isShowing)
                        choose(removed, 1)
                        assertEquals("es", preferences.getString(PREFERENCE, null))
                        controller!!.release()

                        // Use the real floating-player window type, not an Activity-owned popup.
                        val popupContent = content(player)
                        floating = popupContent
                        windows.addView(
                            popupContent,
                            WindowManager.LayoutParams(
                                320,
                                180,
                                PopupPlayerUi.popupLayoutParamType(),
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT
                            )
                        )
                        controller = createController(player, popupContent)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        controller!!.showLanguagePicker()
                        val dialog = picker(controller!!)!!
                        assertOwnedWindow(floating!!, dialog)
                        choose(dialog, 1)
                        assertEquals("fr", preferences.getString(PREFERENCE, null))
                        controller!!.showLanguagePicker()
                        val closing = picker(controller!!)!!
                        windows.removeViewImmediate(floating!!)
                        assertFalse(closing.isShowing)
                        choose(closing, 2)
                        assertEquals("fr", preferences.getString(PREFERENCE, null))
                        controller!!.showLanguagePicker()
                        assertNull(picker(controller!!))
                    }
                } finally {
                    instrumentation.runOnMainSync {
                        controller?.release()
                        floating?.takeIf { it.isAttachedToWindow }?.let(windows::removeViewImmediate)
                    }
                }
            }
        } finally {
            preferences.edit().apply {
                if (previousLanguage == null) remove(PREFERENCE) else putString(PREFERENCE, previousLanguage)
            }.commit()
            shell("appops set $packageName SYSTEM_ALERT_WINDOW $previousOverlayMode")
        }
    }

    private fun content(player: Player) = RelativeLayout(PlayerUiTheme.createContext(player.context)).apply {
        addView(SubtitleView(context))
    }

    private fun createController(player: Player, content: RelativeLayout) = DualCaptionController(
        player,
        content,
        content.getChildAt(0) as SubtitleView
    ).apply {
        onMetadata(
            StreamInfo(0, "captions", "https://example.invalid/captions", "Caption fixture").apply {
                subtitles = listOf("fr", "es").map { language ->
                    SubtitlesStream.Builder()
                        .setContent("WEBVTT\n\n00:00.200 --> 00:00.300\nShort caption\n", false)
                        .setLanguageCode(language).setMediaFormat(MediaFormat.VTT).setAutoGenerated(false).build()
                }
            }
        )
    }

    private fun picker(controller: DualCaptionController): AlertDialog? = DualCaptionController::class.java
        .getDeclaredField("languagePicker").apply { isAccessible = true }.get(controller) as AlertDialog?

    private fun assertOwnedWindow(content: RelativeLayout, dialog: AlertDialog) {
        assertTrue(dialog.isShowing)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, dialog.window!!.attributes.type)
        assertSame(content.windowToken, dialog.window!!.attributes.token)
        assertEquals(3, dialog.listView.adapter.count)
    }

    private fun choose(dialog: AlertDialog, index: Int) {
        dialog.listView.performItemClick(null, index, index.toLong())
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private companion object {
        const val PREFERENCE = "dual_captions_secondary_language"
    }
}
