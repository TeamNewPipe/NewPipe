package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.Optional
import java.util.function.Function

/**
 * This [MediaItemTag] object is designed to contain metadata for a stream
 * that has failed to load. It supplies metadata from an underlying
 * [PlayQueueItem], which is used by the internal players to resolve actual
 * playback info.
 *
 * This [MediaItemTag] does not contain any [StreamInfo] that can be
 * used to start playback and can be detected by checking [ExceptionTag.getErrors]
 * when in generic form.
 */
class ExceptionTag private constructor(private val item: PlayQueueItem,
                                       override val errors: List<Exception>,
                                       private val extras: Any?) : MediaItemTag {
    override val serviceId: Int
        get() {
            return item.getServiceId()
        }
    override val title: String
        get() {
            return item.getTitle()
        }
    override val uploaderName: String
        get() {
            return item.getUploader()
        }
    override val durationSeconds: Long
        get() {
            return item.getDuration()
        }
    override val streamUrl: String
        get() {
            return item.getUrl()
        }
    override val thumbnailUrl: String?
        get() {
            return ImageStrategy.choosePreferredImage(item.getThumbnails())
        }
    override val uploaderUrl: String?
        get() {
            return item.getUploaderUrl()
        }
    override val streamType: StreamType
        get() {
            return item.getStreamType()
        }

    public override fun <T> getMaybeExtras(type: Class<T>): Optional<T>? {
        return Optional.ofNullable(extras).map(Function({ obj: Any? -> type.cast(obj) }))
    }

    public override fun <T> withExtras(extra: T): MediaItemTag {
        return ExceptionTag(item, errors, extra)
    }

    companion object {
        fun of(playQueueItem: PlayQueueItem,
               errors: List<Exception>): ExceptionTag {
            return ExceptionTag(playQueueItem, errors, null)
        }
    }
}
