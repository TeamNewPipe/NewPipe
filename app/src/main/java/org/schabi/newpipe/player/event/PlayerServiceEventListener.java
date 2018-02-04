package org.schabi.newpipe.player.event;

import com.google.android.exoplayer2.ExoPlaybackException;

public interface PlayerServiceEventListener extends PlayerEventListener {
    void onFullScreenButtonClicked(boolean fullscreen);

    void onPlayerError(ExoPlaybackException error);

    boolean isPaused();
}