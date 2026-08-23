package org.schabi.newpipe.player;

import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;

public interface PlayerBinderInterface {
    PlayerServiceInterface getService();
    Player getPlayer();
}
