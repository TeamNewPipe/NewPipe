package org.schabi.newpipe.playlist;

public enum PlayQueueEvent {
    INIT,

    // sent when the user is seamlessly transitioned by exoplayer to the next stream
    NEXT,

    // sent when the user transitions to an unbuffered period
    SELECT,

    // sent when more streams are added to the play queue
    APPEND,

    // sent when a pending stream is removed from the play queue
    REMOVE,

    // sent when the current stream is removed
    REMOVE_CURRENT,

    // sent when two streams swap place in the play queue
    SWAP,

    // sent when streams is cleared
    CLEAR
}

