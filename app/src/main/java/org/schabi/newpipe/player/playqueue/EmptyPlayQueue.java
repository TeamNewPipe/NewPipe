package org.schabi.newpipe.player.playqueue;

import java.util.List;

public class EmptyPlayQueue extends PlayQueue {
    public EmptyPlayQueue() {
        super(0, List.of());
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {
    }
}
