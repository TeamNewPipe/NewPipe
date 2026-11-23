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

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import org.schabi.newpipe.ktx.BundleKt;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserImpl;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer;
import org.schabi.newpipe.player.mediasession.MediaSessionActionProvider;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.player.notification.NotificationUtil;
import org.schabi.newpipe.util.ThemeHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;


/**
 * One service for all players.
 */
public final class PlayerService extends MediaLibraryService {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;

    public static final String SHOULD_START_FOREGROUND_EXTRA = "should_start_foreground_extra";
    public static final String BIND_PLAYER_HOLDER_ACTION = "bind_player_holder_action";

    // These objects are used to cleanly separate the Service implementation (in this file) and the
    // media browser and playback preparer implementations. At the moment the playback preparer is
    // only used in conjunction with the media browser.
    private MediaBrowserImpl mediaBrowserImpl;
    private MediaBrowserPlaybackPreparer mediaBrowserPlaybackPreparer;

    private MediaLibrarySession mediaSession;
    private ExoPlayer browserExoPlayer;
    private androidx.media3.common.Player browserPlayer;
    private boolean shouldIgnoreHardwareMediaButtons;

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
        org.schabi.newpipe.download.offline.OfflineLibrary.playbackStarted();
        org.schabi.newpipe.local.cleanup.ExitCleanup.playerStarted(this);

        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        ThemeHelper.setTheme(this);

        mediaBrowserImpl = new MediaBrowserImpl(this, this::notifyLibraryChildrenChanged);

        mediaBrowserPlaybackPreparer = new MediaBrowserPlaybackPreparer(
                this,
                (message, errorCode) -> mediaSession.setPlaybackException(
                        new PlaybackException(message.toString(), null, errorCode)),
                () -> mediaSession.setPlaybackException(null)
        );

        // MediaLibrarySession requires a player even when the service is started only for
        // browsing. This lightweight player intercepts browsed-item playback and starts the real
        // WizeStream player, which is still created lazily.
        browserExoPlayer = new ExoPlayer.Builder(this).build();
        browserPlayer = new MediaBrowserPlayer(browserExoPlayer, mediaBrowserPlaybackPreparer);
        mediaSession = new MediaLibrarySession.Builder(this, browserPlayer,
                createMediaSessionCallback())
                .setId("WizeStreamPlayer")
                .build();

        // Internal playback uses our local binder and does not pass through Media3's
        // controller-binding path. Register explicitly so Media3 creates its notification
        // controller and exports authorized custom actions to Android's platform session.
        addSession(mediaSession);

        // Note: you might be tempted to create the player instance and call startForeground here,
        // but be aware that the Android system might start the service just to perform media
        // queries. In those cases creating a player instance is a waste of resources, and calling
        // startForeground means creating a useless empty notification. In case it's really needed
        // the player instance can be created here, but startForeground() should definitely not be
        // called here unless the service is actually starting in the foreground, to avoid the
        // useless notification.
    }

    @Override
    public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
        if (intent == null) {
            Log.w(TAG, "onStartCommand() called with a null intent");

            // Android may recreate a previously started service with a null intent. There is
            // nothing to handle in that case. If no player is active, clear only this started
            // service instance; bound media-browser clients may keep the service alive.
            if (PlayerServiceStartPolicy
                    .shouldStopStartedServiceOnNullIntent(player != null)) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], extras = [" + BundleKt.toDebugString(intent.getExtras())
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return super.onStartCommand(intent, flags, startId);
        }

        // All internal WizeStream intents used to interact with the player, that are sent to the
        // PlayerService using startForegroundService(), will have SHOULD_START_FOREGROUND_EXTRA,
        // to ensure startForeground() is called (otherwise Android will force-crash the app).
        if (intent.getBooleanExtra(SHOULD_START_FOREGROUND_EXTRA, false)) {
            final boolean playerWasNull = (player == null);
            if (playerWasNull) {
                // make sure the player exists, in case the service was resumed
                player = new Player(this, mediaSession, browserPlayer);
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

        final Player intentPlayer = player;
        final PlayerType oldPlayerType = intentPlayer.getPlayerType();
        intentPlayer.handleIntent(intent);
        if (player == intentPlayer) {
            intentPlayer.handleIntentPost(oldPlayerType);
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
        if (org.schabi.newpipe.local.cleanup.ExitCleanup.isEnabled(this)) {
            destroyPlayerAndStopService();
            return;
        }
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onTrimMemory(final int level) {
        super.onTrimMemory(level);
        // The service can remain alive when the activity moves to the background. Persist
        // a final confirmed position before background reclamation or increased memory pressure.
        if (player != null && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            player.checkpointPlaybackRecovery();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (player != null) {
            player.checkpointPlaybackRecovery();
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        cleanup();

        mediaBrowserPlaybackPreparer.dispose();
        mediaSession.release();
        browserExoPlayer.release();
        mediaBrowserImpl.dispose();
        org.schabi.newpipe.download.offline.OfflineLibrary.playbackStopped();
        org.schabi.newpipe.local.cleanup.ExitCleanup.playerStopped(this);
        super.onDestroy();
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

        } else {
            // MediaLibraryService handles Media3 and legacy media-browser bind actions.
            return super.onBind(intent);
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

    //region Media session and browser
    @Override
    @Nullable
    public MediaLibrarySession onGetSession(
            @NonNull final MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @NonNull
    public MediaLibrarySession getMediaSession() {
        return mediaSession;
    }

    public void setShouldIgnoreHardwareMediaButtons(final boolean shouldIgnore) {
        shouldIgnoreHardwareMediaButtons = shouldIgnore;
    }

    @Override
    public void onUpdateNotification(@NonNull final MediaSession session,
                                     final boolean startInForegroundRequired) {
        // NotificationPlayerUi owns WizeStream's configurable notification and foreground state.
    }

    private void notifyLibraryChildrenChanged(@NonNull final String parentId) {
        if (mediaSession != null) {
            mediaSession.notifyChildrenChanged(parentId, Integer.MAX_VALUE, null);
        }
    }

    @NonNull
    private MediaLibrarySession.Callback createMediaSessionCallback() {
        return new MediaLibrarySession.Callback() {
            @Override
            @NonNull
            public MediaSession.ConnectionResult onConnect(
                    @NonNull final MediaSession session,
                    @NonNull final MediaSession.ControllerInfo controller) {
                final SessionCommands.Builder commands = new SessionCommands.Builder()
                        .addSessionCommands((controller.isTrusted()
                                ? MediaSession.ConnectionResult
                                        .DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                                : MediaSession.ConnectionResult
                                        .DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS).commands);
                for (final String action : MediaSessionActionProvider.supportedActions()) {
                    commands.add(MediaSessionActionProvider.commandFor(action));
                }
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(
                        session, controller)
                        .setAvailableSessionCommands(commands.build())
                        .build();
            }

            @Override
            public void onPostConnect(@NonNull final MediaSession session,
                                      @NonNull final MediaSession.ControllerInfo controller) {
                if (player != null) {
                    player.UIs().get(MediaSessionPlayerUi.class).ifPresent(
                            ui -> ui.onMediaSessionControllerConnected(controller));
                }
            }

            @Override
            public boolean onMediaButtonEvent(@NonNull final MediaSession session,
                                              @NonNull final MediaSession.ControllerInfo controller,
                                              @NonNull final Intent intent) {
                return shouldIgnoreHardwareMediaButtons;
            }

            @Override
            @NonNull
            public ListenableFuture<SessionResult> onCustomCommand(
                    @NonNull final MediaSession session,
                    @NonNull final MediaSession.ControllerInfo controller,
                    @NonNull final SessionCommand command,
                    @NonNull final Bundle args) {
                if (!MediaSessionActionProvider.isSupported(command)) {
                    return Futures.immediateFuture(
                            new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
                }
                sendBroadcast(new Intent(command.customAction).setPackage(getPackageName()));
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }

            @Override
            @NonNull
            public ListenableFuture<List<MediaItem>> onAddMediaItems(
                    @NonNull final MediaSession session,
                    @NonNull final MediaSession.ControllerInfo controller,
                    @NonNull final List<MediaItem> mediaItems) {
                // MediaBrowserPlayer resolves these IDs into WizeStream PlayQueues.
                return Futures.immediateFuture(mediaItems);
            }

            @Override
            @NonNull
            public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                    @NonNull final MediaLibrarySession session,
                    @NonNull final MediaSession.ControllerInfo browser,
                    @Nullable final LibraryParams params) {
                return mediaBrowserImpl.onGetLibraryRoot(browser, params);
            }

            @Override
            @NonNull
            public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                    @NonNull final MediaLibrarySession session,
                    @NonNull final MediaSession.ControllerInfo browser,
                    @NonNull final String parentId,
                    final int page,
                    final int pageSize,
                    @Nullable final LibraryParams params) {
                return mediaBrowserImpl.onGetChildren(parentId, page, pageSize, params);
            }

            @Override
            @NonNull
            public ListenableFuture<LibraryResult<Void>> onSubscribe(
                    @NonNull final MediaLibrarySession session,
                    @NonNull final MediaSession.ControllerInfo browser,
                    @NonNull final String parentId,
                    @Nullable final LibraryParams params) {
                session.notifyChildrenChanged(browser, parentId, Integer.MAX_VALUE, params);
                return Futures.immediateFuture(LibraryResult.ofVoid(params));
            }

            @Override
            @NonNull
            public ListenableFuture<LibraryResult<Void>> onSearch(
                    @NonNull final MediaLibrarySession session,
                    @NonNull final MediaSession.ControllerInfo browser,
                    @NonNull final String query,
                    @Nullable final LibraryParams params) {
                return mediaBrowserImpl.onSearch(session, browser, query, params);
            }

            @Override
            @NonNull
            public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                    @NonNull final MediaLibrarySession session,
                    @NonNull final MediaSession.ControllerInfo browser,
                    @NonNull final String query,
                    final int page,
                    final int pageSize,
                    @Nullable final LibraryParams params) {
                return mediaBrowserImpl.onGetSearchResult(query, page, pageSize, params);
            }
        };
    }

    private static final class MediaBrowserPlayer extends ForwardingPlayer {
        private final MediaBrowserPlaybackPreparer playbackPreparer;

        MediaBrowserPlayer(@NonNull final androidx.media3.common.Player player,
                           @NonNull final MediaBrowserPlaybackPreparer playbackPreparer) {
            super(player);
            this.playbackPreparer = playbackPreparer;
        }

        @Override
        public void play() {
            playbackPreparer.setPlayWhenReady(true);
        }

        @Override
        public void pause() {
            playbackPreparer.setPlayWhenReady(false);
        }

        @Override
        public void setPlayWhenReady(final boolean playWhenReady) {
            playbackPreparer.setPlayWhenReady(playWhenReady);
        }

        @Override
        public void setMediaItem(@NonNull final MediaItem mediaItem) {
            prepare(mediaItem);
        }

        @Override
        public void setMediaItem(@NonNull final MediaItem mediaItem,
                                 final long startPositionMs) {
            prepare(mediaItem);
        }

        @Override
        public void setMediaItem(@NonNull final MediaItem mediaItem,
                                 final boolean resetPosition) {
            prepare(mediaItem);
        }

        @Override
        public void setMediaItems(@NonNull final List<MediaItem> mediaItems) {
            prepare(mediaItems, 0);
        }

        @Override
        public void setMediaItems(@NonNull final List<MediaItem> mediaItems,
                                  final boolean resetPosition) {
            prepare(mediaItems, 0);
        }

        @Override
        public void setMediaItems(@NonNull final List<MediaItem> mediaItems,
                                  final int startIndex,
                                  final long startPositionMs) {
            prepare(mediaItems, startIndex);
        }

        private void prepare(@NonNull final MediaItem mediaItem) {
            playbackPreparer.prepareFromMediaItem(mediaItem);
        }

        private void prepare(@NonNull final List<MediaItem> mediaItems, final int startIndex) {
            if (mediaItems.isEmpty()) {
                return;
            }
            final int safeIndex = Math.max(0, Math.min(startIndex, mediaItems.size() - 1));
            prepare(mediaItems.get(safeIndex));
        }
    }
    //endregion
}
