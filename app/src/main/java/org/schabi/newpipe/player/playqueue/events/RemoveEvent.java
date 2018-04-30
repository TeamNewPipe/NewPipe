package org.schabi.newpipe.player.playqueue.events;


public class RemoveEvent implements PlayQueueEvent {
    final private int removeIndex;
    final private int queueIndex;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.REMOVE;
    }

    public RemoveEvent(final int removeIndex, final int queueIndex) {
        this.removeIndex = removeIndex;
        this.queueIndex = queueIndex;
    }

    public int getQueueIndex() {
        return queueIndex;
    }

    public int getRemoveIndex() {
        return removeIndex;
    }
}
