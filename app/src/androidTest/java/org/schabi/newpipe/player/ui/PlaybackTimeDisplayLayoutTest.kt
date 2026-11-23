/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.PlayerBinding

class PlaybackTimeDisplayLayoutTest {
    @Test
    fun durationTouchAndAccessibilityTogglePreserveSeekSpaceAcrossPlayerLayouts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val bottomKey = context.getString(R.string.bottom_player_controls_key)
        val oldBottom = preferences.all[bottomKey] as? Boolean
        val oldRemaining = preferences.all[PlaybackTimeDisplay.REMAINING_KEY] as? Boolean
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                listOf(Case(360, 800, false), Case(640, 360, false), Case(360, 800, true)).forEach { case ->
                    listOf(false, true).forEach { bottom ->
                        lateinit var binding: PlayerBinding
                        lateinit var controls: BottomPlayerControls
                        lateinit var display: PlaybackTimeDisplay
                        scenario.onActivity { activity ->
                            preferences.edit().putBoolean(bottomKey, bottom).putBoolean(PlaybackTimeDisplay.REMAINING_KEY, false).commit()
                            val config = Configuration(activity.resources.configuration).apply { fontScale = if (case.rtl) 1.5f else 1f }
                            val themed = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.DarkTheme)
                            binding = PlayerBinding.inflate(LayoutInflater.from(themed))
                            binding.root.layoutDirection = if (case.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                            activity.setContentView(binding.root)
                            binding.loadingPanel.visibility = View.GONE
                            binding.playbackControlRoot.visibility = View.VISIBLE
                            binding.playbackCurrentTime.text = "12:34"
                            binding.playbackSeekBar.max = 2_700_000
                            binding.playbackSeekBar.progress = 754_000
                            binding.playbackSeekBar.isEnabled = true
                            controls = BottomPlayerControls(binding)
                            controls.update(true, 0, 0, 0, 0)
                            display = PlaybackTimeDisplay(binding.playbackEndTime) { PlaybackTimeDisplay.Reading(2_700_000, 605_000) }
                        }
                        try {
                            scenario.onActivity {
                                measure(binding.root, case)
                                val seekBefore = bounds(binding.root, binding.playbackSeekBar)
                                val button = bounds(binding.root, binding.playbackEndTime)
                                assertTrue("Duration target must be 48dp in $case, bottom=$bottom: $button", button.height() >= (48 * binding.root.resources.displayMetrics.density).toInt())
                                assertFalse(Rect.intersects(seekBefore, button))
                                tap(binding.root, button.exactCenterX(), button.exactCenterY())
                            }
                            // A TextView posts the click after ACTION_UP; the next main-thread
                            // callback runs after that work instead of asserting inside dispatch.
                            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                            scenario.onActivity {
                                val label = binding.playbackEndTime
                                assertEquals(label.context.getString(R.string.playback_time_remaining_value, "10:05"), label.text.toString())
                                measure(binding.root, case)
                                val seekAfter = bounds(binding.root, binding.playbackSeekBar)
                                val endAfter = bounds(binding.root, label)
                                assertFalse(Rect.intersects(seekAfter, endAfter))
                                assertTrue(seekAfter.width() > 0)
                                assertTrue(endAfter.left >= 0 && endAfter.right <= binding.root.width)
                                assertTrue(label.paint.measureText(label.text.toString()) <= label.width - label.paddingLeft - label.paddingRight + 1)
                                assertEquals(2_700_000, binding.playbackSeekBar.max)
                                assertEquals(754_000, binding.playbackSeekBar.progress)
                                assertTrue(label.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                                assertEquals("45:00", label.text.toString())
                            }
                        } finally {
                            scenario.onActivity {
                                display.close()
                                controls.restore()
                            }
                        }
                    }
                }
            }
        } finally {
            preferences.edit().apply {
                oldBottom?.let { putBoolean(bottomKey, it) } ?: remove(bottomKey)
                oldRemaining?.let { putBoolean(PlaybackTimeDisplay.REMAINING_KEY, it) } ?: remove(PlaybackTimeDisplay.REMAINING_KEY)
            }.commit()
        }
    }

    private fun measure(root: View, case: Case) {
        val density = root.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((case.width * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((case.height * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun bounds(root: ViewGroup, view: View) = Rect().also {
        view.getDrawingRect(it)
        root.offsetDescendantRectToMyCoords(view, it)
    }

    private fun tap(root: View, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(now, now + index * 16L, action, x, y, 0)
            try {
                assertTrue(root.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }

    private data class Case(val width: Int, val height: Int, val rtl: Boolean)
}
