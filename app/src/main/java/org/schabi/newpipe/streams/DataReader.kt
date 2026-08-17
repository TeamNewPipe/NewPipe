package org.schabi.newpipe.streams

import okio.EOFException
import okio.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import org.schabi.newpipe.streams.io.SharpStream

/**
 * @author kapodamy
 */
class DataReader(private val stream: SharpStream) {
    var position: Long = 0
        private set

    private var view: InputStream? = null
    private var viewSize = 0

    private val readBuffer = ByteArray(BUFFER_SIZE)
    private var readOffset = 0
    private var readCount = 0

    private val primitive = ShortArray(LONG_SIZE)

    init {
        readOffset = readBuffer.size
    }

    @Throws(IOException::class)
    fun read(): Int {
        if (fillBuffer()) {
            return -1
        }

        position++
        readCount--

        return readBuffer[readOffset++].toInt() and 0xFF
    }

    @Throws(IOException::class)
    fun skipBytes(byteAmount: Long): Long {
        var amount = byteAmount
        if (readCount < 0) {
            return 0
        } else if (readCount == 0) {
            amount = stream.skip(amount)
        } else {
            if (readCount > amount) {
                readCount -= amount.toInt()
                readOffset += amount.toInt()
            } else {
                amount = (readCount + stream.skip(amount - readCount))
                readCount = 0
                readOffset = readBuffer.size
            }
        }

        position += amount
        return amount
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        primitiveRead(INTEGER_SIZE)
        return (primitive[0].toInt() shl 24) or (primitive[1].toInt() shl 16) or (primitive[2].toInt() shl 8) or primitive[3].toInt()
    }

    @Throws(IOException::class)
    fun readUnsignedInt(): Long {
        val value = readInt()
        return value.toLong() and 0xffffffffL
    }

    @Throws(IOException::class)
    fun readShort(): Short {
        primitiveRead(SHORT_SIZE)
        return ((primitive[0].toInt() shl 8) or primitive[1].toInt()).toShort()
    }

    @Throws(IOException::class)
    fun readLong(): Long {
        primitiveRead(LONG_SIZE)
        val high = (primitive[0].toInt() shl 24) or (primitive[1].toInt() shl 16) or (primitive[2].toInt() shl 8) or primitive[3].toInt()
        val low = (primitive[4].toInt() shl 24) or (primitive[5].toInt() shl 16) or (primitive[6].toInt() shl 8) or primitive[7].toInt()
        return (high.toLong() shl 32) or (low.toLong() and 0xffffffffL)
    }

    @Throws(IOException::class)
    fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    @Throws(IOException::class)
    fun read(buffer: ByteArray, off: Int, c: Int): Int {
        var offset = off
        var count = c

        if (readCount < 0) {
            return -1
        }
        var total = 0

        if (count >= readBuffer.size) {
            if (readCount > 0) {
                System.arraycopy(readBuffer, readOffset, buffer, offset, readCount)
                readOffset += readCount

                offset += readCount
                count -= readCount

                total = readCount
                readCount = 0
            }
            total += max(stream.read(buffer, offset, count), 0)
        } else {
            while (count > 0 && !fillBuffer()) {
                val read = min(readCount, count)
                System.arraycopy(readBuffer, readOffset, buffer, offset, read)

                readOffset += read
                readCount -= read

                offset += read
                count -= read

                total += read
            }
        }

        position += total.toLong()
        return total
    }

    fun available(): Boolean {
        return readCount > 0 || stream.available() > 0
    }

    @Throws(IOException::class)
    fun rewind() {
        stream.rewind()

        if (position - viewSize > 0) {
            viewSize = 0 // drop view
        } else {
            viewSize += position.toInt()
        }

        position = 0
        readOffset = readBuffer.size
        readCount = 0
    }

    fun canRewind(): Boolean {
        return stream.canRewind()
    }

    /**
     * Wraps this instance of `DataReader` into `InputStream`
     * object. Note: Any read in the `DataReader` will not modify
     * (decrease) the view size
     *
     * @param size the size of the view
     * @return the view
     */
    fun getView(size: Int): InputStream {
        if (view == null) {
            view = object : InputStream() {
                @Throws(IOException::class)
                override fun read(): Int {
                    if (viewSize < 1) {
                        return -1
                    }
                    val res = this@DataReader.read()
                    if (res >= 0) {
                        viewSize--
                    }
                    return res
                }

                @Throws(IOException::class)
                override fun read(buffer: ByteArray): Int {
                    return read(buffer, 0, buffer.size)
                }

                @Throws(IOException::class)
                override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                    if (viewSize < 1) {
                        return -1
                    }

                    val res = this@DataReader.read(buffer, offset, min(viewSize, count))
                    viewSize -= res

                    return res
                }

                @Throws(IOException::class)
                override fun skip(amount: Long): Long {
                    if (viewSize < 1) {
                        return 0
                    }
                    val res = this@DataReader.skipBytes(min(amount, viewSize.toLong()))
                    viewSize -= res.toInt()

                    return res
                }

                override fun available(): Int {
                    return viewSize
                }

                override fun close() {
                    viewSize = 0
                }

                override fun markSupported(): Boolean {
                    return false
                }
            }
        }
        viewSize = size

        return view!!
    }

    @Throws(IOException::class)
    private fun primitiveRead(amount: Int) {
        val buffer = ByteArray(amount)
        val read = read(buffer, 0, amount)

        if (read != amount) {
            throw EOFException("Truncated stream, missing ${amount - read} bytes")
        }

        for (i in 0 until amount) {
            primitive[i] = (buffer[i].toInt() and 0xFF).toShort()
        }
    }

    @Throws(IOException::class)
    private fun fillBuffer(): Boolean {
        if (readCount < 0) {
            return true
        }
        if (readOffset >= readBuffer.size) {
            readCount = stream.read(readBuffer)
            if (readCount < 1) {
                readCount = -1
                return true
            }
            readOffset = 0
        }

        return readCount < 1
    }

    companion object {
        const val SHORT_SIZE = 2
        const val LONG_SIZE = 8
        const val INTEGER_SIZE = 4
        const val FLOAT_SIZE = 4

        private const val BUFFER_SIZE = 128 * 1024 // 128 KiB
    }
}
