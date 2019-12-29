package org.schabi.newpipe.player.event;

import com.google.android.exoplayer2.ExoPlaybackException;

public interface PlayerServiceEventListener extends PlayerEventListener {
    void onFullscreenStateChanged(boolean fullscreen);

    void onMoreOptionsLongClicked();

    void onPlayerError(ExoPlaybackException error);

    boolean isFragmentStopped();

    void hideSystemUIIfNeeded();
}