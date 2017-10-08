package org.schabi.newpipe.playlist.events;


public class ErrorEvent implements PlayQueueMessage {
    final private int index;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.ERROR;
    }

    public ErrorEvent(final int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
