package org.schabi.newpipe.player.helper;

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

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlayQueueNavigator;
import org.schabi.newpipe.player.mediasession.PlayQueuePlaybackController;

import java.util.Optional;

public class MediaSessionManager {
    private static final String TAG = MediaSessionManager.class.getSimpleName();
    public static final boolean DEBUG = MainActivity.DEBUG;

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final MediaSessionConnector sessionConnector;

    private int lastTitleHashCode;
    private int lastArtistHashCode;
    private long lastDuration;
    private int lastAlbumArtHashCode;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback) {
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
        sessionConnector.setControlDispatcher(new PlayQueuePlaybackController(callback));
        sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, callback));
        sessionConnector.setPlayer(player);
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
     * @param optAlbumArt {@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART}
     * @param duration    {@link MediaMetadataCompat#METADATA_KEY_DURATION}
     *                    - should be a negative value for unknown durations, e.g. for livestreams
     */
    public void setMetadata(@NonNull final String title,
                            @NonNull final String artist,
                            @NonNull final Optional<Bitmap> optAlbumArt,
                            final long duration
    ) {
        if (DEBUG) {
            Log.d(TAG, "setMetadata called:"
                    + " t: " + title
                    + " a: " + artist
                    + " thumb: " + (
                    optAlbumArt.isPresent()
                            ? optAlbumArt.get().hashCode()
                            : "<none>")
                    + " d: " + duration);
        }

        if (!mediaSession.isActive()) {
            if (DEBUG) {
                Log.d(TAG, "setMetadata: mediaSession not active - exiting");
            }
            return;
        }

        if (!checkIfMetadataShouldBeSet(title, artist, optAlbumArt, duration)) {
            if (DEBUG) {
                Log.d(TAG, "setMetadata: No update required - exiting");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "setMetadata: N_Metadata update:"
                    + " t: " + title
                    + " a: " + artist
                    + " thumb: " + (
                    optAlbumArt.isPresent()
                            ? optAlbumArt.get().hashCode()
                            : "<none>")
                    + " d: " + duration);
        }

        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        if (optAlbumArt.isPresent()) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, optAlbumArt.get());
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, optAlbumArt.get());
        }

        mediaSession.setMetadata(builder.build());

        lastTitleHashCode = title.hashCode();
        lastArtistHashCode = artist.hashCode();
        lastDuration = duration;
        if (optAlbumArt.isPresent()) {
            lastAlbumArtHashCode = optAlbumArt.get().hashCode();
        }
    }

    private boolean checkIfMetadataShouldBeSet(
            @NonNull final String title,
            @NonNull final String artist,
            @NonNull final Optional<Bitmap> optAlbumArt,
            final long duration
    ) {
        // Check if the values have changed since the last time
        if (title.hashCode() != lastTitleHashCode
                || artist.hashCode() != lastArtistHashCode
                || duration != lastDuration
                || (optAlbumArt.isPresent() && optAlbumArt.get().hashCode() != lastAlbumArtHashCode)
        ) {
            if (DEBUG) {
                Log.d(TAG,
                        "checkIfMetadataShouldBeSet: true - reason: changed values since last");
            }
            return true;
        }

        // Check if the currently set metadata is valid
        if (getMetadataTitle() == null
                || getMetadataArtist() == null
                // Note that the duration can be <= 0 for live streams
        ) {
            if (DEBUG) {
                if (getMetadataTitle() == null) {
                    Log.d(TAG,
                            "N_getMetadataTitle: title == null");
                } else if (getMetadataArtist() == null) {
                    Log.d(TAG,
                            "N_getMetadataArtist: artist == null");
                }
            }
            return true;
        }

        // If we got an album art check if the current set AlbumArt is null
        if (optAlbumArt.isPresent() && getMetadataAlbumArt() == null) {
            if (DEBUG) {
                Log.d(TAG, "N_getMetadataAlbumArt: thumb == null");
            }
            return true;
        }

        // Default - no update required
        return false;
    }


    @Nullable
    private Bitmap getMetadataAlbumArt() {
        return mediaSession.getController().getMetadata()
                .getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
    }

    @Nullable
    private String getMetadataTitle() {
        return mediaSession.getController().getMetadata()
                .getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    @Nullable
    private String getMetadataArtist() {
        return mediaSession.getController().getMetadata()
                .getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
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
