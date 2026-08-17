package us.shandian.giga.io

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import okio.IOException
import java.nio.channels.FileChannel
import org.schabi.newpipe.streams.io.SharpStream

class FileStreamSAF(contentResolver: ContentResolver, fileUri: Uri) : SharpStream() {

    private val inputStream: FileInputStream
    private val outputStream: FileOutputStream
    private val channel: FileChannel
    private val pfd: ParcelFileDescriptor

    private var disposed: Boolean = false

    init {
        // Notes:
        // the file must exists first
        // read-write mode must allow seek!
        // It is not guaranteed to work with files in the cloud (virtual files), tested in local storage devices

        val descriptor = contentResolver.openFileDescriptor(fileUri, "rw")
            ?: throw IOException("Cannot get the ParcelFileDescriptor for $fileUri")

        pfd = descriptor
        inputStream = FileInputStream(pfd.fileDescriptor)
        outputStream = FileOutputStream(pfd.fileDescriptor)
        channel = outputStream.channel // or use inputStream.getChannel()
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return inputStream.read()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int {
        return inputStream.read(buffer)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        return inputStream.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun skip(amount: Long): Long {
        return inputStream.skip(amount)
    }

    override fun available(): Long {
        return try {
            inputStream.available().toLong()
        } catch (e: IOException) {
            0 // but not -1!
        }
    }

    @Throws(IOException::class)
    override fun rewind() {
        seek(0)
    }

    override fun close() {
        try {
            disposed = true
            pfd.close()
            inputStream.close()
            outputStream.close()
            channel.close()
        } catch (e: IOException) {
            Log.e("FileStreamSAF", "close() error", e)
        }
    }

    override fun isClosed(): Boolean = disposed

    override fun canRewind(): Boolean = true

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun canSetLength(): Boolean = true

    override fun canSeek(): Boolean = true

    @Throws(IOException::class)
    override fun write(value: Byte) {
        outputStream.write(value.toInt())
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        outputStream.write(buffer)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        outputStream.write(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun setLength(length: Long) {
        channel.truncate(length)
    }

    @Throws(IOException::class)
    override fun seek(offset: Long) {
        channel.position(offset)
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return channel.size()
    }
}
