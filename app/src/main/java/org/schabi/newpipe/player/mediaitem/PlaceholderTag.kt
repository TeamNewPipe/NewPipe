package org.schabi.newpipe.player.mediaitem

import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.NO_SERVICE_ID

/**
 * This is a Placeholding [MediaItemTag], designed as a dummy metadata object for
 * any stream that has not been resolved.
 *
 * This object cannot be instantiated and does not hold real metadata of any form.
 */
class PlaceholderTag private constructor(private val extras: Any? = null) : MediaItemTag {

    override val errors: List<Exception> = emptyList()

    override val serviceId: Int = NO_SERVICE_ID

    override val title: String = UNKNOWN_VALUE_INTERNAL

    override val uploaderName: String = UNKNOWN_VALUE_INTERNAL

    override val durationSeconds: Long = 0

    override val streamUrl: String = UNKNOWN_VALUE_INTERNAL

    override val thumbnailUrl: String = UNKNOWN_VALUE_INTERNAL

    override val uploaderUrl: String = UNKNOWN_VALUE_INTERNAL

    override val streamType: StreamType = StreamType.NONE

    override fun <T : Any> getMaybeExtras(type: Class<T>): T? {
        return type.cast(extras)
    }

    override fun <T : Any> withExtras(extra: T): MediaItemTag {
        return PlaceholderTag(extra)
    }

    companion object {
        @JvmField
        val EMPTY = PlaceholderTag()

        private const val UNKNOWN_VALUE_INTERNAL = "Placeholder"
    }
}
