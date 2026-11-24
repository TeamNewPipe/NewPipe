package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Keeps actions beside their label, giving large text its own row when necessary. */
class CompactPlaylistControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 2) {
            val label = getChildAt(0)
            val actions = getChildAt(1)
            val labelParams = label.layoutParams as LayoutParams
            val actionParams = actions.layoutParams as LayoutParams
            val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            label.measure(natural, natural)
            if (actions.visibility != View.GONE) actions.measure(natural, natural)
            val actionWidth = if (actions.visibility == View.GONE) {
                0
            } else {
                actions.measuredWidth + actionParams.leftMargin + actionParams.rightMargin
            }
            val fits = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ||
                label.measuredWidth + labelParams.leftMargin + labelParams.rightMargin + actionWidth <=
                MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            orientation = if (fits || actions.visibility == View.GONE) HORIZONTAL else VERTICAL
            labelParams.width = if (orientation == HORIZONTAL) 0 else LayoutParams.MATCH_PARENT
            labelParams.weight = if (orientation == HORIZONTAL) 1f else 0f
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
