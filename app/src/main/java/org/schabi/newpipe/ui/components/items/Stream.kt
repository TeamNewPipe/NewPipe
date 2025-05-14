package org.schabi.newpipe.ui.components.items

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.App
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

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
    val streamId: Long = -1,
) : Info(), Parcelable {

    constructor(item: StreamInfoItem) : this(
        item.serviceId, item.url, item.name, item.thumbnails, item.uploaderName.orEmpty(),
        item.streamType, item.uploaderUrl, item.duration, item.detailText
    )

    constructor(entry: StreamStatisticsEntry) : this(
        entry.streamEntity.serviceId, entry.streamEntity.url, entry.streamEntity.title,
        ImageStrategy.dbUrlToImageList(entry.streamEntity.thumbnailUrl), entry.streamEntity.uploader,
        entry.streamEntity.streamType, entry.streamEntity.uploaderUrl, entry.streamEntity.duration,
        entry.detailText, entry.streamId
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

private val StreamInfoItem.detailText: String
    get() {
        val context = App.instance
        val views = if (viewCount >= 0) {
            when (streamType) {
                StreamType.AUDIO_LIVE_STREAM -> Localization.listeningCount(context, viewCount)
                StreamType.LIVE_STREAM -> Localization.shortWatchingCount(context, viewCount)
                else -> Localization.shortViewCount(context, viewCount)
            }
        } else {
            ""
        }
        val date = Localization.relativeTimeOrTextual(context, uploadDate, textualUploadDate)

        return if (views.isEmpty()) {
            date.orEmpty()
        } else if (date.isNullOrEmpty()) {
            views
        } else {
            "$views • $date"
        }
    }

private val StreamStatisticsEntry.detailText: String
    get() =
        Localization.concatenateStrings(
            Localization.shortViewCount(App.instance, watchCount),
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(latestAccessDate),
            ServiceHelper.getNameOfServiceById(streamEntity.serviceId),
        )
