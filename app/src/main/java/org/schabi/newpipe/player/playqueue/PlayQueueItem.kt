package org.schabi.newpipe.player.playqueue

import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ExtractorHelper

class PlayQueueItem : Serializable {
    val title: String
    val url: String
    val serviceId: Int
    val duration: Long
    val thumbnails: List<Image>
    val uploader: String
    val uploaderUrl: String?
    val streamType: StreamType

    var isAutoQueued: Boolean = false
    var recoveryPosition: Long = RECOVERY_UNSET
    var error: Throwable? = null
        private set

    constructor(info: StreamInfo) : this(
        info.name,
        info.url,
        info.serviceId,
        info.duration,
        info.thumbnails,
        info.uploaderName,
        info.uploaderUrl,
        info.streamType
    ) {
        if (info.startPosition > 0) {
            recoveryPosition = info.startPosition * 1000
        }
    }

    constructor(item: StreamInfoItem) : this(
        item.name,
        item.url,
        item.serviceId,
        item.duration,
        item.thumbnails,
        item.uploaderName,
        item.uploaderUrl,
        item.streamType
    )

    private constructor(
        name: String?,
        url: String?,
        serviceId: Int,
        duration: Long,
        thumbnails: List<Image>,
        uploader: String?,
        uploaderUrl: String?,
        streamType: StreamType
    ) {
        this.title = name ?: ""
        this.url = url ?: ""
        this.serviceId = serviceId
        this.duration = duration
        this.thumbnails = thumbnails
        this.uploader = uploader ?: ""
        this.uploaderUrl = uploaderUrl
        this.streamType = streamType
    }

    /**
     * Whether these two items should be treated as the same stream
     * for the sake of keeping the same player running when e.g. jumping between timestamps.
     *
     * @param other the [PlayQueueItem] to compare against.
     * @return whether the two items are the same so the stream can be re-used.
     */
    fun isSameItem(other: PlayQueueItem?): Boolean {
        if (other == null) return false
        return serviceId == other.serviceId && url == other.url
    }

    suspend fun getStream(): StreamInfo = withContext(Dispatchers.IO) {
        try {
            ExtractorHelper.getStreamInfo(serviceId, url, false)
        } catch (e: Throwable) {
            error = e
            throw e
        }
    }

    companion object {
        const val RECOVERY_UNSET = Long.MIN_VALUE
        private const val serialVersionUID = 1L
    }
}
