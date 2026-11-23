package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.preference.PreferenceManager
import com.xwray.groupie.Group
import com.xwray.groupie.Section
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.subscription.item.SubscriptionChannelsHeader

/** Keeps the latest channel rows while collapsed, independently of the channel groups. */
internal class SubscriptionChannelsSection(
    context: Context,
    profileId: String,
    private val channelsHeader: SubscriptionChannelsHeader,
    private val channels: Section
) : Section(channelsHeader, emptyList<Group>()) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val preferenceKey = "subscription_channels_collapsed_$profileId"
    var collapsed: Boolean = preferences.getBoolean(preferenceKey, false)
        private set

    init {
        updateVisibility()
    }

    fun toggleCollapsed() = changeCollapsed(!collapsed)

    fun revealSearchResults(query: String) {
        if (ContextualSearchHelper.isActive(query)) changeCollapsed(false)
    }

    private fun changeCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        preferences.edit().putBoolean(preferenceKey, value).apply()
        updateVisibility()
    }

    private fun updateVisibility() {
        channelsHeader.collapsed = collapsed
        channelsHeader.notifyChanged()
        update(if (collapsed) emptyList() else listOf(channels))
    }
}
