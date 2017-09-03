package org.schabi.newpipe.playlist.events;


public class SelectEvent implements PlayQueueMessage {
    final private int newIndex;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.SELECT;
    }

    public SelectEvent(final int newIndex) {
        this.newIndex = newIndex;
    }

    public int index() {
        return newIndex;
    }
}
