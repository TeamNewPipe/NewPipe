package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.ui.components.DownloadOptions
import org.schabi.newpipe.ui.components.DownloadType
import org.schabi.newpipe.util.SecondaryStreamHelper
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import java.io.File

object DownloadHelper {

    fun startDownload(context: Context, streamInfo: StreamInfo, options: DownloadOptions) {
        try {
            val isAudio = options.type == DownloadType.AUDIO
            val kind = if (isAudio) 'a' else 'v'
            val stream = options.stream

            val streamUrl = stream.content
            if (streamUrl.isNullOrEmpty()) {
                Toast.makeText(context, "Cannot download: stream URL is empty", Toast.LENGTH_SHORT).show()
                return
            }

            val format = stream.format
            val cleanTitle = (streamInfo.name ?: "media")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { "download_${System.currentTimeMillis()}" }

            val extension: String
            val mimeType: String
            val urls: Array<String>
            val recoveryList: ArrayList<MissionRecoveryInfo>
            val psName: String?

            if (isAudio) {
                val isM4a = format == MediaFormat.M4A || format?.suffix?.equals("m4a", ignoreCase = true) == true
                extension = if (isM4a) "m4a" else (format?.suffix ?: "opus")
                mimeType = if (isM4a) "audio/mp4" else (format?.mimeType ?: "audio/ogg")
                urls = arrayOf(streamUrl)
                recoveryList = arrayListOf(MissionRecoveryInfo(stream))
                psName = if (isM4a) Postprocessing.ALGORITHM_M4A_NO_DASH else null
            } else {
                val videoStream = stream as? VideoStream
                val isVideoOnly = videoStream?.isVideoOnly == true
                val audioStreams = streamInfo.audioStreams ?: emptyList()

                val audioStream = if (isVideoOnly && videoStream != null) {
                    SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, videoStream)
                        ?: audioStreams.firstOrNull()
                } else null

                val isWebm = videoStream?.format == MediaFormat.WEBM || format?.suffix?.equals("webm", ignoreCase = true) == true
                extension = if (isWebm) "webm" else "mp4"
                mimeType = if (isWebm) "video/webm" else "video/mp4"

                if (isVideoOnly && audioStream != null && !audioStream.content.isNullOrEmpty()) {
                    urls = arrayOf(streamUrl, audioStream.content)
                    recoveryList = arrayListOf(MissionRecoveryInfo(videoStream), MissionRecoveryInfo(audioStream))
                    psName = if (isWebm) {
                        Postprocessing.ALGORITHM_WEBM_MUXER
                    } else {
                        Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER
                    }
                } else {
                    urls = arrayOf(streamUrl)
                    recoveryList = arrayListOf(MissionRecoveryInfo(stream))
                    psName = null
                }
            }

            val filename = "$cleanTitle.$extension"

            // Get or create storage directory
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val prefKey = if (isAudio) R.string.download_path_audio_key else R.string.download_path_video_key
            val tag = if (isAudio) DownloadManager.TAG_AUDIO else DownloadManager.TAG_VIDEO
            val savedPath = prefs.getString(context.getString(prefKey), null)

            val storageDir: StoredDirectoryHelper = if (!savedPath.isNullOrEmpty()) {
                try {
                    StoredDirectoryHelper(context, Uri.parse(savedPath), tag)
                } catch (e: Exception) {
                    getDefaultStorageDir(context, tag)
                }
            } else {
                getDefaultStorageDir(context, tag)
            }

            val storedFile = storageDir.createUniqueFile(filename, mimeType)
            if (storedFile == null) {
                Toast.makeText(context, "Failed to create download file", Toast.LENGTH_SHORT).show()
                return
            }

            val threads = 3

            DownloadManagerService.startMission(
                context = context,
                urls = urls,
                storage = storedFile,
                kind = kind,
                threads = threads,
                streamInfo = streamInfo,
                psName = psName,
                psArgs = null,
                nearLength = options.estimatedSize,
                recoveryInfo = recoveryList
            )

            Toast.makeText(context, "Download started: ${storedFile.getName()}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error starting download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getDefaultStorageDir(context: Context, tag: String): StoredDirectoryHelper {
        val baseDir = if (tag == DownloadManager.TAG_VIDEO) {
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (movies != null && movies.exists()) movies else File(context.getExternalFilesDir(null), "Movies").apply { mkdirs() }
        } else {
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (music != null && music.exists()) music else File(context.getExternalFilesDir(null), "Music").apply { mkdirs() }
        }
        return StoredDirectoryHelper(context, Uri.fromFile(baseDir), tag)
    }
}
