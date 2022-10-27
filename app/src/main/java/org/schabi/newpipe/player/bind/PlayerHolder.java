package org.schabi.newpipe.player.bind;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.playqueue.EmptyPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public enum PlayerHolder {
    INSTANCE;

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = PlayerHolder.class.getSimpleName();


    private final PlayerListenerWrapper listenerWrapper =
            new PlayerListenerWrapper(() -> unbind(getCommonContext()));

    private final PlayerServiceConnection serviceConnection = new PlayerServiceConnection();
    private boolean bound;
    @Nullable private PlayerService playerService;
    @Nullable private Player player;

    private final List<Consumer<Player>> actionsWhenConnected = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/
    //region Utils

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

    public int getQueueSize() {
        if (player == null || player.getPlayQueue() == null) {
            // player play queue might be null e.g. while player is starting
            return 0;
        }
        return player.getPlayQueue().size();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Service
    //////////////////////////////////////////////////////////////////////////*/
    //region Service

    public boolean isBound() {
        return bound;
    }

    private void startService() {
        if (bound) {
            return;
        }

        // clear actions to execute on connection
        disposables.clear();
        actionsWhenConnected.clear();

        // startPlayerService() can be called concurrently and it will give random crashes and
        // NullPointerExceptions inside the service because the service will be bound twice. Prevent
        // it with unbinding first
        final Context context = getCommonContext();
        unbind(context);
        ContextCompat.startForegroundService(context, new Intent(context, PlayerService.class));
        bind(context);
    }

    public void stopService() {
        final Context context = getCommonContext();
        unbind(context);
        context.stopService(new Intent(context, PlayerService.class));
    }

    public void setListener(@Nullable final PlayerServiceExtendedEventListener newListener) {
        listenerWrapper.setListener(newListener);

        // Force reload data from service
        if (player != null) {
            listenerWrapper.onServiceConnected(player, playerService);
            startPlayerListener();
        }
    }

    public Optional<Player> getPlayer() {
        return Optional.ofNullable(player);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Starting and modifying the player
    //////////////////////////////////////////////////////////////////////////*/
    //region Starting and modifying the player

    public void start(final PlayerType type, final PlayQueue queue) {
        startService();
        loadStreamStateAndRunAction(queue, (player, queueWithStreamState) -> {
            player.changeType(type);
            player.changePlayQueue(queueWithStreamState);
        });
    }

    public void startWithoutResuming(final PlayerType type, final PlayQueue queue) {
        startService();
        runAction(player -> {
            player.changeType(type);
            player.changePlayQueue(queue);
        });
    }

    public void enqueue(final PlayQueue queue) {
        runAction(player -> player.getPlayQueue().append(queue.getStreams()));
    }

    public void enqueueNext(final PlayQueueItem playQueueItem) {
        runAction(player -> {
            final PlayQueue playerQueue = player.getPlayQueue();
            final int currentIndex = playerQueue.getIndex();
            playerQueue.append(List.of(playQueueItem));
            playerQueue.move(playerQueue.size() - 1, currentIndex + 1);
        });
    }

    public void startOrEnqueue(final PlayerType type, final PlayQueue queue) {
        if (bound) {
            runAction(player -> player.changeType(type));
            enqueue(queue);
        } else {
            startWithoutResuming(type, queue);
        }
    }

    public void runAction(final Consumer<Player> action) {
        if (player != null) {
            action.accept(player);
        } else if (bound) {
            // if the service is bound but the player is null, the player is still starting
            actionsWhenConnected.add(action);
        } else {
            Log.e(TAG, "Ignoring action to run while player not connected and not starting",
                    new Exception()); // include stack trace
        }
    }

    private void loadStreamStateAndRunAction(final PlayQueue queue,
                                             final BiConsumer<Player, PlayQueue> action) {
        final Runnable whenQueueReady = () -> runAction(player -> action.accept(player, queue));

        disposables.add(new HistoryRecordManager(getCommonContext())
                .loadStreamState(Objects.requireNonNull(queue.getItem()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(streamStateEntity -> {
                            queue.setRecovery(queue.getIndex(), streamStateEntity.getProgressMillis());
                            whenQueueReady.run();
                        }, e -> {
                            Log.e(TAG, "Couldn't load stream state for " + queue.getItem().getUrl(), e);
                            whenQueueReady.run();
                        },
                        // make sure to run the action even when there is no state!
                        whenQueueReady::run));
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Helpers
    //////////////////////////////////////////////////////////////////////////*/
    //region Helpers

    /**
     * @return the context to use to bind/unbind a service, where having a common context is crucial
     */
    private Context getCommonContext() {
        return App.getApp();
    }

    private void bind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called");
        }

        final Intent serviceIntent = new Intent(context, PlayerService.class);
        bound = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            // this should never happen, according to the docs of `bindService()`
            Log.e(TAG, "Could not bind player service");
        }
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
            player = null;
            listenerWrapper.onServiceDisconnected();
        }
    }

    private void startPlayerListener() {
        if (player != null) {
            player.setFragmentListener(listenerWrapper);
        }
    }

    private void stopPlayerListener() {
        if (player != null) {
            player.removeFragmentListener(listenerWrapper);
        }
    }

    class PlayerServiceConnection implements ServiceConnection {

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
            player = localBinder.getPlayer();

            listenerWrapper.onServiceConnected(player, playerService);
            actionsWhenConnected.stream().forEach(action -> action.accept(player));
            startPlayerListener();
        }
    }
    //endregion
}
