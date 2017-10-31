package org.schabi.newpipe.playlist.events;


public class RecoveryEvent implements PlayQueueEvent {
    final private int index;
    final private long position;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.RECOVERY;
    }

    public RecoveryEvent(final int index, final long position) {
        this.index = index;
        this.position = position;
    }

    public int getIndex() {
        return index;
    }

    public long getPosition() {
        return position;
    }
}
