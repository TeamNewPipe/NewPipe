package org.schabi.newpipe.player;

import com.google.android.exoplayer2.SimpleExoPlayer;

public interface LocalPlayerListener {
    void onBlocked(SimpleExoPlayer player);
    void onPlaying(SimpleExoPlayer player);
    void onBuffering(SimpleExoPlayer player);
    void onPaused(SimpleExoPlayer player);
    void onPausedSeek(SimpleExoPlayer player);
    void onCompleted(SimpleExoPlayer player);
}
