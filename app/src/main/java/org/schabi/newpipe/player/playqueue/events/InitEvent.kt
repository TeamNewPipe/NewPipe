package org.schabi.newpipe.player.playqueue.events

class InitEvent : PlayQueueEvent {
    override fun type(): PlayQueueEventType {
        return PlayQueueEventType.INIT
    }
}
