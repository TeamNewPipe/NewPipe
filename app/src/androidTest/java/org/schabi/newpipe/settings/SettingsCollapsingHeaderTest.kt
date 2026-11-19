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
                activity.setSettingsSectionTitle("Playback")
                assertEquals("Playback", header.title)
                appBar.setExpanded(false, false)
            }
        }
    }
}
