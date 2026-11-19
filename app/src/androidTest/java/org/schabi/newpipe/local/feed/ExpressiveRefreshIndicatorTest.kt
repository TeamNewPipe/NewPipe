/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.view.View
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity

class ExpressiveRefreshIndicatorTest {
    @Test
    fun indicatorCanBeShownAndHiddenDuringRefresh() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val indicator = ExpressiveRefreshIndicator(activity)
                activity.setContentView(indicator)
                indicator.visibility = View.VISIBLE
                assertEquals(View.VISIBLE, indicator.visibility)
                indicator.visibility = View.GONE
                assertEquals(View.GONE, indicator.visibility)
            }
        }
    }
}
