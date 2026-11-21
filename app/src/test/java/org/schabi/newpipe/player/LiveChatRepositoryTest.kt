package org.schabi.newpipe.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class LiveChatRepositoryTest {
    @Test
    fun liveAndUpcomingYouTubeVideosExposeChatTabButOrdinaryVideosDoNot() {
        fun info(
            type: StreamType,
            upcoming: Boolean = false,
            service: Int = ServiceList.YouTube.serviceId
        ): StreamInfo {
            return StreamInfo(service, "test", "https://example.com/video", "Video").apply {
                streamType = type
                isUpcoming = upcoming
            }
        }

        assertTrue(LiveChatRepository.supports(info(StreamType.LIVE_STREAM)))
        assertTrue(LiveChatRepository.supports(info(StreamType.AUDIO_LIVE_STREAM)))
        assertTrue(LiveChatRepository.supports(info(StreamType.POST_LIVE_STREAM)))
        assertTrue(LiveChatRepository.supports(info(StreamType.VIDEO_STREAM, upcoming = true)))
        assertFalse(LiveChatRepository.supports(info(StreamType.VIDEO_STREAM)))
        assertFalse(LiveChatRepository.supports(info(StreamType.LIVE_STREAM, service = 99)))
    }

    @Test
    fun cancellingCollectionDisconnectsExtractor() = runBlocking {
        val extractor = mock(BulletCommentsExtractor::class.java)
        `when`(extractor.liveMessages).thenReturn(emptyList())
        assertTrue(LiveChatRepository.observe { extractor }.first().isSuccess)
        verify(extractor).disconnect()
    }

    @Test
    fun disabledChatReportsFailureAndDisconnects() = runBlocking {
        val extractor = mock(BulletCommentsExtractor::class.java)
        `when`(extractor.isDisabled).thenReturn(true)
        assertTrue(LiveChatRepository.observe { extractor }.first().isFailure)
        verify(extractor).disconnect()
    }

    @Test
    fun replayChatReceivesCurrentPlaybackPosition() = runBlocking {
        val extractor = mock(BulletCommentsExtractor::class.java)
        `when`(extractor.liveMessages).thenReturn(emptyList())

        assertTrue(LiveChatRepository.observe({ 42_000L }) { extractor }.first().isSuccess)

        verify(extractor).setCurrentPlayPosition(42_000L)
        verify(extractor).disconnect()
    }

    @Test
    fun replayChatPositionSourceCanSuspendWithoutBlockingExtraction() = runBlocking {
        val extractor = mock(BulletCommentsExtractor::class.java)
        `when`(extractor.liveMessages).thenReturn(emptyList())

        val result = LiveChatRepository.observe(
            positionProvider = {
                delay(1)
                3_500L
            },
            factory = { extractor }
        ).first()
        assertTrue(result.isSuccess)

        verify(extractor).setCurrentPlayPosition(3_500L)
        verify(extractor).disconnect()
    }
}
