package org.schabi.newpipe.util.text

import android.view.MotionEvent
import android.widget.TextView

object TouchUtils {
    /**
     * Get the character offset on the closest line to the position pressed by the user of a
     * [TextView] from a [MotionEvent] which was fired on this [TextView].
     *
     * @param textView the [TextView] on which the [MotionEvent] was fired
     * @param event    the [MotionEvent] which was fired
     * @return the character offset on the closest line to the position pressed by the user
     */
    fun getOffsetForHorizontalLine(textView: TextView,
                                   event: MotionEvent): Int {
        var x = event.x.toInt()
        var y = event.y.toInt()
        x -= textView.totalPaddingLeft
        y -= textView.totalPaddingTop
        x += textView.scrollX
        y += textView.scrollY
        val layout = textView.layout
        val line = layout.getLineForVertical(y)
        return layout.getOffsetForHorizontal(line, x.toFloat())
    }
}
