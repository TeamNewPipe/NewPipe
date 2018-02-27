package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.playlist.PlayQueueItem;

public interface ManagedMediaSource extends MediaSource {
    boolean canReplace(@NonNull final PlayQueueItem newIdentity);
}
