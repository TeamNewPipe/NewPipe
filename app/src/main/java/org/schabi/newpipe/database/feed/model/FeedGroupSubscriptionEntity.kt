package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity.Companion.FEED_GROUP_SUBSCRIPTION_TABLE
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity.Companion.GROUP_ID
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity.Companion.SUBSCRIPTION_ID
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Entity(
    tableName = FEED_GROUP_SUBSCRIPTION_TABLE,
    primaryKeys = [GROUP_ID, SUBSCRIPTION_ID],
    indices = [Index(SUBSCRIPTION_ID)],
    foreignKeys = [
        ForeignKey(
            entity = FeedGroupEntity::class,
            parentColumns = [FeedGroupEntity.ID],
            childColumns = [GROUP_ID],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE, deferred = true
        ),

        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = [SubscriptionEntity.SUBSCRIPTION_UID],
            childColumns = [SUBSCRIPTION_ID],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE, deferred = true
        )
    ]
)
data class FeedGroupSubscriptionEntity(
    @ColumnInfo(name = GROUP_ID)
    var feedGroupId: Long,

    @ColumnInfo(name = SUBSCRIPTION_ID)
    var subscriptionId: Long
) {

    companion object {
        const val FEED_GROUP_SUBSCRIPTION_TABLE = "feed_group_subscription_join"

        const val GROUP_ID = "group_id"
        const val SUBSCRIPTION_ID = "subscription_id"
    }
}
