package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R

class StreamSubscriptionActionsTest {
    @Test
    fun contextMenuUsesSubscribeForUnsubscribedChannels() {
        assertEquals(R.string.subscribe_button_title, StreamSubscriptionActions.actionLabel(false))
    }

    @Test
    fun contextMenuUsesUnsubscribeForSubscribedChannels() {
        assertEquals(R.string.unsubscribe, StreamSubscriptionActions.actionLabel(true))
    }
}
