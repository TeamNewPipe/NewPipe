/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDataSourceTest {
    @Test
    fun positionalReadsRespectAssetOffsetsAndLengthsWithoutChangingTheFile() {
        val data = ChapterFixtures.real("id3v24.mp3")
        val file = Files.createTempFile("chapter-asset", ".bin").toFile()
        val prefix = ByteArray(91) { 0x77 }
        val original = prefix + data + ByteArray(123) { 0x55 }
        file.writeBytes(original)
        try {
            FileInputStream(file).use { stream ->
                stream.channel.position(17)
                val source = FileChapterDataSource(stream.channel, prefix.size.toLong(), data.size.toLong())
                assertEquals(ChapterFixtures.expected, EmbeddedChapterParser.read(source, 4000))
                assertEquals(17L, stream.channel.position())
                assertNull(source.readAt(data.size.toLong(), 1))
                assertNull(source.readAt(Long.MAX_VALUE, 10))
                assertNull(source.readAt(-1, 1))
                assertNull(source.readAt(0, -1))
                assertNull(FileChapterDataSource(stream.channel, Long.MAX_VALUE, null).readAt(1, 1))
            }
            assertArrayEquals(original, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedSeekableFilesReturnNoChapters() {
        val file = Files.createTempFile("chapter-truncated", ".bin").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            FileInputStream(file).use { stream -> assertNull(FileChapterDataSource(stream.channel, 0, null).readAt(0, 10)) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun pipesReadLeadingTagsOnceAndKeepEarlierBytesAvailable() {
        val data = ChapterFixtures.real("id3v23.mp3")
        val input = ByteArrayInputStream(data)
        val source = PrefixChapterDataSource(input, null)
        assertEquals(ChapterFixtures.expected, EmbeddedChapterParser.read(source, 4000))
        val unread = input.available()
        assertArrayEquals(data.copyOf(10), source.readAt(0, 10))
        assertEquals(unread, input.available())
        assertTrue(unread > 0)
    }

    @Test
    fun pipesCannotCauseUnboundedScansThroughLargeMediaPayloads() {
        var bytesRead = 0
        val input = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return 0
            }
            override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                bytes.fill(0, offset, offset + count)
                bytesRead += count
                return count
            }
        }
        val source = PrefixChapterDataSource(input, null)
        assertNull(source.readAt(5L * 1024 * 1024 * 1024, 8))
        assertNull(source.readAt(0, Int.MAX_VALUE))
        assertEquals(0, bytesRead)
        assertEquals(1, source.readAt(PrefixChapterDataSource.MAX_PREFIX_BYTES - 1L, 1)?.size)
        assertEquals(PrefixChapterDataSource.MAX_PREFIX_BYTES, bytesRead)
        assertNull(source.readAt(PrefixChapterDataSource.MAX_PREFIX_BYTES.toLong(), 1))
        assertEquals(PrefixChapterDataSource.MAX_PREFIX_BYTES, bytesRead)
    }

    @Test
    fun shortAndZeroReadStreamsTerminateWithoutSpinning() {
        val source = PrefixChapterDataSource(
            object : InputStream() {
                var position = 0
                override fun read(): Int = if (position < 3) ++position else -1
                override fun read(bytes: ByteArray, offset: Int, count: Int): Int = 0
            },
            null
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), source.readAt(0, 3))
        assertNull(source.readAt(0, 4))
        assertNull(source.readAt(-1, 1))
        assertNull(source.readAt(0, -1))
        assertNull(PrefixChapterDataSource(ByteArrayInputStream(ByteArray(100)), 2).readAt(0, 3))
    }

    @Test(expected = CancellationException::class)
    fun pipesStopWhenTheCallerCancels() {
        PrefixChapterDataSource(ByteArrayInputStream(ByteArray(100)), null) { true }.readAt(0, 10)
    }

    @Test
    fun cancellingAnInterruptiblePipeReadUnblocksTheMetadataWorker() {
        val pipe = Pipe.open()
        val channelInput = Channels.newInputStream(pipe.source())
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val result = AtomicReference<List<EmbeddedChapter>>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val source = PrefixChapterDataSource(
                object : InputStream() {
                    override fun read(): Int = channelInput.read()
                    override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                        started.countDown()
                        return channelInput.read(bytes, offset, count)
                    }
                },
                null
            )
            val task = executor.submit {
                try {
                    result.set(EmbeddedChapterParser.read(source, -1))
                } finally {
                    finished.countDown()
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            task.cancel(true)
            assertTrue("Cancellation must unblock a channel-backed provider pipe", finished.await(2, TimeUnit.SECONDS))
            assertEquals(emptyList<EmbeddedChapter>(), result.get())
            assertTrue(!pipe.source().isOpen)
        } finally {
            pipe.sink().close()
            pipe.source().close()
            executor.shutdownNow()
            assertTrue("The metadata worker must terminate before this fixture finishes", executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
