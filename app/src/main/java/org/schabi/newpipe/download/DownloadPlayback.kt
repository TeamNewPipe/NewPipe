package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.text.HtmlCompat
import java.io.File
import java.time.OffsetDateTime
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.NavigationHelper
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.sqlite.FinishedMissionStore

internal object DownloadPlayback {
    @JvmStatic
    fun canPlay(mission: FinishedMission): Boolean = mission.kind == 'a' || mission.kind == 'v'

    @JvmStatic
    fun play(context: Context, mission: FinishedMission) {
        val storage = mission.storage ?: return
        val uri = storage.uri.toString()
        val item = PlayQueueItem.localMedia(
            mission.title?.takeIf { it.isNotBlank() } ?: storage.name.orEmpty(),
            uri,
            mission.duration,
            mission.uploader,
            null,
            null,
            mission.mimeType?.takeIf { it.isNotBlank() } ?: storage.type,
            -1,
            mission.kind == 'v',
            mission.thumbnailUrl
        )
        NavigationHelper.playOnMainPlayer(context, LocalMediaPlayQueue(listOf(item), 0), false)
    }

    @WorkerThread
    fun find(context: Context, storageUri: String): FinishedMission? = runCatching {
        FinishedMissionStore(context).use { store ->
            store.findByStorageUri(storageUri) ?: findMediaStoreAlias(context, store, Uri.parse(storageUri))
        }
    }.getOrNull()

    private fun findMediaStoreAlias(context: Context, store: FinishedMissionStore, uri: Uri): FinishedMission? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val document = runCatching { MediaStore.getDocumentUri(context, uri) }.getOrNull()
            document?.let { store.findByStorageUri(it.toString()) }?.let { return it }
        }

        // Files indexed by MediaStore can also have been downloaded through a direct file path.
        @Suppress("DEPRECATION")
        val path = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return path?.takeIf { it.startsWith("/") }?.let {
            store.findByStorageUri(Uri.fromFile(File(it)).toString())
        }
    }

    fun description(mission: FinishedMission): String {
        val content = mission.description.orEmpty()
        return if (HTML_TAG.containsMatchIn(content)) {
            HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else {
            content
        }
    }

    fun uploadDate(mission: FinishedMission): String? = mission.uploadDate
        ?.takeIf { it.isNotBlank() && !it.startsWith("org.schabi.newpipe.extractor.localization.DateWrapper@") }

    fun parsedUploadDate(mission: FinishedMission): OffsetDateTime? = uploadDate(mission)
        ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    private val HTML_TAG = Regex("</?(?:a|b|br|div|em|i|p|span|strong)(?:\\s|/?>)", RegexOption.IGNORE_CASE)
}
