package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(Player player,
                            PlayerService playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
