package org.schabi.newpipe.streams.io

import java.io.Flushable
import okio.Buffer
import okio.Closeable
import okio.IOException
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * Based on C#'s Stream class with Okio integration. SharpStream is a wrapper around the 2 different APIs for SAF
 * ([us.shandian.giga.io.FileStreamSAF]) and non-SAF ([us.shandian.giga.io.FileStream]).
 * It has both input and output like in C#, while in Java those are usually different classes.
 * [SharpInputStream] and [SharpOutputStream] are simple classes that wrap
 * [SharpStream] and extend respectively [java.io.InputStream] and
 * [java.io.OutputStream].
 *
 * For maximum performance, [asSource] and [asSink] provide zero-copy [okio.BufferedSource]
 * and [okio.BufferedSink] interfaces.
 */
abstract class SharpStream : Closeable, Flushable {
    @Throws(IOException::class)
    abstract fun read(): Int

    @Throws(IOException::class)
    abstract fun read(buffer: ByteArray): Int

    @Throws(IOException::class)
    abstract fun read(buffer: ByteArray, offset: Int, count: Int): Int

    @Throws(IOException::class)
    abstract fun skip(amount: Long): Long

    abstract fun available(): Long

    @Throws(IOException::class)
    abstract fun rewind()

    abstract fun isClosed(): Boolean

    abstract override fun close()

    abstract fun canRewind(): Boolean

    abstract fun canRead(): Boolean

    abstract fun canWrite(): Boolean

    open fun canSetLength(): Boolean = false

    open fun canSeek(): Boolean = false

    @Throws(IOException::class)
    abstract fun write(value: Byte)

    @Throws(IOException::class)
    abstract fun write(buffer: ByteArray)

    @Throws(IOException::class)
    abstract fun write(buffer: ByteArray, offset: Int, count: Int)

    @Throws(IOException::class)
    override fun flush() {
        // STUB
    }

    @Throws(IOException::class)
    open fun setLength(length: Long) {
        throw IOException("Not implemented")
    }

    @Throws(IOException::class)
    open fun seek(offset: Long) {
        throw IOException("Not implemented")
    }

    @Throws(IOException::class)
    open fun length(): Long {
        throw UnsupportedOperationException("Unsupported operation")
    }

    /**
     * Exposes this stream as an Okio [Source] with timeout and zero-copy buffer support.
     */
    fun asSource(): Source = SharpStreamSource(this)

    /**
     * Exposes this stream as an Okio [Sink] with timeout and zero-copy buffer support.
     */
    fun asSink(): Sink = SharpStreamSink(this)

    /**
     * Exposes this stream as a high-performance Okio [okio.BufferedSource].
     */
    fun bufferedSource() = asSource().buffer()

    /**
     * Exposes this stream as a high-performance Okio [okio.BufferedSink].
     */
    fun bufferedSink() = asSink().buffer()
}

/**
 * Okio Source implementation wrapping a readable [SharpStream].
 */
private class SharpStreamSource(private val stream: SharpStream) : Source {
    private val timeout = Timeout()

    @Throws(IOException::class)
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (!stream.canRead()) {
            throw IOException("SharpStream is not readable")
        }
        val toRead = minOf(byteCount, 65536L).toInt()
        val temp = ByteArray(toRead)
        val bytesRead = stream.read(temp, 0, toRead)
        if (bytesRead <= 0) {
            return -1L
        }
        sink.write(temp, 0, bytesRead)
        return bytesRead.toLong()
    }

    override fun timeout(): Timeout = timeout

    @Throws(IOException::class)
    override fun close() {
        stream.close()
    }
}

/**
 * Okio Sink implementation wrapping a writable [SharpStream].
 */
private class SharpStreamSink(private val stream: SharpStream) : Sink {
    private val timeout = Timeout()

    @Throws(IOException::class)
    override fun write(source: Buffer, byteCount: Long) {
        if (!stream.canWrite()) {
            throw IOException("SharpStream is not writable")
        }
        var remaining = byteCount
        val temp = ByteArray(65536)
        while (remaining > 0) {
            val toRead = minOf(remaining, temp.size.toLong()).toInt()
            val bytesRead = source.read(temp, 0, toRead)
            if (bytesRead == -1) break
            stream.write(temp, 0, bytesRead)
            remaining -= bytesRead
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        stream.flush()
    }

    override fun timeout(): Timeout = timeout

    @Throws(IOException::class)
    override fun close() {
        stream.close()
    }
}
