package org.schabi.newpipe.player.playqueue

import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class SinglePlayQueue : PlayQueue {
    constructor(item: StreamInfoItem) : super(0, listOf(PlayQueueItem(item)))
    constructor(info: StreamInfo) : super(0, listOf(PlayQueueItem(info)))
    constructor(item: PlayQueueItem) : super(0, listOf(item))
    constructor(info: StreamInfo, startPosition: Long) : super(0, listOf(PlayQueueItem(info))) {
        item?.recoveryPosition = startPosition
    }

    constructor(items: List<StreamInfoItem>, index: Int) : super(index, items.map { PlayQueueItem(it) })

    override fun isComplete(): Boolean = true

    override fun fetch() {
        // Item was already passed in constructor.
        // No further items need to be fetched as this is a PlayQueue with only one item
    }
}
