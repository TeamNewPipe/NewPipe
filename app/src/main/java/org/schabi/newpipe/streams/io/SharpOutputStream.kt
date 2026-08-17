package org.schabi.newpipe.streams.io

import java.io.OutputStream
import okio.IOException

/**
 * Simply wraps a writable [SharpStream] allowing it to be used with built-in Java stuff that
 * supports [OutputStream].
 */
class SharpOutputStream
@Throws(IOException::class)
constructor(
    private val stream: SharpStream
) : OutputStream() {

    init {
        if (!stream.canWrite()) {
            throw IOException("SharpStream is not writable")
        }
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        stream.write(b.toByte())
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        stream.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        stream.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        stream.flush()
    }

    override fun close() {
        stream.close()
    }
}
