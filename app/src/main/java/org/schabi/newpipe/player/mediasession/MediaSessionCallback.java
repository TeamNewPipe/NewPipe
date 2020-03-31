package org.schabi.newpipe.player.mediasession;

import android.support.v4.media.MediaDescriptionCompat;

public interface MediaSessionCallback {
    void onSkipToPrevious();

    void onSkipToNext();

    void onSkipToIndex(int index);

    int getCurrentPlayingIndex();

    int getQueueSize();

    MediaDescriptionCompat getQueueMetadata(int index);

    void onPlay();

    void onPause();
}
