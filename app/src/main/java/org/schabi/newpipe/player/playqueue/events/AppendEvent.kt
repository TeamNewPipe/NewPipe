package org.schabi.newpipe.player.playqueue.events

class AppendEvent(val amount: Int) : PlayQueueEvent {

    override fun type(): PlayQueueEventType {
        return PlayQueueEventType.APPEND
    }
}
