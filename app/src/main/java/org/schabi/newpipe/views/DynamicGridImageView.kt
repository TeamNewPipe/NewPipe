package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView
import org.schabi.newpipe.util.ThemeHelper.getGridHeight
import org.schabi.newpipe.util.ThemeHelper.getGridWidth

class DynamicGridImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShapeableImageView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val configuredWidth = getGridWidth(context)
        val configuredHeight = getGridHeight(context)
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: configuredWidth
        val measuredHeight = measuredWidth * configuredHeight / configuredWidth
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }
}
