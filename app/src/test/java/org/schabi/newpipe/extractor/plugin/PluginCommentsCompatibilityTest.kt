/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector
import org.schabi.newpipe.extractor.stream.Description

class PluginCommentsCompatibilityTest {
    @Test
    fun commentsWrittenBeforeTheDescriptionApiKeepTheirTextAndMetadata() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/plugin-fixtures/legacy-comment-v1.51.ser"))
        val item = ObjectInputStream(stream).use { it.readObject() as CommentsInfoItem }
        assertEquals(5981935524260474052L, ObjectStreamClass.lookup(CommentsInfoItem::class.java).serialVersionUID)
        assertEquals("A legacy comment\nwith <text> & symbols", item.commentTextContent)
        assertEquals(Description(item.commentTextContent, Description.PLAIN_TEXT), item.commentText)
        assertEquals("legacy-123", item.commentId)
        assertEquals("Legacy author", item.uploaderName)
        assertEquals(12, item.likeCount)
        assertEquals(7, item.serviceId)
        assertTrue(item.isPinned)
    }

    @Test
    fun existingProviderTextIsCollectedAsPlainTextWithoutChangingItsContent() {
        val extractor = object : CommentsInfoItemExtractor {
            override fun getName(): String = "Comment"
            override fun getUrl(): String = "https://fixture.example/watch"
            override fun getCommentTextContent(): String = "Plain <text>\n& symbols"
        }
        val collector = CommentsInfoItemsCollector(12345)
        collector.commit(extractor)
        assertTrue(collector.errors.isEmpty())
        assertEquals(Description("Plain <text>\n& symbols", Description.PLAIN_TEXT), collector.items.single().commentText)
    }

    @Test
    fun aForeignDescriptionImplementationRetainsFormattingThroughTheHostCollector() {
        val description = Description("<p>A <b>formatted</b> comment</p>", Description.HTML)
        val fixture = object : CommentsInfoItemExtractor {
            override fun getName(): String = "Comment"
            override fun getUrl(): String = "https://fixture.example/watch"
            override fun getCommentText(): Description = description
        }
        val loader = object : ClassLoader(CommentsInfoItemExtractor::class.java.classLoader) {}
        val external = Proxy.newProxyInstance(loader, arrayOf(CommentsInfoItemExtractor::class.java)) { _, method, arguments ->
            method.invoke(fixture, *(arguments ?: emptyArray()))
        } as CommentsInfoItemExtractor
        val collector = CommentsInfoItemsCollector(12345)
        collector.commit(external)
        assertTrue(collector.errors.isEmpty())
        assertEquals(description, collector.items.single().commentText)
        assertEquals(description.content, collector.items.single().commentTextContent)
    }

    @Test
    fun descriptionTypesSurviveSerializationAndTheStringSetterResetsPlainText() {
        for (type in listOf(Description.HTML, Description.MARKDOWN)) {
            val item = CommentsInfoItem(12345, "https://fixture.example/watch", "Comment")
            val description = Description("A **formatted** <b>comment</b>", type)
            item.setCommentText(description)
            val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(item) } }.toByteArray()
            val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as CommentsInfoItem }
            assertEquals(description, restored.commentText)
            restored.setCommentText("Plain replacement")
            assertEquals(Description("Plain replacement", Description.PLAIN_TEXT), restored.commentText)
        }
    }
}
