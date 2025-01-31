package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.PlayerService;

/** Gets signalled if our PlayerHolder (dis)connects from the PlayerService. */
public interface PlayerHolderLifecycleEventListener {
    void onServiceConnected(PlayerService playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
