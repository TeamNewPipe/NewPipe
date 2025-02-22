package org.schabi.newpipe.player.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.event.PlayerHolderLifecycleEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;

/**
 * Singleton that manages a `PlayerService`
 * and can be used to control the player instance through the service.
 */
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

    @Nullable private PlayerServiceEventListener listener;
    @Nullable private PlayerHolderLifecycleEventListener holderListener;

    private final PlayerServiceConnection serviceConnection = new PlayerServiceConnection();
    private boolean bound;

    @Nullable private PlayerService playerService;
    @Nullable private Player player;

    /**
     * Returns the current {@link PlayerType} of the {@link PlayerService} service,
     * otherwise `null` if no service is running.
     *
     * @return Current PlayerType
     */
    @Nullable
    public PlayerType getType() {
        if (player == null) {
            return null;
        }
        return player.getPlayerType();
    }

    public boolean isPlaying() {
        if (player == null) {
            return false;
        }
        return player.isPlaying();
    }

    public boolean isPlayerOpen() {
        return player != null;
    }

    /**
     * Use this method to only allow the user to manipulate the play queue (e.g. by enqueueing via
     * the stream long press menu) when there actually is a play queue to manipulate.
     * @return true only if the player is open and its play queue is ready (i.e. it is not null)
     */
    public boolean isPlayQueueReady() {
        return player != null && player.getPlayQueue() != null;
    }

    public boolean isNotBoundYet() {
        return !bound;
    }

    public int getQueueSize() {
        if (player == null || player.getPlayQueue() == null) {
            // player play queue might be null e.g. while player is starting
            return 0;
        }
        return player.getPlayQueue().size();
    }

    public int getQueuePosition() {
        if (player == null || player.getPlayQueue() == null) {
            return 0;
        }
        return player.getPlayQueue().getIndex();
    }

    public void unsetListeners() {
        listener = null;
        holderListener = null;
    }

    public void setListener(@NonNull final PlayerServiceEventListener newListener,
                            @NonNull final PlayerHolderLifecycleEventListener newHolderListener) {
        listener = newListener;
        holderListener = newHolderListener;

        // Force reload data from service
        if (player != null) {
            holderListener.onServiceConnected(playerService, false);
            player.setFragmentListener(internalListener);
        }
    }

    /**
     * Helper to handle context in common place as using the same
     * context to bind/unbind a service is crucial.
     *
     * @return the common context
     * */
    private Context getCommonContext() {
        return App.getInstance();
    }


    /**
     * Connect to (and if needed start) the {@link PlayerService}
     * and bind {@link PlayerServiceConnection} to it.
     * If the service is already started, only set the listener.
     * @param playAfterConnect If the service is started, start playing immediately
     * @param newListener set this listener
     * @param newHolderListener set this listener
     * */
    public void startService(final boolean playAfterConnect,
                             final PlayerServiceEventListener newListener,
                             final PlayerHolderLifecycleEventListener newHolderListener
    ) {
        final Context context = getCommonContext();
        setListener(newListener, newHolderListener);
        if (bound) {
            return;
        }
        // startService() can be called concurrently and it will give a random crashes
        // and NullPointerExceptions inside the service because the service will be
        // bound twice. Prevent it with unbinding first
        unbind(context);
        ContextCompat.startForegroundService(context, new Intent(context, PlayerService.class));
        serviceConnection.playAfterConnect = playAfterConnect;

        if (DEBUG) {
            Log.d(TAG, "bind() called");
        }

        final Intent serviceIntent = new Intent(context, PlayerService.class);
        bound = context.bindService(serviceIntent, serviceConnection,
                Context.BIND_AUTO_CREATE);
        if (!bound) {
            context.unbindService(serviceConnection);
        }
    }

    public void stopService() {
        final Context context = getCommonContext();
        unbind(context);
        context.stopService(new Intent(context, PlayerService.class));
    }

    /**
     * Call {@link Context#unbindService(ServiceConnection)} on our service
     * (does not necesarily stop the service right away).
     * Remove all our listeners and deinitialize them.
     * @param context shared context
     * */
    private void unbind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "unbind() called");
        }

        if (bound) {
            context.unbindService(serviceConnection);
            bound = false;
            if (player != null) {
                player.removeFragmentListener(internalListener);
            }
            playerService = null;
            player = null;
            if (holderListener != null) {
                holderListener.onServiceDisconnected();
            }
        }
    }

    class PlayerServiceConnection implements ServiceConnection {

        private boolean playAfterConnect = false;

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

            playerService = localBinder.getService();
            player = playerService != null ? playerService.getPlayer() : null;

            if (holderListener != null) {
                holderListener.onServiceConnected(playerService, playAfterConnect);
            }
            if (player != null) {
                player.setFragmentListener(internalListener);
            }
        }
    }

    /**
     * Delegate all {@link PlayerServiceEventListener} events to our current `listener` object.
     * Only difference is that if {@link PlayerServiceEventListener#onServiceStopped()} is called,
     * it also calls {@link PlayerHolder#unbind(Context)}.
     * */
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
}
