/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

/** The channel-group visibility setting belongs to each local profile. */
internal object SubscriptionGroupsCollapse {
    fun key(profileId: String): String = "subscription_channel_groups_collapsed_$profileId"
}
