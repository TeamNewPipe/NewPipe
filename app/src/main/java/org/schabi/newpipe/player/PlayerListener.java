package org.schabi.newpipe.player;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public interface PlayerListener {
    void onPlayerMetadataChanged(Player player);
    void onPlayQueueItemChanged(PlayQueueItem item);
}
