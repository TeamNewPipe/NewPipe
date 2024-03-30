package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.tabs.TabLayout
import org.schabi.newpipe.views.ScrollableTabLayout
import kotlin.math.max

/**
 * A TabLayout that is scrollable when tabs exceed its width.
 * Hides when there are less than 2 tabs.
 */
class ScrollableTabLayout : TabLayout {
    private var layoutWidth: Int = 0
    private var prevVisibility: Int = GONE

    constructor(context: Context?) : super((context)!!)
    constructor(context: Context?, attrs: AttributeSet?) : super((context)!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?,
                defStyleAttr: Int) : super((context)!!, attrs, defStyleAttr)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int,
                          b: Int) {
        super.onLayout(changed, l, t, r, b)
        remeasureTabs()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutWidth = w
    }

    public override fun addTab(tab: Tab, position: Int, setSelected: Boolean) {
        super.addTab(tab, position, setSelected)
        hasMultipleTabs()

        // Adding a tab won't decrease total tabs' width so tabMode won't have to change to FIXED
        if (getTabMode() != MODE_SCROLLABLE) {
            remeasureTabs()
        }
    }

    public override fun removeTabAt(position: Int) {
        super.removeTabAt(position)
        hasMultipleTabs()

        // Removing a tab won't increase total tabs' width
        // so tabMode won't have to change to SCROLLABLE
        if (getTabMode() != MODE_FIXED) {
            remeasureTabs()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        // Check width if some tabs have been added/removed while ScrollableTabLayout was invisible
        // We don't have to check if it was GONE because then requestLayout() will be called
        if (changedView === this) {
            if (prevVisibility == INVISIBLE) {
                remeasureTabs()
            }
            prevVisibility = visibility
        }
    }

    var mode: Int
        get() = super.mode
        private set(mode) {
            if (mode == getTabMode()) {
                return
            }
            setTabMode(mode)
        }

    /**
     * Make ScrollableTabLayout not visible if there are less than two tabs.
     */
    private fun hasMultipleTabs() {
        if (getTabCount() > 1) {
            setVisibility(VISIBLE)
        } else {
            setVisibility(GONE)
        }
    }

    /**
     * Calculate minimal width required by tabs and set tabMode accordingly.
     */
    private fun remeasureTabs() {
        if (prevVisibility != VISIBLE) {
            return
        }
        if (layoutWidth == 0) {
            return
        }
        val count: Int = getTabCount()
        var contentWidth: Int = 0
        for (i in 0 until count) {
            val child: View = getTabAt(i)!!.view
            if (child.getVisibility() == VISIBLE) {
                // Use tab's minimum requested width should actual content be too small
                (contentWidth += max(child.getMinimumWidth().toDouble(), child.getMeasuredWidth().toDouble())).toInt()
            }
        }
        if (contentWidth > layoutWidth) {
            this.mode = MODE_SCROLLABLE
        } else {
            this.mode = MODE_FIXED
        }
    }

    companion object {
        private val TAG: String = ScrollableTabLayout::class.java.getSimpleName()
    }
}
