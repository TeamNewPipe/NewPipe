package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.player.playqueue.PlayQueue
import java.io.Serializable

internal class StackItem(private val serviceId: Int, private var url: String?,
                         private var title: String, private var playQueue: PlayQueue?) : Serializable {
    fun setUrl(url: String?) {
        this.url = url
    }

    fun setPlayQueue(queue: PlayQueue?) {
        playQueue = queue
    }

    fun getServiceId(): Int {
        return serviceId
    }

    fun getTitle(): String? {
        return title
    }

    fun setTitle(title: String) {
        this.title = title
    }

    fun getUrl(): String? {
        return url
    }

    fun getPlayQueue(): PlayQueue? {
        return playQueue
    }

    public override fun toString(): String {
        return getServiceId().toString() + ":" + getUrl() + " > " + getTitle()
    }
}
