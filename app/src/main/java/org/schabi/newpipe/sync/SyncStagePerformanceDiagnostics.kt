/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncPerformanceSample(
    val elapsedMillis: Long,
    val processCpuMillis: Long,
    val batches: Int = 1
)

/** Optional, aggregate process-level timing without recording any synchronization content. */
internal object SyncStagePerformanceDiagnostics {
    fun elapsed(start: SyncPerformanceSample?, end: SyncPerformanceSample?): SyncPerformanceSample? {
        if (start == null || end == null) return null
        return SyncPerformanceSample(
            (end.elapsedMillis - start.elapsedMillis).coerceAtLeast(0),
            (end.processCpuMillis - start.processCpuMillis).coerceAtLeast(0)
        )
    }

    fun accumulate(
        previous: SyncPerformanceSample?,
        sample: SyncPerformanceSample?
    ): SyncPerformanceSample? {
        if (sample == null) return previous
        if (previous == null) return sample
        fun add(first: Long, second: Long): Long {
            val safeFirst = first.coerceAtLeast(0)
            val safeSecond = second.coerceAtLeast(0)
            return if (safeFirst > Long.MAX_VALUE - safeSecond) Long.MAX_VALUE else safeFirst + safeSecond
        }
        return SyncPerformanceSample(
            add(previous.elapsedMillis, sample.elapsedMillis),
            add(previous.processCpuMillis, sample.processCpuMillis),
            add(previous.batches.toLong(), sample.batches.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    fun describeIncoming(sample: SyncPerformanceSample): String = "Receiver batches ${sample.batches}: " +
        describe(sample.elapsedMillis, sample.processCpuMillis)

    fun describe(elapsedMillis: Long, processCpuMillis: Long): String {
        return "Elapsed ${elapsedMillis.coerceAtLeast(0)} ms; " +
            "app-process CPU ${processCpuMillis.coerceAtLeast(0)} ms"
    }

    fun combine(retry: String?, performance: String?): String? {
        return listOfNotNull(
            retry?.takeIf(String::isNotBlank),
            performance?.takeIf(String::isNotBlank)
        ).joinToString(" · ").takeIf(String::isNotEmpty)
    }
}
