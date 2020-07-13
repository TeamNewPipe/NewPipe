package org.schabi.newpipe.player.playqueue.events;

public class ErrorEvent implements PlayQueueEvent {
    private final int errorIndex;
    private final int queueIndex;

    public ErrorEvent(final int errorIndex, final int queueIndex) {
        this.errorIndex = errorIndex;
        this.queueIndex = queueIndex;
    }

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.ERROR;
    }

    public int getErrorIndex() {
        return errorIndex;
    }

    public int getQueueIndex() {
        return queueIndex;
    }
}
