/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.annotation.WorkerThread
import java.io.IOException
import java.nio.channels.Channels
import org.schabi.newpipe.R

/**
 * Best-effort chapters from an already accessible local file. This does not acquire permissions,
 * change the file, or perform network requests. Unsupported or damaged metadata leaves playback
 * available without chapters. The caller must use a worker thread and discard stale results.
 *
 * Supported metadata: leading ID3v2.3/2.4 CHAP/CTOC and Nero MP4/M4A moov/udta/chpl.
 * Seekable providers can keep their media payload arbitrarily large; nonseekable providers expose
 * only metadata within the first 4 MiB, with a zero asset offset. A nonpositive duration means the
 * duration is not yet known. If supplied, the cancellation signal must be dedicated to this read.
 */
object EmbeddedChapterReader {
    @JvmStatic
    @JvmOverloads
    @WorkerThread
    fun read(context: Context, uri: Uri, durationMs: Long, cancellationSignal: CancellationSignal? = null): List<EmbeddedChapter> {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) return emptyList()
        val cancelled = { cancellationSignal?.isCanceled == true }
        return try {
            checkChapterCancellation(cancelled)
            val asset = context.contentResolver.openAssetFileDescriptor(uri, "r", cancellationSignal) ?: return emptyList()
            asset.use {
                cancellationSignal?.setOnCancelListener { runCatching { asset.close() } }
                try {
                    checkChapterCancellation(cancelled)
                    // Use the raw descriptor: some framework asset streams skip to startOffset
                    // before callers can check bounds or cancellation on a nonseekable provider.
                    ParcelFileDescriptor.AutoCloseInputStream(asset.parcelFileDescriptor).use { stream ->
                        val seekable = try {
                            stream.channel.position()
                            true
                        } catch (_: IOException) {
                            false
                        }
                        if (!seekable && asset.startOffset != 0L) return emptyList()
                        val length = asset.declaredLength.takeIf { it >= 0 } ?: if (seekable) {
                            stream.channel.size().takeIf { it >= asset.startOffset }?.minus(asset.startOffset)
                        } else {
                            null
                        }
                        val source = if (seekable) {
                            FileChapterDataSource(stream.channel, asset.startOffset, length, cancelled)
                        } else {
                            // FileChannel's interruptible read also supports Future.cancel(true),
                            // unlike a raw InputStream read blocked on a provider pipe.
                            PrefixChapterDataSource(Channels.newInputStream(stream.channel), length, cancelled)
                        }
                        EmbeddedChapterParser.read(source, durationMs, cancelled).mapIndexed { index, chapter ->
                            if (chapter.title.isNotBlank()) chapter else chapter.copy(title = context.getString(R.string.embedded_chapter_fallback_title, index + 1))
                        }
                    }
                } finally {
                    cancellationSignal?.setOnCancelListener(null)
                }
            }
        } catch (_: Exception) {
            // Revoked grants, disappearing files, cancellation and failing document providers
            // must not prevent local playback. Interruption is checked without clearing its flag.
            emptyList()
        }
    }
}
