package org.schabi.newpipe.views

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import org.schabi.newpipe.R

/** Applies the global title preference while this toolbar is attached to a window. */
class InterfaceToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.toolbarStyle
) : MaterialToolbar(context, attrs, defStyleAttr) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val alignmentKey = context.getString(R.string.toolbar_title_alignment_key)
    private var customTitle: LinearLayout? = null
    private var customTitleStartPadding = 0
    private var customTitleEndPadding = 0
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == alignmentKey) applyAlignment()
    }

    init {
        applyAlignment()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        applyAlignment()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDetachedFromWindow()
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        // Only the Feed selector opts in. Search fields and other custom actions keep
        // the sizing and gravity supplied by their own toolbar layout parameters.
        if (child is LinearLayout && child.getTag(R.id.toolbar_custom_title) == true) {
            customTitle = child
            customTitleStartPadding = child.paddingStart
            customTitleEndPadding = child.paddingEnd
            alignCustomTitle(isTitleCentered)
        }
    }

    override fun onViewRemoved(child: View) {
        if (child === customTitle) {
            alignCustomTitle(false)
            customTitle = null
        }
        super.onViewRemoved(child)
    }

    private fun applyAlignment() {
        val centered = preferences.getString(alignmentKey, "start") == "center"
        isTitleCentered = centered
        isSubtitleCentered = centered
        alignCustomTitle(centered)
        requestLayout()
    }

    private fun alignCustomTitle(centered: Boolean) {
        val title = customTitle ?: return
        val gravity = Gravity.CENTER_VERTICAL or if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        title.gravity = gravity
        title.setPaddingRelative(
            customTitleStartPadding,
            title.paddingTop,
            if (centered) customTitleStartPadding else customTitleEndPadding,
            title.paddingBottom
        )
        title.children.filterIsInstance<TextView>().forEach { it.gravity = gravity }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!isTitleCentered) return
        val title = customTitle?.takeIf { it.visibility != GONE } ?: return
        val midpoint = width / 2
        var leftLimit = paddingLeft
        var rightLimit = width - paddingRight
        for (child in children) {
            if (child === title || child.visibility == GONE) continue
            when {
                child.right <= midpoint -> leftLimit = maxOf(leftLimit, child.right)

                child.left >= midpoint -> rightLimit = minOf(rightLimit, child.left)

                // No title space remains when another toolbar action crosses the center.
                else -> {
                    leftLimit = midpoint
                    rightLimit = midpoint
                }
            }
        }
        val availableWidth = (2 * minOf(midpoint - leftLimit, rightLimit - midpoint)).coerceAtLeast(0)
        val titleWidth = minOf(title.measuredWidth, availableWidth)
        title.measure(
            MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(title.measuredHeight, MeasureSpec.EXACTLY)
        )
        val titleLeft = midpoint - titleWidth / 2
        title.layout(titleLeft, title.top, titleLeft + titleWidth, title.bottom)
    }
}
