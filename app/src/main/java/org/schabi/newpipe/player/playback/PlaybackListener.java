package org.schabi.newpipe.player.playback;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.StreamInfo;

public interface PlaybackListener {
    /*
    * Called when the stream at the current queue index is not ready yet.
    * Signals to the listener to block the player from playing anything.
    * */
    void block();

    /*
    * Called when the stream at the current queue index is ready.
    * Signals to the listener to resume the player.
    * May be called at any time, even when the player is unblocked.
    * */
    void unblock();

    /*
    * Called when the queue index is refreshed.
    * Signals to the listener to synchronize the player's window to the manager's
    * window.
    *
    * CAN ONLY BE CALLED ONCE UNBLOCKED!
    * */
    void sync(final StreamInfo info, final int sortedStreamsIndex);

    /*
    * Requests the listener to resolve a stream info into a media source respective
    * of the listener's implementation (background, popup or main video player),
    * */
    MediaSource sourceOf(final StreamInfo info, final int sortedStreamsIndex);

    void shutdown();
}
