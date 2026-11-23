/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding

class PreviousPlaybackReadabilityTest {
    @Test
    fun returnActionStaysReadableAboveLoadingAndErrorsOnPhoneTabletAndRtl() {
        Log.i(TAG, "Launching readability host")
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Log.i(TAG, "Readability host resumed")
                val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
                val key = PlayerActionVisibility.preferenceKey(activity)
                val previousHidden = preferences.getStringSet(key, null)?.toSet()
                PlayerActionVisibility.save(activity, emptySet())
                try {
                    listOf(320, 960).forEach { widthDp ->
                        listOf(false, true).forEach { rtl ->
                            assertReadableLayout(activity, widthDp, rtl)
                        }
                    }
                } finally {
                    preferences.edit().apply {
                        previousHidden?.let { putStringSet(key, it) } ?: remove(key)
                    }.commit()
                }
            }
            Log.i(TAG, "Closing readability host")
        }
        Log.i(TAG, "Readability host closed")
    }

    private fun assertReadableLayout(activity: AboutActivity, widthDp: Int, rtl: Boolean) {
        val label = "$widthDp dp, rtl=$rtl"
        Log.i(TAG, "Inflating $label")
        val configuration = Configuration(activity.resources.configuration).apply {
            fontScale = 1.6f
            screenWidthDp = widthDp
            screenHeightDp = 800
            orientation = if (widthDp == 320) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            setLocale(if (rtl) Locale("ar") else Locale.ENGLISH)
        }
        val themed = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.DarkTheme)
        val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(themed))
        try {
            val action = binding.detailPreviousPlayback
            binding.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            binding.detailThumbnailImageView.layoutParams.height = 100
            binding.detailVideoTitleView.text = "Selected video"
            binding.detailContentRootHiding.visibility = View.GONE
            action.visibility = View.VISIBLE
            binding.errorPanel.root.visibility = View.VISIBLE
            binding.loadingProgressBar.visibility = View.VISIBLE
            activity.setContentView(binding.root)
            Log.i(TAG, "Measuring attached $label")
            val density = themed.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (800 * density).toInt()
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            binding.root.layout(0, 0, width, height)
            Log.i(TAG, "Checking measured $label")
            assertTrue(action.isShown)
            assertTrue(action.height >= (48 * density).toInt())
            assertTrue(action.isFocusable && action.isClickable)
            assertTrue(binding.errorPanel.root.top >= action.bottom)
            assertTrue(binding.loadingProgressBar.top >= action.bottom)
            val layout = action.layout!!
            assertEquals(action.text.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertEquals(0, layout.getEllipsisCount(line))
                assertTrue(layout.getLineWidth(line) <= layout.width + 1f)
            }
        } finally {
            // Stop the synthetic loading state before ActivityScenario waits for the host to idle.
            binding.loadingProgressBar.visibility = View.GONE
            (binding.loadingProgressBar.indeterminateDrawable as? Animatable)?.stop()
            binding.loadingProgressBar.clearAnimation()
            activity.setContentView(FrameLayout(activity))
            Log.i(TAG, "Detached $label")
        }
    }

    companion object {
        private const val TAG = "PreviousPlaybackLayout"
    }
}
