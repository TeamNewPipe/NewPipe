package us.shandian.giga.get

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.schabi.newpipe.BuildConfig
import java.io.File
import java.io.IOException

internal class SabrFfmpegMuxer(
    private val mission: DownloadMission,
) {
    @Throws(IOException::class, InterruptedException::class)
    fun remuxAndCopy(inputs: List<File>, targets: List<SabrDownloadTarget>, workDir: File): Long {
        if (canCopySingleInputDirectly(inputs, targets)) {
            logDebug("copySingleInputDirectly input=${inputs.first().length()}")
            return copyOutputToStorage(inputs.first())
        }
        val output = File(workDir, "output.${outputExtension()}")
        remuxWithFfmpeg(inputs, output)
        return try {
            copyOutputToStorage(output)
        } finally {
            deleteQuietly(output)
        }
    }

    @Throws(IOException::class)
    private fun remuxWithFfmpeg(inputs: List<File>, output: File) {
        mission.psState = 1
        mission.writeThisToFile()
        val command = buildList {
            add("-hide_banner")
            add("-nostats")
            add("-loglevel")
            add("fatal")
            add("-y")
            inputs.forEach { input ->
                add("-i")
                add(input.absolutePath)
            }
            if (mission.kind == 'a' && inputs.size == 1) {
                add("-map")
                add("0:a?")
                add("-vn")
            } else if (inputs.size > 1) {
                inputs.indices.forEach { index ->
                    add("-map")
                    add("$index:v?")
                    add("-map")
                    add("$index:a?")
                }
            }
            add("-c")
            add("copy")
            if (supportsFastStart(output)) {
                add("-movflags")
                add("+faststart")
            }
            add(output.absolutePath)
        }
        logDebug("remuxWithFfmpeg inputs=${inputs.size} output=${output.name}")
        val session = FFmpegKit.executeWithArguments(command.toTypedArray())
        if (!ReturnCode.isSuccess(session.returnCode)) {
            mission.psState = 0
            throw SabrDownloadException(
                SabrDownloadException.Reason.MUXING,
                "SABR download failed: ffmpeg remux failed (${session.returnCode})"
                    + session.output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
            )
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun copyOutputToStorage(output: File): Long {
        var copied = 0L
        try {
            output.inputStream().use { input ->
                mission.storage.getStream().use { storage ->
                    storage.setLength(0)
                    storage.seek(0)
                    val buffer = ByteArray(DownloadMission.BUFFER_SIZE)
                    while (true) {
                        ensureRunning()
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        storage.write(buffer, 0, read)
                        copied += read.toLong()
                    }
                }
            }
        } catch (error: IOException) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.STORAGE,
                "SABR download failed: could not write final file",
                error,
            )
        }
        return copied
    }

    @Throws(InterruptedException::class)
    private fun ensureRunning() {
        if (!mission.running || Thread.currentThread().isInterrupted) {
            throw InterruptedException()
        }
    }

    private fun outputExtension(): String {
        val name = mission.storage.name ?: return "mp4"
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        return extension.takeIf { it.isNotBlank() && it.all { char -> char.isLetterOrDigit() } } ?: "mp4"
    }

    private fun supportsFastStart(output: File): Boolean {
        return when (output.extension.lowercase()) {
            "m4a", "m4v", "mov", "mp4" -> true
            else -> false
        }
    }

    private fun canCopySingleInputDirectly(
        inputs: List<File>,
        targets: List<SabrDownloadTarget>,
    ): Boolean {
        if (inputs.size != 1 || targets.size != 1) {
            return false
        }
        val extension = outputExtension().lowercase()
        val mimeType = targets.first().format.mimeType ?: return false
        return extension == "webm" && mimeType.contains("webm", ignoreCase = true)
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun deleteQuietly(file: File) {
        try {
            file.delete()
        } catch (ignored: Exception) {
            // Nothing to do.
        }
    }

    private companion object {
        private const val TAG = "SabrFfmpegMuxer"
    }
}
