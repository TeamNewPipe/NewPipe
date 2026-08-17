package us.shandian.giga.io

import java.io.File
import java.io.FileNotFoundException
import okio.IOException
import java.io.RandomAccessFile
import org.schabi.newpipe.streams.io.SharpStream

/**
 * @author kapodamy
 */
class FileStream : SharpStream {

    var source: RandomAccessFile? = null

    @Throws(FileNotFoundException::class)
    constructor(target: File) {
        this.source = RandomAccessFile(target, "rw")
    }

    @Throws(FileNotFoundException::class)
    constructor(path: String) {
        this.source = RandomAccessFile(path, "rw")
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return source!!.read()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int {
        return source!!.read(buffer)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        return source!!.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun skip(amount: Long): Long {
        return source!!.skipBytes(amount.toInt()).toLong()
    }

    override fun available(): Long {
        return try {
            source!!.length() - source!!.filePointer
        } catch (e: IOException) {
            0
        }
    }

    override fun close() {
        val src = source ?: return
        try {
            src.close()
        } catch (err: IOException) {
            // nothing to do
        }
        source = null
    }

    override fun isClosed(): Boolean = source == null

    @Throws(IOException::class)
    override fun rewind() {
        source!!.seek(0)
    }

    override fun canRewind(): Boolean = true

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun canSeek(): Boolean = true

    override fun canSetLength(): Boolean = true

    @Throws(IOException::class)
    override fun write(value: Byte) {
        source!!.write(value.toInt())
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        source!!.write(buffer)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        source!!.write(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun setLength(length: Long) {
        source!!.setLength(length)
    }

    @Throws(IOException::class)
    override fun seek(offset: Long) {
        source!!.seek(offset)
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return source!!.length()
    }
}
