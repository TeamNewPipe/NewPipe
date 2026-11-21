/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN

class FullscreenOrientationPreferenceTest {
    @Test
    fun defaultFullscreenMatchesVideoDimensions() {
        assertEquals(
            Configuration.ORIENTATION_PORTRAIT,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.AUTO,
                true,
                PORTRAIT
            )
        )
        assertEquals(
            Configuration.ORIENTATION_LANDSCAPE,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.AUTO,
                true,
                LANDSCAPE
            )
        )
    }

    @Test
    fun manualFullscreenOrientationOverridesVideoWithoutChangingExit() {
        assertEquals(
            Configuration.ORIENTATION_PORTRAIT,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.PORTRAIT,
                true,
                LANDSCAPE
            )
        )
        assertEquals(
            Configuration.ORIENTATION_LANDSCAPE,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.LANDSCAPE,
                true,
                PORTRAIT
            )
        )
        assertEquals(
            Configuration.ORIENTATION_UNDEFINED,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.LANDSCAPE,
                false,
                PORTRAIT
            )
        )
        assertEquals(
            Configuration.ORIENTATION_LANDSCAPE,
            FullscreenOrientationPreference.targetOrientation(
                FullscreenOrientationPreference.Mode.AUTO,
                true,
                UNKNOWN
            )
        )
    }
}
