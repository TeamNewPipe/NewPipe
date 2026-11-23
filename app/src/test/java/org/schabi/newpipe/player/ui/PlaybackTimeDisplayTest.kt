/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], application = android.app.Application::class)
class PlaybackTimeDisplayTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val controllers = mutableListOf<PlaybackTimeDisplay>()
    private var reading = PlaybackTimeDisplay.Reading(1_800_000, 540_000)

    @Before
    fun setUp() {
        preferences.edit().remove(PlaybackTimeDisplay.REMAINING_KEY).commit()
    }

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        preferences.edit().remove(PlaybackTimeDisplay.REMAINING_KEY).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun tapTogglesAndPersistsAcrossPlayerSurfaces() {
        val first = bind()
        assertEquals("30:00", first.text.toString())
        assertTrue(first.performClick())
        assertEquals("−09:00", first.text.toString())
        assertTrue(preferences.getBoolean(PlaybackTimeDisplay.REMAINING_KEY, false))
        val popup = bind()
        assertEquals("−09:00", popup.text.toString())
        assertTrue(popup.performClick())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("30:00", first.text.toString())
        assertEquals("30:00", popup.text.toString())
    }

    @Test
    fun refreshUsesTheLatestPausedPositionSpeedAndSkipEstimate() {
        val view = bind()
        view.performClick()
        reading = PlaybackTimeDisplay.Reading(1_800_000, 123_000)
        controllers.single().update()
        assertEquals("−02:03", view.text.toString())
        reading = PlaybackTimeDisplay.Reading(1_800_000, 1)
        controllers.single().update()
        assertEquals("−00:01", view.text.toString())
        reading = PlaybackTimeDisplay.Reading(1_800_000, 0)
        controllers.single().update()
        assertEquals("−00:00", view.text.toString())
        reading = PlaybackTimeDisplay.Reading(60_000, 50_000)
        controllers.single().update()
        assertEquals("−00:50", view.text.toString())
    }

    @Test
    fun unavailableReadingAndAccessibilityToggleRemainExplicit() {
        val view = bind()
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(context.getString(R.string.playback_time_remaining_description, "09:00"), view.contentDescription)
        assertEquals(context.getString(R.string.playback_time_show_total), clickLabel(view))
        reading = PlaybackTimeDisplay.Reading(-1, null)
        controllers.single().update()
        assertEquals("—", view.text.toString())
        assertEquals(context.getString(R.string.playback_time_unavailable), view.contentDescription)
        assertTrue(view.performClick())
        assertEquals(context.getString(R.string.playback_time_show_remaining), clickLabel(view))
    }

    @Test
    fun closingUnregistersCallbacksAndDoesNotChangeTheSavedChoice() {
        val view = bind()
        view.performClick()
        controllers.single().close()
        assertFalse(view.hasOnClickListeners())
        assertTrue(preferences.getBoolean(PlaybackTimeDisplay.REMAINING_KEY, false))
        preferences.edit().putBoolean(PlaybackTimeDisplay.REMAINING_KEY, false).commit()
        reading = PlaybackTimeDisplay.Reading(120_000, 100_000)
        shadowOf(Looper.getMainLooper()).idle()
        controllers.single().update()
        assertEquals("−09:00", view.text.toString())
        assertFalse(view.performClick())
    }

    private fun bind() = TextView(context).also { view ->
        controllers += PlaybackTimeDisplay(view) { reading }
    }

    private fun clickLabel(view: TextView): CharSequence? = view.createAccessibilityNodeInfo().let { info ->
        try {
            info.actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label
        } finally {
            info.recycle()
        }
    }
}
