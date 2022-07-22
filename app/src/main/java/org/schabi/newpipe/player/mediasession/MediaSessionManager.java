package org.schabi.newpipe.player.mediasession;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.Optional;

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

        sessionConnector.setMetadataDeduplicationEnabled(true);
        sessionConnector.setMediaMetadataProvider(exoPlayer -> {
            if (DEBUG) {
                Log.d(TAG, "MediaMetadataProvider#getMetadata called");
            }

            // set title and artist
            final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName());

            // set duration (-1 for livestreams, since they don't have a duration)
            final long duration = player.getCurrentStreamInfo()
                    .filter(info -> !StreamTypeUtil.isLiveStream(info.getStreamType()))
                    .map(info -> info.getDuration() * 1000L)
                    .orElse(-1L);
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

            // set album art, unless the user asked not to, or there is no thumbnail available
            final boolean showThumbnail = player.getPrefs().getBoolean(
                    context.getString(R.string.show_thumbnail_key), true);
            Optional.ofNullable(player.getThumbnail())
                    .filter(bitmap -> showThumbnail)
                    .ifPresent(bitmap -> {
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap);
            });

            return builder.build();
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

    void triggerMetadataUpdate() {
        sessionConnector.invalidateMediaSessionMetadata();
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
