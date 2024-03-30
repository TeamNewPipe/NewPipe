package org.schabi.newpipe.database.subscription

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(tableName = SubscriptionEntity.SUBSCRIPTION_TABLE, indices = [Index(value = [SubscriptionEntity.SUBSCRIPTION_SERVICE_ID, SubscriptionEntity.SUBSCRIPTION_URL], unique = true)])
class SubscriptionEntity() {
    @PrimaryKey(autoGenerate = true)
    private var uid: Long = 0

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID)
    private var serviceId: Int = NO_SERVICE_ID

    @ColumnInfo(name = SUBSCRIPTION_URL)
    private var url: String? = null

    @ColumnInfo(name = SUBSCRIPTION_NAME)
    private var name: String? = null

    @ColumnInfo(name = SUBSCRIPTION_AVATAR_URL)
    private var avatarUrl: String? = null

    @ColumnInfo(name = SUBSCRIPTION_SUBSCRIBER_COUNT)
    private var subscriberCount: Long? = null

    @ColumnInfo(name = SUBSCRIPTION_DESCRIPTION)
    private var description: String? = null

    @ColumnInfo(name = SUBSCRIPTION_NOTIFICATION_MODE)
    private var notificationMode: Int = 0
    fun getUid(): Long {
        return uid
    }

    fun setUid(uid: Long) {
        this.uid = uid
    }

    fun getServiceId(): Int {
        return serviceId
    }

    fun setServiceId(serviceId: Int) {
        this.serviceId = serviceId
    }

    fun getUrl(): String? {
        return url
    }

    fun setUrl(url: String?) {
        this.url = url
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getAvatarUrl(): String? {
        return avatarUrl
    }

    fun setAvatarUrl(avatarUrl: String?) {
        this.avatarUrl = avatarUrl
    }

    fun getSubscriberCount(): Long? {
        return subscriberCount
    }

    fun setSubscriberCount(subscriberCount: Long?) {
        this.subscriberCount = subscriberCount
    }

    fun getDescription(): String? {
        return description
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    @NotificationMode
    fun getNotificationMode(): Int {
        return notificationMode
    }

    fun setNotificationMode(@NotificationMode notificationMode: Int) {
        this.notificationMode = notificationMode
    }

    @Ignore
    fun setData(n: String?, au: String?, d: String?, sc: Long?) {
        setName(n)
        setAvatarUrl(au)
        setDescription(d)
        setSubscriberCount(sc)
    }

    @Ignore
    fun toChannelInfoItem(): ChannelInfoItem {
        val item: ChannelInfoItem = ChannelInfoItem(getServiceId(), getUrl(), getName())
        item.setThumbnails(ImageStrategy.dbUrlToImageList(getAvatarUrl()))
        item.setSubscriberCount((getSubscriberCount())!!)
        item.setDescription(getDescription())
        return item
    }

    // TODO: Remove these generated methods by migrating this class to a data class from Kotlin.
    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that: SubscriptionEntity = o as SubscriptionEntity
        if (uid != that.uid) {
            return false
        }
        if (serviceId != that.serviceId) {
            return false
        }
        if (!(url == that.url)) {
            return false
        }
        if (if (name != null) !(name == that.name) else that.name != null) {
            return false
        }
        if (if (avatarUrl != null) !(avatarUrl == that.avatarUrl) else that.avatarUrl != null) {
            return false
        }
        if (if (subscriberCount != null) !(subscriberCount == that.subscriberCount) else that.subscriberCount != null) {
            return false
        }
        return if (description != null) (description == that.description) else that.description == null
    }

    public override fun hashCode(): Int {
        var result: Int = (uid xor (uid ushr 32)).toInt()
        result = 31 * result + serviceId
        result = 31 * result + url.hashCode()
        result = 31 * result + (if (name != null) name.hashCode() else 0)
        result = 31 * result + (if (avatarUrl != null) avatarUrl.hashCode() else 0)
        result = 31 * result + (if (subscriberCount != null) subscriberCount.hashCode() else 0)
        result = 31 * result + (if (description != null) description.hashCode() else 0)
        return result
    }

    companion object {
        val SUBSCRIPTION_UID: String = "uid"
        val SUBSCRIPTION_TABLE: String = "subscriptions"
        val SUBSCRIPTION_SERVICE_ID: String = "service_id"
        val SUBSCRIPTION_URL: String = "url"
        val SUBSCRIPTION_NAME: String = "name"
        val SUBSCRIPTION_AVATAR_URL: String = "avatar_url"
        val SUBSCRIPTION_SUBSCRIBER_COUNT: String = "subscriber_count"
        val SUBSCRIPTION_DESCRIPTION: String = "description"
        val SUBSCRIPTION_NOTIFICATION_MODE: String = "notification_mode"
        @JvmStatic
        @Ignore
        fun from(info: ChannelInfo): SubscriptionEntity {
            val result: SubscriptionEntity = SubscriptionEntity()
            result.setServiceId(info.getServiceId())
            result.setUrl(info.getUrl())
            result.setData(info.getName(), ImageStrategy.imageListToDbUrl(info.getAvatars()),
                    info.getDescription(), info.getSubscriberCount())
            return result
        }
    }
}
