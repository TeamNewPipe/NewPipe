package org.schabi.newpipe.local.feed.service

import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FeedUpdateInfo(
    val uid: Long,
    @NotificationMode
    val notificationMode: Int,
    val name: String,
    val avatarUrl: String,
    val listInfo: ListInfo<StreamInfoItem>,
) {
    constructor(subscription: SubscriptionEntity, listInfo: ListInfo<StreamInfoItem>) : this(
        subscription.uid, subscription.notificationMode, subscription.name.orEmpty(),
        subscription.avatarUrl.orEmpty(), listInfo,
    )

    /**
     * Integer id, can be used as notification id, etc.
     */
    val pseudoId: Int
        get() = listInfo.url.hashCode()

    lateinit var newStreams: List<StreamInfoItem>
}
