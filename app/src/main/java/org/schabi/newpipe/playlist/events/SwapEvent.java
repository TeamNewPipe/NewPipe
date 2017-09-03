package org.schabi.newpipe.playlist.events;


public class SwapEvent implements PlayQueueMessage {
    final private int from;
    final private int to;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.SWAP;
    }

    public SwapEvent(final int from, final int to) {
        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }
}
