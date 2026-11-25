package org.schabi.newpipe.util

import android.graphics.Rect
import android.view.View
import android.widget.HorizontalScrollView
import androidx.core.view.doOnLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ListStreamFilterChipsBinding

object StreamFilterControls {
    fun interface Listener {
        fun onChanged(selection: String)
    }

    @JvmStatic
    @JvmOverloads
    fun bind(
        binding: ListStreamFilterChipsBinding,
        saved: String,
        showContentTypes: Boolean,
        showAudio: Boolean = false,
        showHistoryDetails: Boolean = false,
        showHistoryCategories: Boolean = false,
        listener: Listener
    ) {
        val selection = StreamFilterSelection.parse(saved)
        val content = binding.streamFilterChipGroup
        val watch = binding.watchFilterChipGroup
        val downloaded = binding.filterDownloaded
        binding.filterAudio.visibility = if (showAudio) View.VISIBLE else View.GONE
        val historyVisibility = if (showHistoryDetails) View.VISIBLE else View.GONE
        binding.filterLiveAudio.visibility = historyVisibility
        binding.filterPostLiveVideo.visibility = historyVisibility
        binding.filterPostLiveAudio.visibility = historyVisibility
        val categoryVisibility = if (showHistoryCategories) View.VISIBLE else View.GONE
        binding.filterTracks.visibility = categoryVisibility
        binding.filterPodcasts.visibility = categoryVisibility
        binding.filterCourses.visibility = categoryVisibility
        (content.parent as View).visibility = if (showContentTypes) View.VISIBLE else View.GONE
        content.setOnCheckedStateChangeListener(null)
        watch.setOnCheckedStateChangeListener(null)
        downloaded.setOnCheckedChangeListener(null)
        downloaded.isChecked = selection.downloadedOnly
        content.clearCheck()
        watch.clearCheck()
        var updating = false

        fun restore(group: ChipGroup, allId: Int, filters: Set<StreamListFilter>) {
            if (filters.isEmpty()) group.check(allId) else filters.forEach { group.check(it.chipId) }
        }
        restore(content, R.id.filter_all, selection.contentFilters)
        restore(watch, R.id.filter_watch_all, selection.watchFilters)
        if (showContentTypes) {
            revealCheckedChip(content, smooth = false)
        }
        revealCheckedChip(watch, smooth = false)
        if (downloaded.isChecked) {
            revealChip(downloaded, smooth = false)
        }

        fun notifySelection() {
            val ids = (if (showContentTypes) content.checkedChipIds else emptyList()) +
                watch.checkedChipIds
            val filters = ids.map { StreamListFilter.fromChipId(it) }
                .filter { it != StreamListFilter.NONE } +
                (if (downloaded.isChecked) listOf(StreamListFilter.DOWNLOADED) else emptyList())
            listener.onChanged(filters.joinToString(",") { it.name })
        }

        fun observe(group: ChipGroup, allId: Int) {
            var previous = group.checkedChipIds.toSet()
            group.setOnCheckedStateChangeListener { _, ids ->
                if (updating) return@setOnCheckedStateChangeListener
                updating = true
                if (allId in ids && allId !in previous) {
                    group.clearCheck()
                    group.check(allId)
                } else if (ids.any { it != allId }) {
                    group.findViewById<Chip>(allId).isChecked = false
                } else if (ids.isEmpty()) {
                    group.check(allId)
                }
                previous = group.checkedChipIds.toSet()
                updating = false
                revealCheckedChip(group, smooth = true)
                notifySelection()
            }
        }
        observe(content, R.id.filter_all)
        observe(watch, R.id.filter_watch_all)
        downloaded.setOnCheckedChangeListener { _, checked ->
            if (checked) revealChip(downloaded, smooth = true)
            notifySelection()
        }
    }

    private fun revealCheckedChip(group: ChipGroup, smooth: Boolean) {
        val checkedId = group.checkedChipIds.lastOrNull() ?: return
        val checkedChip = group.findViewById<Chip>(checkedId) ?: return
        revealChip(checkedChip, smooth)
    }

    private fun revealChip(chip: Chip, smooth: Boolean) {
        val scrollView = generateSequence(chip.parent) { it.parent }
            .filterIsInstance<HorizontalScrollView>().firstOrNull() ?: return
        scrollView.doOnLayout {
            if (!chip.isChecked) return@doOnLayout
            val bounds = Rect()
            chip.getDrawingRect(bounds)
            scrollView.offsetDescendantRectToMyCoords(chip, bounds)
            val viewportStart = scrollView.scrollX + scrollView.paddingLeft
            val viewportEnd = scrollView.scrollX + scrollView.width - scrollView.paddingRight
            val target = when {
                bounds.width() > viewportEnd - viewportStart -> {
                    // An oversized label should expose its logical start in either layout direction.
                    if (scrollView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        bounds.right - scrollView.width + scrollView.paddingRight
                    } else {
                        bounds.left - scrollView.paddingLeft
                    }
                }

                bounds.left < viewportStart -> bounds.left - scrollView.paddingLeft

                bounds.right > viewportEnd -> bounds.right - scrollView.width + scrollView.paddingRight

                else -> return@doOnLayout
            }.coerceAtLeast(0)
            if (smooth) {
                scrollView.smoothScrollTo(target, scrollView.scrollY)
            } else {
                scrollView.scrollTo(target, scrollView.scrollY)
            }
        }
    }
}
