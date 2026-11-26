/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Measures the actual leading group rows before constraining the inner scrolling viewport. */
class GroupRowsRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var measuredKey: List<Any>? = null
    private var rowsHeight = 0
    private var observedAdapter: Adapter<*>? = null
    private var touchX = 0f
    private var touchY = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var bounded = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == SubscriptionGroupRows.KEY) invalidateRows()
    }
    private val dataObserver = object : AdapterDataObserver() {
        override fun onChanged() = invalidateRows()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = invalidateRows()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = invalidateRows()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidateRows()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidateRows()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = invalidateRows()
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        observe(null)
        super.setAdapter(adapter)
        observe(adapter)
        invalidateRows()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observe(adapter)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        invalidateRows()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        observe(null)
        parent?.requestDisallowInterceptTouchEvent(false)
        super.onDetachedFromWindow()
    }

    private fun observe(next: Adapter<*>?) {
        if (next === observedAdapter) return
        observedAdapter?.unregisterAdapterDataObserver(dataObserver)
        observedAdapter = next
        next?.registerAdapterDataObserver(dataObserver)
    }

    private fun invalidateRows() {
        measuredKey = null
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val rows = SubscriptionGroupRows.get(context)
        val columns = (layoutManager as? GridLayoutManager)?.spanCount ?: 1
        val itemCount = adapter?.itemCount ?: 0
        val width = MeasureSpec.getSize(widthSpec)
        bounded = rows != SubscriptionGroupRows.UNLIMITED && itemCount > rows * columns && width > 0
        isVerticalScrollBarEnabled = bounded
        if (!bounded) {
            super.onMeasure(widthSpec, heightSpec)
            return
        }

        val key = listOf(width, rows, columns, itemCount, layoutDirection, resources.configuration.fontScale)
        if (measuredKey != key) {
            rowsHeight = measureRows(rows, columns, width)
            measuredKey = key
        }
        val limit = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) {
            rowsHeight
        } else {
            min(rowsHeight, MeasureSpec.getSize(heightSpec))
        }
        // An exact viewport makes RecyclerView lay out and recycle only the visible rows.
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit.coerceAtLeast(1), MeasureSpec.EXACTLY))
    }

    @Suppress("UNCHECKED_CAST")
    private fun measureRows(rows: Int, columns: Int, width: Int): Int {
        val currentAdapter = adapter as? Adapter<ViewHolder> ?: return 0
        val cellWidth = ((width - paddingLeft - paddingRight) / columns).coerceAtLeast(1)
        var height = paddingTop + paddingBottom
        var rowHeight = 0
        for (position in 0 until min(currentAdapter.itemCount, rows * columns)) {
            // These are detached measurement holders, never inserted into the visible adapter.
            // Binding real items includes the Create/Import shortcuts and multiline grid titles.
            val holder = currentAdapter.createViewHolder(this, currentAdapter.getItemViewType(position))
            try {
                currentAdapter.bindViewHolder(holder, position)
                val child = holder.itemView
                val params = child.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: ViewGroup.MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                child.measure(
                    ViewGroup.getChildMeasureSpec(
                        MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                        params.leftMargin + params.rightMargin,
                        params.width
                    ),
                    ViewGroup.getChildMeasureSpec(
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        params.topMargin + params.bottomMargin,
                        params.height
                    )
                )
                rowHeight = max(rowHeight, child.measuredHeight + params.topMargin + params.bottomMargin)
                if ((position + 1) % columns == 0) {
                    height += rowHeight
                    rowHeight = 0
                }
            } finally {
                currentAdapter.onViewRecycled(holder)
            }
        }
        return height + rowHeight
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // RecyclerView can claim interception while consuming the last pixels of a drag.
        // Decide who handles its next movement only after it has processed this event.
        val handled = super.dispatchTouchEvent(event)
        if (bounded) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.x
                    touchY = event.y
                    lastTouchY = event.y
                    parent?.requestDisallowInterceptTouchEvent(canScrollVertically(-1) || canScrollVertically(1))
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - touchY
                    val dx = event.x - touchX
                    if (abs(dy) > touchSlop || abs(dx) > touchSlop) {
                        val direction = if (event.y < lastTouchY) 1 else -1
                        parent?.requestDisallowInterceptTouchEvent(abs(dy) > abs(dx) && canScrollVertically(direction))
                    }
                    lastTouchY = event.y
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
}
