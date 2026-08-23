package org.schabi.newpipe.player.playqueue.events;

public class ReorderEvent implements PlayQueueEvent {
    private final int fromSelectedIndex;
    private final int toSelectedIndex;

    public ReorderEvent(final int fromSelectedIndex, final int toSelectedIndex) {
        this.fromSelectedIndex = fromSelectedIndex;
        this.toSelectedIndex = toSelectedIndex;
    }

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.REORDER;
    }

    public int getFromSelectedIndex() {
        return fromSelectedIndex;
    }

    public int getToSelectedIndex() {
        return toSelectedIndex;
    }
}
