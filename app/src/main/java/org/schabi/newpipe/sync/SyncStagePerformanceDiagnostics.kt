/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

/** Optional, aggregate process-level timing without recording any synchronization content. */
internal object SyncStagePerformanceDiagnostics {
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
