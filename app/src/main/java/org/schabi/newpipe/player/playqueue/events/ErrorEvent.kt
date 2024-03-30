package org.schabi.newpipe.player.playqueue.events

class ErrorEvent(val errorIndex: Int, val queueIndex: Int) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.ERROR
    }
}
