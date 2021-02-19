package org.schabi.newpipe.player.playqueue.events;

public class RemoveAllEvent implements PlayQueueEvent  {

    public RemoveAllEvent() {
    }

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.REMOVE_ALL;
    }
}
