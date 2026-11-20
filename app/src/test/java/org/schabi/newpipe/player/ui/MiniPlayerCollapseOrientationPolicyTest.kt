package org.schabi.newpipe.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerCollapseOrientationPolicyTest {
    @Test
    fun phoneCollapseExitsFullscreenEvenWhileOrientationChangeIsPending() {
        val policy = MiniPlayerCollapseOrientationPolicy
        assertTrue(policy.shouldExitFullscreen(true, true, false, false, false))
        assertTrue(policy.shouldExitFullscreen(true, false, true, false, false))
        assertTrue(policy.shouldExitFullscreen(true, true, true, false, false))
    }

    @Test
    fun alreadyMinimizedVideoDoesNotGenerateAnotherOrientationRequest() {
        assertFalse(
            MiniPlayerCollapseOrientationPolicy.shouldExitFullscreen(
                true,
                false,
                false,
                false,
                false
            )
        )
    }

    @Test
    fun androidNativePipAndNonPhoneWindowsRetainTheirOwnOrientation() {
        assertFalse(
            MiniPlayerCollapseOrientationPolicy.shouldExitFullscreen(
                true,
                true,
                false,
                true,
                false
            )
        )
        assertFalse(
            MiniPlayerCollapseOrientationPolicy.shouldExitFullscreen(
                true,
                true,
                false,
                false,
                true
            )
        )
        assertFalse(
            MiniPlayerCollapseOrientationPolicy.shouldExitFullscreen(
                false,
                true,
                false,
                false,
                false
            )
        )
    }
}
