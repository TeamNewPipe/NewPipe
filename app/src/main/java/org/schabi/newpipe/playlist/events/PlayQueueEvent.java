package org.schabi.newpipe.playlist.events;

public enum PlayQueueEvent {
    INIT,

    // sent when the index is changed
    SELECT,

    // sent when more streams are added to the play queue
    APPEND,

    // sent when a pending stream is removed from the play queue
    REMOVE,

    // sent when two streams swap place in the play queue
    MOVE,

    // sent when a stream is updated
    UPDATE,

    // send when queue is shuffled
    REORDER
}

