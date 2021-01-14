package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.Player;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(Player player,
                            MainPlayer playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
