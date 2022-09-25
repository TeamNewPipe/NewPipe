package org.schabi.newpipe.player.mediasession;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.Optional;

public class MediaSessionPlayerUi extends PlayerUi {
    private static final String TAG = "MediaSessUi";

    private MediaSessionCompat mediaSession;
    private MediaSessionConnector sessionConnector;

    public MediaSessionPlayerUi(@NonNull final Player player) {
        super(player);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        destroyPlayer(); // release previously used resources

        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setActive(true);

        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, player));
        sessionConnector.setPlayer(getForwardingPlayer());

        sessionConnector.setMetadataDeduplicationEnabled(true);
        sessionConnector.setMediaMetadataProvider(exoPlayer -> buildMediaMetadata());
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        if (sessionConnector != null) {
            sessionConnector.setPlayer(null);
            sessionConnector.setQueueNavigator(null);
            sessionConnector = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        if (sessionConnector != null) {
            // the thumbnail is now loaded: invalidate the metadata to trigger a metadata update
            sessionConnector.invalidateMediaSessionMetadata();
        }
    }


    public void handleMediaButtonIntent(final Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public Optional<MediaSessionCompat.Token> getSessionToken() {
        return Optional.ofNullable(mediaSession).map(MediaSessionCompat::getSessionToken);
    }


    private ForwardingPlayer getForwardingPlayer() {
        // ForwardingPlayer means that all media session actions called on this player are
        // forwarded directly to the connected exoplayer, except for the overridden methods. So
        // override play and pause since our player adds more functionality to them over exoplayer.
        return new ForwardingPlayer(player.getExoPlayer()) {
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
        };
    }

    private MediaMetadataCompat buildMediaMetadata() {
        if (DEBUG) {
            Log.d(TAG, "buildMediaMetadata called");
        }

        // set title and artist
        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName());

        // set duration (-1 for livestreams or if unknown, see the METADATA_KEY_DURATION docs)
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
    }
}
