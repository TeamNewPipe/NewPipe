/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import kotlin.math.abs
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample

/** Finds chronological replay highlights without treating tiny fluctuations as new destinations. */
object ReplayHeatmapPeaks {
    private const val PLATEAU_TOLERANCE = 0.01f
    private const val MIN_PROMINENCE = 0.05f
    private const val POSITION_TOLERANCE_MS = 500L

    fun find(samples: List<ReplayHeatmapSample>, durationMs: Long): List<Long> {
        if (durationMs <= 0) return emptyList()
        val bins = samples.filter {
            it.startTimeMs >= 0 && it.startTimeMs < durationMs &&
                it.endTimeMs > it.startTimeMs && it.intensity.isFinite() && it.intensity in 0f..1f
        }.sortedBy { it.startTimeMs }
        if (bins.size < 2) return emptyList()

        val plateaus = mutableListOf<Peak>()
        var index = 0
        while (index < bins.size) {
            val first = bins[index]
            var end = index
            while (end + 1 < bins.size && bins[end + 1].startTimeMs <= bins[end].endTimeMs &&
                abs(bins[end + 1].intensity - first.intensity) <= PLATEAU_TOLERANCE
            ) {
                end++
            }
            val finish = bins[end].endTimeMs.coerceAtMost(durationMs)
            plateaus += Peak(first.startTimeMs + (finish - first.startTimeMs) / 2, first.intensity)
            index = end + 1
        }
        if (plateaus.size < 2) return emptyList()

        val candidates = plateaus.filterIndexed { at, peak ->
            val before = plateaus.getOrNull(at - 1)
            val after = plateaus.getOrNull(at + 1)
            peak.value > 0 && (before == null || peak.value > before.value) &&
                (after == null || peak.value > after.value) &&
                prominence(plateaus, at) >= MIN_PROMINENCE
        }
        val minimumSpacing = (durationMs / 200).coerceIn(1000L, 5000L)
        val result = mutableListOf<Peak>()
        candidates.forEach { peak ->
            val previous = result.lastOrNull()
            if (previous == null || peak.timeMs - previous.timeMs >= minimumSpacing) {
                result += peak
            } else if (peak.value > previous.value) {
                result[result.lastIndex] = peak
            }
        }
        return result.map { it.timeMs }
    }

    fun next(peaks: List<Long>, positionMs: Long): Long? = peaks.firstOrNull {
        it > positionMs && it - positionMs > POSITION_TOLERANCE_MS
    }

    private fun prominence(peaks: List<Peak>, index: Int): Float {
        val height = peaks[index].value
        fun floor(step: Int): Float? {
            var at = index + step
            if (at !in peaks.indices) return null
            var lowest = height
            while (at in peaks.indices) {
                val value = peaks[at].value
                if (value > height) break
                lowest = minOf(lowest, value)
                at += step
            }
            return lowest
        }
        val floors = listOfNotNull(floor(-1), floor(1))
        return height - (floors.maxOrNull() ?: height)
    }

    private data class Peak(val timeMs: Long, val value: Float)
}
