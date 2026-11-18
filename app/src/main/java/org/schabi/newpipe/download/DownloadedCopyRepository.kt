package org.schabi.newpipe.download

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Single
import java.net.URI
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.sqlite.FinishedMissionStore

/** Keeps the service URL as identity while using a verified local file as the media source. */
class DownloadedStreamInfo(val copyUri: String, service: Int, sourceUrl: String, title: String) : StreamInfo(service, sourceUrl, sourceUrl, title)

object DownloadedCopyRepository {
    const val PREFERENCE = "prefer_downloaded_copies"
    private val rejectedCopies = ConcurrentHashMap.newKeySet<String>()

    internal fun sameSource(serviceId: Int, requested: String, downloaded: String): Boolean {
        if (downloaded.contains("wizestream-segments=")) return false
        return runCatching {
            if (serviceId == ServiceList.YouTube.serviceId) {
                val factory = YoutubeStreamLinkHandlerFactory.getInstance()
                factory.getId(requested) == factory.getId(downloaded)
            } else {
                URI(requested).normalize() == URI(downloaded).normalize()
            }
        }.getOrDefault(false)
    }

    @JvmStatic
    fun reject(info: DownloadedStreamInfo): Boolean = rejectedCopies.add(info.copyUri)

    @JvmStatic
    fun find(context: Context, service: Int, url: String, title: String?, audioOnly: Boolean, item: PlayQueueItem? = null): DownloadedStreamInfo? {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE, true)) return null
        return runCatching {
            val store = FinishedMissionStore(context)
            try {
                store.loadFinishedMissions().asSequence()
                    .filter { (it.kind == 'v' || audioOnly && it.kind == 'a') && sameSource(service, url, it.source.orEmpty()) }
                    .sortedBy { if (audioOnly && it.kind == 'a') 0 else 1 }
                    .mapNotNull { mission -> openCopy(context, mission, service, url, title, item) }
                    .firstOrNull()
            } finally {
                store.close()
            }
        }.getOrNull()
    }

    private fun openCopy(context: Context, mission: FinishedMission, service: Int, url: String, title: String?, item: PlayQueueItem?): DownloadedStreamInfo? = runCatching {
        val storage = mission.storage ?: return@runCatching null
        val uri = storage.uri.toString()
        if (uri in rejectedCopies || mission.length <= 0 || !storage.existsAsFile() || storage.length() < mission.length) return@runCatching null
        storage.stream.use { if (it.read() < 0) return@runCatching null }
        val displayTitle = mission.title?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: mission.displayName.orEmpty()
        val info = DownloadedStreamInfo(uri, service, url, displayTitle)
        info.streamType = if (mission.kind == 'v') StreamType.VIDEO_STREAM else StreamType.AUDIO_STREAM
        info.uploaderName = mission.uploader?.takeIf { it.isNotBlank() } ?: item?.uploader.orEmpty()
        info.uploaderUrl = item?.uploaderUrl.orEmpty()
        info.thumbnails = mission.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?.let { listOf(Image(it, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN)) }
            ?: item?.thumbnails.orEmpty()
        info.duration = mission.duration.takeIf { it > 0 }
            ?: item?.duration?.takeIf { it > 0 }
            ?: readDuration(context, storage.uri)
        info.textualUploadDate = DownloadPlayback.uploadDate(mission)
        info.uploadDate = DownloadPlayback.parsedUploadDate(mission)?.let { DateWrapper(it) }
        info.tags = mission.tags.orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        info.sponsorBlockSegments = OfflineSponsorBlockStore.load(context, uri).toTypedArray()
        val description = DownloadPlayback.description(mission).ifBlank {
            context.getString(R.string.playing_downloaded_copy)
        }
        info.description = Description(description, Description.PLAIN_TEXT)
        val format = MediaFormat.getFromMimeType(mission.mimeType.orEmpty()) ?: MediaFormat.getFromSuffix(mission.displayName.orEmpty().substringAfterLast('.'))
        if (mission.kind == 'v') {
            info.videoStreams = listOf(VideoStream.Builder().setId("downloaded").setContent(uri, true).setIsVideoOnly(false).setResolution("").setMediaFormat(format).build())
        } else {
            info.audioStreams = listOf(AudioStream.Builder().setId("downloaded").setContent(uri, true).setAverageBitrate(0).setMediaFormat(format).build())
        }
        info
    }.getOrNull()

    private fun readDuration(context: Context, uri: android.net.Uri): Long {
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(context, uri)
            (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000
        } finally {
            reader.release()
        }
    }

    @JvmStatic
    fun streamInfoOrNetwork(context: Context, service: Int, url: String, title: String?, network: Single<StreamInfo>): Single<StreamInfo> = Single.fromCallable {
        Optional.ofNullable(find(context, service, url, title, false))
    }.flatMap { local -> if (local.isPresent) Single.just<StreamInfo>(local.get()) else network }
}
