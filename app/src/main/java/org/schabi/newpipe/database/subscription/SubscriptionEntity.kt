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
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(
    tableName = SubscriptionEntity.Companion.SUBSCRIPTION_TABLE,
    indices = [
        Index(
            value = [
                SubscriptionEntity.Companion.SUBSCRIPTION_PROFILE_ID,
                SubscriptionEntity.Companion.SUBSCRIPTION_SERVICE_ID,
                SubscriptionEntity.Companion.SUBSCRIPTION_URL
            ],
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
    var notificationMode: Int = 0,

    @ColumnInfo(name = SUBSCRIPTION_NOTIFICATION_KEYWORDS, defaultValue = "''")
    var notificationKeywords: String = "",

    @ColumnInfo(name = SUBSCRIPTION_YOUTUBE_MODE_MASK, defaultValue = "1")
    var youtubeModeMask: Int = YOUTUBE_MODE_REGULAR,

    @ColumnInfo(
        name = SUBSCRIPTION_PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    var profileId: String = DEFAULT_PROFILE_ID,

    @ColumnInfo(name = "handle")
    var handle: String? = null,

    /** Local to this profile; pauses aggregate refreshes without hiding cached feed items. */
    @ColumnInfo(name = SUBSCRIPTION_FEED_MUTED, defaultValue = "0")
    var feedMuted: Boolean = false
) {
    @Ignore
    fun toChannelInfoItem(): ChannelInfoItem {
        return ChannelInfoItem(this.serviceId, this.url, this.name).apply {
            ExtractorImageCompat.setThumbnailImages(
                this,
                ImageStrategy.dbUrlToImageList(this@SubscriptionEntity.avatarUrl)
            )
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
        const val SUBSCRIPTION_NOTIFICATION_KEYWORDS: String = "notification_keywords"
        const val SUBSCRIPTION_YOUTUBE_MODE_MASK: String = "youtube_mode_mask"
        const val SUBSCRIPTION_FEED_MUTED: String = "feed_muted"
        const val SUBSCRIPTION_PROFILE_ID: String = "profile_id"
        const val DEFAULT_PROFILE_ID: String = "00000000-0000-0000-0000-000000000000"

        const val YOUTUBE_SERVICE_ID: Int = 0
        const val YOUTUBE_MODE_REGULAR: Int = 1
        const val YOUTUBE_MODE_MUSIC: Int = 1 shl 1
        const val YOUTUBE_MODE_ALL: Int = YOUTUBE_MODE_REGULAR or YOUTUBE_MODE_MUSIC

        @JvmStatic
        @Ignore
        fun from(info: ChannelInfo): SubscriptionEntity {
            return SubscriptionEntity(
                serviceId = info.serviceId,
                url = info.url,
                name = info.name,
                handle = info.handle,
                avatarUrl = ImageStrategy.imageListToDbUrl(info.avatars),
                description = info.description,
                subscriberCount = info.subscriberCount
            )
        }
    }
}
