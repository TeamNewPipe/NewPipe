/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * NewPipeRecyclerView.java is part of NewPipe.
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
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class NewPipeRecyclerView : RecyclerView {
    private val focusRect: Rect = Rect()
    private val tempFocus: Rect = Rect()
    private var allowDpadScroll: Boolean = true

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context,
                attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context,
                attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        setFocusable(true)
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS)
    }

    fun setFocusScrollAllowed(allowed: Boolean) {
        allowDpadScroll = allowed
    }

    public override fun focusSearch(focused: View, direction: Int): View {
        // RecyclerView has buggy focusSearch(), that calls into Adapter several times,
        // but ultimately fails to produce correct results in many cases. To add insult to injury,
        // it's focusSearch() hard-codes several behaviors, incompatible with widely accepted focus
        // handling practices: RecyclerView does not allow Adapter to give focus to itself (!!) and
        // always checks, that returned View is located in "correct" direction (which prevents us
        // from temporarily giving focus to special hidden View).
        return null
    }

    override fun removeDetachedView(child: View, animate: Boolean) {
        if (child.hasFocus()) {
            // If the focused child is being removed (can happen during very fast scrolling),
            // temporarily give focus to ourselves. This will usually result in another child
            // gaining focus (which one does not really matter, because at that point scrolling
            // is FAST, and that child will soon be off-screen too)
            requestFocus()
        }
        super.removeDetachedView(child, animate)
    }

    // we override focusSearch to always return null, so all moves moves lead to
    // dispatchUnhandledMove(). As added advantage, we can fully swallow some kinds of moves
    // (such as downward movement, that happens when loading additional contents is in progress
    public override fun dispatchUnhandledMove(focused: View, direction: Int): Boolean {
        tempFocus.setEmpty()

        // save focus rect before further manipulation (both focusSearch() and scrollBy()
        // can mess with focused View by moving it off-screen and detaching)
        if (focused != null) {
            val focusedItem: View? = findContainingItemView(focused)
            if (focusedItem != null) {
                focusedItem.getHitRect(focusRect)
            }
        }

        // call focusSearch() to initiate layout, but disregard returned View for now
        val adapterResult: View? = super.focusSearch(focused, direction)
        if (adapterResult != null && !isOutside(adapterResult)) {
            adapterResult.requestFocus(direction)
            return true
        }
        if (arrowScroll(direction)) {
            // if RecyclerView can not yield focus, but there is still some scrolling space in
            // indicated, direction, scroll some fixed amount in that direction
            // (the same logic in ScrollView)
            return true
        }
        if ((focused !== this) && (direction == FOCUS_DOWN) && !allowDpadScroll) {
            Log.i(TAG, "Consuming downward scroll: content load in progress")
            return true
        }
        if (tryFocusFinder(direction)) {
            return true
        }
        if (adapterResult != null) {
            adapterResult.requestFocus(direction)
            return true
        }
        return super.dispatchUnhandledMove(focused, direction)
    }

    private fun tryFocusFinder(direction: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 implemented bunch of handy changes to focus, that render code below less
            // useful, and also broke findNextFocusFromRect in way, that render this hack useless
            return false
        }
        val finder: FocusFinder = FocusFinder.getInstance()

        // try to use FocusFinder instead of adapter
        val root: ViewGroup = getRootView() as ViewGroup
        tempFocus.set(focusRect)
        root.offsetDescendantRectToMyCoords(this, tempFocus)
        val focusFinderResult: View? = finder.findNextFocusFromRect(root, tempFocus, direction)
        if (focusFinderResult != null && !isOutside(focusFinderResult)) {
            focusFinderResult.requestFocus(direction)
            return true
        }

        // look for focus in our ancestors, increasing search scope with each failure
        // this provides much better locality than using FocusFinder with root
        var parent: ViewGroup = getParent() as ViewGroup
        while (parent !== root) {
            tempFocus.set(focusRect)
            parent.offsetDescendantRectToMyCoords(this, tempFocus)
            val candidate: View? = finder.findNextFocusFromRect(parent, tempFocus, direction)
            if (candidate != null && candidate.requestFocus(direction)) {
                return true
            }
            parent = parent.getParent() as ViewGroup
        }
        return false
    }

    private fun arrowScroll(direction: Int): Boolean {
        when (direction) {
            FOCUS_DOWN -> {
                if (!canScrollVertically(1)) {
                    return false
                }
                scrollBy(0, 100)
            }

            FOCUS_UP -> {
                if (!canScrollVertically(-1)) {
                    return false
                }
                scrollBy(0, -100)
            }

            FOCUS_LEFT -> {
                if (!canScrollHorizontally(-1)) {
                    return false
                }
                scrollBy(-100, 0)
            }

            FOCUS_RIGHT -> {
                if (!canScrollHorizontally(-1)) {
                    return false
                }
                scrollBy(100, 0)
            }

            else -> return false
        }
        return true
    }

    private fun isOutside(view: View): Boolean {
        return findContainingItemView(view) == null
    }

    companion object {
        private val TAG: String = "NewPipeRecyclerView"
    }
}
