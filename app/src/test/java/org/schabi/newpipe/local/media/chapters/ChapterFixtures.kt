/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

internal object ChapterFixtures {
    val expected = listOf(EmbeddedChapter(0, "Introduction"), EmbeddedChapter(1500, "Résumé – فصل"), EmbeddedChapter(3500, "Closing"))

    fun real(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/local-chapters/$name.base64")).use {
        Base64.getMimeDecoder().decode(it.readBytes())
    }

    fun bytes(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use(block)
    }.toByteArray()

    fun u32(value: Long): ByteArray = bytes { writeInt(value.toInt()) }
    fun u64(value: Long): ByteArray = bytes { writeLong(value) }
    fun syncSafe(value: Int): ByteArray = byteArrayOf((value shr 21).toByte(), (value shr 14 and 127).toByte(), (value shr 7 and 127).toByte(), (value and 127).toByte())
    fun text(value: String): ByteArray = value.toByteArray(Charsets.ISO_8859_1)
    fun frame(id: String, payload: ByteArray, version: Int = 4, flags: Int = 0): ByteArray = text(id) + (if (version == 4) syncSafe(payload.size) else u32(payload.size.toLong())) + byteArrayOf(0, flags.toByte()) + payload

    fun chapter(id: String, start: Long, title: String = id, version: Int = 4, finish: Long = 0xffff_ffffL, extra: ByteArray = byteArrayOf()): ByteArray {
        val titleBytes = if (version == 4) byteArrayOf(3) + title.toByteArray(Charsets.UTF_8) else byteArrayOf(1) + title.toByteArray(Charsets.UTF_16)
        return frame("CHAP", text(id) + byteArrayOf(0) + u32(start) + u32(finish) + u32(0xffff_ffff) + u32(0xffff_ffff) + frame("TIT2", titleBytes, version) + extra, version)
    }

    fun toc(id: String, children: List<String>, root: Boolean = true, version: Int = 4): ByteArray = frame(
        "CTOC",
        text(id) + byteArrayOf(0, (if (root) 3 else 1).toByte(), children.size.toByte()) + children.flatMap { (text(it) + byteArrayOf(0)).toList() }.toByteArray(),
        version
    )

    fun id3(vararg frames: ByteArray, version: Int = 4, flags: Int = 0): ByteArray {
        val dataSize = frames.sumOf { it.size }
        return ByteArray(10 + dataSize).apply {
            text("ID3").copyInto(this)
            this[3] = version.toByte()
            this[5] = flags.toByte()
            syncSafe(dataSize).copyInto(this, 6)
            var offset = 10
            frames.forEach { frame ->
                frame.copyInto(this, offset)
                offset += frame.size
            }
        }
    }

    fun box(type: String, data: ByteArray, extended: Boolean = false): ByteArray = (if (extended) u32(1) + text(type) + u64(data.size + 16L) else u32(data.size + 8L) + text(type)) + data

    fun chpl(chapters: List<EmbeddedChapter> = expected, version: Int = 1): ByteArray = byteArrayOf(version.toByte(), 0, 0, 0) +
        (if (version == 1) u32(0) else byteArrayOf()) + byteArrayOf(chapters.size.toByte()) + bytes {
            chapters.forEach { chapter ->
                writeLong(chapter.startMillis * 10_000)
                val title = chapter.title.toByteArray(Charsets.UTF_8)
                writeByte(title.size)
                write(title)
            }
        }

    val fileType: ByteArray get() = box("ftyp", text("M4A ") + u32(0))
    fun movie(table: ByteArray = chpl(), extended: Boolean = false): ByteArray = box("moov", box("udta", box("chpl", table, extended)), extended)
    fun mp4(table: ByteArray = chpl()): ByteArray = fileType + movie(table)

    class MemorySource(private val data: ByteArray, knownLength: Boolean = true) : ChapterDataSource {
        override val length: Long? = data.size.toLong().takeIf { knownLength }
        var bytesRead = 0
        var reads = 0
        override fun readAt(offset: Long, count: Int): ByteArray? {
            reads++
            if (offset < 0 || offset > data.size.toLong() - count) return null
            bytesRead += count
            return data.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
    }
}
