package org.schabi.newpipe.cast.driver.chromecast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payloadUtf8: String
)

object CastMessageWire {

    private const val TAG_PROTOCOL_VERSION = (1 shl 3) or 0
    private const val TAG_SOURCE_ID = (2 shl 3) or 2
    private const val TAG_DESTINATION_ID = (3 shl 3) or 2
    private const val TAG_NAMESPACE = (4 shl 3) or 2
    private const val TAG_PAYLOAD_TYPE = (5 shl 3) or 0
    private const val TAG_PAYLOAD_UTF8 = (6 shl 3) or 2

    fun writeMessage(outputStream: OutputStream, message: CastMessage) {
        val payloadStream = ByteArrayOutputStream()

        writeIntField(payloadStream, TAG_PROTOCOL_VERSION, 0)
        writeStringField(payloadStream, TAG_SOURCE_ID, message.sourceId)
        writeStringField(payloadStream, TAG_DESTINATION_ID, message.destinationId)
        writeStringField(payloadStream, TAG_NAMESPACE, message.namespace)
        writeIntField(payloadStream, TAG_PAYLOAD_TYPE, 0) // 0 = STRING
        writeStringField(payloadStream, TAG_PAYLOAD_UTF8, message.payloadUtf8)

        val payload = payloadStream.toByteArray()
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()

        outputStream.write(header)
        outputStream.write(payload)
        outputStream.flush()
    }

    fun readMessage(inputStream: InputStream): CastMessage? {
        val header = ByteArray(4)
        var readHeaderBytes = 0
        while (readHeaderBytes < 4) {
            val count = inputStream.read(header, readHeaderBytes, 4 - readHeaderBytes)
            if (count < 0) return null
            readHeaderBytes += count
        }

        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length <= 0 || length > 10 * 1024 * 1024) return null // Guard against invalid frames

        val payload = ByteArray(length)
        var readPayloadBytes = 0
        while (readPayloadBytes < length) {
            val count = inputStream.read(payload, readPayloadBytes, length - readPayloadBytes)
            if (count < 0) return null
            readPayloadBytes += count
        }

        return parsePayload(payload)
    }

    private fun parsePayload(payload: ByteArray): CastMessage {
        val stream = ByteArrayInputStream(payload)
        var sourceId = ""
        var destinationId = ""
        var namespace = ""
        var payloadUtf8 = ""

        while (stream.available() > 0) {
            val tag = readVarint(stream).toInt()
            when (tag) {
                TAG_SOURCE_ID -> sourceId = readString(stream)

                TAG_DESTINATION_ID -> destinationId = readString(stream)

                TAG_NAMESPACE -> namespace = readString(stream)

                TAG_PAYLOAD_UTF8 -> payloadUtf8 = readString(stream)

                TAG_PROTOCOL_VERSION, TAG_PAYLOAD_TYPE -> readVarint(stream)

                // Ignore int values
                else -> {
                    val wireType = tag and 0x07
                    when (wireType) {
                        0 -> readVarint(stream)

                        2 -> {
                            val len = readVarint(stream).toInt()
                            stream.skip(len.toLong())
                        }

                        else -> break // Skip unknown
                    }
                }
            }
        }

        return CastMessage(sourceId, destinationId, namespace, payloadUtf8)
    }

    private fun writeStringField(out: ByteArrayOutputStream, tag: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, tag.toLong())
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeIntField(out: ByteArrayOutputStream, tag: Int, value: Int) {
        writeVarint(out, tag.toLong())
        writeVarint(out, value.toLong())
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun readVarint(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = input.read()
            if (b == -1) break
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readString(input: InputStream): String {
        val len = readVarint(input).toInt()
        val bytes = ByteArray(len)
        var total = 0
        while (total < len) {
            val count = input.read(bytes, total, len - total)
            if (count < 0) break
            total += count
        }
        return String(bytes, Charsets.UTF_8)
    }
}
