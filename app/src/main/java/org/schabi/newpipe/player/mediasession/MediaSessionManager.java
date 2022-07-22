package org.schabi.newpipe.player.mediasession;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.ui.VideoPlayerUi;

public class MediaSessionManager {
    private static final String TAG = MediaSessionManager.class.getSimpleName();
    public static final boolean DEBUG = MainActivity.DEBUG;

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final MediaSessionConnector sessionConnector;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player) {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setActive(true);

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, -1, 1)
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE // was play and pause now play/pause
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                        | PlaybackStateCompat.ACTION_STOP)
                .build());

        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, player));
        sessionConnector.setPlayer(new ForwardingPlayer(player.getExoPlayer()) {
            @Override
            public void play() {
                player.play();
                // hide the player controls even if the play command came from the media session
                player.UIs().get(VideoPlayerUi.class).ifPresent(ui -> ui.hideControls(0, 0));
            }

            @Override
            public void pause() {
                player.pause();
            }
        });
    }

    @Nullable
    @SuppressWarnings("UnusedReturnValue")
    public KeyEvent handleMediaButtonIntent(final Intent intent) {
        return MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    /**
     * sets the Metadata - if required.
     *
     * @param title       {@link MediaMetadataCompat#METADATA_KEY_TITLE}
     * @param artist      {@link MediaMetadataCompat#METADATA_KEY_ARTIST}
     * @param albumArt    {@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART}, if not null
     * @param duration    {@link MediaMetadataCompat#METADATA_KEY_DURATION}
     *                    - should be a negative value for unknown durations, e.g. for livestreams
     */
    public void setMetadata(@NonNull final String title,
                            @NonNull final String artist,
                            @Nullable final Bitmap albumArt,
                            final long duration) {
        if (DEBUG) {
            Log.d(TAG, "setMetadata called with: title = [" + title + "], artist = [" + artist
                    + "], albumArt = [" + (albumArt == null ? "null" : albumArt.hashCode())
                    + "], duration = [" + duration + "]");
        }

        if (!mediaSession.isActive()) {
            if (DEBUG) {
                Log.d(TAG, "setMetadata: media session not active, exiting");
            }
            return;
        }

        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        if (albumArt != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt);
        }

        mediaSession.setMetadata(builder.build());
    }

    /**
     * Should be called on player destruction to prevent leakage.
     */
    public void dispose() {
        sessionConnector.setPlayer(null);
        sessionConnector.setQueueNavigator(null);
        mediaSession.setActive(false);
        mediaSession.release();
    }
}
