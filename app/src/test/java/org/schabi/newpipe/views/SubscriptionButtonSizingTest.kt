package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionButtonSizingTest {
    @Test
    fun translatedOrLargeTextExpandsCompanionGroupButton() {
        assertEquals(72, SubscriptionButtonSizing.height(72, 48, 48))
        assertEquals(96, SubscriptionButtonSizing.height(96, 48, 48))
    }

    @Test
    fun normalTextKeepsMinimumTouchHeight() {
        assertEquals(48, SubscriptionButtonSizing.height(38, 44, 48))
        assertEquals(52, SubscriptionButtonSizing.height(48, 52, 48))
    }
}
