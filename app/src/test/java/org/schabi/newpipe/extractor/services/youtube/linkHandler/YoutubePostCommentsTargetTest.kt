package org.schabi.newpipe.extractor.services.youtube.linkHandler

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeCommentsExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsExtractor
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousCommentsExtractor
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousPostCommentsExtractor

class YoutubePostCommentsTargetTest {
    private val postId = "Ugkx-post-discussion-id"
    private val canonical = "https://www.youtube.com/post/$postId"
    private val factory = YoutubeCommentsLinkHandlerFactory.getInstance()

    @After
    fun restoreBackend() {
        InvidiousBackend.configure(false, "")
    }

    @Test
    fun canonicalizesPostTargetsWithoutTreatingTheirIdsAsVideos() {
        for (url in listOf(canonical, "$canonical?lc=comment#reply", "https://m.youtube.com/post/$postId/")) {
            val handler = factory.fromUrl(url)
            assertEquals("post:$postId", handler.id)
            assertEquals(canonical, handler.url)
            assertEquals(canonical, factory.fromId(handler.id).url)
            assertTrue(factory.acceptUrl(url))
        }
    }

    @Test
    fun rejectsOtherHostsAndMalformedPostIdentities() {
        for (url in listOf(
            "https://youtube.com.example.org/post/$postId",
            "https://example.org/post/$postId",
            "https://www.youtube.com/post/",
            "https://www.youtube.com/post/$postId/extra",
            "https://www.youtube.com/post/$postId%2Fextra",
            "https://user@www.youtube.com/post/$postId",
            "ftp://www.youtube.com/post/$postId",
            "https://www.youtube.com:444/post/$postId"
        )) {
            assertNull(url, YoutubePostCommentsTarget.fromUrl(url))
        }
        assertNull(YoutubePostCommentsTarget.fromId(postId))
        assertNull(YoutubePostCommentsTarget.fromPostId("not-a-post"))
    }

    @Test
    fun preservesExistingVideoLinkHandling() {
        val id = "BaW_jenozKc"
        val handler = factory.fromUrl("https://youtu.be/$id?lc=comment")
        assertEquals(id, handler.id)
        assertEquals("https://www.youtube.com/watch?v=$id", handler.url)
        assertNull(YoutubePostCommentsTarget.fromUrl(handler.url))
        assertFalse(factory.acceptUrl("https://example.org/post/$postId"))
    }

    @Test
    fun selectsPostAndVideoExtractorsWithinTheChosenBackend() {
        InvidiousBackend.configure(false, "")
        assertTrue(ServiceList.YouTube.getCommentsExtractor(canonical) is YoutubePostCommentsExtractor)
        assertEquals(
            YoutubeCommentsExtractor::class.java,
            ServiceList.YouTube.getCommentsExtractor("https://www.youtube.com/watch?v=BaW_jenozKc").javaClass
        )
        InvidiousBackend.configure(true, "https://example.org")
        assertTrue(ServiceList.YouTube.getCommentsExtractor(canonical) is InvidiousPostCommentsExtractor)
        assertEquals(
            InvidiousCommentsExtractor::class.java,
            ServiceList.YouTube.getCommentsExtractor("https://www.youtube.com/watch?v=BaW_jenozKc").javaClass
        )
    }
}
