package org.schabi.newpipe.player.playback;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public interface PlaybackListener {

    /**
     * Called to check if the currently playing stream is approaching the end of its playback.
     * Implementation should return true when the current playback position is progressing within
     * timeToEndMillis or less to its playback during.
     *
     * May be called at any time.
     * */
    boolean isApproachingPlaybackEdge(final long timeToEndMillis);

    /**
     * Called when the stream at the current queue index is not ready yet.
     * Signals to the listener to block the player from playing anything and notify the source
     * is now invalid.
     *
     * May be called at any time.
     * */
    void onPlaybackBlock();

    /**
     * Called when the stream at the current queue index is ready.
     * Signals to the listener to resume the player by preparing a new source.
     *
     * May be called only when the player is blocked.
     * */
    void onPlaybackUnblock(final MediaSource mediaSource);

    /**
     * Called when the queue index is refreshed.
     * Signals to the listener to synchronize the player's window to the manager's
     * window.
     *
     * May be called anytime at any amount once unblock is called.
     * */
    void onPlaybackSynchronize(@NonNull final PlayQueueItem item);

    /**
     * Requests the listener to resolve a stream info into a media source
     * according to the listener's implementation (background, popup or main video player).
     *
     * May be called at any time.
     * */
    @Nullable
    MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info);

    /**
     * Called when the play queue can no longer to played or used.
     * Currently, this means the play queue is empty and complete.
     * Signals to the listener that it should shutdown.
     *
     * May be called at any time.
     * */
    void onPlaybackShutdown();
}
