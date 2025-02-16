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

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

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
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.ktx.BundleKt;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.util.ThemeHelper;

import java.lang.ref.WeakReference;
import java.util.List;


/**
 * One service for all players.
 */
public final class PlayerService extends MediaBrowserServiceCompat {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;

    public static final String SHOULD_START_FOREGROUND_EXTRA = "should_start_foreground_extra";
    public static final String BIND_PLAYER_HOLDER_ACTION = "bind_player_holder_action";

    // these are instantiated in onCreate() as per
    // https://developer.android.com/training/cars/media#browser_workflow
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector sessionConnector;

    @Nullable
    private Player player;

    private final IBinder mBinder = new PlayerService.LocalBinder(this);


    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        assureCorrectAppLanguage(this);
        ThemeHelper.setTheme(this);

        // see https://developer.android.com/training/cars/media#browser_workflow
        mediaSession = new MediaSessionCompat(this, "MediaSessionPlayerServ");
        setSessionToken(mediaSession.getSessionToken());
        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setMetadataDeduplicationEnabled(true);

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
            if (player == null) {
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
        }

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                && (player == null || player.getPlayQueue() == null)) {
            /*
            No need to process media button's actions if the player is not working, otherwise
            the player service would strangely start with nothing to play
            Stop the service in this case, which will be removed from the foreground and its
            notification cancelled in its destruction
             */
            stopSelf();
            return START_NOT_STICKY;
        }

        if (player != null) {
            player.handleIntent(intent);
            player.UIs().get(MediaSessionPlayerUi.class)
                    .ifPresent(ui -> ui.handleMediaButtonIntent(intent));
        }

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

        mediaSession.setActive(false);
        mediaSession.release();
    }

    private void cleanup() {
        if (player != null) {
            player.destroy();
            player = null;
        }
    }

    public void stopService() {
        cleanup();
        stopSelf();
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

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

        public Player getPlayer() {
            return playerService.get().player;
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                 final int clientUid,
                                 @Nullable final Bundle rootHints) {
        return null;
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId,
                               @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {

    }
}
