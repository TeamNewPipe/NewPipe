package org.schabi.newpipe.player.mediabrowser;

import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.PlayerService;

public class MediaBrowserConnector {
    private static final String TAG = MediaBrowserConnector.class.getSimpleName();

    private final PlayerService playerService;
    private final @NonNull MediaSessionConnector sessionConnector;
    private final @NonNull MediaSessionCompat mediaSession;

    public MediaBrowserConnector(@NonNull final PlayerService playerService) {
        this.playerService = playerService;
        mediaSession = new MediaSessionCompat(playerService, TAG);
        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setMetadataDeduplicationEnabled(true);
    }

    public @NonNull MediaSessionConnector getSessionConnector() {
        return sessionConnector;
    }

    public void release() {
        mediaSession.release();
    }
}
