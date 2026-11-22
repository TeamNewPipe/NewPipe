package org.schabi.newpipe.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuPreviewValuesTest {
    @Test
    fun normalizesValuesWithoutChangingPersistedPreferences() {
        val requested = DanmakuPreviewValues(0, 100, -1, 99)
        assertEquals(
            DanmakuPreviewValues(10, 32, 1, 15),
            requested.normalized()
        )
        assertEquals(DanmakuPreviewValues(0, 100, -1, 99), requested)
    }
}
