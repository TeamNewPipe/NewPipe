package org.schabi.newpipe.database.download

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_ADDED_AT
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_DISPLAY_NAME
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_DURATION_MS
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_FILE_URI
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_ID
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_LAST_CHECKED_AT
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_MIME
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_MISSING_SINCE
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_PARENT_URI
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_QUALITY_LABEL
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_SERVICE_ID
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_SIZE_BYTES
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_STATUS
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_STREAM_UID
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.COLUMN_URL
import org.schabi.newpipe.database.download.DownloadedStreamEntity.Companion.TABLE_NAME
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = TABLE_NAME,
    indices = [Index(value = [COLUMN_STREAM_UID], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [COLUMN_STREAM_UID],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DownloadedStreamEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    var id: Long = 0,

    @ColumnInfo(name = COLUMN_STREAM_UID)
    var streamUid: Long,

    @ColumnInfo(name = COLUMN_SERVICE_ID)
    var serviceId: Int,

    @ColumnInfo(name = COLUMN_URL)
    var url: String,

    @ColumnInfo(name = COLUMN_FILE_URI)
    var fileUri: String,

    @ColumnInfo(name = COLUMN_PARENT_URI)
    var parentUri: String? = null,

    @ColumnInfo(name = COLUMN_DISPLAY_NAME)
    var displayName: String? = null,

    @ColumnInfo(name = COLUMN_MIME)
    var mime: String? = null,

    @ColumnInfo(name = COLUMN_SIZE_BYTES)
    var sizeBytes: Long? = null,

    @ColumnInfo(name = COLUMN_QUALITY_LABEL)
    var qualityLabel: String? = null,

    @ColumnInfo(name = COLUMN_DURATION_MS)
    var durationMs: Long? = null,

    @ColumnInfo(name = COLUMN_STATUS)
    var status: DownloadedStreamStatus,

    @ColumnInfo(name = COLUMN_ADDED_AT)
    var addedAt: Long,

    @ColumnInfo(name = COLUMN_LAST_CHECKED_AT)
    var lastCheckedAt: Long? = null,

    @ColumnInfo(name = COLUMN_MISSING_SINCE)
    var missingSince: Long? = null
) {
    companion object {
        const val TABLE_NAME = "downloaded_streams"
        const val COLUMN_ID = "id"
        const val COLUMN_STREAM_UID = "stream_uid"
        const val COLUMN_SERVICE_ID = "service_id"
        const val COLUMN_URL = "url"
        const val COLUMN_FILE_URI = "file_uri"
        const val COLUMN_PARENT_URI = "parent_uri"
        const val COLUMN_DISPLAY_NAME = "display_name"
        const val COLUMN_MIME = "mime"
        const val COLUMN_SIZE_BYTES = "size_bytes"
        const val COLUMN_QUALITY_LABEL = "quality_label"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_STATUS = "status"
        const val COLUMN_ADDED_AT = "added_at"
        const val COLUMN_LAST_CHECKED_AT = "last_checked_at"
        const val COLUMN_MISSING_SINCE = "missing_since"
    }
}
