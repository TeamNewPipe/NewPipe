/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import java.io.IOException
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.MemorySource
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.box
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.chapter
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.chpl
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.expected
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.fileType
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.frame
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.id3
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.movie
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.mp4
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.real
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.syncSafe
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.text
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.toc
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.u32
import org.schabi.newpipe.local.media.chapters.ChapterFixtures.u64

class EmbeddedChapterParserTest {
    @Test
    fun readsIndependentlyGeneratedPlayableMp3M4aAndMp4Fixtures() {
        listOf("id3v23.mp3", "id3v24.mp3", "nero.m4a", "nero.mp4").forEach { name ->
            val source = MemorySource(real(name))
            assertEquals(name, expected, EmbeddedChapterParser.read(source, 4000))
            assertTrue("Metadata reads must skip the audio payload: $name", source.bytesRead < real(name).size)
        }
    }

    @Test
    fun knownDurationExcludesTheEndWhileUnknownDurationPreservesChapters() {
        val data = real("id3v24.mp3")
        assertEquals(expected.take(2), read(data, 3500))
        listOf(0L, -1L, Long.MIN_VALUE).forEach { assertEquals(expected, read(data, it)) }
    }

    @Test
    fun chapterFramesAreSortedAndDuplicateTimesPreferAUsefulTitle() {
        assertEquals(
            listOf(EmbeddedChapter(100, "First"), EmbeddedChapter(1500, "Later")),
            read(id3(chapter("later", 1500, "Later"), chapter("blank", 100, ""), chapter("named", 100, "First")))
        )
    }

    @Test
    fun tableOfContentsReferencesChaptersAndExcludesUnreferencedTimedImages() {
        val data = id3(
            toc("root", listOf("nested", "intro")),
            toc("nested", listOf("last"), root = false),
            chapter("image", 10, "Not a chapter"),
            chapter("last", 1000, "End"),
            chapter("intro", 0, "Start")
        )
        assertEquals(listOf(EmbeddedChapter(0, "Start"), EmbeddedChapter(1000, "End")), read(data))
    }

    @Test
    fun invalidTocGraphsDoNotProduceMisleadingChapters() {
        listOf(
            listOf(toc("root", listOf("missing"))),
            listOf(toc("root", listOf("root"))),
            listOf(toc("root", listOf("chapter")), toc("second-root", listOf("chapter"))),
            listOf(toc("not-root", listOf("chapter"), root = false)),
            listOf(toc("root", listOf("chapter", "chapter"))),
            listOf(toc("root", emptyList()))
        ).forEach { frames -> assertTrue(read(id3(*(frames + chapter("chapter", 0)).toTypedArray())).isEmpty()) }
        val nested = (0..9).map { toc("level$it", listOf(if (it == 9) "chapter" else "level${it + 1}"), root = it == 0) }
        assertTrue(read(id3(*(nested + chapter("chapter", 0)).toTypedArray())).isEmpty())
    }

    @Test
    fun duplicateElementIdsAndInvalidTimeRangesAreHandledSafely() {
        assertTrue(read(id3(chapter("same", 0), chapter("same", 1000))).isEmpty())
        assertTrue(read(id3(chapter("same", 0), toc("same", listOf("same")))).isEmpty())
        assertTrue(read(id3(chapter("same", 10, finish = 1), chapter("same", 1000))).isEmpty())
        assertEquals(listOf(EmbeddedChapter(100, "valid")), read(id3(chapter("end-before-start", 100, finish = 1), chapter("sentinel", 0xffff_ffff), chapter("valid", 100))))
    }

    @Test
    fun supportsBothExtendedHeaderSizeConventionsAndVersionFourFooter() {
        assertEquals(listOf(EmbeddedChapter(0, "intro")), read(id3(u32(6) + byteArrayOf(0, 0) + u32(0), chapter("intro", 0, version = 3), version = 3, flags = 0x40)))
        assertEquals(listOf(EmbeddedChapter(0, "intro")), read(id3(syncSafe(6) + byteArrayOf(1, 0), chapter("intro", 0), flags = 0x40)))
        val tag = id3(chapter("intro", 0), flags = 0x10)
        assertEquals(listOf(EmbeddedChapter(0, "intro")), read(tag + text("3DI") + tag.copyOfRange(3, 10)))
        assertTrue(read(tag).isEmpty())
    }

    @Test
    fun unsupportedWholeTagTransformationsAndInvalidSizesFailSoftly() {
        assertTrue(read(id3(chapter("x", 0), flags = 0x80)).isEmpty())
        assertTrue(read(id3(chapter("x", 0), version = 2)).isEmpty())
        assertTrue(read(id3(chapter("x", 0)).also { it[6] = 0x80.toByte() }).isEmpty())
        assertTrue(read(id3(text("CHAP") + syncSafe(1000) + byteArrayOf(0, 0))).isEmpty())
        assertTrue(read(id3(u32(0), flags = 0x40)).isEmpty())
    }

    @Test
    fun decodesVersionFourFrameUnsynchronisationAndDataLengthIndicator() {
        val payload = text("id") + byteArrayOf(0) + u32(0) + u32(0xffff_ffff) + u64(-1) + frame("TIT2", byteArrayOf(0) + text("Cafÿ"))
        val decoded = syncSafe(payload.size) + payload
        val encoded = decoded.flatMap { if (it == 0xff.toByte()) listOf(it, 0.toByte()) else listOf(it) }.toByteArray()
        assertEquals(listOf(EmbeddedChapter(0, "Cafÿ")), read(id3(frame("CHAP", encoded, flags = 3))))
        assertTrue(read(id3(frame("CHAP", syncSafe(1) + payload, flags = 1))).isEmpty())
        assertTrue(read(id3(frame("CHAP", payload, flags = 8))).isEmpty())
    }

    @Test
    fun titlesPreserveUnicodeAndNeverExposeInternalIds() {
        val title = "😀".repeat(600)
        assertEquals("😀".repeat(512), read(id3(chapter("id", 0, title))).single().title)
        assertEquals("A B C", read(id3(chapter("id", 0, "  A\nB\tC\u0000ignored"))).single().title)
        val payload = text("private-internal-element-id") + byteArrayOf(0) + u32(0) + u32(1000) + u64(-1)
        assertEquals("", read(id3(frame("CHAP", payload))).single().title)
        assertEquals("", read(id3(frame("CHAP", payload + frame("TIT2", byteArrayOf(3, 0xc3.toByte(), 0x28))))).single().title)
    }

    @Test
    fun acceptsNeroVersionsZeroAndOneAndExtendedAtoms() {
        assertEquals(expected, read(mp4(chpl(version = 0))))
        assertEquals(expected, read(fileType + movie(extended = true)))
        val data = movie().copyOf().also { u32(0).copyInto(it) }
        assertEquals(expected, read(fileType + data))
    }

    @Test
    fun neroTableMustBeChronologicalButSubmillisecondDuplicatesAreNormalized() {
        assertTrue(read(mp4(chpl(expected.reversed()))).isEmpty())
        assertTrue(read(mp4(chpl(listOf(expected.first(), expected.first())))).isEmpty())
        val table = byteArrayOf(0, 0, 0, 0, 2) + u64(1) + byteArrayOf(0) + u64(2) + byteArrayOf(1) + text("A")
        assertEquals(listOf(EmbeddedChapter(0, "A")), read(mp4(table)))
    }

    @Test
    fun malformedAtomSizesAndTablesFailSoftly() {
        listOf(
            fileType + u32(7) + text("moov"),
            fileType + u32(1) + text("moov") + u64(Long.MAX_VALUE),
            fileType + u32(1) + text("moov") + u64(-1),
            fileType + box("moov", u32(1000) + text("udta")),
            fileType + box("moov", byteArrayOf(1, 2)),
            mp4(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 1)),
            mp4(chpl().copyOf(chpl().size - 1)),
            mp4(chpl().also { it[0] = 2 })
        ).forEach { assertTrue(read(it).isEmpty()) }
    }

    @Test
    fun seeksPastMoreThanFourGigabytesOfMediaWithoutReadingItsPayload() {
        val mdatSize = 5L * 1024 * 1024 * 1024
        val movieOffset = fileType.size + mdatSize
        val metadata = movie()
        val parts = mapOf(0L to fileType, fileType.size.toLong() to (u32(1) + text("mdat") + u64(mdatSize)), movieOffset to metadata)
        var bytesRead = 0
        val source = object : ChapterDataSource {
            override val length = movieOffset + metadata.size
            override fun readAt(offset: Long, count: Int): ByteArray {
                val part = checkNotNull(parts.entries.firstOrNull { offset >= it.key && offset + count <= it.key + it.value.size }) { "Read media payload at $offset" }
                bytesRead += count
                return part.value.copyOfRange((offset - part.key).toInt(), (offset - part.key).toInt() + count)
            }
        }
        assertEquals(expected, EmbeddedChapterParser.read(source, 4000))
        assertTrue(bytesRead < 512)
    }

    @Test
    fun ignoresArtworkPayloadWhenSeekingToLaterId3Chapters() {
        val artworkSize = 16 * 1024 * 1024
        val chapter = chapter("chapter", 1501, "Later")
        val head = text("ID3") + byteArrayOf(4, 0, 0) + syncSafe(10 + artworkSize + chapter.size) + text("APIC") + syncSafe(artworkSize) + byteArrayOf(0, 0)
        val chapterOffset = 20L + artworkSize
        var bytesRead = 0
        val source = object : ChapterDataSource {
            override val length = chapterOffset + chapter.size
            override fun readAt(offset: Long, count: Int): ByteArray {
                bytesRead += count
                val part = if (offset < head.size) head else chapter
                val start = if (offset < head.size) offset.toInt() else (offset - chapterOffset).toInt()
                check(start >= 0 && start + count <= part.size) { "Read artwork payload" }
                return part.copyOfRange(start, start + count)
            }
        }
        assertEquals(listOf(EmbeddedChapter(1501, "Later")), EmbeddedChapterParser.read(source, 4000))
        assertTrue(bytesRead < 256)
    }

    @Test
    fun rejectsExcessiveChapterCountsStructuresAndMetadataBytes() {
        assertTrue(read(id3(*(0..512).map { chapter("ch$it", it.toLong()) }.toTypedArray())).isEmpty())
        val frames = (0..4100).map { frame("TXXX", byteArrayOf(0)) }
        val structures = MemorySource(id3(*frames.toTypedArray()))
        assertTrue(EmbeddedChapterParser.read(structures, -1).isEmpty())
        assertTrue(structures.reads <= 4097)
        val padding = frame("APIC", ByteArray(100_000))
        val source = MemorySource(id3(*(0..44).map { chapter("ch$it", it.toLong(), extra = padding) }.toTypedArray()))
        assertTrue(EmbeddedChapterParser.read(source, -1).isEmpty())
        assertTrue(source.bytesRead <= 4 * 1024 * 1024)
    }

    @Test
    fun truncatedAndUnsupportedFilesAreIgnoredWithoutBreakingPlayback() {
        val data = id3(chapter("a", 0), toc("root", listOf("a")))
        (0 until data.size).forEach { assertTrue("Truncated at $it", read(data.copyOf(it)).isEmpty()) }
        assertTrue(read(text("OggS") + ByteArray(100)).isEmpty())
        assertTrue(read(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()) + ByteArray(100)).isEmpty())
        assertTrue(read(fileType + box("moov", box("trak", ByteArray(32)))).isEmpty())
        assertTrue(
            EmbeddedChapterParser.read(
                object : ChapterDataSource {
                    override val length: Long? = null
                    override fun readAt(offset: Long, count: Int): ByteArray = throw IOException("provider unavailable")
                },
                -1
            ).isEmpty()
        )
    }

    @Test
    fun cancellationStopsReadsAndLeavesTheThreadsInterruptFlagIntact() {
        val source = MemorySource(real("id3v24.mp3"))
        assertTrue(EmbeddedChapterParser.read(source, -1) { true }.isEmpty())
        assertEquals(0, source.reads)
        assertTrue(EmbeddedChapterParser.read(source, -1) { source.reads >= 2 }.isEmpty())
        assertEquals(2, source.reads)
        Thread.currentThread().interrupt()
        try {
            assertTrue(EmbeddedChapterParser.read(source, -1).isEmpty())
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun randomMalformedHeadersCannotEscapeTheSoftFailureBoundary() {
        val random = Random(1675)
        repeat(1000) {
            val data = ByteArray(random.nextInt(512))
            random.nextBytes(data)
            if (data.size >= 10) {
                if (it % 2 == 0) text("ID3").copyInto(data) else text("ftyp").copyInto(data, 4)
            }
            assertTrue(read(data).isEmpty())
        }
    }

    private fun read(data: ByteArray, durationMs: Long = -1): List<EmbeddedChapter> = EmbeddedChapterParser.read(MemorySource(data), durationMs)
}
