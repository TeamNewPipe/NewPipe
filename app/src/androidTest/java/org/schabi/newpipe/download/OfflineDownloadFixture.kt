/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.sqlite.FinishedMissionStore

/** Real decodable media plus saved download metadata; no extraction/cache initialization. */
internal class OfflineDownloadFixture(val context: Context) : AutoCloseable {
    val directory = File(context.cacheDir, "update/offline-${UUID.randomUUID()}").apply { mkdirs() }
    val video = File(directory, "lesson.mp4").apply {
        val encoded = InstrumentationRegistry.getInstrumentation().context.assets.open("native-pip/portrait.mp4.base64").use { it.readBytes() }
        writeBytes(Base64.decode(encoded, Base64.DEFAULT))
    }
    val caption = File(directory, "lesson.en.vtt").apply {
        writeText("WEBVTT\n\n00:01.250 --> 00:03.000\nFirst offline line\n\n00:05.000 --> 00:07.500\nSecond offline line\n")
    }
    val source = "https://www.youtube.com/watch?v=${UUID.randomUUID().toString().replace("-", "").take(11)}"
    val info = StreamInfo(0, source, source, "Offline lesson").apply {
        duration = 8
        uploaderName = "Offline teacher"
        streamType = StreamType.VIDEO_STREAM
        streamSegments = listOf(PreciseStreamSegment("Introduction", 0), PreciseStreamSegment("First lesson", 1250), PreciseStreamSegment("Second lesson", 5000))
    }
    val videoMission = mission(video, 'v', "video/mp4")
    val captionMission = mission(caption, 's', "text/vtt")
    val missions = listOf(videoMission, captionMission)
    private val registered = mutableListOf<FinishedMission>()
    private val metadataUris = mutableListOf<String>()

    init {
        save(video, 'v')
        save(caption, 's')
    }

    fun uri(file: File = video): String = Uri.fromFile(file).toString()

    fun save(file: File, kind: Char, metadata: OfflineDownloadMetadata = OfflinePlaybackMetadata.capture(context, info, kind, source, null)!!) {
        OfflinePlaybackMetadata.save(context, uri(file), file.name, metadata)
        metadataUris += uri(file)
    }

    fun register() {
        val store = FinishedMissionStore(context)
        try {
            missions.forEach {
                store.addFinishedMission(it)
                registered += it
            }
        } finally {
            store.close()
        }
    }

    fun mission(file: File, kind: Char, mime: String): FinishedMission = FinishedMission().apply {
        syncId = UUID.randomUUID().toString()
        timestamp = System.currentTimeMillis()
        this.source = this@OfflineDownloadFixture.source
        this.kind = kind
        storage = StoredFileHelper(context, null, Uri.fromFile(file), "offline-test")
        length = file.length()
        displayName = file.name
        mimeType = mime
        title = info.name
        uploader = info.uploaderName
        duration = info.duration
    }

    override fun close() {
        val store = FinishedMissionStore(context)
        try {
            registered.forEach(store::deleteMission)
        } finally {
            store.close()
        }
        metadataUris.forEach { OfflinePlaybackMetadata.remove(context, it) }
        directory.deleteRecursively()
    }
}
