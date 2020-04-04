package org.schabi.newpipe.player.mediasession;

import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;

public class PlayQueuePlaybackController extends DefaultControlDispatcher {
    private final MediaSessionCallback callback;

    public PlayQueuePlaybackController(final MediaSessionCallback callback) {
        super();
        this.callback = callback;
    }

    @Override
    public boolean dispatchSetPlayWhenReady(final Player player, final boolean playWhenReady) {
        if (playWhenReady) {
            callback.onPlay();
        } else {
            callback.onPause();
        }
        return true;
    }
}
