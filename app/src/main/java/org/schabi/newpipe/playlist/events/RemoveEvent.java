package org.schabi.newpipe.playlist.events;


public class RemoveEvent implements PlayQueueEvent {
    final private int index;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.REMOVE;
    }

    public RemoveEvent(final int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
