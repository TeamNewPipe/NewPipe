package org.schabi.newpipe.util.text

import android.annotation.SuppressLint
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.TextView

class CommentTextOnTouchListener : OnTouchListener {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (v !is TextView) {
            return false
        }
        val widget = v
        val text = widget.getText()
        if (text is Spanned) {
            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                val offset = TouchUtils.getOffsetForHorizontalLine(widget, event)
                val links = text.getSpans(offset, offset, ClickableSpan::class.java)
                if (links.size != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        links[0].onClick(widget)
                    }
                    // we handle events that intersect links, so return true
                    return true
                }
            }
        }
        return false
    }

    companion object {
        val INSTANCE = CommentTextOnTouchListener()
    }
}
