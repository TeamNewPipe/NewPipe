package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.profiles.ProfileManager

/** Read current local choices instead of relying on the subscription captured before fetching. */
internal class FeedNotificationPolicy(context: Context, private val profileId: String) {
    private val context = context.applicationContext
    private val subscriptions = SubscriptionManager(context, profileId)

    fun prepare(update: FeedUpdateInfo): Boolean {
        val subscription = currentSubscription(update.uid) ?: return false
        update.newStreams = NotificationKeywordFilter.filter(
            update.newStreams,
            subscription.notificationMode,
            subscription.notificationKeywords
        )
        return update.newStreams.isNotEmpty()
    }

    fun canNotify(subscriptionId: Long): Boolean = currentSubscription(subscriptionId) != null

    private fun currentSubscription(subscriptionId: Long): SubscriptionEntity? {
        if (ProfileManager.getActiveProfileId(context) != profileId) return null
        return subscriptions.getSubscriptionById(subscriptionId)?.takeIf {
            !it.feedMuted && it.notificationMode in setOf(NotificationMode.ENABLED, NotificationMode.KEYWORDS_ONLY)
        }
    }
}
