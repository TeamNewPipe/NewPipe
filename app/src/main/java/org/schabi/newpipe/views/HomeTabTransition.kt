package org.schabi.newpipe.views

import kotlin.math.abs

object HomeTabTransition {
    /** A long ViewPager animation retains the departing pages alongside the destination pages. */
    @JvmStatic
    fun shouldJump(current: Int, destination: Int, offscreenPageLimit: Int): Boolean {
        return abs(destination - current) > offscreenPageLimit
    }
}
