package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.schabi.newpipe.database.subscription.SubscriptionEntity

class ChannelGroupCreationTest {
    @Test
    fun opensExistingSubscriptionOnlyInCurrentMode() {
        val existing = mock(SubscriptionEntity::class.java)
        assertTrue(ChannelGroupCreation.needsSubscription(null, false))
        assertTrue(ChannelGroupCreation.needsSubscription(existing, false))
        assertFalse(ChannelGroupCreation.needsSubscription(existing, true))
    }
}
