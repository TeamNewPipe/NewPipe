package org.schabi.newpipe.player.event;

import androidx.annotation.Nullable;

import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(@Nullable Player player,
                            PlayerService playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
