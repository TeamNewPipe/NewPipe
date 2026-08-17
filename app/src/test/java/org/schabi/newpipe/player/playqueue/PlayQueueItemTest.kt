package org.schabi.newpipe.player.playqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayQueueItemTest {

    @Test
    fun equalsMustNotBeOverloaded() {
        val a = PlayQueueTest.makeItemWithUrl(URL)
        val b = PlayQueueTest.makeItemWithUrl(URL)
        assertEquals(a, a)
        assertNotEquals(a, b) // they should compare different even if they have the same data
    }

    companion object {
        const val URL: String = "MY_URL"
    }
}
