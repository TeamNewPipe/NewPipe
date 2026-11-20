/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedResumeOrientationPolicyTest {
    private fun check(
        collapsed: Boolean = true,
        videoSelected: Boolean = true,
        fullscreen: Boolean = false,
        pip: Boolean = false,
        nonPhone: Boolean = false,
        orientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ): Boolean = CollapsedResumeOrientationPolicy.shouldRestore(
        collapsed,
        videoSelected,
        fullscreen,
        pip,
        nonPhone,
        orientation
    )

    @Test
    fun restoresStaleLandscapeLockOnForegroundResume() {
        assertTrue(check(orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE))
        assertTrue(check(orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE))
        assertTrue(check(fullscreen = true))
    }

    @Test
    fun ignoresExpandedNativePipAndNonPhoneWindows() {
        assertFalse(check(collapsed = false, fullscreen = true))
        assertFalse(check(pip = true, fullscreen = true))
        assertFalse(check(nonPhone = true, fullscreen = true))
        assertFalse(check(videoSelected = false, fullscreen = true))
        assertFalse(check())
    }
}
