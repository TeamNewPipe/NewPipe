package org.schabi.newpipe.player.playqueue.events

import java.io.Serializable

open interface PlayQueueEvent : Serializable {
    fun type(): PlayQueueEventType?
}
