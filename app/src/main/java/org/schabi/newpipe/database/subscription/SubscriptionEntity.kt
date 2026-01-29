/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.subscription

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(
    tableName = SubscriptionEntity.Companion.SUBSCRIPTION_TABLE,
    indices = [
        Index(
            value = [SubscriptionEntity.Companion.SUBSCRIPTION_SERVICE_ID, SubscriptionEntity.Companion.SUBSCRIPTION_URL],
            unique = true
        )
    ]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    var uid: Long = 0,

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID)
    var serviceId: Int = NO_SERVICE_ID,

    @ColumnInfo(name = SUBSCRIPTION_URL)
    var url: String? = null,

    @ColumnInfo(name = SUBSCRIPTION_NAME)
    var name: String? = null,

    @ColumnInfo(name = SUBSCRIPTION_AVATAR_URL)
    var avatarUrl: String? = null,

    @ColumnInfo(name = SUBSCRIPTION_SUBSCRIBER_COUNT)
    var subscriberCount: Long? = null,

    @ColumnInfo(name = SUBSCRIPTION_DESCRIPTION)
    var description: String? = null,

    @get:NotificationMode
    @ColumnInfo(name = SUBSCRIPTION_NOTIFICATION_MODE)
    var notificationMode: Int = 0
) {
    @Ignore
    fun toChannelInfoItem(): ChannelInfoItem {
        return ChannelInfoItem(this.serviceId, this.url, this.name).apply {
            thumbnails = ImageStrategy.dbUrlToImageList(this@SubscriptionEntity.avatarUrl)
            subscriberCount = this@SubscriptionEntity.subscriberCount ?: -1
            description = this@SubscriptionEntity.description
        }
    }

    companion object {
        const val SUBSCRIPTION_UID: String = "uid"
        const val SUBSCRIPTION_TABLE: String = "subscriptions"
        const val SUBSCRIPTION_SERVICE_ID: String = "service_id"
        const val SUBSCRIPTION_URL: String = "url"
        const val SUBSCRIPTION_NAME: String = "name"
        const val SUBSCRIPTION_AVATAR_URL: String = "avatar_url"
        const val SUBSCRIPTION_SUBSCRIBER_COUNT: String = "subscriber_count"
        const val SUBSCRIPTION_DESCRIPTION: String = "description"
        const val SUBSCRIPTION_NOTIFICATION_MODE: String = "notification_mode"

        @JvmStatic
        @Ignore
        fun from(info: ChannelInfo): SubscriptionEntity {
            return SubscriptionEntity(
                serviceId = info.serviceId,
                url = info.url,
                name = info.name,
                avatarUrl = ImageStrategy.imageListToDbUrl(info.avatars),
                description = info.description,
                subscriberCount = info.subscriberCount
            )
        }
    }
}
