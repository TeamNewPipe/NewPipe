package org.schabi.newpipe.util.text

import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView

// Class adapted from https://stackoverflow.com/a/31786969
class LongPressLinkMovementMethod : LinkMovementMethod() {
    private var longClickHandler: Handler? = null
    private var isLongPressed = false
    override fun onTouchEvent(widget: TextView,
                              buffer: Spannable,
                              event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_CANCEL && longClickHandler != null) {
            longClickHandler!!.removeCallbacksAndMessages(null)
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            val offset = TouchUtils.getOffsetForHorizontalLine(widget, event)
            val link = buffer.getSpans(offset, offset,
                    LongPressClickableSpan::class.java)
            if (link.size != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if (longClickHandler != null) {
                        longClickHandler!!.removeCallbacksAndMessages(null)
                    }
                    if (!isLongPressed) {
                        link[0].onClick(widget)
                    }
                    isLongPressed = false
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]))
                    if (longClickHandler != null) {
                        longClickHandler!!.postDelayed({
                            link[0].onLongClick(widget)
                            isLongPressed = true
                        }, LONG_PRESS_TIME.toLong())
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private val LONG_PRESS_TIME = ViewConfiguration.getLongPressTimeout()
        var instance: LongPressLinkMovementMethod? = null
            get() {
                if (field == null) {
                    field = LongPressLinkMovementMethod()
                    field!!.longClickHandler = Handler(Looper.myLooper()!!)
                }
                return field
            }
            private set
    }
}
