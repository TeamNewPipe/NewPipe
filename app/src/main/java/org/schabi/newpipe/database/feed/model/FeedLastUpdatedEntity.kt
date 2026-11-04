package org.schabi.newpipe.database.feed.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity.Companion.FEED_LAST_UPDATED_TABLE
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity.Companion.SUBSCRIPTION_ID
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Entity(
    tableName = FEED_LAST_UPDATED_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = [SubscriptionEntity.SUBSCRIPTION_UID],
            childColumns = [SUBSCRIPTION_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class FeedLastUpdatedEntity(
    @PrimaryKey
    @ColumnInfo(name = SUBSCRIPTION_ID)
    var subscriptionId: Long,

    @ColumnInfo(name = LAST_UPDATED)
    var lastUpdated: OffsetDateTime? = null
) {
    companion object {
        const val FEED_LAST_UPDATED_TABLE = "feed_last_updated"

        const val SUBSCRIPTION_ID = "subscription_id"
        const val LAST_UPDATED = "last_updated"
    }
}
