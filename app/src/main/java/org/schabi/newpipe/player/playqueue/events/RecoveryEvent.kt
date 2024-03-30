package org.schabi.newpipe.player.playqueue.events

class RecoveryEvent(val index: Int, val position: Long) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.RECOVERY
    }
}
