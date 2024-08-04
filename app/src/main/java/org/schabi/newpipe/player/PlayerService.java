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
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.mediabrowser.MediaBrowserConnector;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.util.ThemeHelper;

import java.lang.ref.WeakReference;

import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * One service for all players.
 */
public final class PlayerService extends MediaBrowserServiceCompat {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;

    @Nullable
    private Player player;

    private final IBinder mBinder = new PlayerService.LocalBinder(this);


    private MediaBrowserConnector mediaBrowserConnector;
    private final CompositeDisposable compositeDisposableLoadChildren = new CompositeDisposable();


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

        mediaBrowserConnector = new MediaBrowserConnector(this);
    }

    private void initializePlayerIfNeeded() {
        if (player == null) {
            player = new Player(this);
            /*
            Create the player notification and start immediately the service in foreground,
            otherwise if nothing is played or initializing the player and its components (especially
            loading stream metadata) takes a lot of time, the app would crash on Android 8+ as the
            service would never be put in the foreground while we said to the system we would do so
             */
            player.UIs().get(NotificationPlayerUi.class)
                    .ifPresent(NotificationPlayerUi::createNotificationAndStartForeground);
        }
    }

    // Suppress Sonar warning to not always return the same value, as we need to do some actions
    // before returning
    @SuppressWarnings("squid:S3516")
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }

        /*
        Be sure that the player notification is set and the service is started in foreground,
        otherwise, the app may crash on Android 8+ as the service would never be put in the
        foreground while we said to the system we would do so
        The service is always requested to be started in foreground, so always creating a
        notification if there is no one already and starting the service in foreground should
        not create any issues
        If the service is already started in foreground, requesting it to be started shouldn't
        do anything
         */
        if (player != null) {
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

        initializePlayerIfNeeded();
        Objects.requireNonNull(player).handleIntent(intent);
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

        cleanup();

        if (mediaBrowserConnector != null) {
            mediaBrowserConnector.release();
            mediaBrowserConnector = null;
        }

        compositeDisposableLoadChildren.clear();
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
    public IBinder onBind(@NonNull final Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return mBinder;
    }

    @NonNull
    public MediaSessionConnector getSessionConnector() {
        return mediaBrowserConnector.getSessionConnector();
    }

    // MediaBrowserServiceCompat methods
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                 final int clientUid,
                                 @Nullable final Bundle rootHints) {
        return mediaBrowserConnector.onGetRoot(clientPackageName, clientUid, rootHints);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId,
                               @NonNull final Result<List<MediaItem>> result) {
        result.detach();
        final Disposable disposable = mediaBrowserConnector.onLoadChildren(parentId)
                .subscribe(result::sendResult);
        compositeDisposableLoadChildren.add(disposable);
    }

    @Override
    public void onSearch(@NonNull final String query,
                         final Bundle extras,
                         @NonNull final Result<List<MediaItem>> result) {
        mediaBrowserConnector.onSearch(query, result);
    }

    public static final class LocalBinder extends Binder {
        private final WeakReference<PlayerService> playerService;

        LocalBinder(final PlayerService playerService) {
            this.playerService = new WeakReference<>(playerService);
        }

        @Nullable
        public PlayerService getService() {
            return playerService.get();
        }

        @Nullable
        public Player getPlayer() {
            return playerService.get().player;
        }
    }
}
