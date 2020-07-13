package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.feed.model.FeedEntity.Companion.FEED_TABLE
import org.schabi.newpipe.database.feed.model.FeedEntity.Companion.STREAM_ID
import org.schabi.newpipe.database.feed.model.FeedEntity.Companion.SUBSCRIPTION_ID
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Entity(tableName = FEED_TABLE,
        primaryKeys = [STREAM_ID, SUBSCRIPTION_ID],
        indices = [Index(SUBSCRIPTION_ID)],
        foreignKeys = [
            ForeignKey(
                    entity = StreamEntity::class,
                    parentColumns = [StreamEntity.STREAM_ID],
                    childColumns = [STREAM_ID],
                    onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE, deferred = true),
            ForeignKey(
                    entity = SubscriptionEntity::class,
                    parentColumns = [SubscriptionEntity.SUBSCRIPTION_UID],
                    childColumns = [SUBSCRIPTION_ID],
                    onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE, deferred = true)
        ]
)
data class FeedEntity(
    @ColumnInfo(name = STREAM_ID)
    var streamId: Long,

    @ColumnInfo(name = SUBSCRIPTION_ID)
    var subscriptionId: Long
) {

    companion object {
        const val FEED_TABLE = "feed"

        const val STREAM_ID = "stream_id"
        const val SUBSCRIPTION_ID = "subscription_id"
    }
}
