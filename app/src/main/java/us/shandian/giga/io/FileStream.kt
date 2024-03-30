package us.shandian.giga.io

import org.schabi.newpipe.streams.io.SharpStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * @author kapodamy
 */
class FileStream : SharpStream {
    var source: RandomAccessFile?

    constructor(target: File) {
        source = RandomAccessFile(target, "rw")
    }

    constructor(path: String) {
        source = RandomAccessFile(path, "rw")
    }

    @Throws(IOException::class)
    public override fun read(): Int {
        return source!!.read()
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray): Int {
        return source!!.read(b)
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return source!!.read(b, off, len)
    }

    @Throws(IOException::class)
    public override fun skip(pos: Long): Long {
        return source!!.skipBytes(pos.toInt()).toLong()
    }

    public override fun available(): Long {
        try {
            return source!!.length() - source!!.getFilePointer()
        } catch (e: IOException) {
            return 0
        }
    }

    public override fun close() {
        if (source == null) return
        try {
            source!!.close()
        } catch (err: IOException) {
            // nothing to do
        }
        source = null
    }

    public override fun isClosed(): Boolean {
        return source == null
    }

    @Throws(IOException::class)
    public override fun rewind() {
        source!!.seek(0)
    }

    public override fun canRewind(): Boolean {
        return true
    }

    public override fun canRead(): Boolean {
        return true
    }

    public override fun canWrite(): Boolean {
        return true
    }

    public override fun canSeek(): Boolean {
        return true
    }

    public override fun canSetLength(): Boolean {
        return true
    }

    @Throws(IOException::class)
    public override fun write(value: Byte) {
        source!!.write(value.toInt())
    }

    @Throws(IOException::class)
    public override fun write(buffer: ByteArray?) {
        source!!.write(buffer)
    }

    @Throws(IOException::class)
    public override fun write(buffer: ByteArray?, offset: Int, count: Int) {
        source!!.write(buffer, offset, count)
    }

    @Throws(IOException::class)
    public override fun setLength(length: Long) {
        source!!.setLength(length)
    }

    @Throws(IOException::class)
    public override fun seek(offset: Long) {
        source!!.seek(offset)
    }

    @Throws(IOException::class)
    public override fun length(): Long {
        return source!!.length()
    }
}
