package us.shandian.giga.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.media3.common.util.Util
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.HttpURLConnection
import java.util.Locale
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.schabi.newpipe.R
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper

object Utility {

    enum class FileType {
        VIDEO,
        MUSIC,
        SUBTITLE,
        UNKNOWN
    }

    @JvmStatic
    fun formatBytes(bytes: Long): String {
        val locale = Locale.getDefault()
        return when {
            bytes < 1024 -> String.format(locale, "%d B", bytes)
            bytes < 1024 * 1024 -> String.format(locale, "%.2f kB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(locale, "%.2f MB", bytes / 1024.0 / 1024.0)
            else -> String.format(locale, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        }
    }

    @JvmStatic
    fun formatSpeed(speed: Double): String {
        val locale = Locale.getDefault()
        return when {
            speed < 1024 -> String.format(locale, "%d B/s", speed.toLong())
            speed < 1024 * 1024 -> String.format(locale, "%.2f kB/s", speed / 1024)
            speed < 1024 * 1024 * 1024 -> String.format(locale, "%.2f MB/s", speed / 1024 / 1024)
            else -> String.format(locale, "%.2f GB/s", speed / 1024 / 1024 / 1024)
        }
    }

    @JvmStatic
    fun writeToFile(file: File, serializable: Serializable) {
        try {
            val okioPath = file.toOkioPath()
            val parent = okioPath.parent
            if (parent != null && !FileSystem.SYSTEM.exists(parent)) {
                FileSystem.SYSTEM.createDirectories(parent)
            }
            FileSystem.SYSTEM.sink(okioPath).buffer().outputStream().use { outputStream ->
                ObjectOutputStream(outputStream).use { objectOutputStream ->
                    objectOutputStream.writeObject(serializable)
                }
            }
        } catch (e: Exception) {
            // nothing to do
        }
    }

    @JvmStatic
    fun <T> readFromFile(file: File): T? {
        return try {
            val okioPath = file.toOkioPath()
            if (!FileSystem.SYSTEM.exists(okioPath)) return null
            FileSystem.SYSTEM.source(okioPath).buffer().inputStream().use { inputStream ->
                ObjectInputStream(inputStream).use { objectInputStream ->
                    @Suppress("UNCHECKED_CAST")
                    objectInputStream.readObject() as T
                }
            }
        } catch (e: Exception) {
            Log.e("Utility", "Failed to deserialize the object", e)
            null
        }
    }

    @JvmStatic
    fun getFileExt(url: String): String? {
        var mutableUrl = url
        val index = mutableUrl.indexOf("?")
        if (index > -1) {
            mutableUrl = mutableUrl.substring(0, index)
        }

        val dotIndex = mutableUrl.lastIndexOf(".")
        return if (dotIndex == -1) {
            null
        } else {
            var ext = mutableUrl.substring(dotIndex)
            val percentIndex = ext.indexOf("%")
            if (percentIndex > -1) {
                ext = ext.substring(0, percentIndex)
            }
            val slashIndex = ext.indexOf("/")
            if (slashIndex > -1) {
                ext = ext.substring(0, slashIndex)
            }
            ext.lowercase(Locale.getDefault())
        }
    }

    @JvmStatic
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
        } else if (file.endsWith(".mp4") || file.endsWith(".mpeg") || file.endsWith(".rm") || file.endsWith(".rmvb") ||
            file.endsWith(".flv") || file.endsWith(".webp") || file.endsWith(".webm")
        ) {
            return FileType.VIDEO
        }

        return FileType.UNKNOWN
    }

    @JvmStatic
    @ColorInt
    fun getBackgroundForFileType(ctx: Context, type: FileType): Int {
        val colorRes = when (type) {
            FileType.MUSIC -> R.color.audio_left_to_load_color
            FileType.VIDEO -> R.color.video_left_to_load_color
            FileType.SUBTITLE -> R.color.subtitle_left_to_load_color
            else -> R.color.gray
        }
        return ContextCompat.getColor(ctx, colorRes)
    }

    @JvmStatic
    @ColorInt
    fun getForegroundForFileType(ctx: Context, type: FileType): Int {
        val colorRes = when (type) {
            FileType.MUSIC -> R.color.audio_already_load_color
            FileType.VIDEO -> R.color.video_already_load_color
            FileType.SUBTITLE -> R.color.subtitle_already_load_color
            else -> R.color.gray
        }
        return ContextCompat.getColor(ctx, colorRes)
    }

    @JvmStatic
    @DrawableRes
    fun getIconForFileType(type: FileType): Int {
        return when (type) {
            FileType.MUSIC -> R.drawable.ic_headset
            FileType.VIDEO -> R.drawable.ic_movie
            FileType.SUBTITLE -> R.drawable.ic_subtitles
            else -> R.drawable.ic_movie
        }
    }

    @JvmStatic
    fun mkdir(p: File, allDirs: Boolean): Boolean {
        val okioPath = p.toOkioPath()
        if (FileSystem.SYSTEM.exists(okioPath)) return true

        return try {
            if (allDirs) {
                FileSystem.SYSTEM.createDirectories(okioPath)
            } else {
                val parent = okioPath.parent
                if (parent == null || FileSystem.SYSTEM.exists(parent)) {
                    FileSystem.SYSTEM.createDirectory(okioPath)
                }
            }
            FileSystem.SYSTEM.exists(okioPath)
        } catch (e: Exception) {
            p.exists()
        }
    }

    @JvmStatic
    fun getContentLength(connection: HttpURLConnection): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return connection.contentLengthLong
        }

        return try {
            connection.getHeaderField("Content-Length").toLong()
        } catch (err: Exception) {
            -1L
        }
    }

    @JvmStatic
    fun getTotalContentLength(connection: HttpURLConnection): Long {
        return try {
            if (connection.responseCode == 206) {
                val rangeStr = connection.getHeaderField("Content-Range")
                val bytesStr = rangeStr.split("/".toRegex(), limit = 2).toTypedArray()[1]
                bytesStr.toLong()
            } else {
                getContentLength(connection)
            }
        } catch (err: Exception) {
            -1L
        }
    }

    private fun pad(number: Int): String {
        return if (number < 10) "0$number" else number.toString()
    }

    @JvmStatic
    fun stringifySeconds(seconds: Long): String {
        val h = (seconds / 3600).toInt()
        val m = ((seconds - h * 3600L) / 60).toInt()
        val s = (seconds - h * 3600 - m * 60).toInt()

        var str = ""

        if (h < 1 && m < 1) {
            str = "00:"
        } else {
            if (h > 0) str = pad(h) + ":"
            if (m > 0) str += pad(m) + ":"
        }

        return str + pad(s)
    }
}
