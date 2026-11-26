package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

/** Only notification fetches install this hook; ordinary feed refreshes never create alerts. */
internal class StickyNotificationCapture(
    private val context: Context,
    private val session: StickyNotificationSession
) {
    private val insertedChannels = mutableSetOf<Pair<Int, String>>()

    fun newlyInsertedChannels(): Set<Pair<Int, String>> = synchronized(insertedChannels) { insertedChannels.toSet() }

    fun persist(database: AppDatabase, info: FeedUpdateInfo, subscription: SubscriptionEntity): (() -> Unit)? {
        check(database.inTransaction())
        if (subscription.profileId != session.profileId ||
            !StickyNotificationSettings.isCurrent(context, session)
        ) {
            return null
        }

        // Re-read notification policy from the subscription that still exists at commit time.
        // A fetch started before the user disabled a channel must not create a new alert.
        val eligible = NotificationKeywordFilter.filter(
            info.newStreams,
            subscription.notificationMode,
            subscription.notificationKeywords
        )
        val now = System.currentTimeMillis()
        val entries = eligible.map { stream ->
            PendingStreamNotification(
                profileId = session.profileId,
                generation = session.generation,
                serviceId = stream.serviceId,
                streamUrl = stream.url,
                channelUrl = subscription.url ?: info.url,
                channelName = info.name,
                streamTitle = stream.name,
                thumbnailUrl = ImageStrategy.imageListToDbUrl(ExtractorImageCompat.thumbnailImages(stream)),
                createdAt = now
            )
        }
        val inserted = database.pendingStreamNotificationDAO().insert(entries)
        val channels = entries.zip(inserted)
            .filter { (_, rowId) -> rowId != -1L }
            .map { (entry, _) -> entry.serviceId to entry.channelUrl }.toSet()
        // The caller invokes this only after the surrounding feed transaction commits.
        return {
            synchronized(insertedChannels) { insertedChannels.addAll(channels) }
            Unit
        }
    }
}
