package org.schabi.newpipe.streams.io

import java.io.IOException
import java.io.InputStream

/**
 * Simply wraps a readable [SharpStream] allowing it to be used with built-in Java stuff that
 * supports [InputStream].
 */
class SharpInputStream(stream: SharpStream?) : InputStream() {
    private val stream: SharpStream?

    init {
        if (!stream!!.canRead()) {
            throw IOException("SharpStream is not readable")
        }
        this.stream = stream
    }

    @Throws(IOException::class)
    public override fun read(): Int {
        return stream!!.read()
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray): Int {
        return stream!!.read(b)
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray, off: Int, len: Int): Int {
        return stream!!.read(b, off, len)
    }

    @Throws(IOException::class)
    public override fun skip(n: Long): Long {
        return stream!!.skip(n)
    }

    public override fun available(): Int {
        val res: Long = stream!!.available()
        return if (res > Int.MAX_VALUE) Int.MAX_VALUE else res.toInt()
    }

    public override fun close() {
        stream!!.close()
    }
}
