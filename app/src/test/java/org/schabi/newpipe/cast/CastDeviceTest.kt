package org.schabi.newpipe.cast

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType

class CastDeviceTest {

    @Test
    fun testCastDeviceProperties() {
        val host = InetAddress.getByName("192.168.1.50")
        val device = CastDevice(
            id = "test-device-1",
            name = "Living Room Chromecast",
            type = CastDeviceType.CHROMECAST,
            hostAddress = host,
            port = 8009
        )

        assertEquals("test-device-1", device.id)
        assertEquals("Living Room Chromecast", device.name)
        assertEquals(CastDeviceType.CHROMECAST, device.type)
        assertEquals(host, device.hostAddress)
        assertEquals(8009, device.port)
        assertNull(device.screenId)
        assertNull(device.loungeToken)
    }

    @Test
    fun testYouTubeLoungeDeviceProperties() {
        val device = CastDevice(
            id = "lounge-screen-123",
            name = "Living Room Smart TV",
            type = CastDeviceType.YOUTUBE_LOUNGE,
            screenId = "screen-xyz",
            loungeToken = "token-abc-123"
        )

        assertEquals("lounge-screen-123", device.id)
        assertEquals(CastDeviceType.YOUTUBE_LOUNGE, device.type)
        assertEquals("screen-xyz", device.screenId)
        assertEquals("token-abc-123", device.loungeToken)
        assertNull(device.hostAddress)
    }
}
