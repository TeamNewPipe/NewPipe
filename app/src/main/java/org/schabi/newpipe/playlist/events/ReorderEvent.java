package org.schabi.newpipe.playlist.events;

public class ReorderEvent implements PlayQueueEvent {
    private final int fromSelectedIndex;
    private final int toSelectedIndex;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.REORDER;
    }

    public ReorderEvent(final int fromSelectedIndex, final int toSelectedIndex) {
        this.fromSelectedIndex = fromSelectedIndex;
        this.toSelectedIndex = toSelectedIndex;
    }

    public int getFromSelectedIndex() {
        return fromSelectedIndex;
    }

    public int getToSelectedIndex() {
        return toSelectedIndex;
    }
}
