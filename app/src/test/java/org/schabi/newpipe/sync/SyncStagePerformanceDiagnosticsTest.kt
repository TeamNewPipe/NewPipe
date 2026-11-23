package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncStagePerformanceDiagnosticsTest {
    @Test
    fun measuresOneBatchOnlyWhenBothClockSamplesAreAvailable() {
        assertEquals(
            SyncPerformanceSample(150, 210),
            SyncStagePerformanceDiagnostics.elapsed(
                SyncPerformanceSample(1_000, 3_000),
                SyncPerformanceSample(1_150, 3_210)
            )
        )
        assertNull(SyncStagePerformanceDiagnostics.elapsed(null, SyncPerformanceSample(100, 200)))
        assertNull(SyncStagePerformanceDiagnostics.elapsed(SyncPerformanceSample(100, 200), null))
        assertEquals(
            SyncPerformanceSample(0, 0),
            SyncStagePerformanceDiagnostics.elapsed(
                SyncPerformanceSample(100, 200),
                SyncPerformanceSample(50, 100)
            )
        )
    }

    @Test
    fun accumulatingManySamplesDoesNotOverflow() {
        assertEquals(
            SyncPerformanceSample(Long.MAX_VALUE, Long.MAX_VALUE, Int.MAX_VALUE),
            SyncStagePerformanceDiagnostics.accumulate(
                SyncPerformanceSample(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2, Int.MAX_VALUE),
                SyncPerformanceSample(10, 20)
            )
        )
        assertNull(SyncStagePerformanceDiagnostics.accumulate(null, null))
    }

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
