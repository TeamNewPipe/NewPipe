package org.schabi.newpipe.views

import android.text.style.ClickableSpan
import android.view.View

abstract class LongPressClickableSpan : ClickableSpan() {
    abstract fun onLongClick(view: View)
}
