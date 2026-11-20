package org.schabi.newpipe.fragments.list.comments

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

class CommentSearchControllerTest {
    @Test
    fun matchesTextOrAuthorAndClearingRestoresEveryLoadedPage() {
        val first = comment("An interesting VIDEO", "Alice")
        val second = comment("مرحبا بالعالم", "Bob")
        val state = CommentSearchController()
        state.initial(listOf(first))
        state.query = "bob"
        assertEquals(emptyList<CommentsInfoItem>(), state.matches())
        state.append(listOf(second))
        assertEquals(listOf(second), state.matches())
        state.query = " video "
        assertEquals(listOf(first), state.matches())
        state.query = "مرحبا"
        assertEquals(listOf(second), state.matches())
        state.query = ""
        assertEquals(listOf(first, second), state.matches())
    }

    @Test
    fun restoringFilteredHistoryKeepsUnfilteredCommentsAndSortingReloadDropsOldPages() {
        val state = CommentSearchController()
        val first = comment("First", "Author")
        val second = comment("Second", "Author")
        state.initial(listOf(first))
        state.append(listOf(second))
        state.query = "Second"
        val restored = CommentSearchController()
        restored.restore(state.snapshot())
        restored.initial(listOf(first))
        assertEquals(listOf(first, second), restored.matches())
        restored.clearItems()
        restored.initial(listOf(second))
        assertEquals(listOf(second), restored.matches())
    }

    @Test
    fun pinnedOnlyFiltersAllLoadedPagesAndCanBeCleared() {
        val pinned = comment("Pinned", "Creator").apply { isPinned = true }
        val normal = comment("Normal", "Viewer")
        val state = CommentSearchController()
        state.initial(listOf(pinned))
        state.append(listOf(normal))
        state.pinnedOnly = true
        assertEquals(listOf(pinned), state.matches())
        state.query = "normal"
        assertEquals(emptyList<CommentsInfoItem>(), state.matches())
        state.query = ""
        state.pinnedOnly = false
        assertEquals(listOf(pinned, normal), state.matches())
    }

    private fun comment(text: String, author: String) = CommentsInfoItem(0, "https://example.org/$author", text).apply {
        commentText = text
        uploaderName = author
    }
}
