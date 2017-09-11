package org.schabi.newpipe.playlist.events;

public class UpdateEvent implements PlayQueueMessage {
    final private int updatedIndex;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.UPDATE;
    }

    public UpdateEvent(final int updatedIndex) {
        this.updatedIndex = updatedIndex;
    }

    public int index() {
        return updatedIndex;
    }
}
