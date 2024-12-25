package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.PlayerService;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(PlayerService playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
