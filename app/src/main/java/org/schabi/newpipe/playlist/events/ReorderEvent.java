package org.schabi.newpipe.playlist.events;

public class ReorderEvent implements PlayQueueMessage {
    final private boolean randomize;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.REORDER;
    }

    public ReorderEvent(final boolean randomize) {
        this.randomize = randomize;
    }

    public boolean isRandomize() {
        return randomize;
    }
}
