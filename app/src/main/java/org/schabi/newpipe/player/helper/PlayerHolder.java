package org.schabi.newpipe.player.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Optional;
import java.util.function.Consumer;

public final class PlayerHolder {

    private PlayerHolder() {
    }

    private static PlayerHolder instance;
    public static synchronized PlayerHolder getInstance() {
        if (PlayerHolder.instance == null) {
            PlayerHolder.instance = new PlayerHolder();
        }
        return PlayerHolder.instance;
    }

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = PlayerHolder.class.getSimpleName();

    @Nullable private PlayerServiceExtendedEventListener listener;

    private final PlayerServiceConnection serviceConnection = new PlayerServiceConnection();
    private boolean bound;
    @Nullable private PlayerService playerService;

    private Optional<Player> getPlayer() {
        return Optional.ofNullable(playerService)
                .flatMap(s -> Optional.ofNullable(s.getPlayer()));
    }

    private Optional<PlayQueue> getPlayQueue() {
        // player play queue might be null e.g. while player is starting
        return getPlayer().flatMap(p -> Optional.ofNullable(p.getPlayQueue()));
    }

    /**
     * Returns the current {@link PlayerType} of the {@link PlayerService} service,
     * otherwise `null` if no service is running.
     *
     * @return Current PlayerType
     */
    @Nullable
    public PlayerType getType() {
        return getPlayer().map(Player::getPlayerType).orElse(null);
    }

    public boolean isPlaying() {
        return getPlayer().map(Player::isPlaying).orElse(false);
    }

    public boolean isPlayerOpen() {
        return getPlayer().isPresent();
    }

    /**
     * Use this method to only allow the user to manipulate the play queue (e.g. by enqueueing via
     * the stream long press menu) when there actually is a play queue to manipulate.
     * @return true only if the player is open and its play queue is ready (i.e. it is not null)
     */
    public boolean isPlayQueueReady() {
        return getPlayQueue().isPresent();
    }

    public boolean isBound() {
        return bound;
    }

    public int getQueueSize() {
        return getPlayQueue().map(PlayQueue::size).orElse(0);
    }

    public int getQueuePosition() {
        return getPlayQueue().map(PlayQueue::getIndex).orElse(0);
    }

    public void setListener(@Nullable final PlayerServiceExtendedEventListener newListener) {
        listener = newListener;

        if (listener == null) {
            return;
        }

        // Force reload data from service
        if (playerService != null) {
            listener.onServiceConnected(playerService);
            startPlayerListener();
            // ^ will call listener.onPlayerConnected() down the line if there is an active player
        }
    }

    // helper to handle context in common place as using the same
    // context to bind/unbind a service is crucial
    private Context getCommonContext() {
        return App.getInstance();
    }

    /**
     * Connect to (and if needed start) the {@link PlayerService}
     * and bind {@link PlayerServiceConnection} to it.
     * If the service is already started, only set the listener.
     * @param playAfterConnect If this holder’s service was already started,
     *                         start playing immediately
     * @param newListener set this listener
     * */
    public void startService(final boolean playAfterConnect,
                             final PlayerServiceExtendedEventListener newListener) {
        if (DEBUG) {
            Log.d(TAG, "startService() called with playAfterConnect=" + playAfterConnect);
        }
        final Context context = getCommonContext();
        setListener(newListener);
        if (bound) {
            return;
        }
        // startService() can be called concurrently and it will give a random crashes
        // and NullPointerExceptions inside the service because the service will be
        // bound twice. Prevent it with unbinding first
        unbind(context);
        final Intent intent = new Intent(context, PlayerService.class);
        intent.putExtra(PlayerService.SHOULD_START_FOREGROUND_EXTRA, true);
        ContextCompat.startForegroundService(context, intent);
        serviceConnection.doPlayAfterConnect(playAfterConnect);
        bind(context);
    }

    public void stopService() {
        if (DEBUG) {
            Log.d(TAG, "stopService() called");
        }
        if (playerService != null) {
            playerService.destroyPlayerAndStopService();
        }
        final Context context = getCommonContext();
        unbind(context);
        // destroyPlayerAndStopService() already runs the next line of code, but run it again just
        // to make sure to stop the service even if playerService is null by any chance.
        context.stopService(new Intent(context, PlayerService.class));
    }

    class PlayerServiceConnection implements ServiceConnection {

        private boolean playAfterConnect = false;

        public void doPlayAfterConnect(final boolean playAfterConnection) {
            this.playAfterConnect = playAfterConnection;
        }

        @Override
        public void onServiceDisconnected(final ComponentName compName) {
            if (DEBUG) {
                Log.d(TAG, "Player service is disconnected");
            }

            final Context context = getCommonContext();
            unbind(context);
        }

        @Override
        public void onServiceConnected(final ComponentName compName, final IBinder service) {
            if (DEBUG) {
                Log.d(TAG, "Player service is connected");
            }
            final PlayerService.LocalBinder localBinder = (PlayerService.LocalBinder) service;

            @Nullable final PlayerService s = localBinder.getService();
            if (s == null) {
                throw new IllegalArgumentException(
                        "PlayerService.LocalBinder.getService() must never be"
                                + "null after the service connects");
            }
            playerService = s;
            if (listener != null) {
                listener.onServiceConnected(s);
                getPlayer().ifPresent(p -> listener.onPlayerConnected(p, playAfterConnect));
            }
            startPlayerListener();
            // ^ will call listener.onPlayerConnected() down the line if there is an active player

            // notify the main activity that binding the service has completed, so that it can
            // open the bottom mini-player
            NavigationHelper.sendPlayerStartedEvent(s);
        }
    }

    private void bind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called");
        }
        // BIND_AUTO_CREATE starts the service if it's not already running
        bound = bind(context, Context.BIND_AUTO_CREATE);
        if (!bound) {
            context.unbindService(serviceConnection);
        }
    }

    public void tryBindIfNeeded(final Context context) {
        if (!bound) {
            // flags=0 means the service will not be started if it does not already exist. In this
            // case the return value is not useful, as a value of "true" does not really indicate
            // that the service is going to be bound.
            bind(context, 0);
        }
    }

    private boolean bind(final Context context, final int flags) {
        final Intent serviceIntent = new Intent(context, PlayerService.class);
        serviceIntent.setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION);
        return context.bindService(serviceIntent, serviceConnection, flags);
    }

    private void unbind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "unbind() called");
        }

        if (bound) {
            context.unbindService(serviceConnection);
            bound = false;
            stopPlayerListener();
            playerService = null;
            if (listener != null) {
                listener.onPlayerDisconnected();
                listener.onServiceDisconnected();
            }
        }
    }

    private void startPlayerListener() {
        if (playerService != null) {
            // setting the player listener will take care of calling relevant callbacks if the
            // player in the service is (not) already active, also see playerStateListener below
            playerService.setPlayerListener(playerStateListener);
        }
        getPlayer().ifPresent(p -> p.setFragmentListener(internalListener));
    }

    private void stopPlayerListener() {
        if (playerService != null) {
            playerService.setPlayerListener(null);
        }
        getPlayer().ifPresent(p -> p.removeFragmentListener(internalListener));
    }

    /**
     * This listener will be held by the players created by {@link PlayerService}.
     */
    private final PlayerServiceEventListener internalListener =
            new PlayerServiceEventListener() {
                @Override
                public void onViewCreated() {
                    if (listener != null) {
                        listener.onViewCreated();
                    }
                }

                @Override
                public void onFullscreenStateChanged(final boolean fullscreen) {
                    if (listener != null) {
                        listener.onFullscreenStateChanged(fullscreen);
                    }
                }

                @Override
                public void onScreenRotationButtonClicked() {
                    if (listener != null) {
                        listener.onScreenRotationButtonClicked();
                    }
                }

                @Override
                public void onMoreOptionsLongClicked() {
                    if (listener != null) {
                        listener.onMoreOptionsLongClicked();
                    }
                }

                @Override
                public void onPlayerError(final PlaybackException error,
                                          final boolean isCatchableException) {
                    if (listener != null) {
                        listener.onPlayerError(error, isCatchableException);
                    }
                }

                @Override
                public void hideSystemUiIfNeeded() {
                    if (listener != null) {
                        listener.hideSystemUiIfNeeded();
                    }
                }

                @Override
                public void onQueueUpdate(final PlayQueue queue) {
                    if (listener != null) {
                        listener.onQueueUpdate(queue);
                    }
                }

                @Override
                public void onPlaybackUpdate(final int state,
                                             final int repeatMode,
                                             final boolean shuffled,
                                             final PlaybackParameters parameters) {
                    if (listener != null) {
                        listener.onPlaybackUpdate(state, repeatMode, shuffled, parameters);
                    }
                }

                @Override
                public void onProgressUpdate(final int currentProgress,
                                             final int duration,
                                             final int bufferPercent) {
                    if (listener != null) {
                        listener.onProgressUpdate(currentProgress, duration, bufferPercent);
                    }
                }

                @Override
                public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
                    if (listener != null) {
                        listener.onMetadataUpdate(info, queue);
                    }
                }

                @Override
                public void onServiceStopped() {
                    if (listener != null) {
                        listener.onServiceStopped();
                    }
                    unbind(getCommonContext());
                }
            };

    /**
     * This listener will be held by bound {@link PlayerService}s to notify of the player starting
     * or stopping. This is necessary since the service outlives the player e.g. to answer Android
     * Auto media browser queries.
     */
    private final Consumer<Player> playerStateListener = (@Nullable final Player player) -> {
        if (listener != null) {
            if (player == null) {
                // player.fragmentListener=null is already done by player.stopActivityBinding(),
                // which is called by player.destroy(), which is in turn called by PlayerService
                // before setting its player to null
                listener.onPlayerDisconnected();
            } else {
                listener.onPlayerConnected(player, serviceConnection.playAfterConnect);
                player.setFragmentListener(internalListener);
            }
        }
    };
}
