package org.schabi.newpipe.player.mediasource;

import com.google.android.exoplayer2.source.MediaSource;

public interface ManagedMediaSource extends MediaSource {
    boolean canReplace();
}
