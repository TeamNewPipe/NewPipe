package us.shandian.giga.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.util.Util
import okio.ByteString
import org.schabi.newpipe.R
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.HttpURLConnection
import java.util.Locale

object Utility {
    /**
     * Get amount of free system's memory.
     * @return free memory (bytes)
     */
    fun getSystemFreeMemory(): Long {
        try {
            val statFs: StatFs = StatFs(Environment.getExternalStorageDirectory().getPath())
            return statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong()
        } catch (e: Exception) {
            // do nothing
        }
        return -1
    }

    fun formatBytes(bytes: Long): String {
        val locale: Locale = Locale.getDefault()
        if (bytes < 1024) {
            return String.format(locale, "%d B", bytes)
        } else if (bytes < 1024 * 1024) {
            return String.format(locale, "%.2f kB", bytes / 1024.0)
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(locale, "%.2f MB", bytes / 1024.0 / 1024.0)
        } else {
            return String.format(locale, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }

    fun formatSpeed(speed: Double): String {
        val locale: Locale = Locale.getDefault()
        if (speed < 1024) {
            return String.format(locale, "%.2f B/s", speed)
        } else if (speed < 1024 * 1024) {
            return String.format(locale, "%.2f kB/s", speed / 1024)
        } else if (speed < 1024 * 1024 * 1024) {
            return String.format(locale, "%.2f MB/s", speed / 1024 / 1024)
        } else {
            return String.format(locale, "%.2f GB/s", speed / 1024 / 1024 / 1024)
        }
    }

    fun writeToFile(file: File, serializable: Serializable) {
        try {
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use({ objectOutputStream -> objectOutputStream.writeObject(serializable) })
        } catch (e: Exception) {
            //nothing to do
        }
        //nothing to do
    }

    fun <T> readFromFile(file: File?): T? {
        var `object`: T?
        try {
            ObjectInputStream(FileInputStream(file)).use({ objectInputStream -> `object` = objectInputStream.readObject() as T })
        } catch (e: Exception) {
            Log.e("Utility", "Failed to deserialize the object", e)
            `object` = null
        }
        return `object`
    }

    fun getFileExt(url: String): String? {
        var url: String = url
        var index: Int
        if ((url.indexOf("?").also({ index = it })) > -1) {
            url = url.substring(0, index)
        }
        index = url.lastIndexOf(".")
        if (index == -1) {
            return null
        } else {
            var ext: String = url.substring(index)
            if ((ext.indexOf("%").also({ index = it })) > -1) {
                ext = ext.substring(0, index)
            }
            if ((ext.indexOf("/").also({ index = it })) > -1) {
                ext = ext.substring(0, index)
            }
            return ext.lowercase(Locale.getDefault())
        }
    }

    fun getFileType(kind: Char, file: String): FileType {
        when (kind) {
            'v' -> return FileType.VIDEO
            'a' -> return FileType.MUSIC
            's' -> return FileType.SUBTITLE
        }
        if (file.endsWith(".srt") || file.endsWith(".vtt") || file.endsWith(".ssa")) {
            return FileType.SUBTITLE
        } else if (file.endsWith(".mp3") || file.endsWith(".wav") || file.endsWith(".flac") || file.endsWith(".m4a") || file.endsWith(".opus")) {
            return FileType.MUSIC
        } else if ((file.endsWith(".mp4") || file.endsWith(".mpeg") || file.endsWith(".rm") || file.endsWith(".rmvb")
                        || file.endsWith(".flv") || file.endsWith(".webp") || file.endsWith(".webm"))) {
            return FileType.VIDEO
        }
        return FileType.UNKNOWN
    }

    @ColorInt
    fun getBackgroundForFileType(ctx: Context?, type: FileType?): Int {
        val colorRes: Int
        when (type) {
            FileType.MUSIC -> colorRes = R.color.audio_left_to_load_color
            FileType.VIDEO -> colorRes = R.color.video_left_to_load_color
            FileType.SUBTITLE -> colorRes = R.color.subtitle_left_to_load_color
            else -> colorRes = R.color.gray
        }
        return ContextCompat.getColor((ctx)!!, colorRes)
    }

    @ColorInt
    fun getForegroundForFileType(ctx: Context?, type: FileType?): Int {
        val colorRes: Int
        when (type) {
            FileType.MUSIC -> colorRes = R.color.audio_already_load_color
            FileType.VIDEO -> colorRes = R.color.video_already_load_color
            FileType.SUBTITLE -> colorRes = R.color.subtitle_already_load_color
            else -> colorRes = R.color.gray
        }
        return ContextCompat.getColor((ctx)!!, colorRes)
    }

    @DrawableRes
    fun getIconForFileType(type: FileType?): Int {
        when (type) {
            FileType.MUSIC -> return R.drawable.ic_headset
            FileType.VIDEO -> return R.drawable.ic_movie
            FileType.SUBTITLE -> return R.drawable.ic_subtitles
            else -> return R.drawable.ic_movie
        }
    }

    @Throws(IOException::class)
    fun checksum(source: StoredFileHelper, algorithmId: Int): String {
        var byteString: ByteString
        SharpInputStream(source.getStream()).use({ inputStream -> byteString = ByteString.of(*Util.toByteArray(inputStream)) })
        if (algorithmId == R.id.md5) {
            byteString = byteString.md5()
        } else if (algorithmId == R.id.sha1) {
            byteString = byteString.sha1()
        }
        return byteString.hex()
    }

    fun mkdir(p: File, allDirs: Boolean): Boolean {
        if (p.exists()) return true
        if (allDirs) p.mkdirs() else p.mkdir()
        return p.exists()
    }

    fun getContentLength(connection: HttpURLConnection?): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return connection!!.getContentLengthLong()
        }
        try {
            return connection!!.getHeaderField("Content-Length").toLong()
        } catch (err: Exception) {
            // nothing to do
        }
        return -1
    }

    /**
     * Get the content length of the entire file even if the HTTP response is partial
     * (response code 206).
     * @param connection http connection
     * @return content length
     */
    fun getTotalContentLength(connection: HttpURLConnection?): Long {
        try {
            if (connection!!.getResponseCode() == 206) {
                val rangeStr: String = connection.getHeaderField("Content-Range")
                val bytesStr: String = rangeStr.split("/".toRegex(), limit = 2).toTypedArray().get(1)
                return bytesStr.toLong()
            } else {
                return getContentLength(connection)
            }
        } catch (err: Exception) {
            // nothing to do
        }
        return -1
    }

    private fun pad(number: Int): String {
        return if (number < 10) ("0" + number) else number.toString()
    }

    fun stringifySeconds(seconds: Long): String {
        val h: Int = Math.floorDiv(seconds, 3600).toInt()
        val m: Int = Math.floorDiv(seconds - (h * 3600L), 60).toInt()
        val s: Int = (seconds - (h * 3600) - (m * 60)).toInt()
        var str: String = ""
        if (h < 1 && m < 1) {
            str = "00:"
        } else {
            if (h > 0) str = pad(h) + ":"
            if (m > 0) str += pad(m) + ":"
        }
        return str + pad(s)
    }

    enum class FileType {
        VIDEO,
        MUSIC,
        SUBTITLE,
        UNKNOWN
    }
}
