package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.extractor.stream.StreamInfo

/** A future live broadcast can be displayed but must not start a player without media. */
object UpcomingPlayback {
    @JvmStatic
    fun shouldBlock(info: StreamInfo?): Boolean = info?.isUpcoming == true
}
