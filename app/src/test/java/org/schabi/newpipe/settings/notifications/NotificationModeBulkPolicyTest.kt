package org.schabi.newpipe.settings.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.database.subscription.NotificationMode

class NotificationModeBulkPolicyTest {
    @Test
    fun noChannelsMeansNoBulkAction() {
        assertNull(NotificationModeBulkPolicy.nextMode(emptyList()))
    }

    @Test
    fun mixedChannelsEnableAllRegardlessOfFirstRow() {
        assertEquals(
            NotificationMode.ENABLED,
            NotificationModeBulkPolicy.nextMode(
                listOf(
                    NotificationMode.ENABLED,
                    NotificationMode.KEYWORDS_ONLY,
                    NotificationMode.DISABLED
                )
            )
        )
        assertEquals(
            NotificationMode.ENABLED,
            NotificationModeBulkPolicy.nextMode(
                listOf(
                    NotificationMode.DISABLED,
                    NotificationMode.ENABLED
                )
            )
        )
    }

    @Test
    fun fullyEnabledOrKeywordConfiguredChannelsDisableTogether() {
        assertEquals(
            NotificationMode.DISABLED,
            NotificationModeBulkPolicy.nextMode(
                listOf(
                    NotificationMode.ENABLED,
                    NotificationMode.KEYWORDS_ONLY
                )
            )
        )
    }
}
