package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * This [MediaItemTag] object is designed to contain metadata for a stream
 * that has failed to load. It supplies metadata from an underlying
 * [PlayQueueItem], which is used by the internal players to resolve actual
 * playback info.
 *
 * This [MediaItemTag] does not contain any [org.schabi.newpipe.extractor.stream.StreamInfo] that can be
 * used to start playback and can be detected by checking [getErrors]
 * when in generic form.
 */
class ExceptionTag private constructor(
    private val item: PlayQueueItem,
    private val errorList: List<Exception>,
    private val extras: Any? = null
) : MediaItemTag {

    override val errors: List<Exception> = errorList

    override val serviceId: Int = item.serviceId

    override val title: String? = item.title

    override val uploaderName: String? = item.uploader

    override val durationSeconds: Long = item.duration

    override val streamUrl: String = item.url ?: ""

    override val thumbnailUrl: String? = ImageStrategy.choosePreferredImage(item.thumbnails)

    override val uploaderUrl: String? = item.uploaderUrl

    override val streamType: StreamType? = item.streamType

    override fun <T : Any> getMaybeExtras(type: Class<T>): T? {
        return type.cast(extras)
    }

    override fun <T : Any> withExtras(extra: T): MediaItemTag {
        return ExceptionTag(item, errors, extra)
    }

    companion object {
        @JvmStatic
        fun of(playQueueItem: PlayQueueItem, errors: List<Exception>): ExceptionTag {
            return ExceptionTag(playQueueItem, errors)
        }
    }
}
