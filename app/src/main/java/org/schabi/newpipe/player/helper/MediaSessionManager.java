package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlayQueueNavigator;
import org.schabi.newpipe.player.mediasession.PlayQueuePlaybackController;

public class MediaSessionManager {
    private static final String TAG = "MediaSessionManager";

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final MediaSessionConnector sessionConnector;

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
    public void setLockScreenArt(final NotificationCompat.Builder builder,
                                 @Nullable final Bitmap thumbnailBitmap) {
        if (thumbnailBitmap == null || !mediaSession.isActive()) {
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void clearLockScreenArt(final NotificationCompat.Builder builder) {
        mediaSession.setMetadata(
                new MediaMetadataCompat.Builder()
                        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null)
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
