package org.schabi.newpipe.fragments.list.playlist;

import org.schabi.newpipe.player.playqueue.PlayQueue;

/**
 * Interface for {@code R.layout.playlist_control} view holders
 * to give access to the play queue.
 */
public interface PlaylistControlViewHolder {
    PlayQueue getPlayQueue();
}
