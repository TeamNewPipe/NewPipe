package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStagePerformanceDiagnosticsTest {
    @Test
    fun reportsElapsedAndAggregateAppCpuTimeWithoutAnInvalidPercentage() {
        assertEquals(
            "Elapsed 500 ms; app-process CPU 720 ms",
            SyncStagePerformanceDiagnostics.describe(500, 720)
        )
    }

    @Test
    fun clampsNegativeOrInvalidElapsedSamples() {
        assertEquals(
            "Elapsed 0 ms; app-process CPU 0 ms",
            SyncStagePerformanceDiagnostics.describe(-3, -5)
        )
    }

    @Test
    fun keepsExistingRetryInformationAndLeavesDisabledDiagnosticsUnchanged() {
        assertEquals(
            "Reconnected · Elapsed 100 ms; app-process CPU 35 ms",
            SyncStagePerformanceDiagnostics.combine(
                "Reconnected",
                "Elapsed 100 ms; app-process CPU 35 ms"
            )
        )
        assertEquals(
            "Reconnected",
            SyncStagePerformanceDiagnostics.combine("Reconnected", null)
        )
        assertNull(SyncStagePerformanceDiagnostics.combine(null, null))
    }
}
