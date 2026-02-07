package org.schabi.newpipe.player.playqueue

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.Serializable
import java.util.Objects
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ExtractorHelper

class PlayQueueItem private constructor(
    val title: String,
    val url: String,
    val serviceId: Int,
    val duration: Long,
    val thumbnails: List<Image>,
    val uploader: String,
    val uploaderUrl: String?,
    val streamType: StreamType
) : Serializable {
    //
    // ////////////////////////////////////////////////////////////////////// */
    // Item States, keep external access out
    //
    // ////////////////////////////////////////////////////////////////////// */
    var isAutoQueued: Boolean = false

    // package-private
    var recoveryPosition = RECOVERY_UNSET
    var error: Throwable? = null
        private set

    constructor(info: StreamInfo) : this(
        info.name.orEmpty(),
        info.url.orEmpty(),
        info.serviceId,
        info.duration,
        info.thumbnails,
        info.uploaderName.orEmpty(),
        info.uploaderUrl,
        info.streamType
    ) {
        if (info.startPosition > 0) {
            this.recoveryPosition = info.startPosition * 1000
        }
    }

    constructor(item: StreamInfoItem) : this(
        item.name.orEmpty(),
        item.url.orEmpty(),
        item.serviceId,
        item.duration,
        item.thumbnails,
        item.uploaderName.orEmpty(),
        item.uploaderUrl,
        item.streamType
    )

    val stream: Single<StreamInfo>
        get() =
            ExtractorHelper
                .getStreamInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .doOnError { throwable -> error = throwable }

    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, title, streamType)
        item.duration = duration
        item.thumbnails = thumbnails
        item.uploaderName = uploader
        item.uploaderUrl = uploaderUrl
        return item
    }

    override fun equals(o: Any?) = o is PlayQueueItem && serviceId == o.serviceId && url == o.url

    override fun hashCode() = Objects.hash(url, serviceId)

    companion object {
        const val RECOVERY_UNSET = Long.MIN_VALUE
    }
}
