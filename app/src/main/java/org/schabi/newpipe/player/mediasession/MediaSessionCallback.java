package org.schabi.newpipe.player.mediasession;

import android.support.v4.media.MediaDescriptionCompat;

public interface MediaSessionCallback {
    void onSkipToPrevious();
    void onSkipToNext();
    void onSkipToIndex(final int index);

    int getCurrentPlayingIndex();
    int getQueueSize();
    MediaDescriptionCompat getQueueMetadata(final int index);

    void onPlay();
    void onPause();
    void onSetShuffle(final boolean isShuffled);
}
