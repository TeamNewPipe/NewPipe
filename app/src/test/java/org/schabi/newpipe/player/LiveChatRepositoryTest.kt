package org.schabi.newpipe.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor

class LiveChatRepositoryTest {
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
}
