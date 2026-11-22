/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample

class ReplayHeatmapPeaksTest {
    @Test
    fun plateauIsOneChronologicalPeakAndNextDoesNotWrap() {
        val samples = samples(0.1f, 0.8f, 0.8f, 0.1f, 0.2f, 0.9f, 0.1f)
        val peaks = ReplayHeatmapPeaks.find(samples, 70_000)
        assertEquals(listOf(20_000L, 55_000L), peaks)
        assertEquals(20_000L, ReplayHeatmapPeaks.next(peaks, 0))
        assertEquals(55_000L, ReplayHeatmapPeaks.next(peaks, 19_700))
        assertEquals(55_000L, ReplayHeatmapPeaks.next(peaks, 20_000))
        assertNull(ReplayHeatmapPeaks.next(peaks, 55_000))
        assertNull(ReplayHeatmapPeaks.next(peaks, Long.MAX_VALUE))
    }

    @Test
    fun smoothHillSurvivesWhileSmallFluctuationsDoNotCreateExtraStops() {
        assertEquals(listOf(45_000L), ReplayHeatmapPeaks.find(samples(0f, .2f, .4f, .55f, .6f, .58f, .4f, .2f, 0f), 90_000))
        assertEquals(listOf(35_000L), ReplayHeatmapPeaks.find(samples(.1f, .6f, .58f, .61f, .1f), 50_000))
    }

    @Test
    fun flatOrAbsentDataHasNoReplayDestination() {
        assertTrue(ReplayHeatmapPeaks.find(emptyList(), 50_000).isEmpty())
        assertTrue(ReplayHeatmapPeaks.find(samples(.4f, .4f, .4f), 30_000).isEmpty())
        assertTrue(ReplayHeatmapPeaks.find(samples(0f, 0f, 0f), 30_000).isEmpty())
        assertTrue(ReplayHeatmapPeaks.find(samples(0f, 1f, 0f), -1).isEmpty())
    }

    @Test
    fun strongerNearbyPeakWinsWithoutAClusterOfTinyJumps() {
        val samples = listOf(0f, .8f, 0f, .9f, 0f).mapIndexed { index, score ->
            ReplayHeatmapSample(index * 300L, (index + 1) * 300L, score)
        }
        assertEquals(listOf(1050L), ReplayHeatmapPeaks.find(samples, 1500))
    }

    @Test
    fun unsortedAndOutOfDurationSamplesCannotProduceInvalidDestinations() {
        val samples = samples(0f, 1f, 0f).reversed() + listOf(
            ReplayHeatmapSample(30_000, Long.MAX_VALUE, 1f)
        )
        assertEquals(listOf(15_000L), ReplayHeatmapPeaks.find(samples, 30_000))
        assertNull(ReplayHeatmapPeaks.next(emptyList(), 0))
    }

    private fun samples(vararg values: Float) = values.mapIndexed { index, score ->
        ReplayHeatmapSample(index * 10_000L, (index + 1) * 10_000L, score)
    }
}
