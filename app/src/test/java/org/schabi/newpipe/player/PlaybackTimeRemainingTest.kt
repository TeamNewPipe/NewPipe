/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.player.PlaybackTimeRemaining.Interval

class PlaybackTimeRemainingTest {
    @Test
    fun accountsForPositionAndPlaybackSpeed() {
        assertEquals(540_000L, remaining(1_800_000, 600_000, 2f, Interval(900_000, 1_020_000)))
        assertEquals(2_400_000L, remaining(1_800_000, 600_000, 0.5f))
    }

    @Test
    fun mergesOverlapsDuplicatesAndAdjacentIntervalsOnce() {
        assertEquals(
            15_000L,
            remaining(
                100_000,
                20_000,
                2f,
                Interval(70_000, 90_000),
                Interval(10_000, 30_000),
                Interval(20_000, 40_000),
                Interval(20_000, 40_000),
                Interval(40_000, 50_000)
            )
        )
    }

    @Test
    fun clipsSkipsToUnplayedPartOfTheCurrentItem() {
        assertEquals(
            65_000L,
            remaining(
                100_000,
                20_000,
                1f,
                Interval(0, 25_000),
                Interval(90_000, 150_000),
                Interval(150_000, 200_000)
            )
        )
    }

    @Test
    fun ignoresInvalidIntervalsAndClampsPositions() {
        assertEquals(
            100_000L,
            remaining(
                100_000,
                -500,
                1f,
                Interval(-10, 50_000),
                Interval(20_000, 10_000),
                Interval(0, 0)
            )
        )
        assertEquals(0L, remaining(100_000, 100_001, 1f, Interval(0, 100_000)))
    }

    @Test
    fun unknownDurationsAndInvalidSpeedsHaveNoEstimate() {
        listOf(0L, -1L, Long.MIN_VALUE + 1).forEach { assertNull(remaining(it, 0, 1f)) }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { assertNull(remaining(1000, 0, it)) }
    }

    @Test
    fun neverShowsFinishedBeforeTheFinalMillisecondAndDoesNotOverflow() {
        assertEquals(1L, remaining(2, 1, 2f))
        assertEquals(Long.MAX_VALUE, remaining(Long.MAX_VALUE, 0, 0.5f))
        assertEquals(0L, remaining(Long.MAX_VALUE, 0, 1f, Interval(0, Long.MAX_VALUE), Interval(1, Long.MAX_VALUE)))
    }

    private fun remaining(duration: Long, position: Long, speed: Float, vararg intervals: Interval) = PlaybackTimeRemaining.estimate(duration, position, speed, intervals.toList())
}
