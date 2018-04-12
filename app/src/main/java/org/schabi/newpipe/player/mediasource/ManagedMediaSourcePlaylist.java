package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;

public class ManagedMediaSourcePlaylist {
    @NonNull private final DynamicConcatenatingMediaSource internalSource;

    public ManagedMediaSourcePlaylist() {
        internalSource = new DynamicConcatenatingMediaSource(/*isPlaylistAtomic=*/false,
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
     * */
    @Nullable
    public ManagedMediaSource get(final int index) {
        return (index < 0 || index >= size()) ?
                null : (ManagedMediaSource) internalSource.getMediaSource(index);
    }

    public void dispose() {
        internalSource.releaseSource();
    }

    @NonNull
    public DynamicConcatenatingMediaSource getParentMediaSource() {
        return internalSource;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Expands the {@link DynamicConcatenatingMediaSource} by appending it with a
     * {@link PlaceholderMediaSource}.
     *
     * @see #append(ManagedMediaSource)
     * */
    public synchronized void expand() {
        append(new PlaceholderMediaSource());
    }

    /**
     * Appends a {@link ManagedMediaSource} to the end of {@link DynamicConcatenatingMediaSource}.
     * @see DynamicConcatenatingMediaSource#addMediaSource
     * */
    public synchronized void append(@NonNull final ManagedMediaSource source) {
        internalSource.addMediaSource(source);
    }

    /**
     * Removes a {@link ManagedMediaSource} from {@link DynamicConcatenatingMediaSource}
     * at the given index. If this index is out of bound, then the removal is ignored.
     * @see DynamicConcatenatingMediaSource#removeMediaSource(int)
     * */
    public synchronized void remove(final int index) {
        if (index < 0 || index > internalSource.getSize()) return;

        internalSource.removeMediaSource(index);
    }

    /**
     * Moves a {@link ManagedMediaSource} in {@link DynamicConcatenatingMediaSource}
     * from the given source index to the target index. If either index is out of bound,
     * then the call is ignored.
     * @see DynamicConcatenatingMediaSource#moveMediaSource(int, int)
     * */
    public synchronized void move(final int source, final int target) {
        if (source < 0 || target < 0) return;
        if (source >= internalSource.getSize() || target >= internalSource.getSize()) return;

        internalSource.moveMediaSource(source, target);
    }

    /**
     * Invalidates the {@link ManagedMediaSource} at the given index by replacing it
     * with a {@link PlaceholderMediaSource}.
     * @see #update(int, ManagedMediaSource, Runnable)
     * */
    public synchronized void invalidate(final int index,
                                        @Nullable final Runnable finalizingAction) {
        if (get(index) instanceof PlaceholderMediaSource) return;
        update(index, new PlaceholderMediaSource(), finalizingAction);
    }

    /**
     * Updates the {@link ManagedMediaSource} in {@link DynamicConcatenatingMediaSource}
     * at the given index with a given {@link ManagedMediaSource}.
     * @see #update(int, ManagedMediaSource, Runnable)
     * */
    public synchronized void update(final int index, @NonNull final ManagedMediaSource source) {
        update(index, source, /*doNothing=*/null);
    }

    /**
     * Updates the {@link ManagedMediaSource} in {@link DynamicConcatenatingMediaSource}
     * at the given index with a given {@link ManagedMediaSource}. If the index is out of bound,
     * then the replacement is ignored.
     * @see DynamicConcatenatingMediaSource#addMediaSource
     * @see DynamicConcatenatingMediaSource#removeMediaSource(int, Runnable)
     * */
    public synchronized void update(final int index, @NonNull final ManagedMediaSource source,
                                    @Nullable final Runnable finalizingAction) {
        if (index < 0 || index >= internalSource.getSize()) return;

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
        internalSource.removeMediaSource(index, finalizingAction);
    }
}
