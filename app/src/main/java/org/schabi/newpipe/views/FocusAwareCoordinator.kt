/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FocusAwareCoordinator.java is part of NewPipe.
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
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.schabi.newpipe.R

class FocusAwareCoordinator : CoordinatorLayout {
    private val childFocus: Rect = Rect()

    constructor(context: Context) : super(context)
    constructor(context: Context,
                attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context,
                attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    public override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        if (!isInTouchMode()) {
            if (focused.getHeight() >= getHeight()) {
                focused.getFocusedRect(childFocus)
                (child as ViewGroup).offsetDescendantRectToMyCoords(focused, childFocus)
            } else {
                focused.getHitRect(childFocus)
                (child as ViewGroup).offsetDescendantRectToMyCoords(focused.getParent() as View?,
                        childFocus)
            }
            requestChildRectangleOnScreen(child, childFocus, false)
        }
    }

    /**
     * Applies window insets to all children, not just for the first who consume the insets.
     * Makes possible for multiple fragments to co-exist. Without this code
     * the first ViewGroup who consumes will be the last who receive the insets
     */
    public override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var consumed: Boolean = false
        for (i in 0 until getChildCount()) {
            val child: View = getChildAt(i)
            val res: WindowInsets = child.dispatchApplyWindowInsets(insets)
            if (res.isConsumed()) {
                consumed = true
            }
        }
        return if (consumed) (WindowInsetsCompat.CONSUMED.toWindowInsets())!! else insets
    }

    /**
     * Adjusts player's controls manually because onApplyWindowInsets doesn't work when multiple
     * receivers adjust its bounds. So when two listeners are present (like in profile page)
     * the player's controls will not receive insets. This method fixes it
     */
    public override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        val windowInsetsCompat: WindowInsetsCompat = WindowInsetsCompat.toWindowInsetsCompat(windowInsets, this)
        val insets: Insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
        val controls: ViewGroup? = findViewById(R.id.playbackControlRoot)
        if (controls != null) {
            controls.setPadding(insets.left, insets.top, insets.right, insets.bottom)
        }
        return super.onApplyWindowInsets(windowInsets)
    }
}
