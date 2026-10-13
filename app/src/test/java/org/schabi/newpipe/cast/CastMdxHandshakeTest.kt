/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType
import org.schabi.newpipe.cast.util.CastUtils

class CastMdxHandshakeTest {

    @Test
    fun testParseMdxSessionStatusWithRootScreenId() {
        val payload = """
            {
                "type": "mdxSessionStatus",
                "screenId": "screen-abc-123"
            }
        """.trimIndent()

        val screenId = CastUtils.extractScreenId(payload)

        assertEquals("screen-abc-123", screenId)
    }

    @Test
    fun testParseMdxSessionStatusWithNestedDataScreenId() {
        val payload = """
            {
                "type": "mdxSessionStatus",
                "data": {
                    "screenId": "nested-screen-456",
                    "device": "AndroidTV"
                }
            }
        """.trimIndent()

        val screenId = CastUtils.extractScreenId(payload)

        assertEquals("nested-screen-456", screenId)
    }

    @Test
    fun testParseMdxSessionStatusWithScreenUnderscoreId() {
        val payload = """
            {
                "data": {
                    "screen_id": "screen-und-789"
                }
            }
        """.trimIndent()

        val screenId = CastUtils.extractScreenId(payload)

        assertEquals("screen-und-789", screenId)
    }

    @Test
    fun testLoungeDeviceCreationFromMdxScreenId() {
        val screenId = "tv-screen-789"
        val deviceName = "Living Room Android TV"

        val loungeDevice = CastDevice(
            id = "lounge_$screenId",
            name = deviceName,
            type = CastDeviceType.YOUTUBE_LOUNGE,
            screenId = screenId
        )

        assertNotNull(loungeDevice)
        assertEquals("lounge_tv-screen-789", loungeDevice.id)
        assertEquals(CastDeviceType.YOUTUBE_LOUNGE, loungeDevice.type)
        assertEquals("tv-screen-789", loungeDevice.screenId)
    }
}
