package org.schabi.newpipe.database.notification

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** An alert snapshot, independent of the prunable feed and stream cache. */
@Entity(
    tableName = "pending_stream_notifications",
    primaryKeys = ["profile_id", "generation", "service_id", "stream_url"],
    indices = [Index(value = ["profile_id", "generation", "dismissed"])]
)
data class PendingStreamNotification(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "generation") val generation: String,
    @ColumnInfo(name = "service_id") val serviceId: Int,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "channel_url") val channelUrl: String,
    @ColumnInfo(name = "channel_name") val channelName: String,
    @ColumnInfo(name = "stream_title") val streamTitle: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "dismissed", defaultValue = "0") val dismissed: Boolean = false
)
