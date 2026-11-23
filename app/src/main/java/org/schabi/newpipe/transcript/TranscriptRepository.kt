/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.transcript

import android.content.Context
import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.InputStream
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.SubtitlesStream

internal data class TranscriptLine(val timeMillis: Long, val text: String)

internal object TranscriptRepository {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_LINES = 20_000
    private val supported = setOf("application/ttml+xml", "text/vtt", "application/x-subrip")

    fun supports(track: SubtitlesStream): Boolean = track.format?.mimeType in supported &&
        track.deliveryMethod != DeliveryMethod.TORRENT && track.content.isNotBlank()

    fun load(context: Context, track: SubtitlesStream, expectedIdentity: String? = null): Single<List<TranscriptLine>> = Single.create<List<TranscriptLine>> { emitter ->
        try {
            val uri = if (track.isUrl) Uri.parse(track.content) else null
            val local = uri?.scheme == "file" || uri?.scheme == "content"
            val bytes = if (local) {
                require(expectedIdentity != null && OfflinePlaybackMetadata.identity(context, track.content)?.signature == expectedIdentity) { "Caption file changed" }
                context.contentResolver.openInputStream(uri!!)?.use { input ->
                    emitter.setCancellable(input::close)
                    readBounded(input)
                } ?: error("Caption file is unavailable")
            } else if (track.isUrl) {
                require(uri?.scheme == "https" || uri?.scheme == "http") { "Unsupported caption URI" }
                val request = Request.Builder().url(track.content).build()
                val call = DownloaderImpl.getInstance().client.newCall(request)
                emitter.setCancellable(call::cancel)
                call.execute().use { response ->
                    check(response.isSuccessful) { "Caption request failed" }
                    val body = checkNotNull(response.body)
                    require(body.contentLength() <= MAX_BYTES) { "Caption file is too large" }
                    body.byteStream().use(::readBounded)
                }
            } else {
                require(track.content.length <= MAX_BYTES)
                track.content.toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
            }
            if (local) require(OfflinePlaybackMetadata.identity(context, track.content)?.signature == expectedIdentity) { "Caption file changed" }
            if (!emitter.isDisposed) emitter.onSuccess(parse(bytes, checkNotNull(track.format).mimeType))
        } catch (error: Exception) {
            emitter.tryOnError(error)
        }
    }.subscribeOn(Schedulers.io())

    internal fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "Caption loading cancelled" }
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Caption file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    internal fun parse(bytes: ByteArray, mimeType: String): List<TranscriptLine> {
        require(bytes.size <= MAX_BYTES && mimeType in supported)
        val lines = mutableListOf<TranscriptLine>()
        val parser = DefaultSubtitleParserFactory().create(Format.Builder().setSampleMimeType(mimeType).build())
        parser.parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { group ->
            if (group.startTimeUs >= 0) {
                for (cue in group.cues) {
                    val text = cue.text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        require(lines.size < MAX_LINES) { "Too many caption lines" }
                        lines.add(TranscriptLine(group.startTimeUs / 1000, text))
                    }
                }
            }
        }
        // Preserve repeated phrases at different timestamps; remove duplicate parser events only.
        return lines.distinct().sortedBy { it.timeMillis }
    }

    fun filter(lines: List<TranscriptLine>, query: String): List<TranscriptLine> = if (query.isBlank()) lines else lines.filter { it.text.contains(query.trim(), ignoreCase = true) }
}
