package org.schabi.newpipe.playlist.events;


public class ErrorEvent implements PlayQueueEvent {
    final private int index;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.ERROR;
    }

    public ErrorEvent(final int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
