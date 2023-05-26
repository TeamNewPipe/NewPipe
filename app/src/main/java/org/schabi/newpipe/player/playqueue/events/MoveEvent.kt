package org.schabi.newpipe.player.playqueue.events

class MoveEvent(val fromIndex: Int, val toIndex: Int) : PlayQueueEvent {

    override fun type(): PlayQueueEventType {
        return PlayQueueEventType.MOVE
    }
}
