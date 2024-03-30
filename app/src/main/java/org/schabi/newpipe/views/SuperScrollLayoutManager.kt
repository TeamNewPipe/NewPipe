/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * SuperScrollLayoutManager.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SuperScrollLayoutManager(context: Context?) : LinearLayoutManager(context) {
    private val handy: Rect = Rect()
    private val focusables: ArrayList<View> = ArrayList()
    public override fun requestChildRectangleOnScreen(parent: RecyclerView,
                                                      child: View,
                                                      rect: Rect,
                                                      immediate: Boolean,
                                                      focusedChildVisible: Boolean): Boolean {
        if (!parent.isInTouchMode()) {
            // only activate when in directional navigation mode (Android TV etc) — fine grained
            // touch scrolling is better served by nested scroll system
            if (!focusedChildVisible || getFocusedChild() === child) {
                handy.set(rect)
                parent.offsetDescendantRectToMyCoords(child, handy)
                parent.requestRectangleOnScreen(handy, immediate)
            }
        }
        return super.requestChildRectangleOnScreen(parent, child, rect, immediate,
                focusedChildVisible)
    }

    public override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        val focusedItem: View? = findContainingItemView(focused)
        if (focusedItem == null) {
            return super.onInterceptFocusSearch(focused, direction)
        }
        val listDirection: Int = getAbsoluteDirection(direction)
        if (listDirection == 0) {
            return super.onInterceptFocusSearch(focused, direction)
        }

        // FocusFinder has an oddity: it considers size of Views more important
        // than closeness to source View. This means, that big Views far away from current item
        // are preferred to smaller sub-View of closer item. Setting focusability of closer item
        // to FOCUS_AFTER_DESCENDANTS does not solve this, because ViewGroup#addFocusables omits
        // such parent itself from list, if any of children are focusable.
        // Fortunately we can intercept focus search and implement our own logic, based purely
        // on position along the LinearLayoutManager axis
        val recycler: ViewGroup = focusedItem.getParent() as ViewGroup
        val sourcePosition: Int = getPosition(focusedItem)
        if (sourcePosition == 0 && listDirection < 0) {
            return super.onInterceptFocusSearch(focused, direction)
        }
        var preferred: View? = null
        var distance: Int = Int.MAX_VALUE
        focusables.clear()
        recycler.addFocusables(focusables, direction, if (recycler.isInTouchMode()) View.FOCUSABLES_TOUCH_MODE else View.FOCUSABLES_ALL)
        try {
            for (view: View in focusables) {
                if (view === focused || view === recycler) {
                    continue
                }
                if (view === focusedItem) {
                    // do not pass focus back to the item View itself - it makes no sense
                    // (we can still pass focus to it's children however)
                    continue
                }
                val candidate: Int = getDistance(sourcePosition, view, listDirection)
                if (candidate < 0) {
                    continue
                }
                if (candidate < distance) {
                    distance = candidate
                    preferred = view
                }
            }
        } finally {
            focusables.clear()
        }
        return preferred
    }

    private fun getAbsoluteDirection(direction: Int): Int {
        when (direction) {
            View.FOCUS_FORWARD -> return 1
            View.FOCUS_BACKWARD -> return -1
            else -> {}
        }
        if (getOrientation() == RecyclerView.HORIZONTAL) {
            when (direction) {
                View.FOCUS_LEFT -> return if (getReverseLayout()) 1 else -1
                View.FOCUS_RIGHT -> return if (getReverseLayout()) -1 else 1
                else -> {}
            }
        } else {
            when (direction) {
                View.FOCUS_UP -> return if (getReverseLayout()) 1 else -1
                View.FOCUS_DOWN -> return if (getReverseLayout()) -1 else 1
                else -> {}
            }
        }
        return 0
    }

    private fun getDistance(sourcePosition: Int, candidate: View, direction: Int): Int {
        val itemView: View? = findContainingItemView(candidate)
        if (itemView == null) {
            return -1
        }
        val position: Int = getPosition(itemView)
        return direction * (position - sourcePosition)
    }
}
