package org.schabi.newpipe.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.cast.driver.chromecast.CastMessage
import org.schabi.newpipe.cast.driver.chromecast.CastMessageWire

class CastMessageWireTest {

    @Test
    fun testCastMessageRoundTrip() {
        val original = CastMessage(
            sourceId = "sender-0",
            destinationId = "receiver-0",
            namespace = "urn:x-cast:com.google.cast.tp.connection",
            payloadUtf8 = "{\"type\":\"CONNECT\"}"
        )

        val out = ByteArrayOutputStream()
        CastMessageWire.writeMessage(out, original)

        val bytes = out.toByteArray()
        val inStream = ByteArrayInputStream(bytes)

        val deserialized = CastMessageWire.readMessage(inStream)

        assertNotNull(deserialized)
        assertEquals(original.sourceId, deserialized?.sourceId)
        assertEquals(original.destinationId, deserialized?.destinationId)
        assertEquals(original.namespace, deserialized?.namespace)
        assertEquals(original.payloadUtf8, deserialized?.payloadUtf8)
    }

    @Test
    fun testCastMessageWithUnicodeAndComplexJson() {
        val jsonPayload = "{\"type\":\"LOAD\",\"media\":{\"title\":\"Music 🎵 日本語\",\"contentId\":\"https://example.com/stream.mp4\"}}"
        val original = CastMessage(
            sourceId = "sender-42",
            destinationId = "transport-1234",
            namespace = "urn:x-cast:com.google.cast.media",
            payloadUtf8 = jsonPayload
        )

        val out = ByteArrayOutputStream()
        CastMessageWire.writeMessage(out, original)

        val inStream = ByteArrayInputStream(out.toByteArray())
        val deserialized = CastMessageWire.readMessage(inStream)

        assertNotNull(deserialized)
        assertEquals(original.sourceId, deserialized?.sourceId)
        assertEquals(original.destinationId, deserialized?.destinationId)
        assertEquals(original.namespace, deserialized?.namespace)
        assertEquals(original.payloadUtf8, deserialized?.payloadUtf8)
    }

    @Test
    fun testTruncatedInputReturnsNull() {
        val truncated = ByteArray(2) // Only 2 bytes, needs at least 4 for header
        val inStream = ByteArrayInputStream(truncated)
        val deserialized = CastMessageWire.readMessage(inStream)
        assertNull(deserialized)
    }
}
