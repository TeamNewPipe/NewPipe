/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.AtomicFile
import androidx.annotation.WorkerThread
import java.io.File
import java.io.Serializable
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.sqlite.FinishedMissionStore

/** Download-time public media metadata, independent of History and never exported through Sync. */
data class OfflineDownloadMetadata(
    val service: Int,
    val source: String,
    val title: String,
    val uploader: String,
    val durationMillis: Long,
    val kind: Char,
    val partial: Boolean,
    val chapters: List<OfflineChapter>
) : Serializable {
    @JvmField var saved = false
    companion object {
        private const val serialVersionUID = 1L
    }
}

data class ResolvedOfflineMedia(
    val identity: String,
    val chapters: List<StreamSegment>,
    val subtitles: List<SubtitlesStream>,
    val sourceInfo: StreamInfo?,
    val durationMillis: Long = 0,
    val captionIdentities: Map<String, String> = emptyMap(),
    val staleDownload: Boolean = false
)

internal data class OfflineFileIdentity(val key: String, val length: Long, val modified: Long, val sample: String) {
    val signature: String get() = "$key:$length:$modified:$sample"
}

/** Per-file records follow URI aliases and renames, and are rejected after replacement or editing. */
object OfflinePlaybackMetadata {
    private const val MAX_RECORD_BYTES = 1024 * 1024
    private val lock = Any()
    private fun directory(context: Context) = File(context.filesDir, "offline_playback_metadata").apply { mkdirs() }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @JvmStatic
    fun capture(context: Context, info: StreamInfo, kind: Char, source: String, ranges: String?): OfflineDownloadMetadata? {
        if (kind !in listOf('a', 'v', 's')) return null
        val duration = info.duration.coerceIn(0, Long.MAX_VALUE / 1000) * 1000
        var chapters = ChapterTimes.normalize(info.streamSegments.map { OfflineChapter(it.title.orEmpty(), ChapterTimes.startMillis(it)) }, duration)
        val partial = !ranges.isNullOrBlank()
        var savedDuration = duration
        if (partial) {
            val normalized = runCatching { DownloadSegments.normalize(DownloadSegments.decode(ranges!!), duration) }.getOrDefault(emptyList())
            if (normalized.isEmpty()) {
                chapters = emptyList()
            } else {
                chapters = ChapterTimes.remap(chapters, normalized) { context.getString(R.string.offline_media_part, it) }
                savedDuration = normalized.sumOf { it.endMs - it.startMs }
            }
        }
        return OfflineDownloadMetadata(info.serviceId, source.take(8192), info.name.orEmpty().take(4096), info.uploaderName.orEmpty().take(4096), savedDuration, kind, partial, if (kind == 's') emptyList() else chapters)
    }

    /** Called on the downloader's worker before the finished mission is published. */
    @JvmStatic
    fun completed(mission: DownloadMission) {
        val metadata = mission.offlineMetadata ?: return
        if (metadata.saved || mission.isCancellationRequested) return
        runCatching { save(App.instance, mission.storage.uri.toString(), mission.storage.name.orEmpty(), metadata) }
        metadata.saved = true
    }

    @WorkerThread
    internal fun identity(context: Context, uri: String): OfflineFileIdentity? = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file" && parsed.scheme != "content") return null
        context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { asset ->
            val stat = Os.fstat(asset.fileDescriptor)
            val length = asset.declaredLength.takeIf { it >= 0 } ?: (stat.st_size - asset.startOffset)
            if (length <= 0) return null
            val digest = MessageDigest.getInstance("SHA-256")
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(asset.fileDescriptor)).use { input ->
                val size = minOf(length, 32 * 1024L).toInt()
                val offsets = listOf(0L, (length - size) / 2, length - size).distinct()
                for (offset in offsets) {
                    if (Thread.currentThread().isInterrupted) return null
                    input.channel.position(asset.startOffset + offset)
                    digest.update(ByteBuffer.allocate(8).putLong(offset).array())
                    val bytes = ByteArray(size)
                    var read = 0
                    while (read < size) {
                        val count = input.read(bytes, read, size - read)
                        if (count <= 0) return null
                        read += count
                    }
                    digest.update(bytes)
                }
            }
            OfflineFileIdentity(hash("${stat.st_dev}:${stat.st_ino}:${asset.startOffset}"), length, stat.st_mtime, digest.digest().joinToString("") { "%02x".format(it) })
        }
    }.getOrNull()

    @WorkerThread
    fun save(context: Context, uri: String, filename: String, metadata: OfflineDownloadMetadata) {
        val file = identity(context, uri) ?: return
        val chapterJson = JSONArray()
        metadata.chapters.forEach { chapterJson.put(JSONObject().put("title", it.title).put("start", it.startMillis)) }
        val json = JSONObject().put("version", 1).put("signature", file.signature).put("uri", uri)
            .put("filename", filename.take(4096)).put("service", metadata.service).put("source", metadata.source)
            .put("title", metadata.title).put("uploader", metadata.uploader).put("duration", metadata.durationMillis)
            .put("kind", metadata.kind.toString()).put("partial", metadata.partial).put("chapters", chapterJson)
        val bytes = json.toString().toByteArray()
        require(bytes.size <= MAX_RECORD_BYTES)
        synchronized(lock) {
            val dir = directory(context)
            write(File(dir, file.key + ".json"), bytes)
            write(File(dir, hash(uri) + ".alias"), file.key.toByteArray())
        }
    }

    private fun write(path: File, bytes: ByteArray) {
        val file = AtomicFile(path)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun read(context: Context, uri: String, file: OfflineFileIdentity): JSONObject? = synchronized(lock) {
        runCatching {
            val record = File(directory(context), file.key + ".json")
            if ((!record.isFile && !File(record.path + ".bak").isFile) || record.length() > MAX_RECORD_BYTES) return null
            val json = JSONObject(
                AtomicFile(record).openRead().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_RECORD_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            )
            if (json.optInt("version") != 1 || json.optString("signature") != file.signature) return null
            val alias = File(directory(context), hash(uri) + ".alias")
            if (!alias.exists()) write(alias, file.key.toByteArray())
            json
        }.getOrNull()
    }

    private fun hasRejectedRecord(context: Context, uri: String, file: OfflineFileIdentity, saved: JSONObject?): Boolean {
        if (saved != null) return false
        val dir = directory(context)
        val oldKey = runCatching { File(dir, hash(uri) + ".alias").readText() }.getOrNull()
        return File(dir, file.key + ".json").exists() || oldKey != null
    }

    @JvmStatic
    fun remove(context: Context, uri: String) = synchronized(lock) {
        val dir = directory(context)
        val alias = File(dir, hash(uri) + ".alias")
        val key = identity(context, uri)?.key ?: runCatching { alias.readText().takeIf { it.matches(Regex("[0-9a-f]{64}")) } }.getOrNull()
        key?.let { AtomicFile(File(dir, "$it.json")).delete() }
        AtomicFile(alias).delete()
    }

    @WorkerThread
    @JvmStatic
    @JvmOverloads
    fun resolve(context: Context, uri: String, knownMission: FinishedMission? = null, knownMissions: List<FinishedMission>? = null): ResolvedOfflineMedia = resolveSaved(context, uri, knownMission, knownMissions).withEmbeddedChapters(context, uri)

    @WorkerThread
    private fun resolveSaved(context: Context, uri: String, knownMission: FinishedMission?, knownMissions: List<FinishedMission>?): ResolvedOfflineMedia {
        val file = identity(context, uri) ?: return ResolvedOfflineMedia("", emptyList(), emptyList(), null)
        val saved = read(context, uri, file)
        if (hasRejectedRecord(context, uri, file, saved)) return ResolvedOfflineMedia(file.signature, emptyList(), emptyList(), null, staleDownload = true)
        val mission = knownMission ?: DownloadPlayback.find(context, uri) ?: saved?.optString("uri")?.let { DownloadPlayback.find(context, it) }
        if (saved == null && mission?.storage != null && hasRejectedRecord(context, mission.storage.uri.toString(), file, null)) return ResolvedOfflineMedia(file.signature, emptyList(), emptyList(), null, staleDownload = true)
        val source = saved?.optString("source") ?: mission?.source.orEmpty()
        val service = saved?.optInt("service") ?: runCatching { NewPipe.getServiceByUrl(source).serviceId }.getOrDefault(-1)
        val title = saved?.optString("title") ?: mission?.title.orEmpty()
        val duration = saved?.optLong("duration") ?: (mission?.duration ?: 0).coerceIn(0, Long.MAX_VALUE / 1000) * 1000
        val kind = saved?.optString("kind")?.firstOrNull() ?: mission?.kind
        val partial = saved?.optBoolean("partial") ?: source.contains("wizestream-segments=")
        val chapterArray = saved?.optJSONArray("chapters") ?: JSONArray()
        val chapters = ChapterTimes.normalize(
            (0 until minOf(chapterArray.length(), 1000)).mapNotNull { index ->
                runCatching {
                    val item = chapterArray.getJSONObject(index)
                    OfflineChapter(item.getString("title"), item.getLong("start"))
                }.getOrNull()
            },
            duration
        ).map { PreciseStreamSegment(it.title, it.startMillis) }
        val video = mission?.takeIf { it.source == source } ?: FinishedMission().apply {
            this.kind = 'v'
            this.source = source
            displayName = saved?.optString("filename").orEmpty()
            syncId = file.key
        }
        val captionIdentities = mutableMapOf<String, String>()
        val captions = if (kind == 'v' && !partial && source.isNotBlank()) {
            val all = knownMissions ?: loadFinishedMissions(context)
            DownloadCaptionGrouping.associated(video, all).mapNotNull { (caption, language) ->
                val storage = caption.storage ?: return@mapNotNull null
                if (!storage.existsAsFile() || caption.length <= 0 || storage.length() != caption.length) return@mapNotNull null
                val captionUri = storage.uri.toString()
                val captionFile = identity(context, captionUri) ?: return@mapNotNull null
                val captionRecord = read(context, captionUri, captionFile)
                // New records verify file identity and source. Old captions retain exact source/name matching.
                if (hasRejectedRecord(context, captionUri, captionFile, captionRecord) || captionRecord != null && (captionRecord.optString("source") != source || captionRecord.optInt("service") != service)) return@mapNotNull null
                val format = MediaFormat.getFromSuffix(caption.displayName.orEmpty().substringAfterLast('.')) ?: return@mapNotNull null
                captionIdentities[captionUri] = captionFile.signature
                SubtitlesStream.Builder().setId("local:$language").setContent(captionUri, true).setLanguageCode(language)
                    .setAutoGenerated(false).setMediaFormat(format).build()
            }
        } else {
            emptyList()
        }
        val info = if (service >= 0 && source.isNotBlank()) {
            StreamInfo(service, source, source, title).apply {
                streamType = if (kind == 'a') StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM
                uploaderName = saved?.optString("uploader") ?: mission?.uploader.orEmpty()
                this.duration = duration / 1000
                streamSegments = chapters
                subtitles = captions
            }
        } else {
            null
        }
        return ResolvedOfflineMedia(file.signature, chapters, captions, info, duration, captionIdentities)
    }

    private fun loadFinishedMissions(context: Context): List<FinishedMission> {
        val store = FinishedMissionStore(context)
        // SQLiteOpenHelper is not AutoCloseable on every supported Android version.
        return try {
            store.loadFinishedMissions()
        } finally {
            store.close()
        }
    }
}
