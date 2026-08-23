package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(Player player,
                            PlayerServiceInterface playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
