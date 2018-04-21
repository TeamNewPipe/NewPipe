package org.schabi.newpipe.player.playqueue.events;


public class SelectEvent implements PlayQueueEvent {
    final private int oldIndex;
    final private int newIndex;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.SELECT;
    }

    public SelectEvent(final int oldIndex, final int newIndex) {
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
    }

    public int getOldIndex() {
        return oldIndex;
    }

    public int getNewIndex() {
        return newIndex;
    }
}
