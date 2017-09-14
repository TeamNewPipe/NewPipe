package org.schabi.newpipe.player.playback;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.StreamInfo;

public interface PlaybackListener {
    /*
    * Called when the stream at the current queue index is not ready yet.
    * Signals to the listener to block the player from playing anything.
    *
    * May be called at any time.
    * */
    void block();


    /*
    * Called when the media source is rebuilt.
    * Signals to the listener to prepare the media source again.
    * The provided media source is always non-empty.
    *
    * May be called only after blocking and before unblocking.
    * */
    void prepare(final MediaSource mediaSource);

    /*
    * Called when the stream at the current queue index is ready.
    * Signals to the listener to resume the player.
    *
    * May be called only when the player is blocked.
    * */
    void unblock();

    /*
    * Called when the queue index is refreshed.
    * Signals to the listener to synchronize the player's window to the manager's
    * window.
    *
    * May be called only when playback is unblocked.
    * */
    void sync(final StreamInfo info, final int sortedStreamsIndex);

    /*
    * Requests the listener to resolve a stream info into a media source
    * according to the listener's implementation (background, popup or main video player).
    *
    * May be called at any time.
    * */
    MediaSource sourceOf(final StreamInfo info, final int sortedStreamsIndex);

    /*
    * Called when the play queue can no longer to played or used.
    * Currently, this means the play queue is empty and complete.
    * Signals to the listener that it should shutdown.
    *
    * May be called at any time.
    * */
    void shutdown();
}
