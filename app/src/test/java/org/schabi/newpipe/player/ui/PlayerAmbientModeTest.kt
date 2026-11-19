/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAmbientModeTest {
    @Test
    fun neverAppearsUnlessExplicitlyEnabledInFullscreenVideo() {
        assertFalse(PlayerAmbientMode.shouldShow(false, true, true, false, false))
        assertFalse(PlayerAmbientMode.shouldShow(true, false, true, false, false))
        assertFalse(PlayerAmbientMode.shouldShow(true, true, false, false, false))
        assertTrue(PlayerAmbientMode.shouldShow(true, true, true, false, false))
    }

    @Test
    fun remainsHiddenForSphericalAndMiniPlayers() {
        assertFalse(PlayerAmbientMode.shouldShow(true, true, true, true, false))
        assertFalse(PlayerAmbientMode.shouldShow(true, true, true, false, true))
    }
}
