package org.schabi.newpipe.player.playqueue

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ExtractorHelper
import java.io.Serializable

class PlayQueueItem private constructor(name: String?, url: String?,
                                        val serviceId: Int, val duration: Long,
                                        val thumbnails: List<Image>, uploader: String?,
                                        val uploaderUrl: String, val streamType: StreamType) : Serializable {
    val title: String
    @JvmField
    val url: String
    val uploader: String

    ////////////////////////////////////////////////////////////////////////////
    // Item States, keep external access out
    ////////////////////////////////////////////////////////////////////////////
    var isAutoQueued: Boolean = false
    /*package-private*/ var recoveryPosition: Long
    var error: Throwable? = null
        private set

    internal constructor(info: StreamInfo) : this(info.getName(), info.getUrl(), info.getServiceId(), info.getDuration(),
            info.getThumbnails(), info.getUploaderName(),
            info.getUploaderUrl(), info.getStreamType()) {
        if (info.getStartPosition() > 0) {
            recoveryPosition = info.getStartPosition() * 1000
        }
    }

    internal constructor(item: StreamInfoItem) : this(item.getName(), item.getUrl(), item.getServiceId(), item.getDuration(),
            item.getThumbnails(), item.getUploaderName(),
            item.getUploaderUrl(), item.getStreamType())

    init {
        title = if (name != null) name else EMPTY_STRING
        this.url = if (url != null) url else EMPTY_STRING
        this.uploader = if (uploader != null) uploader else EMPTY_STRING
        recoveryPosition = RECOVERY_UNSET
    }

    val stream: Single<StreamInfo>
        get() {
            return ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .doOnError(Consumer({ throwable: Throwable? -> error = throwable }))
        }

    companion object {
        val RECOVERY_UNSET: Long = Long.MIN_VALUE
        private val EMPTY_STRING: String = ""
    }
}
