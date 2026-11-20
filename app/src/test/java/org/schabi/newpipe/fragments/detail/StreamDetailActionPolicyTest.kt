package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamDetailActionPolicyTest {
    private fun info(type: StreamType): StreamInfo = StreamInfo(
        0,
        "test",
        "https://example.com/watch",
        "Sample"
    ).apply { streamType = type }

    @Test
    fun manifestOnlyLiveStreamsKeepPopupAndAudioOptionsWithoutFalseDownload() {
        val active = info(StreamType.LIVE_STREAM).apply {
            hlsUrl = "https://example.com/live.m3u8"
        }
        val finished = info(StreamType.POST_LIVE_STREAM).apply {
            dashMpdUrl = "https://example.com/replay.mpd"
        }

        listOf(active, finished).forEach { stream ->
            val actions = StreamDetailActionPolicy.forStream(stream)
            assertTrue(actions.showPopup)
            assertTrue(actions.showBackground)
            assertFalse(actions.showDownload)
        }
    }

    @Test
    fun upcomingBroadcastCannotStartOrDownloadUntilAvailable() {
        val upcoming = info(StreamType.LIVE_STREAM).apply {
            isUpcoming = true
            hlsUrl = "https://example.com/live.m3u8"
        }
        val actions = StreamDetailActionPolicy.forStream(upcoming)
        assertFalse(actions.showPopup)
        assertFalse(actions.showBackground)
        assertFalse(actions.showDownload)
    }

    @Test
    fun endedBroadcastWithoutDownloadableFilesDoesNotShowEmptyDownloadDialog() {
        val actions = StreamDetailActionPolicy.forStream(info(StreamType.POST_LIVE_STREAM))
        assertFalse(actions.showDownload)
        assertFalse(actions.showPopup)
        assertFalse(actions.showBackground)
    }

    @Test
    fun progressiveVideoOffersPlaybackAndDownload() {
        val progressive = mock(VideoStream::class.java)
        `when`(progressive.isUrl).thenReturn(true)
        `when`(progressive.deliveryMethod).thenReturn(DeliveryMethod.PROGRESSIVE_HTTP)
        val video = info(StreamType.VIDEO_STREAM).apply {
            videoStreams = listOf(progressive)
        }

        val actions = StreamDetailActionPolicy.forStream(video)
        assertTrue(actions.showPopup)
        assertTrue(actions.showBackground)
        assertTrue(actions.showDownload)
    }

    @Test
    fun nonUrlProgressiveSourceCannotBeDownloaded() {
        val source = mock(AudioStream::class.java)
        `when`(source.deliveryMethod).thenReturn(DeliveryMethod.PROGRESSIVE_HTTP)
        val video = info(StreamType.POST_LIVE_STREAM).apply {
            audioStreams = listOf(source)
        }

        val actions = StreamDetailActionPolicy.forStream(video)
        assertTrue(actions.showBackground)
        assertFalse(actions.showDownload)
    }

    @Test
    fun audioOnlyPlaybackDoesNotInventPopupVideo() {
        val audio = mock(AudioStream::class.java)
        `when`(audio.isUrl).thenReturn(true)
        `when`(audio.deliveryMethod).thenReturn(DeliveryMethod.PROGRESSIVE_HTTP)
        val stream = info(StreamType.AUDIO_STREAM).apply {
            audioStreams = listOf(audio)
        }

        val actions = StreamDetailActionPolicy.forStream(stream)
        assertTrue(actions.showBackground)
        assertFalse(actions.showPopup)
        assertTrue(actions.showDownload)
    }
}
