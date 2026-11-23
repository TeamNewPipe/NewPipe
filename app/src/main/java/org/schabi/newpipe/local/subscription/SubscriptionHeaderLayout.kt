package org.schabi.newpipe.local.subscription

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Moves the complete action row below the labels when large text needs more space. */
class SubscriptionHeaderLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 2) {
            val labels = getChildAt(0)
            val actions = getChildAt(1)
            val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            labels.measure(natural, natural)
            actions.measure(natural, natural)
            val fits = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ||
                labels.measuredWidth + actions.measuredWidth <=
                MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            orientation = if (fits) HORIZONTAL else VERTICAL
            (labels.layoutParams as LayoutParams).apply {
                width = if (fits) 0 else LayoutParams.MATCH_PARENT
                weight = if (fits) 1f else 0f
            }
            ((labels as LinearLayout).getChildAt(0).layoutParams as LayoutParams).apply {
                width = if (fits) LayoutParams.WRAP_CONTENT else 0
                weight = if (fits) 0f else 1f
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
