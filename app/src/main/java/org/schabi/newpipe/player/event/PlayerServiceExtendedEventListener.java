package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;

public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    void onServiceConnected(VideoPlayerImpl player,
                            MainPlayer playerService,
                            boolean playAfterConnect);
    void onServiceDisconnected();
}
