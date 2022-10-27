package org.schabi.newpipe.player.bind;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;

import javax.annotation.Nullable;

public class PlayerListenerWrapper implements PlayerServiceExtendedEventListener {

    @Nullable PlayerServiceExtendedEventListener listener;
    final Runnable runOnServiceStopped;

    PlayerListenerWrapper(final Runnable runOnServiceStopped) {
        this.listener = null;
        this.runOnServiceStopped = runOnServiceStopped;
    }

    void setListener(@Nullable final PlayerServiceExtendedEventListener listener) {
        this.listener = listener;
    }


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
        runOnServiceStopped.run();
    }

    @Override
    public void onServiceConnected(final Player player, final PlayerService playerService) {
        if (listener != null) {
            listener.onServiceConnected(player, playerService);
        }
    }

    @Override
    public void onServiceDisconnected() {
        if (listener != null) {
            listener.onServiceDisconnected();
        }
    }
}
