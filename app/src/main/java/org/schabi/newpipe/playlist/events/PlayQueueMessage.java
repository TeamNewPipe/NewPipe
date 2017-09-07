package org.schabi.newpipe.playlist.events;

import java.io.Serializable;

public interface PlayQueueMessage extends Serializable {
    PlayQueueEvent type();
}
