/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubscriptionGroupsCollapseTest {
    @Test
    fun collapsePreferenceIsProfileScoped() {
        val first = SubscriptionGroupsCollapse.key("profile-1")
        val second = SubscriptionGroupsCollapse.key("profile-2")
        assertNotEquals(first, second)
        assertEquals("subscription_channel_groups_collapsed_profile-1", first)
    }
}
