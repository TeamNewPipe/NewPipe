package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.tabs.TabLayout

/**
 * A TabLayout that is scrollable when tabs exceed its width.
 * Hides when there are less than 2 tabs.
 */
class ScrollableTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.tabStyle
) : TabLayout(context, attrs, defStyleAttr) {

    private var layoutWidth = 0
    private var prevVisibility = GONE

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        remeasureTabs()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutWidth = w
    }

    override fun addTab(tab: Tab, position: Int, setSelected: Boolean) {
        super.addTab(tab, position, setSelected)
        checkMultipleTabs()
        if (tabMode != MODE_SCROLLABLE) {
            remeasureTabs()
        }
    }

    override fun removeTabAt(position: Int) {
        super.removeTabAt(position)
        checkMultipleTabs()
        if (tabMode != MODE_FIXED) {
            remeasureTabs()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this) {
            if (prevVisibility == INVISIBLE) {
                remeasureTabs()
            }
            prevVisibility = visibility
        }
    }

    private fun setMode(mode: Int) {
        if (mode == tabMode) return
        tabMode = mode
    }

    private fun checkMultipleTabs() {
        visibility = if (tabCount > 1) View.VISIBLE else View.GONE
    }

    private fun remeasureTabs() {
        if (prevVisibility != VISIBLE || layoutWidth == 0) return

        val count = tabCount
        var contentWidth = 0
        for (i in 0 until count) {
            val child = getTabAt(i)?.view ?: continue
            if (child.visibility == VISIBLE) {
                contentWidth += child.minimumWidth.coerceAtLeast(child.measuredWidth)
            }
        }

        if (contentWidth > layoutWidth) {
            setMode(MODE_SCROLLABLE)
        } else {
            setMode(MODE_FIXED)
        }
    }
}
