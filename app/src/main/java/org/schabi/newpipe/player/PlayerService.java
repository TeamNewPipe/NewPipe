/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.ktx.BundleKt;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserImpl;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.player.notification.NotificationUtil;
import org.schabi.newpipe.util.ThemeHelper;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;


/**
 * One service for all players.
 */
public final class PlayerService extends MediaBrowserServiceCompat {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;

    public static final String SHOULD_START_FOREGROUND_EXTRA = "should_start_foreground_extra";
    public static final String BIND_PLAYER_HOLDER_ACTION = "bind_player_holder_action";

    // These objects are used to cleanly separate the Service implementation (in this file) and the
    // media browser and playback preparer implementations. At the moment the playback preparer is
    // only used in conjunction with the media browser.
    private MediaBrowserImpl mediaBrowserImpl;
    private MediaBrowserPlaybackPreparer mediaBrowserPlaybackPreparer;

    // these are instantiated in onCreate() as per
    // https://developer.android.com/training/cars/media#browser_workflow
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector sessionConnector;

    @Nullable
    private Player player;

    private final IBinder mBinder = new PlayerService.LocalBinder(this);

    /**
     * The parameter taken by this {@link Consumer} can be null to indicate the player is being
     * stopped.
     */
    @Nullable
    private Consumer<Player> onPlayerStartedOrStopped = null;


    //region Service lifecycle
    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        ThemeHelper.setTheme(this);

        mediaBrowserImpl = new MediaBrowserImpl(this, this::notifyChildrenChanged);

        // see https://developer.android.com/training/cars/media#browser_workflow
        mediaSession = new MediaSessionCompat(this, "MediaSessionPlayerServ");
        setSessionToken(mediaSession.getSessionToken());
        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setMetadataDeduplicationEnabled(true);

        mediaBrowserPlaybackPreparer = new MediaBrowserPlaybackPreparer(
                this,
                sessionConnector::setCustomErrorMessage,
                () -> sessionConnector.setCustomErrorMessage(null),
                (playWhenReady) -> {
                    if (player != null) {
                        player.onPrepare();
                    }
                }
        );
        sessionConnector.setPlaybackPreparer(mediaBrowserPlaybackPreparer);

        // Note: you might be tempted to create the player instance and call startForeground here,
        // but be aware that the Android system might start the service just to perform media
        // queries. In those cases creating a player instance is a waste of resources, and calling
        // startForeground means creating a useless empty notification. In case it's really needed
        // the player instance can be created here, but startForeground() should definitely not be
        // called here unless the service is actually starting in the foreground, to avoid the
        // useless notification.
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], extras = [" + BundleKt.toDebugString(intent.getExtras())
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }

        // All internal NewPipe intents used to interact with the player, that are sent to the
        // PlayerService using startForegroundService(), will have SHOULD_START_FOREGROUND_EXTRA,
        // to ensure startForeground() is called (otherwise Android will force-crash the app).
        if (intent.getBooleanExtra(SHOULD_START_FOREGROUND_EXTRA, false)) {
            final boolean playerWasNull = (player == null);
            if (playerWasNull) {
                // make sure the player exists, in case the service was resumed
                player = new Player(this, mediaSession, sessionConnector);
            }

            // Be sure that the player notification is set and the service is started in foreground,
            // otherwise, the app may crash on Android 8+ as the service would never be put in the
            // foreground while we said to the system we would do so. The service is always
            // requested to be started in foreground, so always creating a notification if there is
            // no one already and starting the service in foreground should not create any issues.
            // If the service is already started in foreground, requesting it to be started
            // shouldn't do anything.
            player.UIs().get(NotificationPlayerUi.class)
                    .ifPresent(NotificationPlayerUi::createNotificationAndStartForeground);

            if (playerWasNull && onPlayerStartedOrStopped != null) {
                // notify that a new player was created (but do it after creating the foreground
                // notification just to make sure we don't incur, due to slowness, in
                // "Context.startForegroundService() did not then call Service.startForeground()")
                onPlayerStartedOrStopped.accept(player);
            }
        }

        if (player == null) {
            // No need to process media button's actions or other system intents if the player is
            // not running. However, since the current intent might have been issued by the system
            // with `startForegroundService()` (for unknown reasons), we need to ensure that we post
            // a (dummy) foreground notification, otherwise we'd incur in
            // "Context.startForegroundService() did not then call Service.startForeground()". Then
            // we stop the service again.
            Log.d(TAG, "onStartCommand() got a useless intent, closing the service");
            NotificationUtil.startForegroundWithDummyNotification(this);
            destroyPlayerAndStopService();
            return START_NOT_STICKY;
        }

        final PlayerType oldPlayerType = player.getPlayerType();
        player.handleIntent(intent);
        player.handleIntentPost(oldPlayerType);
        player.UIs().get(MediaSessionPlayerUi.class)
                .ifPresent(ui -> ui.handleMediaButtonIntent(intent));

        return START_NOT_STICKY;
    }

    public void stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called");
        }

        if (player != null && !player.exoPlayerIsNull()) {
            // Releases wifi & cpu, disables keepScreenOn, etc.
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            player.smoothStopForImmediateReusing();
        }
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (player != null && !player.videoPlayerSelected()) {
            return;
        }
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        super.onDestroy();

        cleanup();

        mediaBrowserPlaybackPreparer.dispose();
        mediaSession.release();
        mediaBrowserImpl.dispose();
    }

    private void cleanup() {
        if (player != null) {
            if (onPlayerStartedOrStopped != null) {
                // notify that the player is being destroyed
                onPlayerStartedOrStopped.accept(null);
            }
            player.destroy();
            player = null;
        }

        // Should already be handled by MediaSessionPlayerUi, but just to be sure.
        mediaSession.setActive(false);

        // Should already be handled by NotificationUtil.cancelNotificationAndStopForeground() in
        // NotificationPlayerUi, but let's make sure that the foreground service is stopped.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    /**
     * Destroys the player and allows the player instance to be garbage collected. Sets the media
     * session to inactive. Stops the foreground service and removes the player notification
     * associated with it. Tries to stop the {@link PlayerService} completely, but this step will
     * have no effect in case some service connection still uses the service (e.g. the Android Auto
     * system accesses the media browser even when no player is running).
     */
    public void destroyPlayerAndStopService() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayerAndStopService() called");
        }

        cleanup();

        // This only really stops the service if there are no other service connections (see docs):
        // for example the (Android Auto) media browser binder will block stopService().
        // This is why we also stopForeground() above, to make sure the notification is removed.
        // If we were to call stopSelf(), then the service would be surely stopped (regardless of
        // other service connections), but this would be a waste of resources since the service
        // would be immediately restarted by those same connections to perform the queries.
        stopService(new Intent(this, PlayerService.class));
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }
    //endregion

    //region Bind
    @Override
    public IBinder onBind(final Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onBind() called with: intent = [" + intent
                    + "], extras = [" + BundleKt.toDebugString(intent.getExtras()) + "]");
        }

        if (BIND_PLAYER_HOLDER_ACTION.equals(intent.getAction())) {
            // Note that this binder might be reused multiple times while the service is alive, even
            // after unbind() has been called: https://stackoverflow.com/a/8794930 .
            return mBinder;

        } else if (MediaBrowserServiceCompat.SERVICE_INTERFACE.equals(intent.getAction())) {
            // MediaBrowserService also uses its own binder, so for actions related to the media
            // browser service, pass the onBind to the superclass.
            return super.onBind(intent);

        } else {
            // This is an unknown request, avoid returning any binder to not leak objects.
            return null;
        }
    }

    public static class LocalBinder extends Binder {
        private final WeakReference<PlayerService> playerService;

        LocalBinder(final PlayerService playerService) {
            this.playerService = new WeakReference<>(playerService);
        }

        public PlayerService getService() {
            return playerService.get();
        }
    }

    /**
     * @return the current active player instance. May be null, since the player service can outlive
     * the player e.g. to respond to Android Auto media browser queries.
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /**
     * Sets the listener that will be called when the player is started or stopped. If a
     * {@code null} listener is passed, then the current listener will be unset. The parameter taken
     * by the {@link Consumer} can be null to indicate that the player is stopping.
     * @param listener the listener to set or unset
     */
    public void setPlayerListener(@Nullable final Consumer<Player> listener) {
        this.onPlayerStartedOrStopped = listener;
        if (listener != null) {
            // if there is no player, then `null` will be sent here, to ensure the state is synced
            listener.accept(player);
        }
    }
    //endregion

    //region Media browser
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                 final int clientUid,
                                 @Nullable final Bundle rootHints) {
        return mediaBrowserImpl.onGetRoot(clientPackageName, clientUid, rootHints);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId,
                               @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        mediaBrowserImpl.onLoadChildren(parentId, result);
    }

    @Override
    public void onSearch(@NonNull final String query,
                         final Bundle extras,
                         @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        mediaBrowserImpl.onSearch(query, result);
    }
    //endregion
}
