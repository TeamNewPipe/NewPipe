package org.schabi.newpipe.ui.components.items

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.concurrent.TimeUnit

sealed class Info

@Parcelize
class Stream(
    val serviceId: Int = NO_SERVICE_ID,
    val url: String = "",
    val name: String = "",
    val thumbnails: List<Image> = emptyList(),
    val uploaderName: String = "",
    val type: StreamType,
    val uploaderUrl: String? = null,
    val duration: Long = TimeUnit.HOURS.toSeconds(1),
    val detailText: String = "",
) : Info(), Parcelable {

    constructor(item: StreamInfoItem, detailText: String) : this(
        item.serviceId, item.url, item.name, item.thumbnails, item.uploaderName.orEmpty(),
        item.streamType, item.uploaderUrl, item.duration, detailText
    )

    constructor(entry: StreamEntity, detailText: String) : this(
        entry.serviceId, entry.url, entry.title,
        ImageStrategy.dbUrlToImageList(entry.thumbnailUrl), entry.uploader,
        entry.streamType, entry.uploaderUrl, entry.duration, detailText
    )

    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, name, type)
        item.duration = duration
        item.uploaderName = uploaderName
        item.uploaderUrl = uploaderUrl
        item.thumbnails = thumbnails
        return item
    }
}

class Playlist(
    val serviceId: Int = NO_SERVICE_ID,
    val url: String = "",
    val name: String = "",
    val thumbnails: List<Image> = emptyList(),
    val uploaderName: String = "",
    val streamCount: Long = 10,
) : Info() {

    constructor(item: PlaylistInfoItem) : this(
        item.serviceId, item.url, item.name, item.thumbnails, item.uploaderName.orEmpty(),
        item.streamCount
    )
}

object Unknown : Info()
