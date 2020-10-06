package org.schabi.newpipe.player.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;

public final class PlayerHolder {
    private PlayerHolder() {
    }

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = "PlayerHolder";

    private static PlayerServiceExtendedEventListener listener;

    private static ServiceConnection serviceConnection;
    public static boolean bound;
    private static MainPlayer playerService;
    private static VideoPlayerImpl player;

    /**
     * Returns the current {@link MainPlayer.PlayerType} of the {@link MainPlayer} service,
     * otherwise `null` if no service running.
     *
     * @return Current PlayerType
     */
    @Nullable
    public static MainPlayer.PlayerType getType() {
        if (player == null) {
            return null;
        }

        return player.videoPlayerSelected() ? MainPlayer.PlayerType.VIDEO
                : player.popupPlayerSelected() ? MainPlayer.PlayerType.POPUP
                : player.audioPlayerSelected() ? MainPlayer.PlayerType.AUDIO
                : null;
    }

    public static void setListener(final PlayerServiceExtendedEventListener newListener) {
        listener = newListener;
        // Force reload data from service
        if (player != null) {
            listener.onServiceConnected(player, playerService, false);
            startPlayerListener();
        }
    }

    public static void removeListener() {
        listener = null;
    }


    public static void startService(final Context context,
                                    final boolean playAfterConnect,
                                    final PlayerServiceExtendedEventListener newListener) {
        setListener(newListener);
        if (bound) {
            return;
        }
        // startService() can be called concurrently and it will give a random crashes
        // and NullPointerExceptions inside the service because the service will be
        // bound twice. Prevent it with unbinding first
        unbind(context);
        context.startService(new Intent(context, MainPlayer.class));
        serviceConnection = getServiceConnection(context, playAfterConnect);
        bind(context);
    }

    public static void stopService(final Context context) {
        unbind(context);
        context.stopService(new Intent(context, MainPlayer.class));
    }

    private static ServiceConnection getServiceConnection(final Context context,
                                                          final boolean playAfterConnect) {
        return new ServiceConnection() {
            @Override
            public void onServiceDisconnected(final ComponentName compName) {
                if (DEBUG) {
                    Log.d(TAG, "Player service is disconnected");
                }

                unbind(context);
            }

            @Override
            public void onServiceConnected(final ComponentName compName, final IBinder service) {
                if (DEBUG) {
                    Log.d(TAG, "Player service is connected");
                }
                final MainPlayer.LocalBinder localBinder = (MainPlayer.LocalBinder) service;

                playerService = localBinder.getService();
                player = localBinder.getPlayer();
                if (listener != null) {
                    listener.onServiceConnected(player, playerService, playAfterConnect);
                }
                startPlayerListener();
            }
        };
    }

    private static void bind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "bind() called");
        }

        final Intent serviceIntent = new Intent(context, MainPlayer.class);
        bound = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            context.unbindService(serviceConnection);
        }
    }

    private static void unbind(final Context context) {
        if (DEBUG) {
            Log.d(TAG, "unbind() called");
        }

        if (bound) {
            context.unbindService(serviceConnection);
            bound = false;
            stopPlayerListener();
            playerService = null;
            player = null;
            if (listener != null) {
                listener.onServiceDisconnected();
            }
        }
    }


    private static void startPlayerListener() {
        if (player != null) {
            player.setFragmentListener(INNER_LISTENER);
        }
    }

    private static void stopPlayerListener() {
        if (player != null) {
            player.removeFragmentListener(INNER_LISTENER);
        }
    }


    private static final PlayerServiceEventListener INNER_LISTENER =
            new PlayerServiceEventListener() {
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
                public void onPlayerError(final ExoPlaybackException error) {
                    if (listener != null) {
                        listener.onPlayerError(error);
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
                    unbind(App.getApp());
                }
            };
}
