package org.schabi.newpipe.database.subscription

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.util.NO_SERVICE_ID

@Entity(
    tableName = SubscriptionEntity.SUBSCRIPTION_TABLE,
    indices = [
        Index(
            value = [SubscriptionEntity.SUBSCRIPTION_SERVICE_ID, SubscriptionEntity.SUBSCRIPTION_URL],
            unique = true
        )
    ]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID, defaultValue = "$NO_SERVICE_ID")
    val serviceId: Int = NO_SERVICE_ID,

    @ColumnInfo(name = SUBSCRIPTION_URL, defaultValue = "")
    val url: String = "",

    @ColumnInfo(name = SUBSCRIPTION_NAME)
    val name: String? = null,

    @ColumnInfo(name = SUBSCRIPTION_AVATAR_URL)
    val avatarUrl: String? = null,

    @ColumnInfo(name = SUBSCRIPTION_SUBSCRIBER_COUNT, defaultValue = "0")
    val subscriberCount: Long = 0,

    @ColumnInfo(name = SUBSCRIPTION_DESCRIPTION)
    val description: String? = null
) {
    @NotificationMode
    @ColumnInfo(name = SUBSCRIPTION_NOTIFICATION_MODE, defaultValue = "0")
    var notificationMode = 0

    @Ignore
    constructor(info: ChannelInfo) : this(
        serviceId = info.serviceId, url = info.url, name = info.name, avatarUrl = info.avatarUrl,
        subscriberCount = info.subscriberCount, description = info.description
    )

    @Ignore
    fun toChannelInfoItem(): ChannelInfoItem {
        val item = ChannelInfoItem(serviceId, url, name)
        item.thumbnailUrl = avatarUrl
        item.subscriberCount = subscriberCount
        item.description = description
        return item
    }

    companion object {
        const val SUBSCRIPTION_UID = "uid"
        const val SUBSCRIPTION_TABLE = "subscriptions"
        const val SUBSCRIPTION_SERVICE_ID = "service_id"
        const val SUBSCRIPTION_URL = "url"
        const val SUBSCRIPTION_NAME = "name"
        const val SUBSCRIPTION_AVATAR_URL = "avatar_url"
        const val SUBSCRIPTION_SUBSCRIBER_COUNT = "subscriber_count"
        const val SUBSCRIPTION_DESCRIPTION = "description"
        const val SUBSCRIPTION_NOTIFICATION_MODE = "notification_mode"
    }
}
