package org.schabi.newpipe.fragments;

/**
 * Created by avently on 08.11.17.
 */

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;

/**
 * Indicates that the current fragment want to listen a queue changes
 */
public interface QueueListener {
    /**
     * We'll get all changes in queue
     */
    void onSync(@NonNull final PlayQueueItem item, @Nullable final StreamInfo info);
}
