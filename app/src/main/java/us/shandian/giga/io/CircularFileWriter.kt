package us.shandian.giga.io

import org.schabi.newpipe.streams.io.SharpStream
import java.io.File
import java.io.IOException
import java.util.Objects
import kotlin.math.max
import kotlin.math.min

class CircularFileWriter(target: SharpStream?, temp: File?, checker: OffsetChecker) : SharpStream() {
    private val callback: OffsetChecker
    var onProgress: ProgressReport? = null
    var onWriteError: WriteErrorHandle? = null
    private var reportPosition: Long
    private var maxLengthKnown: Long = -1
    private var out: BufferedFile?
    private var aux: BufferedFile?

    init {
        Objects.requireNonNull(checker)
        if (!temp!!.exists()) {
            if (!temp.createNewFile()) {
                throw IOException("Cannot create a temporal file")
            }
        }
        aux = BufferedFile((temp))
        out = BufferedFile(target)
        callback = checker
        reportPosition = NOTIFY_BYTES_INTERVAL.toLong()
    }

    @Throws(IOException::class)
    private fun flushAuxiliar(amount: Long) {
        var amount: Long = amount
        if (aux!!.length < 1) {
            return
        }
        out!!.flush()
        aux!!.flush()
        val underflow: Boolean = aux!!.offset < aux!!.length || out!!.offset < out!!.length
        val buffer: ByteArray = ByteArray(COPY_BUFFER_SIZE)
        aux!!.target!!.seek(0)
        out!!.target!!.seek(out!!.length)
        var length: Long = amount
        while (length > 0) {
            var read: Int = min(length.toDouble(), Int.MAX_VALUE) as Int
            read = aux!!.target!!.read(buffer, 0, min(read.toDouble(), buffer.size.toDouble()).toInt())
            if (read < 1) {
                amount -= length
                break
            }
            out!!.writeProof(buffer, read)
            length -= read.toLong()
        }
        if (underflow) {
            if (out!!.offset >= out!!.length) {
                // calculate the aux underflow pointer
                if (aux!!.offset < amount) {
                    out!!.offset += aux!!.offset
                    aux!!.offset = 0
                    out!!.target!!.seek(out!!.offset)
                } else {
                    aux!!.offset -= amount
                    out!!.offset = out!!.length + amount
                }
            } else {
                aux!!.offset = 0
            }
        } else {
            out!!.offset += amount
            aux!!.offset -= amount
        }
        out!!.length += amount
        if (out!!.length > maxLengthKnown) {
            maxLengthKnown = out!!.length
        }
        if (amount < aux!!.length) {
            // move the excess data to the beginning of the file
            var readOffset: Long = amount
            var writeOffset: Long = 0
            aux!!.length -= amount
            length = aux!!.length
            while (length > 0) {
                var read: Int = min(length.toDouble(), Int.MAX_VALUE) as Int
                read = aux!!.target!!.read(buffer, 0, min(read.toDouble(), buffer.size.toDouble()).toInt())
                aux!!.target!!.seek(writeOffset)
                aux!!.writeProof(buffer, read)
                writeOffset += read.toLong()
                readOffset += read.toLong()
                length -= read.toLong()
                aux!!.target!!.seek(readOffset)
            }
            aux!!.target!!.setLength(aux!!.length)
            return
        }
        if (aux!!.length > THRESHOLD_AUX_LENGTH) {
            aux!!.target!!.setLength(THRESHOLD_AUX_LENGTH.toLong()) // or setLength(0);
        }
        aux!!.reset()
    }

    /**
     * Flush any buffer and close the output file. Use this method if the
     * operation is successful
     *
     * @return the final length of the file
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    fun finalizeFile(): Long {
        flushAuxiliar(aux!!.length)
        out!!.flush()

        // change file length (if required)
        val length: Long = max(maxLengthKnown.toDouble(), out!!.length.toDouble()).toLong()
        if (length != out!!.target!!.length()) {
            out!!.target!!.setLength(length)
        }
        close()
        return length
    }

    /**
     * Close the file without flushing any buffer
     */
    public override fun close() {
        if (out != null) {
            out!!.close()
            out = null
        }
        if (aux != null) {
            aux!!.close()
            aux = null
        }
    }

    @Throws(IOException::class)
    public override fun write(b: Byte) {
        write(byteArrayOf(b), 0, 1)
    }

    @Throws(IOException::class)
    public override fun write(b: ByteArray?) {
        write(b, 0, b!!.size)
    }

    @Throws(IOException::class)
    public override fun write(b: ByteArray?, off: Int, len: Int) {
        var off: Int = off
        var len: Int = len
        if (len == 0) {
            return
        }
        var available: Long
        val offsetOut: Long = out!!.getOffset()
        val offsetAux: Long = aux!!.getOffset()
        val end: Long = callback.check()
        if (end == -1L) {
            available = Int.MAX_VALUE.toLong()
        } else if (end < offsetOut) {
            throw IOException("The reported offset is invalid: " + end + "<" + offsetOut)
        } else {
            available = end - offsetOut
        }
        val usingAux: Boolean = aux!!.length > 0 && offsetOut >= out!!.length
        val underflow: Boolean = offsetAux < aux!!.length || offsetOut < out!!.length
        if (usingAux) {
            // before continue calculate the final length of aux
            var length: Long = offsetAux + len
            if (underflow) {
                if (aux!!.length > length) {
                    length = aux!!.length // the length is not changed
                }
            } else {
                length = aux!!.length + len
            }
            aux!!.write(b, off, len)
            if (length >= THRESHOLD_AUX_LENGTH && length <= available) {
                flushAuxiliar(available)
            }
        } else {
            if (underflow) {
                available = out!!.length - offsetOut
            }
            val length: Int = min(len.toDouble(), (min(Int.MAX_VALUE, available.toDouble()) as Int).toDouble()).toInt()
            out!!.write(b, off, length)
            len -= length
            off += length
            if (len > 0) {
                aux!!.write(b, off, len)
            }
        }
        if (onProgress != null) {
            val absoluteOffset: Long = out!!.getOffset() + aux!!.getOffset()
            if (absoluteOffset > reportPosition) {
                reportPosition = absoluteOffset + NOTIFY_BYTES_INTERVAL
                onProgress!!.report(absoluteOffset)
            }
        }
    }

    @Throws(IOException::class)
    public override fun flush() {
        aux!!.flush()
        out!!.flush()
        val total: Long = out!!.length + aux!!.length
        if (total > maxLengthKnown) {
            maxLengthKnown = total // save the current file length in case the method {@code rewind()} is called
        }
    }

    @Throws(IOException::class)
    public override fun skip(amount: Long): Long {
        seek(out!!.getOffset() + aux!!.getOffset() + amount)
        return amount
    }

    @Throws(IOException::class)
    public override fun rewind() {
        if (onProgress != null) {
            onProgress!!.report(0) // rollback the whole progress
        }
        seek(0)
        reportPosition = NOTIFY_BYTES_INTERVAL.toLong()
    }

    @Throws(IOException::class)
    public override fun seek(offset: Long) {
        val total: Long = out!!.length + aux!!.length
        if (offset == total) {
            // do not ignore the seek offset if a underflow exists
            val relativeOffset: Long = out!!.getOffset() + aux!!.getOffset()
            if (relativeOffset == total) {
                return
            }
        }

        // flush everything, avoid any underflow
        flush()
        if (offset < 0 || offset > total) {
            throw IOException("desired offset is outside of range=0-" + total + " offset=" + offset)
        }
        if (offset > out!!.length) {
            out!!.seek(out!!.length)
            aux!!.seek(offset - out!!.length)
        } else {
            out!!.seek(offset)
            aux!!.seek(0)
        }
    }

    public override fun isClosed(): Boolean {
        return out == null
    }

    public override fun canRewind(): Boolean {
        return true
    }

    public override fun canWrite(): Boolean {
        return true
    }

    public override fun canSeek(): Boolean {
        return true
    }

    // <editor-fold defaultstate="collapsed" desc="stub read methods">
    public override fun canRead(): Boolean {
        return false
    }

    public override fun read(): Int {
        throw UnsupportedOperationException("write-only")
    }

    public override fun read(buffer: ByteArray
    ): Int {
        throw UnsupportedOperationException("write-only")
    }

    public override fun read(buffer: ByteArray?, offset: Int, count: Int
    ): Int {
        throw UnsupportedOperationException("write-only")
    }

    public override fun available(): Long {
        throw UnsupportedOperationException("write-only")
    }

    //</editor-fold>
    open interface OffsetChecker {
        /**
         * Checks the amount of available space ahead
         *
         * @return absolute offset in the file where no more data SHOULD NOT be
         * written. If the value is -1 the whole file will be used
         */
        fun check(): Long
    }

    open interface WriteErrorHandle {
        /**
         * Attempts to handle a I/O exception
         *
         * @param err the cause
         * @return `true` to retry and continue, otherwise, `false`
         * and throw the exception
         */
        fun handle(err: Exception?): Boolean
    }

    internal inner class BufferedFile {
        val target: SharpStream?
        var offset: Long = 0
        var length: Long = 0
        private var queue: ByteArray? = ByteArray(QUEUE_BUFFER_SIZE)
        private var queueSize: Int = 0

        constructor(file: File) {
            target = FileStream(file)
        }

        constructor(target: SharpStream?) {
            this.target = target
        }

        fun getOffset(): Long {
            return offset + queueSize // absolute offset in the file
        }

        fun close() {
            queue = null
            target!!.close()
        }

        @Throws(IOException::class)
        fun write(b: ByteArray?, off: Int, len: Int) {
            var off: Int = off
            var len: Int = len
            while (len > 0) {
                // if the queue is full, the method available() will flush the queue
                val read: Int = min(available().toDouble(), len.toDouble()).toInt()

                // enqueue incoming buffer
                System.arraycopy(b, off, queue, queueSize, read)
                queueSize += read
                len -= read
                off += read
            }
            val total: Long = offset + queueSize
            if (total > length) {
                length = total // save length
            }
        }

        @Throws(IOException::class)
        fun flush() {
            writeProof(queue, queueSize)
            offset += queueSize.toLong()
            queueSize = 0
        }

        @Throws(IOException::class)
        protected fun rewind() {
            offset = 0
            target!!.seek(0)
        }

        @Throws(IOException::class)
        fun available(): Int {
            if (queueSize >= queue!!.size) {
                flush()
                return queue!!.size
            }
            return queue!!.size - queueSize
        }

        @Throws(IOException::class)
        fun reset() {
            offset = 0
            length = 0
            target!!.seek(0)
        }

        @Throws(IOException::class)
        fun seek(absoluteOffset: Long) {
            if (absoluteOffset == offset) {
                return  // nothing to do
            }
            offset = absoluteOffset
            target!!.seek(absoluteOffset)
        }

        @Throws(IOException::class)
        fun writeProof(buffer: ByteArray?, length: Int) {
            if (onWriteError == null) {
                target!!.write(buffer, 0, length)
                return
            }
            while (true) {
                try {
                    target!!.write(buffer, 0, length)
                    return
                } catch (e: Exception) {
                    if (!onWriteError!!.handle(e)) {
                        throw e // give up
                    }
                }
            }
        }

        public override fun toString(): String {
            var absLength: String?
            try {
                absLength = target!!.length().toString()
            } catch (e: IOException) {
                absLength = "[" + e.getLocalizedMessage() + "]"
            }
            return String.format(
                    "offset=%s  length=%s  queue=%s  absLength=%s",
                    offset, length, queueSize, absLength
            )
        }
    }

    companion object {
        private val QUEUE_BUFFER_SIZE: Int = 8 * 1024 // 8 KiB
        private val COPY_BUFFER_SIZE: Int = 128 * 1024 // 128 KiB
        private val NOTIFY_BYTES_INTERVAL: Int = 64 * 1024 // 64 KiB
        private val THRESHOLD_AUX_LENGTH: Int = 15 * 1024 * 1024 // 15 MiB
    }
}
