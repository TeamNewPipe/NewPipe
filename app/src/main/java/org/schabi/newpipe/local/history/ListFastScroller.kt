package org.schabi.newpipe.local.history

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

/**
 * Reuses the History fast-scroller for large virtualized lists.
 * Only visible adapter positions are referenced; no full-list copy or extra database query.
 */
class ListFastScroller private constructor(
    private val list: RecyclerView,
    private val track: HistoryDateFastScroller,
    private val added: Boolean,
    private val originalScrollBar: Boolean
) : AutoCloseable {
    private val adapter = requireNotNull(list.adapter)
    private var disposed = false
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = update()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = update()
    }
    private val scroll = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = update()
    }
    private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }

    init {
        track.setOnPositionChangedListener { pos -> list.scrollToPosition(pos) }
        track.setLabelProvider { pos -> label(pos, adapter.itemCount) }
        track.setOnDragStateChangedListener { dragging ->
            if (!dragging) list.post { update() }
        }
        adapter.registerAdapterDataObserver(observer)
        list.addOnScrollListener(scroll)
        list.addOnLayoutChangeListener(layout)
        update()
    }

    private fun update() {
        if (disposed) return
        val count = adapter.itemCount
        val visible = shouldEnable(count) && list.visibility == View.VISIBLE
        track.visibility = if (visible) View.VISIBLE else View.GONE
        list.isVerticalScrollBarEnabled = !visible
        track.setItemCount(count)
        val position = (list.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.coerceAtLeast(0) ?: 0
        if (!track.isDragging) track.setPosition(position)
    }

    override fun close() {
        if (disposed) return
        disposed = true
        adapter.unregisterAdapterDataObserver(observer)
        list.removeOnScrollListener(scroll)
        list.removeOnLayoutChangeListener(layout)
        list.isVerticalScrollBarEnabled = originalScrollBar
        track.dismissBubble()
        track.setOnPositionChangedListener(null)
        track.setOnDragStateChangedListener(null)
        track.setLabelProvider(null)
        if (added) {
            (track.parent as? ViewGroup)?.removeView(track)
        } else {
            track.visibility = View.GONE
        }
    }

    companion object {
        private const val MIN_ITEMS = 50

        internal fun shouldEnable(count: Int): Boolean = count >= MIN_ITEMS

        internal fun label(position: Int, count: Int): String {
            return "${(position + 1).coerceIn(0, count)} / $count"
        }

        @JvmStatic
        fun attach(list: RecyclerView): ListFastScroller? {
            if (list.adapter == null) return null
            val parent = list.parent as? ViewGroup ?: return null
            val existing = parent.findViewById<HistoryDateFastScroller>(
                R.id.history_date_fast_scroller
            )
            val track = existing ?: HistoryDateFastScroller(list.context)
            val added = existing == null
            if (added) {
                val width = (48 * list.resources.displayMetrics.density + 0.5f).toInt()
                val params = when (parent) {
                    is RelativeLayout -> RelativeLayout.LayoutParams(width, 0).apply {
                        addRule(RelativeLayout.ALIGN_TOP, list.id)
                        addRule(RelativeLayout.ALIGN_BOTTOM, list.id)
                        addRule(RelativeLayout.ALIGN_PARENT_END)
                    }

                    is FrameLayout -> FrameLayout.LayoutParams(
                        width,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END
                    )

                    else -> return null
                }
                parent.addView(track, params)
            }
            return ListFastScroller(list, track, added, list.isVerticalScrollBarEnabled)
        }
    }
}
