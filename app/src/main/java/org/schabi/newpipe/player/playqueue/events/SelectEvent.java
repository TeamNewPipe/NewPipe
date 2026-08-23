package org.schabi.newpipe.player.playqueue.events;

public class SelectEvent implements PlayQueueEvent {
    private final int oldIndex;
    private final int newIndex;

    public SelectEvent(final int oldIndex, final int newIndex) {
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
    }

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.SELECT;
    }

    public int getOldIndex() {
        return oldIndex;
    }

    public int getNewIndex() {
        return newIndex;
    }
}
