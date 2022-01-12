package org.schabi.newpipe.player.mediasource;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;

public class ManagedMediaSourcePlaylist {
    @NonNull
    private final ConcatenatingMediaSource internalSource;

    public ManagedMediaSourcePlaylist() {
        internalSource = new ConcatenatingMediaSource(/*isPlaylistAtomic=*/false,
                new ShuffleOrder.UnshuffledShuffleOrder(0));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Delegations
    //////////////////////////////////////////////////////////////////////////*/

    public int size() {
        return internalSource.getSize();
    }

    /**
     * Returns the {@link ManagedMediaSource} at the given index of the playlist.
     * If the index is invalid, then null is returned.
     *
     * @param index index of {@link ManagedMediaSource} to get from the playlist
     * @return the {@link ManagedMediaSource} at the given index of the playlist
     */
    @Nullable
    public ManagedMediaSource get(final int index) {
        return (index < 0 || index >= size())
                ? null : (ManagedMediaSource) internalSource.getMediaSource(index).getTag();
    }

    @NonNull
    public ConcatenatingMediaSource getParentMediaSource() {
        return internalSource;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Expands the {@link ConcatenatingMediaSource} by appending it with a
     * {@link PlaceholderMediaSource}.
     *
     * @see #append(ManagedMediaSource)
     */
    public synchronized void expand() {
        append(new PlaceholderMediaSource());
    }

    /**
     * Appends a {@link ManagedMediaSource} to the end of {@link ConcatenatingMediaSource}.
     *
     * @see ConcatenatingMediaSource#addMediaSource
     * @param source {@link ManagedMediaSource} to append
     */
    public synchronized void append(@NonNull final ManagedMediaSource source) {
        internalSource.addMediaSource(source);
    }

    /**
     * Removes a {@link ManagedMediaSource} from {@link ConcatenatingMediaSource}
     * at the given index. If this index is out of bound, then the removal is ignored.
     *
     * @see ConcatenatingMediaSource#removeMediaSource(int)
     * @param index of {@link ManagedMediaSource} to be removed
     */
    public synchronized void remove(final int index) {
        if (index < 0 || index > internalSource.getSize()) {
            return;
        }

        internalSource.removeMediaSource(index);
    }

    /**
     * Moves a {@link ManagedMediaSource} in {@link ConcatenatingMediaSource}
     * from the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     *
     * @see ConcatenatingMediaSource#moveMediaSource(int, int)
     * @param source original index of {@link ManagedMediaSource}
     * @param target new index of {@link ManagedMediaSource}
     */
    public synchronized void move(final int source, final int target) {
        if (source < 0 || target < 0) {
            return;
        }
        if (source >= internalSource.getSize() || target >= internalSource.getSize()) {
            return;
        }

        internalSource.moveMediaSource(source, target);
    }

    /**
     * Invalidates the {@link ManagedMediaSource} at the given index by replacing it
     * with a {@link PlaceholderMediaSource}.
     *
     * @see #update(int, ManagedMediaSource, Handler, Runnable)
     * @param index            index of {@link ManagedMediaSource} to invalidate
     * @param handler          the {@link Handler} to run {@code finalizingAction}
     * @param finalizingAction a {@link Runnable} which is executed immediately
     *                         after the media source has been removed from the playlist
     */
    public synchronized void invalidate(final int index,
                                        @Nullable final Handler handler,
                                        @Nullable final Runnable finalizingAction) {
        if (get(index) instanceof PlaceholderMediaSource) {
            return;
        }
        update(index, new PlaceholderMediaSource(), handler, finalizingAction);
    }

    /**
     * Updates the {@link ManagedMediaSource} in {@link ConcatenatingMediaSource}
     * at the given index with a given {@link ManagedMediaSource}.
     *
     * @see #update(int, ManagedMediaSource, Handler, Runnable)
     * @param index  index of {@link ManagedMediaSource} to update
     * @param source new {@link ManagedMediaSource} to use
     */
    public synchronized void update(final int index, @NonNull final ManagedMediaSource source) {
        update(index, source, null, /*doNothing=*/null);
    }

    /**
     * Updates the {@link ManagedMediaSource} in {@link ConcatenatingMediaSource}
     * at the given index with a given {@link ManagedMediaSource}. If the index is out of bound,
     * then the replacement is ignored.
     *
     * @see ConcatenatingMediaSource#addMediaSource
     * @see ConcatenatingMediaSource#removeMediaSource(int, Handler, Runnable)
     * @param index            index of {@link ManagedMediaSource} to update
     * @param source           new {@link ManagedMediaSource} to use
     * @param handler          the {@link Handler} to run {@code finalizingAction}
     * @param finalizingAction a {@link Runnable} which is executed immediately
     *                         after the media source has been removed from the playlist
     */
    public synchronized void update(final int index, @NonNull final ManagedMediaSource source,
                                    @Nullable final Handler handler,
                                    @Nullable final Runnable finalizingAction) {
        if (index < 0 || index >= internalSource.getSize()) {
            return;
        }

        // Add and remove are sequential on the same thread, therefore here, the exoplayer
        // message queue must receive and process add before remove, effectively treating them
        // as atomic.

        // Since the finalizing action occurs strictly after the timeline has completed
        // all its changes on the playback thread, thus, it is possible, in the meantime,
        // other calls that modifies the playlist media source occur in between. This makes
        // it unsafe to call remove as the finalizing action of add.
        internalSource.addMediaSource(index + 1, source);

        // Because of the above race condition, it is thus only safe to synchronize the player
        // in the finalizing action AFTER the removal is complete and the timeline has changed.
        internalSource.removeMediaSource(index, handler, finalizingAction);
    }
}
