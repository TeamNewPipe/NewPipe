package org.schabi.newpipe.local.history

import android.content.SharedPreferences
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.preference.PreferenceManager
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
    private val originalScrollBar: Boolean,
    private val hideWhileFirstItemVisible: Boolean
) : AutoCloseable {
    private val adapter = requireNotNull(list.adapter)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(list.context.applicationContext)
    private val visibilityKey = list.context.getString(R.string.show_list_scrollbar_key)
    private var disposed = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == visibilityKey) list.post { update() }
    }
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
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        update()
    }

    private fun update() {
        if (disposed) return
        val count = adapter.itemCount
        val position = (list.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.coerceAtLeast(0) ?: 0
        val enabled = preferences.getBoolean(visibilityKey, true)
        val visible = enabled && shouldShow(count, position, hideWhileFirstItemVisible) &&
            list.visibility == View.VISIBLE
        if (!visible) {
            if (track.isDragging) track.parent?.requestDisallowInterceptTouchEvent(false)
            track.dismissBubble()
        }
        track.visibility = if (visible) View.VISIBLE else View.GONE
        list.isVerticalScrollBarEnabled = enabled && !visible && originalScrollBar
        track.setItemCount(count)
        if (!track.isDragging) track.setPosition(position)
    }

    override fun close() {
        if (disposed) return
        disposed = true
        adapter.unregisterAdapterDataObserver(observer)
        list.removeOnScrollListener(scroll)
        list.removeOnLayoutChangeListener(layout)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
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

        internal fun shouldShow(count: Int, firstVisiblePosition: Int, avoidFirstItem: Boolean): Boolean {
            return shouldEnable(count) && (!avoidFirstItem || firstVisiblePosition > 0)
        }

        internal fun label(position: Int, count: Int): String {
            return "${(position + 1).coerceIn(0, count)} / $count"
        }

        @JvmStatic
        @JvmOverloads
        fun attach(
            list: RecyclerView,
            topClearanceDp: Int = 0,
            hideWhileFirstItemVisible: Boolean = false
        ): ListFastScroller? {
            if (list.adapter == null) return null
            val parent = list.parent as? ViewGroup ?: return null
            val existing = parent.findViewById<HistoryDateFastScroller>(
                R.id.history_date_fast_scroller
            )
            val track = existing ?: HistoryDateFastScroller(list.context)
            val added = existing == null
            if (added) {
                val density = list.resources.displayMetrics.density
                val width = (48 * density + 0.5f).toInt()
                // On subscription lists, the full-width drag surface otherwise covers
                // the collapsed channel-groups expand button in the first header row.
                val topClearance = (topClearanceDp.coerceAtLeast(0) * density + 0.5f).toInt()
                val params = when (parent) {
                    is RelativeLayout -> RelativeLayout.LayoutParams(width, 0).apply {
                        addRule(RelativeLayout.ALIGN_TOP, list.id)
                        addRule(RelativeLayout.ALIGN_BOTTOM, list.id)
                        addRule(RelativeLayout.ALIGN_PARENT_END)
                        topMargin = topClearance
                    }

                    is FrameLayout -> FrameLayout.LayoutParams(
                        width,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END
                    ).apply { topMargin = topClearance }

                    else -> return null
                }
                parent.addView(track, params)
            }
            return ListFastScroller(
                list,
                track,
                added,
                list.isVerticalScrollBarEnabled,
                hideWhileFirstItemVisible
            )
        }
    }
}
