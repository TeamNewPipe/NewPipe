/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException

/** Reads leading ID3v2.3/2.4 CHAP/CTOC tags and MP4 moov/udta/chpl tables. */
internal object EmbeddedChapterParser {
    const val MAX_CHAPTERS = 512
    const val MAX_TITLE_CODE_POINTS = 512
    private const val MAX_READ_BYTES = 4 * 1024 * 1024
    private const val MAX_PAYLOAD_BYTES = 128 * 1024
    private const val MAX_STRUCTURES = 4096
    private const val MAX_TOC_DEPTH = 8

    fun read(source: ChapterDataSource, durationMs: Long, cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }): List<EmbeddedChapter> = try {
        val input = Budget(source, cancelled)
        val header = input.bytes(0, 10)
        val chapters = when {
            header.ascii(0, 3) == "ID3" -> readId3(input, header)
            header.ascii(4, 4) == "ftyp" -> readMp4(input)
            else -> emptyList()
        }
        checkChapterCancellation(cancelled)
        normalize(chapters, durationMs)
    } catch (_: InvalidData) {
        emptyList()
    } catch (_: IOException) {
        emptyList()
    } catch (_: CancellationException) {
        emptyList()
    }

    private fun readId3(input: Budget, header: ByteArray): List<EmbeddedChapter> {
        val version = header.u8(3)
        if (version !in 3..4 || header.u8(4) == 255) return emptyList()
        val flags = header.u8(5)
        // Whole-tag unsynchronisation changes physical frame boundaries. Unsupported tags
        // remain playable; do not mistake their encoded bytes for ordinary frame offsets.
        if (flags and 0x80 != 0 || flags and (if (version == 3) 0x1f else 0x0f) != 0) return emptyList()
        val end = 10L + header.syncSafe(6)
        input.requireRange(0, end + if (version == 4 && flags and 0x10 != 0) 10 else 0)
        var offset = 10L
        if (flags and 0x40 != 0) {
            val extended = input.bytes(offset, 4)
            val size = if (version == 4) extended.syncSafe(0).toLong() else extended.u32(0) + 4
            if (size < (if (version == 4) 6 else 10) || size > end - offset) invalid()
            offset += size
        }
        val identifiers = mutableSetOf<String>()
        val chapters = linkedMapOf<String, EmbeddedChapter>()
        val tables = linkedMapOf<String, Toc>()
        while (offset < end) {
            input.structure()
            if (end - offset < 10) {
                if (input.bytes(offset, (end - offset).toInt()).any { it != 0.toByte() }) invalid()
                break
            }
            val frame = input.bytes(offset, 10)
            if (frame.take(4).all { it == 0.toByte() }) break
            val id = frame.frameId()
            val size = frame.frameSize(version)
            if (size <= 0 || size > end - offset - 10) invalid()
            if (id == "CHAP" || id == "CTOC") {
                if (size > MAX_PAYLOAD_BYTES) invalid()
                val payload = framePayload(input.bytes(offset + 10, size.toInt()), frame.u8(9), version)
                if (payload != null) {
                    val cursor = Cursor(payload)
                    val element = cursor.identifier()
                    if (!identifiers.add(element)) invalid()
                    if (id == "CHAP") {
                        val start = cursor.u32()
                        val finish = cursor.u32()
                        cursor.skip(8) // Byte offsets are not trusted as seek destinations.
                        val title = readTitle(payload, cursor.position, version)
                        if (start != 0xffff_ffffL && (finish == 0xffff_ffffL || finish > start)) {
                            if (chapters.size >= MAX_CHAPTERS) invalid()
                            chapters[element] = EmbeddedChapter(start, title)
                        }
                    } else {
                        if (tables.size >= 64) invalid()
                        val tocFlags = cursor.u8()
                        if (tocFlags and 0xfc != 0) invalid()
                        val count = cursor.u8()
                        if (count == 0) invalid()
                        val children = List(count) { cursor.identifier() }
                        // Validate any nested frames even though a group title is not a chapter.
                        readTitle(payload, cursor.position, version)
                        tables[element] = Toc(tocFlags and 2 != 0, children)
                    }
                }
            }
            offset += 10 + size
        }
        if (tables.isEmpty()) return chapters.values.toList()
        val roots = tables.filterValues { it.root }.keys
        if (roots.size != 1) invalid()
        val result = mutableListOf<EmbeddedChapter>()
        val visited = mutableSetOf<String>()
        fun visit(id: String, depth: Int) {
            input.structure()
            if (depth > MAX_TOC_DEPTH || !visited.add(id)) invalid()
            chapters[id]?.let { result += it } ?: run {
                val table = tables[id] ?: invalid()
                table.children.forEach { visit(it, depth + 1) }
            }
        }
        visit(roots.single(), 0)
        return result
    }

    private fun readTitle(data: ByteArray, start: Int, version: Int): String {
        var offset = start
        var title = ""
        var frames = 0
        while (offset < data.size) {
            if (++frames > 64) invalid()
            if (data.size - offset < 10) {
                if (data.copyOfRange(offset, data.size).any { it != 0.toByte() }) invalid()
                break
            }
            val frame = data.copyOfRange(offset, offset + 10)
            if (frame.take(4).all { it == 0.toByte() }) break
            val id = frame.frameId()
            val size = frame.frameSize(version)
            if (size <= 0 || size > data.size - offset - 10) invalid()
            if (id == "TIT2" && title.isEmpty()) {
                framePayload(data.copyOfRange(offset + 10, offset + 10 + size.toInt()), frame.u8(9), version)?.let { payload ->
                    if (payload.isNotEmpty()) title = decodeId3Text(payload, version)
                }
            }
            offset += 10 + size.toInt()
        }
        return title
    }

    private fun framePayload(payload: ByteArray, flags: Int, version: Int): ByteArray? {
        // Compression, encryption, grouping and unknown format flags are deliberately unsupported.
        if (version == 3) return payload.takeIf { flags == 0 }
        if (flags and 0xfc != 0) return null
        var data = if (flags and 2 != 0) deUnsynchronize(payload) else payload
        if (flags and 1 != 0) {
            if (data.size < 4 || data.syncSafe(0) != data.size - 4) invalid()
            data = data.copyOfRange(4, data.size)
        }
        return data
    }

    private fun deUnsynchronize(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        var source = 0
        var target = 0
        while (source < data.size) {
            val value = data[source++]
            result[target++] = value
            if (value == 0xff.toByte() && source < data.size && data[source] == 0.toByte()) source++
        }
        return result.copyOf(target)
    }

    private fun decodeId3Text(data: ByteArray, version: Int): String {
        val encoding = data.u8(0)
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> if (version == 4) Charsets.UTF_16BE else return ""
            3 -> if (version == 4) Charsets.UTF_8 else return ""
            else -> return ""
        }
        if (encoding == 1 && (data.size < 3 || !(data[1] == 0xff.toByte() && data[2] == 0xfe.toByte() || data[1] == 0xfe.toByte() && data[2] == 0xff.toByte()))) return ""
        return decode(data.copyOfRange(1, data.size), charset)
    }

    private fun readMp4(input: Budget): List<EmbeddedChapter> {
        fun boxes(start: Long, end: Long?, depth: Int): List<EmbeddedChapter> {
            if (depth > 3) invalid()
            var offset = start
            while (end == null || offset < end) {
                input.structure()
                if (end != null && end - offset < 8) invalid()
                val header = input.optionalBytes(offset, 8) ?: return emptyList()
                val type = header.ascii(4, 4)
                val shortSize = header.u32(0)
                val headerSize = if (shortSize == 1L) 16L else 8L
                val size = when (shortSize) {
                    0L -> end?.minus(offset) ?: return emptyList()
                    1L -> input.bytes(offset + 8, 8).positiveU64(0)
                    else -> shortSize
                }
                if (size < headerSize || offset > Long.MAX_VALUE - size || end != null && size > end - offset) invalid()
                input.requireRange(offset, size)
                val next = offset + size
                val result = when {
                    depth == 0 && type == "moov" -> boxes(offset + headerSize, next, 1)

                    depth == 1 && type == "udta" -> boxes(offset + headerSize, next, 2)

                    depth == 2 && type == "chpl" -> {
                        val length = size - headerSize
                        if (length > MAX_PAYLOAD_BYTES) invalid()
                        readChpl(input.bytes(offset + headerSize, length.toInt()))
                    }

                    else -> emptyList()
                }
                if (result.isNotEmpty()) return result
                offset = next
            }
            return emptyList()
        }
        return boxes(0, input.length, 0)
    }

    private fun readChpl(data: ByteArray): List<EmbeddedChapter> {
        val cursor = Cursor(data)
        val version = cursor.u8()
        if (version !in 0..1 || cursor.u8() != 0 || cursor.u8() != 0 || cursor.u8() != 0) return emptyList()
        if (version == 1) cursor.skip(4)
        val count = cursor.u8()
        var previous = -1L
        return List(count) {
            val ticks = cursor.u64() // Nero uses 100 ns units.
            if (ticks <= previous) invalid()
            previous = ticks
            val title = decode(cursor.bytes(cursor.u8()), Charsets.UTF_8)
            EmbeddedChapter(ticks / 10_000, title)
        }
    }

    private fun normalize(chapters: List<EmbeddedChapter>, durationMs: Long): List<EmbeddedChapter> = chapters.asSequence()
        .filter { it.startMillis >= 0 && (durationMs <= 0 || it.startMillis < durationMs) }
        .sortedBy { it.startMillis }
        .groupBy { it.startMillis }
        .values.map { sameStart -> sameStart.firstOrNull { it.title.isNotBlank() } ?: sameStart.first() }
        .take(MAX_CHAPTERS)

    private fun decode(bytes: ByteArray, charset: Charset): String = try {
        val text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().substringBefore('\u0000')
            .map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("").trim()
        if (text.codePointCount(0, text.length) > MAX_TITLE_CODE_POINTS) text.substring(0, text.offsetByCodePoints(0, MAX_TITLE_CODE_POINTS)) else text
    } catch (_: java.nio.charset.CharacterCodingException) {
        ""
    }

    private data class Toc(val root: Boolean, val children: List<String>)

    private class Budget(private val source: ChapterDataSource, private val cancelled: () -> Boolean) {
        val length get() = source.length
        private var total = 0
        private var structures = 0
        fun structure() {
            checkChapterCancellation(cancelled)
            if (++structures > MAX_STRUCTURES) invalid()
        }
        fun requireRange(offset: Long, size: Long) {
            if (offset < 0 || size < 0 || offset > Long.MAX_VALUE - size || length?.let { offset > it - size } == true) invalid()
        }
        fun optionalBytes(offset: Long, count: Int): ByteArray? {
            checkChapterCancellation(cancelled)
            if (count < 0 || count > MAX_PAYLOAD_BYTES || total > MAX_READ_BYTES - count) invalid()
            requireRange(offset, count.toLong())
            total += count
            return source.readAt(offset, count)?.also { if (it.size != count) invalid() }
        }
        fun bytes(offset: Long, count: Int): ByteArray = optionalBytes(offset, count) ?: invalid()
    }

    private class Cursor(private val data: ByteArray) {
        var position = 0
            private set
        fun u8(): Int = bytes(1)[0].toInt() and 255
        fun u32(): Long = bytes(4).u32(0)
        fun u64(): Long = bytes(8).positiveU64(0)
        fun skip(count: Int) {
            bytes(count)
        }
        fun bytes(count: Int): ByteArray {
            if (count < 0 || count > data.size - position) invalid()
            return data.copyOfRange(position, position + count).also { position += count }
        }
        fun identifier(): String {
            val start = position
            while (position < data.size && data[position] != 0.toByte() && position - start <= 255) position++
            if (position == start || position >= data.size || position - start > 255) invalid()
            return data.ascii(start, position++ - start)
        }
    }

    private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 255
    private fun ByteArray.u32(offset: Int): Long = (u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong()
    private fun ByteArray.positiveU64(offset: Int): Long {
        if (u8(offset) and 0x80 != 0) invalid()
        return (u32(offset) shl 32) or u32(offset + 4)
    }
    private fun ByteArray.syncSafe(offset: Int): Int {
        if ((0..3).any { u8(offset + it) and 0x80 != 0 }) invalid()
        return (u8(offset) shl 21) or (u8(offset + 1) shl 14) or (u8(offset + 2) shl 7) or u8(offset + 3)
    }
    private fun ByteArray.frameSize(version: Int): Long = if (version == 4) syncSafe(4).toLong() else u32(4)
    private fun ByteArray.frameId(): String {
        if ((0..3).any { u8(it) !in 65..90 && u8(it) !in 48..57 }) invalid()
        return ascii(0, 4)
    }
    private fun ByteArray.ascii(start: Int, count: Int): String = String(this, start, count, Charsets.ISO_8859_1)
    private class InvalidData : RuntimeException()
    private fun invalid(): Nothing = throw InvalidData()
}
