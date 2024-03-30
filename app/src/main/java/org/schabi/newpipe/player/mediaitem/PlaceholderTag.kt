package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Optional
import java.util.function.Function

/**
 * This is a Placeholding [MediaItemTag], designed as a dummy metadata object for
 * any stream that has not been resolved.
 *
 * This object cannot be instantiated and does not hold real metadata of any form.
 */
class PlaceholderTag private constructor(private val extras: Any?) : MediaItemTag {
    override val errors: List<Exception>
        get() {
            return emptyList()
        }
    override val serviceId: Int
        get() {
            return NO_SERVICE_ID
        }
    override val durationSeconds: Long
        get() {
            return 0
        }
    override val thumbnailUrl: String?
        get() {
            return Companion.streamUrl
        }
    override val uploaderUrl: String?
        get() {
            return Companion.streamUrl
        }
    override val streamType: StreamType
        get() {
            return StreamType.NONE
        }

    public override fun <T> getMaybeExtras(type: Class<T>): Optional<T>? {
        return Optional.ofNullable(extras).map(Function({ obj: Any? -> type.cast(obj) }))
    }

    public override fun <T> withExtras(extra: T): MediaItemTag {
        return PlaceholderTag(extra)
    }

    companion object {
        val EMPTY: PlaceholderTag = PlaceholderTag(null)
        val streamUrl: String = "Placeholder"
            get() {
                return Companion.field
            }
        get()
        {
            return PlaceholderTag.Companion.field
        }
        get()
        {
            return PlaceholderTag.Companion.field
        }
    }
}
