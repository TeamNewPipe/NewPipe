package org.schabi.newpipe.streams.io

import java.io.Closeable
import java.io.Flushable
import java.io.IOException

/**
 * Based on C#'s Stream class. SharpStream is a wrapper around the 2 different APIs for SAF
 * ([us.shandian.giga.io.FileStreamSAF]) and non-SAF ([us.shandian.giga.io.FileStream]).
 * It has both input and output like in C#, while in Java those are usually different classes.
 * [SharpInputStream] and [SharpOutputStream] are simple classes that wrap
 * [SharpStream] and extend respectively [java.io.InputStream] and
 * [java.io.OutputStream], since unfortunately a class can only extend one class, so that a
 * sharp stream can be used with built-in Java stuff that supports [java.io.InputStream]
 * or [java.io.OutputStream].
 */
abstract class SharpStream() : Closeable, Flushable {
    @Throws(IOException::class)
    abstract fun read(): Int
    @Throws(IOException::class)
    abstract fun read(buffer: ByteArray): Int
    @Throws(IOException::class)
    abstract fun read(buffer: ByteArray?, offset: Int, count: Int): Int
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
    open fun canSetLength(): Boolean {
        return false
    }

    open fun canSeek(): Boolean {
        return false
    }

    @Throws(IOException::class)
    abstract fun write(value: Byte)
    @Throws(IOException::class)
    abstract fun write(buffer: ByteArray?)
    @Throws(IOException::class)
    abstract fun write(buffer: ByteArray?, offset: Int, count: Int)
    @Throws(IOException::class)
    public override fun flush() {
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
}
