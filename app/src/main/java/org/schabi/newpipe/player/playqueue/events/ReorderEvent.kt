package org.schabi.newpipe.player.playqueue.events

class ReorderEvent(val fromSelectedIndex: Int, val toSelectedIndex: Int) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.REORDER
    }
}
