package org.schabi.newpipe.local.comments

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentArchivePolicyTest {
    private fun item(id: String, text: String, parent: String? = null): CachedComment {
        return CachedComment(id, "Author", text, "2026-11-21", parent)
    }

    @Test
    fun archivesOnlyUniqueVisibleCommentsAndRepliesWithinBudget() {
        val original = listOf(item("1", "First"))
        val result = CommentArchive.merge(
            original,
            listOf(item("1", "Updated"), item("2", "Reply", "1")),
            2
        )
        assertEquals(2, result.size)
        assertEquals("Updated", result[0].body)
        assertEquals("1", result[1].parentId)
    }

    @Test
    fun capsSnapshotSize() {
        val received = (0 until 200).map { item(it.toString(), "Body") }
        assertEquals(100, CommentArchive.merge(emptyList(), received, 100).size)
    }

    @Test
    fun replyIdsAreScopedToParentComments() {
        val result = CommentArchive.merge(
            emptyList(),
            listOf(item("42", "Reply A", "parent1"), item("42", "Reply B", "parent2")),
            100
        )
        assertEquals(2, result.size)
    }
}
