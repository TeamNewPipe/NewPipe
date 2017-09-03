package org.schabi.newpipe.playlist.events;


public class NextEvent implements PlayQueueMessage {
    final private int newIndex;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.NEXT;
    }

    public NextEvent(final int newIndex) {
        this.newIndex = newIndex;
    }

    public int index() {
        return newIndex;
    }
}
