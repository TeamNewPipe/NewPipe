package us.shandian.giga.io

import java.io.File
import java.io.FileNotFoundException
import okio.IOException
import org.schabi.newpipe.streams.io.SharpStream

class CircularFileWriter(target: SharpStream, temp: File?, checker: OffsetChecker) : SharpStream() {

    private val callback: OffsetChecker = checker

    var onProgress: ProgressReport? = null
    var onWriteError: WriteError? = null

    private var reportPosition: Long
    private var maxLengthKnown: Long = -1

    private var out: BufferedFile?
    private var aux: BufferedFile?

    init {
        if (temp?.exists() == false) {
            if (!temp.createNewFile()) {
                throw IOException("Cannot create a temporal file")
            }
        }

        aux = BufferedFile(temp!!)
        out = BufferedFile(target)

        reportPosition = NOTIFY_BYTES_INTERVAL.toLong()
    }

    @Throws(IOException::class)
    private fun flushAuxiliar(amount: Long) {
        val auxLocal = aux!!
        val outLocal = out!!
        if (auxLocal.length < 1) {
            return
        }

        outLocal.flush()
        auxLocal.flush()

        val underflow = auxLocal.baseOffset < auxLocal.length || outLocal.baseOffset < outLocal.length
        val buffer = ByteArray(COPY_BUFFER_SIZE)

        auxLocal.target.seek(0)
        outLocal.target.seek(outLocal.length)

        var remaining = amount
        while (remaining > 0) {
            var read = Math.min(remaining, Int.MAX_VALUE.toLong()).toInt()
            read = auxLocal.target.read(buffer, 0, Math.min(read, buffer.size))

            if (read < 1) {
                break
            }

            outLocal.writeProof(buffer, read)
            remaining -= read.toLong()
        }
        val actualAmount = amount - remaining

        if (underflow) {
            if (outLocal.baseOffset >= outLocal.length) {
                // calculate the aux underflow pointer
                if (auxLocal.baseOffset < actualAmount) {
                    outLocal.baseOffset += auxLocal.baseOffset
                    auxLocal.baseOffset = 0
                    outLocal.target.seek(outLocal.baseOffset)
                } else {
                    auxLocal.baseOffset -= actualAmount
                    outLocal.baseOffset = outLocal.length + actualAmount
                }
            } else {
                auxLocal.baseOffset = 0
            }
        } else {
            outLocal.baseOffset += actualAmount
            auxLocal.baseOffset -= actualAmount
        }

        outLocal.length += actualAmount

        if (outLocal.length > maxLengthKnown) {
            maxLengthKnown = outLocal.length
        }

        if (actualAmount < auxLocal.length) {
            // move the excess data to the beginning of the file
            var readOffset = actualAmount
            var writeOffset: Long = 0

            auxLocal.length -= actualAmount
            var length = auxLocal.length
            while (length > 0) {
                var read = Math.min(length, Int.MAX_VALUE.toLong()).toInt()
                read = auxLocal.target.read(buffer, 0, Math.min(read, buffer.size))

                auxLocal.target.seek(writeOffset)
                auxLocal.writeProof(buffer, read)

                writeOffset += read.toLong()
                readOffset += read.toLong()
                length -= read.toLong()

                auxLocal.target.seek(readOffset)
            }

            auxLocal.target.setLength(auxLocal.length)
            return
        }

        if (auxLocal.length > THRESHOLD_AUX_LENGTH) {
            auxLocal.target.setLength(THRESHOLD_AUX_LENGTH.toLong()) // or setLength(0);
        }

        auxLocal.reset()
    }

    @Throws(IOException::class)
    fun finalizeFile(): Long {
        flushAuxiliar(aux!!.length)

        out!!.flush()

        // change file length (if required)
        val length = Math.max(maxLengthKnown, out!!.length)
        if (length != out!!.target.length()) {
            out!!.target.setLength(length)
        }

        close()

        return length
    }

    override fun close() {
        out?.close()
        out = null
        aux?.close()
        aux = null
    }

    @Throws(IOException::class)
    override fun write(value: Byte) {
        write(byteArrayOf(value), 0, 1)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        write(buffer, 0, buffer.size)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        if (count == 0) {
            return
        }

        val outLocal = out!!
        val auxLocal = aux!!

        val available: Long
        val offsetOut = outLocal.getOffset()
        val end = callback.check()

        if (end == -1L) {
            available = Int.MAX_VALUE.toLong()
        } else if (end < offsetOut) {
            throw IOException("The reported offset is invalid: $end<$offsetOut")
        } else {
            available = end - offsetOut
        }

        val usingAux = auxLocal.length > 0 && offsetOut >= outLocal.length
        val underflow = auxLocal.baseOffset < auxLocal.length || offsetOut < outLocal.length

        if (usingAux) {
            // before continue calculate the final length of aux
            var length = auxLocal.getOffset() + count
            if (underflow) {
                if (auxLocal.length > length) {
                    length = auxLocal.length // the length is not changed
                }
            } else {
                length = auxLocal.length + count
            }

            auxLocal.write(buffer, offset, count)

            if (length >= THRESHOLD_AUX_LENGTH && length <= available) {
                flushAuxiliar(available)
            }
        } else {
            var currentAvailable = available
            if (underflow) {
                currentAvailable = outLocal.length - offsetOut
            }

            val lengthToWrite = Math.min(count.toLong(), Math.min(Int.MAX_VALUE.toLong(), currentAvailable)).toInt()
            outLocal.write(buffer, offset, lengthToWrite)

            val remainingCount = count - lengthToWrite
            val nextOffset = offset + lengthToWrite

            if (remainingCount > 0) {
                auxLocal.write(buffer, nextOffset, remainingCount)
            }
        }

        onProgress?.let {
            val absoluteOffset = outLocal.getOffset() + auxLocal.getOffset()
            if (absoluteOffset > reportPosition) {
                reportPosition = absoluteOffset + NOTIFY_BYTES_INTERVAL
                it.report(absoluteOffset)
            }
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        aux?.flush()
        out?.flush()

        val total = (out?.length ?: 0) + (aux?.length ?: 0)
        if (total > maxLengthKnown) {
            maxLengthKnown = total
        }
    }

    @Throws(IOException::class)
    override fun skip(amount: Long): Long {
        seek(out!!.getOffset() + aux!!.getOffset() + amount)
        return amount
    }

    @Throws(IOException::class)
    override fun rewind() {
        onProgress?.report(0) // rollback the whole progress

        seek(0)

        reportPosition = NOTIFY_BYTES_INTERVAL.toLong()
    }

    @Throws(IOException::class)
    override fun seek(offset: Long) {
        val total = out!!.length + aux!!.length

        if (offset == total) {
            // do not ignore the seek offset if a underflow exists
            val relativeOffset = out!!.getOffset() + aux!!.getOffset()
            if (relativeOffset == total) {
                return
            }
        }

        // flush everything, avoid any underflow
        flush()

        if (offset < 0 || offset > total) {
            throw IOException("desired offset is outside of range=0-$total offset=$offset")
        }

        if (offset > out!!.length) {
            out!!.seek(out!!.length)
            aux!!.seek(offset - out!!.length)
        } else {
            out!!.seek(offset)
            aux!!.seek(0)
        }
    }

    override fun isClosed(): Boolean = out == null

    override fun canRewind(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun canSeek(): Boolean = true

    override fun canRead(): Boolean = false

    override fun read(): Int {
        throw UnsupportedOperationException("write-only")
    }

    override fun read(buffer: ByteArray): Int {
        throw UnsupportedOperationException("write-only")
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        throw UnsupportedOperationException("write-only")
    }

    override fun available(): Long {
        throw UnsupportedOperationException("write-only")
    }

    fun interface OffsetChecker {
        fun check(): Long
    }

    fun interface WriteError {
        fun handle(err: Exception?): Boolean
    }

    inner class BufferedFile {

        val target: SharpStream

        var baseOffset: Long = 0
        var length: Long = 0

        private var queue: ByteArray? = ByteArray(QUEUE_BUFFER_SIZE)
        private var queueSize: Int = 0

        @Throws(FileNotFoundException::class)
        constructor(file: File) {
            this.target = FileStream(file)
        }

        constructor(target: SharpStream) {
            this.target = target
        }

        fun getOffset(): Long {
            return baseOffset + queueSize // absolute offset in the file
        }

        fun close() {
            queue = null
            target.close()
        }

        @Throws(IOException::class)
        fun write(b: ByteArray, off: Int, len: Int) {
            var currentOff = off
            var currentLen = len
            while (currentLen > 0) {
                val availableSpace = available()
                val read = Math.min(availableSpace, currentLen)

                System.arraycopy(b, currentOff, queue!!, queueSize, read)
                queueSize += read

                currentLen -= read
                currentOff += read
            }

            val total = baseOffset + queueSize
            if (total > length) {
                length = total // save length
            }
        }

        @Throws(IOException::class)
        fun flush() {
            writeProof(queue!!, queueSize)
            baseOffset += queueSize.toLong()
            queueSize = 0
        }

        @Throws(IOException::class)
        protected fun rewind() {
            baseOffset = 0
            target.seek(0)
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
            baseOffset = 0
            length = 0
            target.seek(0)
        }

        @Throws(IOException::class)
        fun seek(absoluteOffset: Long) {
            if (absoluteOffset == baseOffset) {
                return // nothing to do
            }
            baseOffset = absoluteOffset
            target.seek(absoluteOffset)
        }

        @Throws(IOException::class)
        fun writeProof(buffer: ByteArray, length: Int) {
            val errorHandle = onWriteError
            if (errorHandle == null) {
                target.write(buffer, 0, length)
                return
            }

            while (true) {
                try {
                    target.write(buffer, 0, length)
                    return
                } catch (e: Exception) {
                    if (!errorHandle.handle(e)) {
                        throw e // give up
                    }
                }
            }
        }

        override fun toString(): String {
            val absLength = try {
                target.length().toString()
            } catch (e: IOException) {
                "[" + e.localizedMessage + "]"
            }

            return String.format(
                "offset=%s  length=%s  queue=%s  absLength=%s",
                baseOffset,
                length,
                queueSize,
                absLength
            )
        }
    }

    companion object {
        private const val QUEUE_BUFFER_SIZE = 8 * 1024 // 8 KiB
        private const val COPY_BUFFER_SIZE = 128 * 1024 // 128 KiB
        private const val NOTIFY_BYTES_INTERVAL = 64 * 1024 // 64 KiB
        private const val THRESHOLD_AUX_LENGTH = 15 * 1024 * 1024 // 15 MiB
    }
}
