package org.schabi.newpipe.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout

class FocusAwareDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        var hasOpenPanels = false

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams

            if (lp.gravity != 0 && isDrawerVisible(child)) {
                hasOpenPanels = true
                if (child.requestFocus(direction, previouslyFocusedRect)) {
                    return true
                }
            }
        }

        if (hasOpenPanels) return false

        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        var hasOpenPanels = false
        var content: View? = null

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams

            if (lp.gravity == 0) {
                content = child
            } else {
                if (isDrawerVisible(child)) {
                    hasOpenPanels = true
                    child.addFocusables(views, direction, focusableMode)
                }
            }
        }

        if (content != null && !hasOpenPanels) {
            content.addFocusables(views, direction, focusableMode)
        }
    }

    @SuppressLint("RtlHardcoded")
    override fun openDrawer(drawerView: View, animate: Boolean) {
        super.openDrawer(drawerView, animate)
        drawerView.requestFocus(FOCUS_FORWARD)
    }
}
