package org.schabi.newpipe.player.playqueue.events;

public class MoveEvent implements PlayQueueEvent {
    final private int fromIndex;
    final private int toIndex;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.MOVE;
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
