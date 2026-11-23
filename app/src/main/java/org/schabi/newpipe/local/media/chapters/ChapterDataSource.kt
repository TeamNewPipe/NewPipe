/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CancellationException

internal interface ChapterDataSource {
    val length: Long?
    fun readAt(offset: Long, count: Int): ByteArray?
}

/** Positional reads preserve the descriptor position and respect an SAF asset's slice. */
internal class FileChapterDataSource(
    private val channel: FileChannel,
    private val startOffset: Long,
    override val length: Long?,
    private val cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }
) : ChapterDataSource {
    override fun readAt(offset: Long, count: Int): ByteArray? {
        checkChapterCancellation(cancelled)
        if (startOffset < 0 || offset < 0 || count < 0 || offset > Long.MAX_VALUE - startOffset - count) return null
        if (length != null && (length < 0 || offset > length - count)) return null
        val bytes = ByteArray(count)
        val buffer = ByteBuffer.wrap(bytes)
        var position = startOffset + offset
        while (buffer.hasRemaining()) {
            checkChapterCancellation(cancelled)
            val read = channel.read(buffer, position)
            if (read <= 0) return null
            position += read
        }
        return bytes
    }
}

/** Pipes can expose front-loaded tags, but never force an unbounded scan through media payload. */
internal class PrefixChapterDataSource(
    private val input: InputStream,
    override val length: Long?,
    private val cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }
) : ChapterDataSource {
    private var buffer = ByteArray(8 * 1024)
    private var filled = 0
    private var ended = false

    override fun readAt(offset: Long, count: Int): ByteArray? {
        checkChapterCancellation(cancelled)
        if (offset < 0 || count < 0 || offset > MAX_PREFIX_BYTES - count) return null
        if (length != null && (length < 0 || offset > length - count)) return null
        val end = offset.toInt() + count
        if (end > buffer.size) buffer = buffer.copyOf(maxOf(end, buffer.size * 2).coerceAtMost(MAX_PREFIX_BYTES))
        while (filled < end && !ended) {
            checkChapterCancellation(cancelled)
            val read = input.read(buffer, filled, minOf(end - filled, 64 * 1024))
            when {
                read < 0 -> ended = true

                read > 0 -> filled += read

                else -> {
                    checkChapterCancellation(cancelled)
                    val single = input.read()
                    if (single < 0) ended = true else buffer[filled++] = single.toByte()
                }
            }
        }
        return if (filled >= end) buffer.copyOfRange(offset.toInt(), end) else null
    }

    companion object {
        const val MAX_PREFIX_BYTES = 4 * 1024 * 1024
    }
}

internal fun checkChapterCancellation(cancelled: () -> Boolean) {
    if (Thread.currentThread().isInterrupted || cancelled()) throw CancellationException("Chapter reading cancelled")
}
