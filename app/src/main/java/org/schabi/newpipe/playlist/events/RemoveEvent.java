package org.schabi.newpipe.playlist.events;


public class RemoveEvent implements PlayQueueMessage {
    final private int index;
    final private boolean isCurrent;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.REMOVE;
    }

    public RemoveEvent(final int index, final boolean isCurrent) {
        this.index = index;
        this.isCurrent = isCurrent;
    }

    public int index() {
        return index;
    }

    public boolean isCurrent() {
        return isCurrent;
    }
}
