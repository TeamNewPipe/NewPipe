package org.schabi.newpipe.player.mediasession;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.DefaultPlaybackController;

public class PlayQueuePlaybackController extends DefaultPlaybackController {
    private final MediaSessionCallback callback;

    public PlayQueuePlaybackController(final MediaSessionCallback callback) {
        super();
        this.callback = callback;
    }

    @Override
    public void onPlay(Player player) {
        callback.onPlay();
    }

    @Override
    public void onPause(Player player) {
        callback.onPause();
    }
}
