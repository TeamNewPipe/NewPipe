/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R

class SettingsCollapsingHeaderTest {
    @Test
    fun settingsScreensShareOneCollapsingAppBar() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appBar = activity.findViewById<AppBarLayout>(R.id.settings_app_bar)
                val header = activity.findViewById<CollapsingToolbarLayout>(
                    R.id.settings_collapsing_toolbar
                )
                assertNotNull(appBar)
                assertNotNull(header)
                // Keep the large M3 title while reclaiming the empty space below the toolbar.
                val density = activity.resources.displayMetrics.density
                assertEquals(120, kotlin.math.round(header.layoutParams.height / density).toInt())
                val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                assertTrue(header.layoutParams.height > toolbar.layoutParams.height)
                activity.setSettingsSectionTitle("Playback")
                assertEquals("Playback", header.title)
                appBar.setExpanded(false, false)
            }
        }
    }
}
