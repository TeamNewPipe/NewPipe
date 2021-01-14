package org.schabi.newpipe.player.mediasession;

import android.support.v4.media.MediaDescriptionCompat;

public interface MediaSessionCallback {
    void playPrevious();

    void playNext();

    void playItemAtIndex(int index);

    int getCurrentPlayingIndex();

    int getQueueSize();

    MediaDescriptionCompat getQueueMetadata(int index);

    void play();

    void pause();
}
