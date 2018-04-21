package org.schabi.newpipe.player.playqueue.events;

public enum PlayQueueEventType {
    INIT,

    // sent when the index is changed
    SELECT,

    // sent when more streams are added to the play queue
    APPEND,

    // sent when a pending stream is removed from the play queue
    REMOVE,

    // sent when two streams swap place in the play queue
    MOVE,

    // sent when queue is shuffled
    REORDER,

    // sent when recovery record is set on a stream
    RECOVERY,

    // sent when the item at index has caused an exception
    ERROR
}

