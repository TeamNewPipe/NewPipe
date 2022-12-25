package org.schabi.newpipe.player.mediabrowser;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.PlayerService;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

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
        playerService.setSessionToken(mediaSession.getSessionToken());
    }

    public @NonNull MediaSessionConnector getSessionConnector() {
        return sessionConnector;
    }

    public void release() {
        mediaSession.release();
    }

    @NonNull
    private static final String MY_MEDIA_ROOT_ID = "media_root_id";

    @Nullable
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                                           final int clientUid,
                                                           @Nullable final Bundle rootHints) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onGetRoot(%s, %s, %s)",
                  clientPackageName, clientUid, rootHints));
        }

        return new MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    public Single<List<MediaItem>> onLoadChildren(@NonNull final String parentId) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId));
        }

        final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        return Single.just(mediaItems);
    }
}
