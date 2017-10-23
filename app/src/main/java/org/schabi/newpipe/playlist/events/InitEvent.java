package org.schabi.newpipe.playlist.events;

public class InitEvent implements PlayQueueEvent {
    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.INIT;
    }
}
