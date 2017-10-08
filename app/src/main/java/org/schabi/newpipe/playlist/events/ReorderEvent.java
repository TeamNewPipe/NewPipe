package org.schabi.newpipe.playlist.events;

public class ReorderEvent implements PlayQueueMessage {
    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.REORDER;
    }

    public ReorderEvent() {

    }
}
