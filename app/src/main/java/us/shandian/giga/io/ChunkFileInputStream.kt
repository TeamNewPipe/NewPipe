package us.shandian.giga.io

import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException
import kotlin.math.min

class ChunkFileInputStream(private var source: SharpStream?, private val offset: Long, end: Long, private val onProgress: ProgressReport?) : SharpStream() {
    private val length: Long
    private var position: Long = 0
    private var progressReport: Long

    init {
        length = end - offset
        progressReport = REPORT_INTERVAL.toLong()
        if (length < 1) {
            source!!.close()
            throw IOException("The chunk is empty or invalid")
        }
        if (source!!.length() < end) {
            try {
                throw IOException(String.format("invalid file length. expected = %s  found = %s", end, source!!.length()))
            } finally {
                source!!.close()
            }
        }
        source!!.seek(offset)
    }

    /**
     * Get absolute position on file
     *
     * @return the position
     */
    fun getFilePointer(): Long {
        return offset + position
    }

    @Throws(IOException::class)
    public override fun read(): Int {
        if ((position + 1) > length) {
            return 0
        }
        val res: Int = source!!.read()
        if (res >= 0) {
            position++
        }
        return res
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    public override fun read(b: ByteArray?, off: Int, len: Int): Int {
        var len: Int = len
        if ((position + len) > length) {
            len = (length - position).toInt()
        }
        if (len == 0) {
            return 0
        }
        val res: Int = source!!.read(b, off, len)
        position += res.toLong()
        if (onProgress != null && position > progressReport) {
            onProgress.report(position)
            progressReport = position + REPORT_INTERVAL
        }
        return res
    }

    @Throws(IOException::class)
    public override fun skip(pos: Long): Long {
        var pos: Long = pos
        pos = min((pos + position).toDouble(), length.toDouble()).toLong()
        if (pos == 0L) {
            return 0
        }
        source!!.seek(offset + pos)
        val oldPos: Long = position
        position = pos
        return pos - oldPos
    }

    public override fun available(): Long {
        return length - position
    }

    public override fun close() {
        source!!.close()
        source = null
    }

    public override fun isClosed(): Boolean {
        return source == null
    }

    @Throws(IOException::class)
    public override fun rewind() {
        position = 0
        source!!.seek(offset)
    }

    public override fun canRewind(): Boolean {
        return true
    }

    public override fun canRead(): Boolean {
        return true
    }

    public override fun canWrite(): Boolean {
        return false
    }

    public override fun write(value: Byte) {}
    public override fun write(buffer: ByteArray?) {}
    public override fun write(buffer: ByteArray?, offset: Int, count: Int) {}

    companion object {
        private val REPORT_INTERVAL: Int = 256 * 1024
    }
}
