package org.schabi.newpipe.error

import android.app.Activity
import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.error.ErrorActivity.Companion.getReturnActivity
import org.schabi.newpipe.fragments.detail.VideoDetailFragment

/**
 * Unit tests for [ErrorActivity].
 */
class ErrorActivityTest {
    @get:Test
    val returnActivity: Unit
        get() {
            var returnActivity: Class<out Activity?>?
            returnActivity = getReturnActivity(MainActivity::class.java)
            Assert.assertEquals(MainActivity::class.java, returnActivity)
            returnActivity = getReturnActivity(RouterActivity::class.java)
            Assert.assertEquals(RouterActivity::class.java, returnActivity)
            returnActivity = getReturnActivity(null)
            Assert.assertNull(returnActivity)
            returnActivity = getReturnActivity(Int::class.java)
            Assert.assertEquals(MainActivity::class.java, returnActivity)
            returnActivity = getReturnActivity(VideoDetailFragment::class.java)
            Assert.assertEquals(MainActivity::class.java, returnActivity)
        }
}
