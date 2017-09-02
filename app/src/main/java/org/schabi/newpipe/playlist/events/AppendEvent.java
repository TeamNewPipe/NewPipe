package org.schabi.newpipe.playlist.events;


public class AppendEvent implements PlayQueueMessage {
    private int amount;

    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.APPEND;
    }

    public AppendEvent(final int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
