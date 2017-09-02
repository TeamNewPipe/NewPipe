package org.schabi.newpipe.playlist.events;

public class InitEvent implements PlayQueueMessage {
    @Override
    public PlayQueueEvent type() {
        return PlayQueueEvent.INIT;
    }
}
