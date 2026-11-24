package org.schabi.newpipe.fragments.list.comments

import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.search.ContextualSearchHelper

/** Keeps extractor pages intact so clearing a query restores every loaded comment. */
class CommentSearchController {
    private val loaded = mutableListOf<CommentsInfoItem>()
    var query: String = ""
    var expanded: Boolean = false
    var pinnedOnly: Boolean = false
    private var root: View? = null

    fun initial(items: List<CommentsInfoItem>) {
        if (loaded.isEmpty()) loaded.addAll(items)
    }

    fun append(items: List<CommentsInfoItem>) {
        loaded.addAll(items)
    }
    fun clearItems() {
        loaded.clear()
    }
    fun snapshot(): List<CommentsInfoItem> = ArrayList(loaded)
    fun restore(items: List<CommentsInfoItem>) {
        loaded.clear()
        loaded.addAll(items)
    }
    fun matches(): List<CommentsInfoItem> = loaded.filter {
        (!pinnedOnly || it.isPinned) &&
            ContextualSearchHelper.matches(query, it.commentTextContent, it.uploaderName)
    }

    fun bind(view: View, changed: Runnable, loadMore: Runnable) {
        root = view
        val field = view.findViewById<TextInputLayout>(R.id.comments_search_field)
        field.isVisible = expanded
        val input = requireNotNull(field.editText)
        input.setText(query)
        input.doAfterTextChanged {
            query = it?.toString().orEmpty()
            changed.run()
        }
        view.findViewById<View>(R.id.comments_search_toggle).setOnClickListener {
            expanded = !expanded
            field.isVisible = expanded
            if (expanded) input.requestFocus() else input.setText("")
            changed.run()
        }
        view.findViewById<View>(R.id.comments_load_more).setOnClickListener { loadMore.run() }
    }

    fun updatePagination(hasMore: Boolean, loading: Boolean) {
        root?.findViewById<View>(R.id.comments_load_more)?.apply {
            isVisible = expanded && hasMore
            isEnabled = !loading
        }
    }

    fun paginationFinished(hasMore: Boolean) {
        val view = root ?: return
        view.post { if (root === view) updatePagination(hasMore, false) }
    }

    fun unbind() {
        root = null
    }
}
