/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.R

class PlayerActionVisibilityTest {
    @Test
    fun missingAndUnknownPreferencesKeepTheExistingControls() {
        assertEquals(emptySet<String>(), PlayerActionVisibility.normalize(null))
        assertEquals(emptySet<String>(), PlayerActionVisibility.normalize(setOf("unknown", "fullscreen")))
        assertEquals(setOf("cast"), PlayerActionVisibility.normalize(setOf("cast", "unknown")))
        assertEquals(View.VISIBLE, PlayerActionVisibility.visibility(View.VISIBLE, "cast_tv", setOf("cast")))
    }

    @Test
    fun userVisibilityMasksButNeverOverridesFeatureAvailability() {
        for (requested in listOf(View.VISIBLE, View.INVISIBLE, View.GONE)) {
            assertEquals(View.GONE, PlayerActionVisibility.visibility(requested, "captions", setOf("captions")))
            assertEquals(requested, PlayerActionVisibility.visibility(requested, "captions", emptySet()))
        }
    }

    @Test
    fun castDestinationsAndKodiHaveIndependentIdentities() {
        assertEquals("cast", PlayerActionVisibility.actionForView(R.id.castToPairedDevice)?.key)
        assertEquals("cast_tv", PlayerActionVisibility.actionForView(R.id.detail_controls_cast)?.key)
        assertEquals("kodi", PlayerActionVisibility.actionForView(R.id.playWithKodi)?.key)
        assertEquals("kodi", PlayerActionVisibility.actionForView(R.id.detail_controls_play_with_kodi)?.key)
    }

    @Test
    fun transportAndWaysToExitOrUnlockCannotBeHidden() {
        for (id in listOf(
            R.id.playPauseButton,
            R.id.playPreviousButton,
            R.id.playNextButton,
            R.id.playbackSeekBar,
            R.id.playerCloseButton,
            R.id.screenRotationButton,
            R.id.fullScreenButton,
            R.id.touchUnlockButton,
            R.id.listenModeButton,
            R.id.moreOptionsButton
        )) {
            assertNull(PlayerActionVisibility.actionForView(id))
        }
    }
}
