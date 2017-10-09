package org.schabi.newpipe.playlist.events;

public class MoveEvent implements PlayQueueMessage {
    final private int fromIndex;
    final private int toIndex;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.MOVE;
    }

    public MoveEvent(final int oldIndex, final int newIndex) {
        this.fromIndex = oldIndex;
        this.toIndex = newIndex;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getToIndex() {
        return toIndex;
    }
}
