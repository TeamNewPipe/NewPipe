/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.streams

import android.util.Log
import android.util.Pair
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Tests the Opus metadata header built by [OggFromWebMWriter].
 *
 * [Pair] and [Log] are only stubs in local unit tests and are mocked statically.
 */
class OggFromWebMWriterTest {
    private lateinit var logMock: MockedStatic<Log>
    private lateinit var pairMock: MockedStatic<Pair<*, *>>

    @Before
    fun setUp() {
        logMock = mockStatic(Log::class.java)
        pairMock = mockStatic(Pair::class.java)
        pairMock.`when`<Pair<Any?, Any?>> { Pair.create<Any?, Any?>(any(), any()) }
            .thenAnswer { pair(it.getArgument(0), it.getArgument(1)) }
    }

    @After
    fun tearDown() {
        pairMock.close()
        logMock.close()
    }

    @Test
    fun opusMetadataWithoutUploadDate() {
        val info = mock(StreamInfo::class.java)
        `when`(info.url).thenReturn("https://example.com/watch?v=1")
        `when`(info.category).thenReturn("Music")
        `when`(info.uploaderName).thenReturn("Uploader")
        `when`(info.name).thenReturn("Title")
        `when`(info.uploadDate).thenReturn(null)

        val header = OggFromWebMWriter.makeMetadata("A_OPUS", info)!!

        val text = String(header, Charsets.UTF_8)
        assertTrue(text.startsWith("OpusTags"))
        assertEquals(4, tagCount(header))
        assertTrue(text.contains("COMMENT=https://example.com/watch?v=1"))
        assertTrue(text.contains("TITLE=Title"))
        assertFalse(text.contains("DATE="))
    }

    @Test
    fun opusMetadataSkipsNullTextFields() {
        val info = mock(StreamInfo::class.java)
        `when`(info.url).thenReturn("https://example.com/watch?v=1")
        `when`(info.category).thenReturn(null)
        `when`(info.uploaderName).thenReturn(null)
        `when`(info.name).thenReturn("Title")
        `when`(info.uploadDate).thenReturn(
            DateWrapper(OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC))
        )

        val header = OggFromWebMWriter.makeMetadata("A_OPUS", info)!!

        val text = String(header, Charsets.UTF_8)
        assertEquals(3, tagCount(header))
        assertTrue(text.contains("DATE=2026-10-01"))
        assertFalse(text.contains("GENRE="))
        assertFalse(text.contains("ARTIST="))
    }

    // number of comments, stored after "OpusTags" and the empty vendor string
    private fun tagCount(header: ByteArray): Int = ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int

    // the stub constructor of Pair throws, so its final fields are set directly
    private fun pair(first: Any?, second: Any?): Pair<Any?, Any?> {
        @Suppress("UNCHECKED_CAST")
        val pair = mock(Pair::class.java) as Pair<Any?, Any?>
        Pair::class.java.getField("first").apply { isAccessible = true }.set(pair, first)
        Pair::class.java.getField("second").apply { isAccessible = true }.set(pair, second)
        return pair
    }
}
