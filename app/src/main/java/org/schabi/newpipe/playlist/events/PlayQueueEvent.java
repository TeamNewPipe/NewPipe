package org.schabi.newpipe.playlist.events;

import java.io.Serializable;

public interface PlayQueueEvent extends Serializable {
    PlayQueueEventType type();
}
