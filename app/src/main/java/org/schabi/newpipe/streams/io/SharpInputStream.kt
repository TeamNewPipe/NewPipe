package org.schabi.newpipe.streams.io

import java.io.InputStream
import okio.IOException

/**
 * Simply wraps a readable [SharpStream] allowing it to be used with built-in Java stuff that
 * supports [InputStream].
 */
class SharpInputStream
@Throws(IOException::class)
constructor(
    private val stream: SharpStream
) : InputStream() {

    init {
        if (!stream.canRead()) {
            throw IOException("SharpStream is not readable")
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return stream.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return stream.read(b)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return stream.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return stream.skip(n)
    }

    override fun available(): Int {
        val res = stream.available()
        return if (res > Int.MAX_VALUE) Int.MAX_VALUE else res.toInt()
    }

    override fun close() {
        stream.close()
    }
}
