package us.shandian.giga.io

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import org.schabi.newpipe.streams.io.SharpStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

class FileStreamSAF(contentResolver: ContentResolver, fileUri: Uri) : SharpStream() {
    private val `in`: FileInputStream
    private val out: FileOutputStream
    private val channel: FileChannel
    private val file: ParcelFileDescriptor?
    private var disposed: Boolean = false

    init {
        // Notes:
        // the file must exists first
        // ¡read-write mode must allow seek!
        // It is not guaranteed to work with files in the cloud (virtual files), tested in local storage devices
        file = contentResolver.openFileDescriptor(fileUri, "rw")
        if (file == null) {
            throw IOException("Cannot get the ParcelFileDescriptor for " + fileUri.toString())
        }
        `in` = FileInputStream(file.getFileDescriptor())
        out = FileOutputStream(file.getFileDescriptor())
        channel = out.getChannel() // or use in.getChannel()
    }

    @Throws(IOException::class)
    public override fun read(): Int {
        return `in`.read()
    }

    @Throws(IOException::class)
    public override fun read(buffer: ByteArray): Int {
        return `in`.read(buffer)
    }

    @Throws(IOException::class)
    public override fun read(buffer: ByteArray?, offset: Int, count: Int): Int {
        return `in`.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    public override fun skip(amount: Long): Long {
        return `in`.skip(amount) // ¿or use channel.position(channel.position() + amount)?
    }

    public override fun available(): Long {
        try {
            return `in`.available().toLong()
        } catch (e: IOException) {
            return 0 // ¡but not -1!
        }
    }

    @Throws(IOException::class)
    public override fun rewind() {
        seek(0)
    }

    public override fun close() {
        try {
            disposed = true
            file!!.close()
            `in`.close()
            out.close()
            channel.close()
        } catch (e: IOException) {
            Log.e("FileStreamSAF", "close() error", e)
        }
    }

    public override fun isClosed(): Boolean {
        return disposed
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

    public override fun canSetLength(): Boolean {
        return true
    }

    public override fun canSeek(): Boolean {
        return true
    }

    @Throws(IOException::class)
    public override fun write(value: Byte) {
        out.write(value.toInt())
    }

    @Throws(IOException::class)
    public override fun write(buffer: ByteArray?) {
        out.write(buffer)
    }

    @Throws(IOException::class)
    public override fun write(buffer: ByteArray?, offset: Int, count: Int) {
        out.write(buffer, offset, count)
    }

    @Throws(IOException::class)
    public override fun setLength(length: Long) {
        channel.truncate(length)
    }

    @Throws(IOException::class)
    public override fun seek(offset: Long) {
        channel.position(offset)
    }

    @Throws(IOException::class)
    public override fun length(): Long {
        return channel.size()
    }
}
