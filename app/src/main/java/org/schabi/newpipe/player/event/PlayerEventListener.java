package org.schabi.newpipe.player.event;


import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.extractor.stream.StreamInfo;

public interface PlayerEventListener {
    void onPlaybackUpdate(int state, int repeatMode, boolean shuffled,
                          PlaybackParameters parameters);

    void onProgressUpdate(int currentProgress, int duration, int bufferPercent);

    void onMetadataUpdate(StreamInfo info);

    void onServiceStopped();
}
