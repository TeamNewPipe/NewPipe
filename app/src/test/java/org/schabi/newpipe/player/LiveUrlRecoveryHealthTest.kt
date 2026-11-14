package org.schabi.newpipe.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUrlRecoveryHealthTest {
    private val health = LiveUrlRecoveryHealth()

    @Test
    fun advancingPlaybackRenewsOnlyAfterThirtySeconds() {
        assertFalse(health.onProgress("live", 0, 1_000, true))
        assertFalse(health.onProgress("live", 29_999, 30_999, true))
        assertTrue(health.onProgress("live", 30_000, 31_000, true))
    }

    @Test
    fun audioAndVideoCallbacksAtTheSamePositionDoNotRestartHealthyPlayback() {
        health.onProgress("live", 0, 1_000, true)
        assertFalse(health.onProgress("live", 0, 1_000, true))
        assertFalse(health.onProgress("live", 20_000, 21_000, true))
        assertFalse(health.onProgress("live", 20_001, 21_000, true))
        assertTrue(health.onProgress("live", 30_000, 31_000, true))
    }

    @Test
    fun frozenPositionAndPausedPlaybackCannotRenewBudget() {
        health.onProgress("live", 0, 1_000, true)
        assertFalse(health.onProgress("live", 30_000, 1_000, true))
        assertFalse(health.onProgress("live", 60_000, 1_000, true))
        assertFalse(health.onProgress("live", 90_000, 2_000, false))
        assertFalse(health.onProgress("live", 120_000, 3_000, true))
    }

    @Test
    fun resetAndAnotherStreamRequireANewHealthyInterval() {
        health.onProgress("live", 0, 1_000, true)
        health.reset()
        assertFalse(health.onProgress("live", 30_000, 31_000, true))
        assertFalse(health.onProgress("other", 60_000, 61_000, true))
        assertTrue(health.onProgress("other", 90_000, 91_000, true))
    }

    @Test
    fun rewindAndClockRollbackCannotCompleteAnOldInterval() {
        health.onProgress("live", 0, 10_000, true)
        assertFalse(health.onProgress("live", 30_000, 5_000, true))
        assertFalse(health.onProgress("live", 1_000, 6_000, true))
    }
}
