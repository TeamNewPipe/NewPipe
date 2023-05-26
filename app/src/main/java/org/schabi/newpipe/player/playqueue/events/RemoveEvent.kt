package org.schabi.newpipe.player.playqueue.events

class RemoveEvent(val removeIndex: Int, val queueIndex: Int) : PlayQueueEvent {

    override fun type(): PlayQueueEventType {
        return PlayQueueEventType.REMOVE
    }
}
