package org.schabi.newpipe.local.feed.service

import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FeedUpdateInfo(
    val uid: Long,
    @NotificationMode
    val notificationMode: Int,
    val name: String,
    val avatarUrl: String,
    val originalInfo: Info,
    val streams: List<StreamInfoItem>,
    val errors: List<Throwable>,
) {
    constructor(
        subscription: SubscriptionEntity,
        originalInfo: Info,
        streams: List<StreamInfoItem>,
        errors: List<Throwable>,
    ) : this(
        uid = subscription.uid,
        notificationMode = subscription.notificationMode,
        name = subscription.name,
        avatarUrl = subscription.avatarUrl,
        originalInfo = originalInfo,
        streams = streams,
        errors = errors,
    )

    /**
     * Integer id, can be used as notification id, etc.
     */
    val pseudoId: Int
        get() = originalInfo.url.hashCode()

    lateinit var newStreams: List<StreamInfoItem>
}
