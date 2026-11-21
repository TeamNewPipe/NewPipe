package org.schabi.newpipe.local.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFastScrollerVisibilityTest {
    @Test fun subscriptionHeaderStaysFreeOfFastScrollOverlay() {
        assertFalse(ListFastScroller.shouldShow(100, 0, true))
        assertTrue(ListFastScroller.shouldShow(100, 1, true))
        assertFalse(ListFastScroller.shouldShow(40, 1, true))
    }

    @Test fun otherLongListsKeepScrollTrackAvailableAtTop() {
        assertTrue(ListFastScroller.shouldShow(100, 0, false))
        assertFalse(ListFastScroller.shouldShow(1, 0, false))
    }
}
