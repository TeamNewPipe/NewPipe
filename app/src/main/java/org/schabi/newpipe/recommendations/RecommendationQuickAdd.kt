package org.schabi.newpipe.recommendations

import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Only downloadable regular streams can be quick-added from For You. */
internal object RecommendationQuickAdd {
    fun eligible(item: StreamInfoItem): Boolean {
        return (
            item.streamType == StreamType.VIDEO_STREAM ||
                item.streamType == StreamType.AUDIO_STREAM
            ) &&
            item.url.isNotBlank() && !item.requiresMembership()
    }
}
