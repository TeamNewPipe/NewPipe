package org.schabi.newpipe.fragments.list.playlist

import org.schabi.newpipe.player.playqueue.PlayQueue

/**
 * Interface for `R.layout.playlist_control` view holders
 * to give access to the play queue.
 */
open interface PlaylistControlViewHolder {
    val playQueue: PlayQueue
}
