package org.schabi.newpipe.player;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.playlist.PlayQueue;

import java.util.List;

public class PlaybackManager {

    private DynamicConcatenatingMediaSource source;

    private PlayQueue playQueue;
    private int index;

    private List<MediaSource> sources;

    public PlaybackManager(PlayQueue playQueue, int index) {
        this.source = new DynamicConcatenatingMediaSource();

        this.playQueue = playQueue;
        this.index = index;



    }

    interface OnChangeListener {
        void isLoading();
        void isLoaded();
    }
}
