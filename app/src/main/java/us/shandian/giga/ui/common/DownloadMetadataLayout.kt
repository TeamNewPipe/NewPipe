package us.shandian.giga.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Keeps the download status before its date, stacking them when their text needs more room. */
class DownloadMetadataLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var neededWidth = paddingLeft + paddingRight
        val naturalWidth = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, naturalWidth, 0, heightMeasureSpec, 0)
            val margins = child.layoutParams as LayoutParams
            neededWidth += child.measuredWidth + margins.leftMargin + margins.rightMargin
        }
        orientation = if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            neededWidth > MeasureSpec.getSize(widthMeasureSpec)
        ) {
            VERTICAL
        } else {
            HORIZONTAL
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
