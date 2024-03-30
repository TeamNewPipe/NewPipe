package org.schabi.newpipe.player.playqueue.events

class SelectEvent(val oldIndex: Int, val newIndex: Int) : PlayQueueEvent {

    public override fun type(): PlayQueueEventType? {
        return PlayQueueEventType.SELECT
    }
}
