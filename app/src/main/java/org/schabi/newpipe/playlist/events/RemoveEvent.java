package org.schabi.newpipe.playlist.events;


public class RemoveEvent implements PlayQueueMessage {
    final private int index;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.REMOVE;
    }

    public RemoveEvent(final int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
