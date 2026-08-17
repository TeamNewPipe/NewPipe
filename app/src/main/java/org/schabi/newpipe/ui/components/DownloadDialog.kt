package org.schabi.newpipe.ui.components

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.SecondaryStreamHelper
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.util.Utility
import java.io.File

@Composable
fun DownloadDialog(
    streamInfo: StreamInfo,
    onDismiss: () -> Unit,
    onDownload: (DownloadOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(DownloadType.VIDEO) }
    val context = LocalContext.current

    val videoStreams = remember(streamInfo) {
        ListHelper.getSortedStreamVideosList(context, streamInfo.videoStreams, streamInfo.videoOnlyStreams, false, false)
    }
    val audioStreams = remember(streamInfo) {
        streamInfo.audioStreams ?: emptyList()
    }

    var selectedVideoStream by remember(videoStreams) { mutableStateOf(videoStreams.firstOrNull()) }
    var selectedAudioStream by remember(audioStreams) { mutableStateOf(audioStreams.firstOrNull()) }

    // Map to cache exact Content-Length fetched in background
    val exactSizes = remember { mutableStateMapOf<String, Long>() }

    // Fetch exact sizes asynchronously
    LaunchedEffect(streamInfo) {
        withContext(Dispatchers.IO) {
            val allStreams = (streamInfo.videoStreams.orEmpty() +
                    streamInfo.videoOnlyStreams.orEmpty() +
                    streamInfo.audioStreams.orEmpty())

            for (s in allStreams) {
                val contentUrl = s.content ?: continue
                if (exactSizes.containsKey(contentUrl)) continue
                try {
                    val response = DownloaderImpl.instance?.head(contentUrl)
                    val cl = response?.getHeader("Content-Length")?.toLongOrNull()
                    if (cl != null && cl > 0) {
                        withContext(Dispatchers.Main) {
                            exactSizes[contentUrl] = cl
                        }
                    }
                } catch (e: Exception) {
                    // ignore network errors for background head requests
                }
            }
        }
    }

    val isAudio = selectedType == DownloadType.AUDIO
    val availableStorage = remember(isAudio) { getAvailableStorageBytes(context, isAudio) }
    val totalStorage = remember { getTotalStorageBytes(context) }
    val durationSec = remember(streamInfo) { streamInfo.duration.coerceAtLeast(0L) }

    // Compute size for a video stream (including paired audio for video-only streams)
    fun computeVideoCombinedSize(videoStream: VideoStream): Long {
        val exactVideo = exactSizes[videoStream.content]
        val videoBytes = if (exactVideo != null && exactVideo > 0) {
            exactVideo
        } else {
            estimateStreamSizeBytes(videoStream, durationSec)
        }

        val audioBytes = if (videoStream.isVideoOnly) {
            val pairedAudio = SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, videoStream)
                ?: audioStreams.firstOrNull()
            if (pairedAudio != null) {
                val exactAudio = exactSizes[pairedAudio.content]
                if (exactAudio != null && exactAudio > 0) exactAudio
                else estimateStreamSizeBytes(pairedAudio, durationSec)
            } else 0L
        } else 0L

        return videoBytes + audioBytes
    }

    // Compute size for an audio stream
    fun computeAudioSize(audioStream: AudioStream): Long {
        val exactAudio = exactSizes[audioStream.content]
        return if (exactAudio != null && exactAudio > 0) {
            exactAudio
        } else {
            estimateStreamSizeBytes(audioStream, durationSec)
        }
    }

    val selectedSize = remember(selectedType, selectedVideoStream, selectedAudioStream, exactSizes.toMap()) {
        if (selectedType == DownloadType.VIDEO && selectedVideoStream != null) {
            computeVideoCombinedSize(selectedVideoStream!!)
        } else if (selectedType == DownloadType.AUDIO && selectedAudioStream != null) {
            computeAudioSize(selectedAudioStream!!)
        } else {
            0L
        }
    }

    val hasEnoughSpaceForSelected = selectedSize <= availableStorage

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Download Media",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = streamInfo.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Storage indicator
                Surface(
                    color = if (availableStorage < 500L * 1024 * 1024) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (availableStorage < 500L * 1024 * 1024) Icons.Default.WarningAmber else Icons.Default.SdCard,
                            contentDescription = null,
                            tint = if (availableStorage < 500L * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val storageText = if (totalStorage > 0) {
                            "Available: ${Utility.formatBytes(availableStorage)} / ${Utility.formatBytes(totalStorage)}"
                        } else {
                            "Available: ${Utility.formatBytes(availableStorage)}"
                        }
                        Text(
                            text = storageText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (availableStorage < 500L * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Type Switcher Tabs (Video / Audio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == DownloadType.VIDEO,
                        onClick = { selectedType = DownloadType.VIDEO },
                        label = { Text("Video + Audio") },
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == DownloadType.AUDIO,
                        onClick = { selectedType = DownloadType.AUDIO },
                        label = { Text("Audio Only") },
                        leadingIcon = {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (selectedType == DownloadType.VIDEO) "Select Video Quality (with Audio)" else "Select Audio Quality",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Qualities List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    if (selectedType == DownloadType.VIDEO) {
                        if (videoStreams.isNotEmpty()) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(videoStreams) { stream ->
                                    val isSelected = selectedVideoStream == stream
                                    val combinedSize = computeVideoCombinedSize(stream)
                                    val isEnoughSpace = combinedSize <= availableStorage

                                    Surface(
                                        color = when {
                                            isSelected && isEnoughSpace -> MaterialTheme.colorScheme.primaryContainer
                                            isSelected && !isEnoughSpace -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (isSelected && !isEnoughSpace) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedVideoStream = stream }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedVideoStream = stream }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = stream.resolution ?: "Standard",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = Utility.formatBytes(combinedSize),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp
                                                        ),
                                                        color = if (isEnoughSpace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "${stream.format?.name ?: "MP4"} • Video + Audio",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (!isEnoughSpace) {
                                                        Text(
                                                            text = "Not enough space",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("No video streams available", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        if (audioStreams.isNotEmpty()) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(audioStreams) { stream ->
                                    val isSelected = selectedAudioStream == stream
                                    val audioSize = computeAudioSize(stream)
                                    val isEnoughSpace = audioSize <= availableStorage

                                    Surface(
                                        color = when {
                                            isSelected && isEnoughSpace -> MaterialTheme.colorScheme.primaryContainer
                                            isSelected && !isEnoughSpace -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (isSelected && !isEnoughSpace) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedAudioStream = stream }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedAudioStream = stream }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (stream.averageBitrate > 0) "${stream.averageBitrate} kbps" else "Audio",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = Utility.formatBytes(audioSize),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp
                                                        ),
                                                        color = if (isEnoughSpace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "${stream.format?.name ?: "M4A"} • Audio only",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (!isEnoughSpace) {
                                                        Text(
                                                            text = "Not enough space",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("No audio streams available", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedStream = if (selectedType == DownloadType.VIDEO) selectedVideoStream else selectedAudioStream
            Button(
                onClick = {
                    if (selectedStream != null && hasEnoughSpaceForSelected) {
                        onDownload(DownloadOptions(selectedType, selectedStream, selectedSize))
                    }
                },
                enabled = selectedStream != null && hasEnoughSpaceForSelected,
                colors = if (!hasEnoughSpaceForSelected) ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = when {
                        selectedStream == null -> "Select Quality"
                        !hasEnoughSpaceForSelected -> "Not enough space"
                        selectedSize > 0 -> "Download (${Utility.formatBytes(selectedSize)})"
                        else -> "Download"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class DownloadType {
    VIDEO, AUDIO, SUBTITLE
}

data class DownloadOptions(
    val type: DownloadType,
    val stream: Stream,
    val estimatedSize: Long = 0L
)

fun getAvailableStorageBytes(context: Context, isAudio: Boolean): Long {
    return try {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = if (isAudio) R.string.download_path_audio_key else R.string.download_path_video_key
        val savedPath = prefs.getString(context.getString(prefKey), null)

        val dir = if (!savedPath.isNullOrEmpty() && savedPath.startsWith("file://")) {
            File(Uri.parse(savedPath).path ?: context.filesDir.path)
        } else if (isAudio) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: context.filesDir
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
        }

        val targetDir = if (dir.exists()) dir else context.filesDir
        val stat = StatFs(targetDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: Exception) {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (ex: Exception) {
            Long.MAX_VALUE
        }
    }
}

fun getTotalStorageBytes(context: Context): Long {
    return try {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.blockCountLong * stat.blockSizeLong
    } catch (e: Exception) {
        0L
    }
}

fun estimateStreamSizeBytes(stream: Stream, durationSec: Long): Long {
    if (durationSec <= 0) return 0L

    return when (stream) {
        is AudioStream -> {
            val kbps = if (stream.averageBitrate > 0) stream.averageBitrate.toLong() else 128L
            (kbps * 1000L / 8L) * durationSec
        }
        is VideoStream -> {
            val res = stream.resolution.orEmpty().lowercase()
            val kbps = when {
                res.contains("4320") || res.contains("8k") -> 35_000L
                res.contains("2160") || res.contains("4k") -> 18_000L
                res.contains("1440") || res.contains("2k") -> 9_000L
                res.contains("1080") -> 4_000L
                res.contains("720") -> 2_000L
                res.contains("480") -> 900L
                res.contains("360") -> 500L
                res.contains("240") -> 300L
                res.contains("144") -> 150L
                else -> 1_800L
            }
            (kbps * 1000L / 8L) * durationSec
        }
        else -> 0L
    }
}
