package org.schabi.newpipe.player.playqueue

import org.junit.Assert
import org.junit.Test

class PlayQueueItemTest {
    @Test
    fun equalsMustNotBeOverloaded() {
        val a = PlayQueueTest.makeItemWithUrl(URL)
        val b = PlayQueueTest.makeItemWithUrl(URL)
        Assert.assertEquals(a, a)
        Assert.assertNotEquals(a, b) // they should compare different even if they have the same data
    }

    companion object {
        const val URL = "MY_URL"
    }
}
