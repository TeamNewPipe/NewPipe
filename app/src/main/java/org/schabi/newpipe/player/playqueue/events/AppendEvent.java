package org.schabi.newpipe.player.playqueue.events;


public class AppendEvent implements PlayQueueEvent {
    final private int amount;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.APPEND;
    }

    public AppendEvent(final int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
