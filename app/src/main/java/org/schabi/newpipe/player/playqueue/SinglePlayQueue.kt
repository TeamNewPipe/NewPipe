package org.schabi.newpipe.player.playqueue

import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class SinglePlayQueue : PlayQueue {
    constructor(item: StreamInfoItem) : super(0, java.util.List.of<PlayQueueItem?>(PlayQueueItem(item)))
    constructor(info: StreamInfo?) : super(0, java.util.List.of<PlayQueueItem?>(PlayQueueItem((info)!!)))
    constructor(info: StreamInfo, startPosition: Long) : super(0, java.util.List.of<PlayQueueItem?>(PlayQueueItem(info))) {
        getItem().setRecoveryPosition(startPosition)
    }

    constructor(items: List<StreamInfoItem>, index: Int) : super(index, playQueueItemsOf(items))

    override val isComplete: Boolean
        get() {
            return true
        }

    public override fun fetch() {
        // Item was already passed in constructor.
        // No further items need to be fetched as this is a PlayQueue with only one item
    }

    companion object {
        private fun playQueueItemsOf(items: List<StreamInfoItem>): List<PlayQueueItem?> {
            val playQueueItems: MutableList<PlayQueueItem?> = ArrayList(items.size)
            for (item: StreamInfoItem in items) {
                playQueueItems.add(PlayQueueItem(item))
            }
            return playQueueItems
        }
    }
}
