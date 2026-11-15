package org.schabi.newpipe.util

import android.view.View
import android.widget.HorizontalScrollView
import com.google.android.material.chip.ChipGroup
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ListStreamFilterChipsBinding

object StreamFilterControls {
    fun interface Listener {
        fun onChanged(selection: String)
    }

    @JvmStatic
    fun bind(binding: ListStreamFilterChipsBinding, saved: String, showContentTypes: Boolean, listener: Listener) {
        val selection = StreamFilterSelection.parse(saved)
        val content = binding.streamFilterChipGroup
        val watch = binding.watchFilterChipGroup
        (content.parent as View).visibility = if (showContentTypes) View.VISIBLE else View.GONE
        content.setOnCheckedStateChangeListener(null)
        watch.setOnCheckedStateChangeListener(null)
        content.clearCheck()
        watch.clearCheck()
        var updating = false

        fun restore(group: ChipGroup, allId: Int, filters: Set<StreamListFilter>) {
            if (filters.isEmpty()) group.check(allId) else filters.forEach { group.check(it.chipId) }
        }
        restore(content, R.id.filter_all, selection.contentFilters)
        restore(watch, R.id.filter_watch_all, selection.watchFilters)
        if (showContentTypes) {
            revealCheckedChip(content)
        }
        revealCheckedChip(watch)

        fun observe(group: ChipGroup, allId: Int) {
            var previous = group.checkedChipIds.toSet()
            group.setOnCheckedStateChangeListener { _, ids ->
                if (updating) return@setOnCheckedStateChangeListener
                updating = true
                if (allId in ids && allId !in previous) {
                    group.clearCheck()
                    group.check(allId)
                } else if (ids.any { it != allId }) {
                    group.findViewById<com.google.android.material.chip.Chip>(allId).isChecked = false
                } else if (ids.isEmpty()) {
                    group.check(allId)
                }
                previous = group.checkedChipIds.toSet()
                updating = false
                revealCheckedChip(group)
                val filters = (if (showContentTypes) content.checkedChipIds else emptyList()) + watch.checkedChipIds
                listener.onChanged(filters.map { StreamListFilter.fromChipId(it) }.filter { it != StreamListFilter.NONE }.joinToString(",") { it.name })
            }
        }
        observe(content, R.id.filter_all)
        observe(watch, R.id.filter_watch_all)
    }

    private fun revealCheckedChip(group: ChipGroup) {
        val scrollView = group.parent as? HorizontalScrollView ?: return
        val checkedId = group.checkedChipIds.lastOrNull() ?: return
        val checkedChip = group.findViewById<View>(checkedId) ?: return
        scrollView.post {
            val viewportStart = scrollView.scrollX
            val viewportEnd = viewportStart + scrollView.width
            val chipStart = checkedChip.left
            val chipEnd = checkedChip.right + group.paddingEnd
            when {
                chipStart < viewportStart -> scrollView.smoothScrollTo(chipStart, 0)

                chipEnd > viewportEnd ->
                    scrollView.smoothScrollTo((chipEnd - scrollView.width).coerceAtLeast(0), 0)
            }
        }
    }
}
