package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.mediasession.DummyPlaybackPreparer;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlayQueueNavigator;
import org.schabi.newpipe.player.mediasession.PlayQueuePlaybackController;

public class MediaSessionManager {
    private static final String TAG = "MediaSessionManager";

    private final MediaSessionCompat mediaSession;
    private final MediaSessionConnector sessionConnector;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback) {
        this.mediaSession = new MediaSessionCompat(context, TAG);
        this.sessionConnector = new MediaSessionConnector(mediaSession,
                new PlayQueuePlaybackController(callback));
        this.sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, callback));
        this.sessionConnector.setPlayer(player, new DummyPlaybackPreparer());
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public MediaSessionConnector getSessionConnector() {
        return sessionConnector;
    }
}
