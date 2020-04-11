package org.schabi.newpipe.player.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public interface PlaybackListener {
    /**
     * Called to check if the currently playing stream is approaching the end of its playback.
     * Implementation should return true when the current playback position is progressing within
     * timeToEndMillis or less to its playback during.
     * <p>
     * May be called at any time.
     * </p>
     *
     * @param timeToEndMillis
     * @return whether the stream is approaching end of playback
     */
    boolean isApproachingPlaybackEdge(long timeToEndMillis);

    /**
     * Called when the stream at the current queue index is not ready yet.
     * Signals to the listener to block the player from playing anything and notify the source
     * is now invalid.
     * <p>
     * May be called at any time.
     * </p>
     */
    void onPlaybackBlock();

    /**
     * Called when the stream at the current queue index is ready.
     * Signals to the listener to resume the player by preparing a new source.
     * <p>
     * May be called only when the player is blocked.
     * </p>
     *
     * @param mediaSource
     */
    void onPlaybackUnblock(MediaSource mediaSource);

    /**
     * Called when the queue index is refreshed.
     * Signals to the listener to synchronize the player's window to the manager's
     * window.
     * <p>
     * May be called anytime at any amount once unblock is called.
     * </p>
     *
     * @param item
     */
    void onPlaybackSynchronize(@NonNull PlayQueueItem item);

    /**
     * Requests the listener to resolve a stream info into a media source
     * according to the listener's implementation (background, popup or main video player).
     * <p>
     * May be called at any time.
     * </p>
     * @param item
     * @param info
     * @return the corresponding {@link MediaSource}
     */
    @Nullable
    MediaSource sourceOf(PlayQueueItem item, StreamInfo info);

    /**
     * Called when the play queue can no longer to played or used.
     * Currently, this means the play queue is empty and complete.
     * Signals to the listener that it should shutdown.
     * <p>
     * May be called at any time.
     * </p>
     */
    void onPlaybackShutdown();
}
