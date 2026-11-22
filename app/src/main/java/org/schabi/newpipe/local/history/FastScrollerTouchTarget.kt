package org.schabi.newpipe.local.history

/** Limit fast-scroller gesture capture to its thumb instead of covering feed action buttons. */
object FastScrollerTouchTarget {
    fun isOnThumb(
        x: Float,
        y: Float,
        thumbX: Float,
        thumbY: Float,
        horizontalRadius: Float,
        verticalRadius: Float
    ): Boolean {
        return x.isFinite() && y.isFinite() &&
            kotlin.math.abs(x - thumbX) <= horizontalRadius &&
            kotlin.math.abs(y - thumbY) <= verticalRadius
    }
}
