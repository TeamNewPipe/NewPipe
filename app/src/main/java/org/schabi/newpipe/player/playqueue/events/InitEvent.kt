package org.schabi.newpipe.player.playqueue.events

class InitEvent() : PlayQueueEvent {
    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.INIT
    }
}
