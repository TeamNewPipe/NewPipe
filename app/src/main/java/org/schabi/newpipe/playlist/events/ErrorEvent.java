package org.schabi.newpipe.playlist.events;


public class ErrorEvent implements PlayQueueEvent {
    final private int index;
    final private boolean skippable;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.ERROR;
    }

    public ErrorEvent(final int index, final boolean skippable) {
        this.index = index;
        this.skippable = skippable;
    }

    public int index() {
        return index;
    }

    public boolean isSkippable() {
        return skippable;
    }
}
