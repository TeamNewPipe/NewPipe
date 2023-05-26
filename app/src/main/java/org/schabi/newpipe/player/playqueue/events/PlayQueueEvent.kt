package org.schabi.newpipe.player.playqueue.events

import java.io.Serializable

interface PlayQueueEvent : Serializable {
    fun type(): PlayQueueEventType?
}
