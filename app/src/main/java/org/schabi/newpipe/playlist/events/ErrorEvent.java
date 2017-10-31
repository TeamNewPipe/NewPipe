package org.schabi.newpipe.playlist.events;


public class ErrorEvent implements PlayQueueEvent {
    final private int errorIndex;
    final private int queueIndex;
    final private boolean skippable;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.ERROR;
    }

    public ErrorEvent(final int errorIndex, final int queueIndex, final boolean skippable) {
        this.errorIndex = errorIndex;
        this.queueIndex = queueIndex;
        this.skippable = skippable;
    }

    public int getErrorIndex() {
        return errorIndex;
    }

    public int getQueueIndex() {
        return queueIndex;
    }

    public boolean isSkippable() {
        return skippable;
    }
}
