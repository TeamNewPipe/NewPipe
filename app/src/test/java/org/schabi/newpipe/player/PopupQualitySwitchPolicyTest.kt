package org.schabi.newpipe.player

import androidx.media3.common.Player as Media3Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupQualitySwitchPolicyTest {
    @Test
    fun onlyHighSpeedPlayingNonLivePopupRequiresStabilization() {
        assertTrue(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.POPUP, true, false, 3.5f))
        assertFalse(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.POPUP, false, false, 3.5f))
        assertFalse(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.MAIN, true, false, 3.5f))
        assertFalse(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.POPUP, true, true, 3.5f))
        assertFalse(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.POPUP, true, false, 2.0f))
        assertFalse(PopupQualitySwitchPolicy.shouldStabilize(PlayerType.POPUP, true, false, Float.NaN))
    }

    @Test
    fun intermediateIdleMustNotRestoreExtremeSpeedBeforeNewDecoderIsReady() {
        assertFalse(
            PopupQualitySwitchPolicy.shouldFinishOnState(Media3Player.STATE_IDLE, false)
        )
        assertFalse(
            PopupQualitySwitchPolicy.shouldFinishOnState(Media3Player.STATE_IDLE, true)
        )
        assertFalse(
            PopupQualitySwitchPolicy.shouldFinishOnState(Media3Player.STATE_READY, false)
        )
        assertTrue(
            PopupQualitySwitchPolicy.shouldFinishOnState(Media3Player.STATE_READY, true)
        )
        assertTrue(
            PopupQualitySwitchPolicy.shouldFinishOnState(Media3Player.STATE_ENDED, false)
        )
    }

    @Test
    fun manualSpeedChangeOrNewMediaPreventsRestoration() {
        assertTrue(PopupQualitySwitchPolicy.shouldRestore(true, true, 1.0f))
        assertFalse(PopupQualitySwitchPolicy.shouldRestore(true, true, 1.25f))
        assertFalse(PopupQualitySwitchPolicy.shouldRestore(false, true, 1.0f))
        assertFalse(PopupQualitySwitchPolicy.shouldRestore(true, false, 1.0f))
    }
}
