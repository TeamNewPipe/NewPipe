package org.schabi.newpipe.player.playqueue.events;

public class RecoveryEvent implements PlayQueueEvent {
    private final int index;
    private final long position;

    public RecoveryEvent(final int index, final long position) {
        this.index = index;
        this.position = position;
    }

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.RECOVERY;
    }

    public int getIndex() {
        return index;
    }

    public long getPosition() {
        return position;
    }
}
