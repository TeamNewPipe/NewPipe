package org.schabi.newpipe.database.stream.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(
    tableName = "streams",
    indices = [
        Index(value = ["service_id", "url"], unique = true)
    ]
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uid")
    var uid: Long = 0,

    @ColumnInfo(name = "service_id")
    var serviceId: Int,

    @ColumnInfo(name = "url")
    var url: String,

    @ColumnInfo(name = "title")
    var title: String,

    @ColumnInfo(name = "stream_type")
    var streamType: StreamType,

    @ColumnInfo(name = "duration")
    var duration: Long,

    @ColumnInfo(name = "uploader")
    var uploader: String,

    @ColumnInfo(name = "uploader_url")
    var uploaderUrl: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    var thumbnailUrl: String? = null,

    @ColumnInfo(name = "view_count")
    var viewCount: Long? = null,

    @ColumnInfo(name = "textual_upload_date")
    var textualUploadDate: String? = null,

    @ColumnInfo(name = "upload_date")
    var uploadDate: OffsetDateTime? = null,

    @ColumnInfo(name = "is_upload_date_approximation")
    var isUploadDateApproximation: Boolean? = null
) : Serializable {
    @Ignore
    constructor(item: StreamInfoItem) : this(
        serviceId = item.serviceId, url = item.url, title = item.name,
        streamType = item.streamType, duration = item.duration, uploader = item.uploaderName,
        uploaderUrl = item.uploaderUrl,
        thumbnailUrl = ImageStrategy.imageListToDbUrl(item.thumbnails), viewCount = item.viewCount,
        textualUploadDate = item.textualUploadDate, uploadDate = item.uploadDate?.offsetDateTime(),
        isUploadDateApproximation = item.uploadDate?.isApproximation
    )

    @Ignore
    constructor(info: StreamInfo) : this(
        serviceId = info.serviceId, url = info.url, title = info.name,
        streamType = info.streamType, duration = info.duration, uploader = info.uploaderName,
        uploaderUrl = info.uploaderUrl,
        thumbnailUrl = ImageStrategy.imageListToDbUrl(info.thumbnails), viewCount = info.viewCount,
        textualUploadDate = info.textualUploadDate, uploadDate = info.uploadDate?.offsetDateTime(),
        isUploadDateApproximation = info.uploadDate?.isApproximation
    )

    @Ignore
    constructor(item: PlayQueueItem) : this(
        serviceId = item.serviceId,
        url = item.url,
        title = item.title,
        streamType = item.streamType,
        duration = item.duration,
        uploader = item.uploader,
        uploaderUrl = item.uploaderUrl,
        thumbnailUrl = ImageStrategy.imageListToDbUrl(item.thumbnails)
    )

    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, title, streamType)
        item.duration = duration
        item.uploaderName = uploader
        item.uploaderUrl = uploaderUrl
        item.thumbnails = ImageStrategy.dbUrlToImageList(thumbnailUrl)

        if (viewCount != null) item.viewCount = viewCount as Long
        item.textualUploadDate = textualUploadDate
        item.uploadDate = uploadDate?.let {
            DateWrapper(it, isUploadDateApproximation ?: false)
        }

        return item
    }
}
