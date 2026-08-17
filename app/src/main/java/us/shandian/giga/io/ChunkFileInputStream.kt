package us.shandian.giga.io

import okio.IOException
import org.schabi.newpipe.streams.io.SharpStream

class ChunkFileInputStream(target: SharpStream, start: Long, end: Long, callback: ProgressReport?) : SharpStream() {

    private var source: SharpStream? = target
    private val offset: Long = start
    private val length: Long = end - start
    private var position: Long = 0

    private var progressReport: Long
    private val onProgress: ProgressReport? = callback

    init {
        progressReport = REPORT_INTERVAL.toLong()

        if (length < 1) {
            source?.close()
            throw IOException("The chunk is empty or invalid")
        }
        val sourceLength = source!!.length()
        if (sourceLength < end) {
            try {
                throw IOException(String.format("invalid file length. expected = %s  found = %s", end, sourceLength))
            } finally {
                source?.close()
            }
        }

        source!!.seek(offset)
    }

    /**
     * Get absolute position on file
     *
     * @return the position
     */
    val filePointer: Long
        get() = offset + position

    @Throws(IOException::class)
    override fun read(): Int {
        if (position >= length) {
            return -1
        }

        val res = source!!.read()
        if (res >= 0) {
            position++
        }

        return res
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) {
            return -1
        }
        var len = count
        if (position + len > length) {
            len = (length - position).toInt()
        }
        if (len <= 0) {
            return -1
        }

        val res = source!!.read(buffer, offset, len)
        if (res > 0) {
            position += res.toLong()

            if (onProgress != null && position > progressReport) {
                onProgress.report(position)
                progressReport = position + REPORT_INTERVAL
            }
        }

        return res
    }

    @Throws(IOException::class)
    override fun skip(amount: Long): Long {
        val newPos = Math.min(amount + position, length)

        if (newPos == 0L) {
            return 0
        }

        source!!.seek(offset + newPos)

        val oldPos = position
        position = newPos

        return newPos - oldPos
    }

    override fun available(): Long {
        return length - position
    }

    override fun close() {
        source?.close()
        source = null
    }

    override fun isClosed(): Boolean = source == null

    @Throws(IOException::class)
    override fun rewind() {
        position = 0
        source?.seek(offset)
    }

    override fun canRewind(): Boolean = true

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = false

    override fun write(value: Byte) {}

    override fun write(buffer: ByteArray) {}

    override fun write(buffer: ByteArray, offset: Int, count: Int) {}

    @Throws(IOException::class)
    override fun seek(offset: Long) {
        val newPos = Math.min(offset, length)
        source!!.seek(this.offset + newPos)
        position = newPos
    }

    companion object {
        private const val REPORT_INTERVAL = 256 * 1024
    }
}
