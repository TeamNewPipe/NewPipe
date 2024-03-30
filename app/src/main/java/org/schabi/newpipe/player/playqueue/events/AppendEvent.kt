package org.schabi.newpipe.player.playqueue.events

class AppendEvent(val amount: Int) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.APPEND
    }
}
