/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.Subtitle
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import okhttp3.Call
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.stream.SubtitlesStream

/** Loads the second caption track without sending local URIs to the network. */
internal object SecondaryCaptionLoader {
    internal const val MAX_BYTES = 4 * 1024 * 1024

    fun load(
        context: Context,
        stream: SubtitlesStream,
        expectedIdentity: String? = null,
        remoteCall: (Request) -> Call = { DownloaderImpl.getInstance().client.newCall(it) }
    ): Single<Subtitle> = Single.create<Subtitle> { emitter ->
        try {
            val mime = stream.format?.mimeType ?: throw IOException("Unsupported subtitle format")
            val format = Format.Builder().setSampleMimeType(mime).setLanguage(stream.languageTag).build()
            val factory = DefaultSubtitleParserFactory()
            require(factory.supportsFormat(format)) { "Unsupported subtitle format: $mime" }
            val uri = if (stream.isUrl) Uri.parse(stream.content) else null
            val local = uri?.scheme == "file" || uri?.scheme == "content"
            val cancelled = { emitter.isDisposed || Thread.currentThread().isInterrupted }
            val bytes = when {
                local -> {
                    require(expectedIdentity != null && OfflinePlaybackMetadata.identity(context, stream.content)?.signature == expectedIdentity) { "Caption file changed" }
                    context.contentResolver.openInputStream(uri!!)?.use { input ->
                        emitter.setCancellable(input::close)
                        readBounded(input, cancelled)
                    } ?: throw IOException("Caption file is unavailable")
                }

                stream.isUrl -> {
                    require(uri?.scheme == "http" || uri?.scheme == "https") { "Unsupported caption URI" }
                    val call = remoteCall(Request.Builder().url(stream.content).build())
                    emitter.setCancellable(call::cancel)
                    call.execute().use { response ->
                        check(response.isSuccessful) { "Subtitle HTTP ${response.code}" }
                        val body = response.body ?: throw IOException("Empty subtitle")
                        require(body.contentLength() <= MAX_BYTES) { "Caption file is too large" }
                        body.byteStream().use { readBounded(it, cancelled) }
                    }
                }

                else -> {
                    require(stream.content.length <= MAX_BYTES) { "Caption file is too large" }
                    stream.content.toByteArray(Charsets.UTF_8).also {
                        require(it.size <= MAX_BYTES) { "Caption file is too large" }
                    }
                }
            }
            if (cancelled()) return@create
            if (local) {
                require(OfflinePlaybackMetadata.identity(context, stream.content)?.signature == expectedIdentity) { "Caption file changed" }
            }
            val subtitle = factory.create(format).parseToLegacySubtitle(bytes, 0, bytes.size)
            if (!cancelled()) emitter.onSuccess(subtitle)
        } catch (error: Exception) {
            emitter.tryOnError(error)
        }
    }.subscribeOn(Schedulers.io())

    internal fun readBounded(input: InputStream, cancelled: () -> Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (cancelled()) throw CancellationException("Caption loading cancelled")
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                if (cancelled()) throw CancellationException("Caption loading cancelled")
                val value = input.read()
                if (value < 0) break
                require(output.size() < MAX_BYTES) { "Caption file is too large" }
                output.write(value)
            } else {
                require(output.size() + count <= MAX_BYTES) { "Caption file is too large" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}
