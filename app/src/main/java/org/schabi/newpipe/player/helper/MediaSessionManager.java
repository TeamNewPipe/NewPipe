package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import android.support.v4.media.app.NotificationCompat.MediaStyle;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlayQueueNavigator;
import org.schabi.newpipe.player.mediasession.PlayQueuePlaybackController;

public class MediaSessionManager {
    private static final String TAG = "MediaSessionManager";

    @NonNull private final MediaSessionCompat mediaSession;
    @NonNull private final MediaSessionConnector sessionConnector;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback) {
        this.mediaSession = new MediaSessionCompat(context, TAG);
        this.mediaSession.setActive(true);

        this.sessionConnector = new MediaSessionConnector(mediaSession);
        this.sessionConnector.setControlDispatcher(new PlayQueuePlaybackController(callback));
        this.sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, callback));
        this.sessionConnector.setPlayer(player);
    }

    @Nullable
    @SuppressWarnings("UnusedReturnValue")
    public KeyEvent handleMediaButtonIntent(final Intent intent) {
        return MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setLockScreenArt(
            NotificationCompat.Builder builder,
            @Nullable Bitmap thumbnailBitmap
    ) {
        if (thumbnailBitmap == null) {
            return;
        }

        if (!mediaSession.isActive()) {
            return;
        }

        mediaSession.setMetadata(
                new MediaMetadataCompat.Builder()
                        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, thumbnailBitmap)
                        .build()
        );

        MediaStyle mediaStyle = new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken());

        builder.setStyle(mediaStyle);
    }

    /**
     * Should be called on player destruction to prevent leakage.
     */
    public void dispose() {
        this.sessionConnector.setPlayer(null);
        this.sessionConnector.setQueueNavigator(null);
        this.mediaSession.setActive(false);
        this.mediaSession.release();
    }
}
