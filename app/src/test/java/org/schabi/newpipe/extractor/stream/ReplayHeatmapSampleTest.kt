/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.stream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ParsingException

class ReplayHeatmapSampleTest {
    @Test
    fun sampleKeepsMillisecondBoundsAndIntensityWhenSerialized() {
        val sample = ReplayHeatmapSample(1234, 2345, 0.75f)
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(sample) }
        val restored = ObjectInputStream(ByteArrayInputStream(output.toByteArray())).use { it.readObject() }
        assertEquals(sample, restored)
    }

    @Test
    fun streamInfoStartsEmptyAndOwnsAnImmutableSnapshotOfSamples() {
        val info = StreamInfo()
        assertTrue(info.replayHeatmap.isEmpty())
        val sample = ReplayHeatmapSample(0, 1000, 0.5f)
        val input = mutableListOf(sample)
        info.setReplayHeatmap(input)
        input.clear()

        assertEquals(listOf(sample), info.replayHeatmap)
        assertThrows(UnsupportedOperationException::class.java) {
            (info.replayHeatmap as MutableList<ReplayHeatmapSample>).clear()
        }
    }

    @Test
    fun optionalHeatmapFailureDoesNotStopManifestPlayback() {
        val extractor = mock(StreamExtractor::class.java)
        `when`(extractor.service).thenReturn(mock(StreamingService::class.java))
        `when`(extractor.url).thenReturn("https://example.com/video")
        `when`(extractor.originalUrl).thenReturn("https://example.com/video")
        `when`(extractor.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(extractor.id).thenReturn("video")
        `when`(extractor.name).thenReturn("Video")
        `when`(extractor.hlsUrl).thenReturn("https://example.com/video.m3u8")
        val samples = listOf(ReplayHeatmapSample(0, 1000, 0.5f))
        `when`(extractor.replayHeatmap).thenReturn(samples)
        assertEquals(samples, StreamInfo.getInfo(extractor).replayHeatmap)

        val failure = ParsingException("Optional replay data is malformed")
        `when`(extractor.replayHeatmap).thenThrow(failure)
        val info = StreamInfo.getInfo(extractor)
        assertEquals("https://example.com/video.m3u8", info.hlsUrl)
        assertTrue(info.replayHeatmap.isEmpty())
        assertTrue(info.errors.contains(failure))
    }
}
