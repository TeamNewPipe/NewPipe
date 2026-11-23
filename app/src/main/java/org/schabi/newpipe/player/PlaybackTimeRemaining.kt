/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import kotlin.math.ceil

/** Estimate one pass through a finite item, counting overlapping automatic skips once. */
internal object PlaybackTimeRemaining {
    data class Interval(val startMs: Long, val endMs: Long)

    fun estimate(durationMs: Long, positionMs: Long, speed: Float, skips: List<Interval>): Long? {
        if (durationMs <= 0 || !speed.isFinite() || speed <= 0f) return null
        val position = positionMs.coerceIn(0, durationMs)
        val intervals = skips.asSequence()
            .filter { it.startMs >= 0 && it.endMs > it.startMs }
            .map { Interval(maxOf(position, it.startMs), minOf(durationMs, it.endMs)) }
            .filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }
            .toList()
        var skipped = 0L
        var coveredUntil = position
        intervals.forEach { interval ->
            val start = maxOf(coveredUntil, interval.startMs)
            if (interval.endMs > start) skipped += interval.endMs - start
            coveredUntil = maxOf(coveredUntil, interval.endMs)
        }
        val mediaRemaining = durationMs - position - skipped
        return ceil(mediaRemaining / speed.toDouble()).toLong()
    }
}
