/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod.DASH
import org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import us.shandian.giga.postprocessing.Postprocessing

class DownloadMissionRequestFactoryTest {
    @Test
    fun `selected video and audio keep separate header snapshots`() {
        val video = video(MediaFormat.MPEG_4, "video").apply { requestHeaders = mapOf("Authorization" to "video") }
        val audio = audio(MediaFormat.M4A, "audio").apply { requestHeaders = mapOf("Authorization" to "audio") }
        val request = DownloadMissionRequestFactory.forVideo(video, audio, 100, 50, 3)
        video.requestHeaders = mapOf("Authorization" to "changed")
        assertEquals(listOf("video", "audio"), request.requestHeaders!!.map { it["Authorization"] })
    }

    @Test
    fun `muxed audio downloads preserve original source headers`() {
        val fallback = video(MediaFormat.MPEG_4, "fallback").apply { requestHeaders = mapOf("Referer" to "https://source.example/") }
        val request = DownloadMissionRequestFactory.forAudio(audio(MediaFormat.M4A, "fallback"), fallback, false, 192, 3)
        assertEquals("https://source.example/", request.requestHeaders!!.single()["Referer"])
    }

    @Test
    fun `MP3 audio request enables transcoding`() {
        val request = DownloadMissionRequestFactory.forAudio(
            selectedStream = audio(MediaFormat.M4A, "audio"),
            muxedFallbackSource = null,
            mp3OutputSelected = true,
            mp3BitrateKbps = 256,
            threads = 4
        )

        assertArrayEquals(arrayOf("https://example.com/audio"), request.urls)
        assertEquals('a', request.kind)
        assertEquals(4, request.threads)
        assertEquals(Postprocessing.ALGORITHM_MP3_FROM_AUDIO, request.postprocessingName)
        assertArrayEquals(arrayOf("256"), request.postprocessingArguments)
    }

    @Test
    fun `muxed audio fallback uses video recovery metadata`() {
        val fallback = video(MediaFormat.MPEG_4, "fallback")
        val request = DownloadMissionRequestFactory.forAudio(
            selectedStream = audio(MediaFormat.M4A, "fallback"),
            muxedFallbackSource = fallback,
            mp3OutputSelected = false,
            mp3BitrateKbps = 192,
            threads = 3
        )

        assertEquals(
            Postprocessing.ALGORITHM_M4A_FROM_MP4_DEMUXER,
            request.postprocessingName
        )
        assertEquals('v', request.recoveryInfo.single().kind)
        assertEquals(MediaFormat.MPEG_4, request.recoveryInfo.single().format)
    }

    @Test
    fun `video request combines progressive secondary audio`() {
        val request = DownloadMissionRequestFactory.forVideo(
            selectedStream = video(MediaFormat.MPEG_4, "video"),
            secondaryStream = audio(MediaFormat.M4A, "audio"),
            videoSize = 1_000,
            secondarySize = 200,
            threads = 5
        )

        assertArrayEquals(
            arrayOf("https://example.com/video", "https://example.com/audio"),
            request.urls
        )
        assertEquals(
            Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER,
            request.postprocessingName
        )
        assertEquals(1_200, request.nearLength)
        assertEquals(listOf('v', 'a'), request.recoveryInfo.map { it.kind })
    }

    @Test
    fun `unknown video size keeps estimated length unset`() {
        val request = DownloadMissionRequestFactory.forVideo(
            selectedStream = video(MediaFormat.WEBM, "video"),
            secondaryStream = audio(MediaFormat.WEBMA, "audio"),
            videoSize = -1,
            secondarySize = 200,
            threads = 2
        )

        assertEquals(Postprocessing.ALGORITHM_WEBM_MUXER, request.postprocessingName)
        assertEquals(0, request.nearLength)
    }

    @Test
    fun `subtitle request uses one thread and TTML conversion`() {
        val request = DownloadMissionRequestFactory.forSubtitle(subtitle(MediaFormat.TTML))

        assertEquals('s', request.kind)
        assertEquals(1, request.threads)
        assertEquals(
            Postprocessing.ALGORITHM_TTML_CONVERTER,
            request.postprocessingName
        )
        assertArrayEquals(
            arrayOf(MediaFormat.TTML.getSuffix(), "false"),
            request.postprocessingArguments
        )
    }

    @Test
    fun `adaptive secondary audio is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadMissionRequestFactory.forVideo(
                selectedStream = video(MediaFormat.MPEG_4, "video"),
                secondaryStream = audio(MediaFormat.M4A, "audio", DASH),
                videoSize = 1_000,
                secondarySize = 200,
                threads = 3
            )
        }
    }

    private fun audio(
        format: MediaFormat,
        name: String,
        deliveryMethod: org.schabi.newpipe.extractor.stream.DeliveryMethod = PROGRESSIVE_HTTP
    ): AudioStream {
        return AudioStream.Builder()
            .setId(Stream.ID_UNKNOWN)
            .setContent("https://example.com/$name", true)
            .setMediaFormat(format)
            .setDeliveryMethod(deliveryMethod)
            .setAverageBitrate(192)
            .build()
    }

    private fun video(format: MediaFormat, name: String): VideoStream {
        return VideoStream.Builder()
            .setId(Stream.ID_UNKNOWN)
            .setContent("https://example.com/$name", true)
            .setMediaFormat(format)
            .setDeliveryMethod(PROGRESSIVE_HTTP)
            .setResolution("720p")
            .setIsVideoOnly(true)
            .build()
    }

    private fun subtitle(format: MediaFormat): SubtitlesStream {
        return SubtitlesStream.Builder()
            .setContent("https://example.com/subtitle", true)
            .setMediaFormat(format)
            .setLanguageCode("en")
            .setAutoGenerated(false)
            .build()
    }
}
