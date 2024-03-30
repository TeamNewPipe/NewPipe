package org.schabi.newpipe.player.playqueue.events

class MoveEvent(val fromIndex: Int, val toIndex: Int) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.MOVE
    }
}
