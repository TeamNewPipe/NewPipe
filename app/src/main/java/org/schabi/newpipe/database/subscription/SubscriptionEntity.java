package org.schabi.newpipe.database.subscription;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.util.Constants;

import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_SERVICE_ID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_TABLE;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_URL;

@Entity(tableName = SUBSCRIPTION_TABLE,
        indices = {@Index(value = {SUBSCRIPTION_SERVICE_ID, SUBSCRIPTION_URL}, unique = true)})
public class SubscriptionEntity {
    public static final String SUBSCRIPTION_UID = "uid";
    public static final String SUBSCRIPTION_TABLE = "subscriptions";
    public static final String SUBSCRIPTION_SERVICE_ID = "service_id";
    public static final String SUBSCRIPTION_URL = "url";
    public static final String SUBSCRIPTION_NAME = "name";
    public static final String SUBSCRIPTION_AVATAR_URL = "avatar_url";
    public static final String SUBSCRIPTION_SUBSCRIBER_COUNT = "subscriber_count";
    public static final String SUBSCRIPTION_DESCRIPTION = "description";

    @PrimaryKey(autoGenerate = true)
    private long uid = 0;

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID)
    private int serviceId = Constants.NO_SERVICE_ID;

    @ColumnInfo(name = SUBSCRIPTION_URL)
    private String url;

    @ColumnInfo(name = SUBSCRIPTION_NAME)
    private String name;

    @ColumnInfo(name = SUBSCRIPTION_AVATAR_URL)
    private String avatarUrl;

    @ColumnInfo(name = SUBSCRIPTION_SUBSCRIBER_COUNT)
    private Long subscriberCount;

    @ColumnInfo(name = SUBSCRIPTION_DESCRIPTION)
    private String description;

    @Ignore
    public static SubscriptionEntity from(@NonNull final ChannelInfo info) {
        SubscriptionEntity result = new SubscriptionEntity();
        result.setServiceId(info.getServiceId());
        result.setUrl(info.getUrl());
        result.setData(info.getName(), info.getAvatarUrl(), info.getDescription(),
                info.getSubscriberCount());
        return result;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(final long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(final int serviceId) {
        this.serviceId = serviceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(final String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getSubscriberCount() {
        return subscriberCount;
    }

    public void setSubscriberCount(final Long subscriberCount) {
        this.subscriberCount = subscriberCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Ignore
    public void setData(final String n, final String au, final String d, final Long sc) {
        this.setName(n);
        this.setAvatarUrl(au);
        this.setDescription(d);
        this.setSubscriberCount(sc);
    }

    @Ignore
    public ChannelInfoItem toChannelInfoItem() {
        ChannelInfoItem item = new ChannelInfoItem(getServiceId(), getUrl(), getName());
        item.setThumbnailUrl(getAvatarUrl());
        item.setSubscriberCount(getSubscriberCount());
        item.setDescription(getDescription());
        return item;
    }
}
