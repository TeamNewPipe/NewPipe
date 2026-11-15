package org.schabi.newpipe.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevicePerformanceTest {
    @Test
    fun lowRamDevicesUseQuarterSizedLocalThumbnailCache() {
        assertEquals(
            4 * 1024 * 1024,
            DevicePerformance.localThumbnailCacheBytes(lowRam = true)
        )
        assertEquals(
            16 * 1024 * 1024,
            DevicePerformance.localThumbnailCacheBytes(lowRam = false)
        )
    }
}
