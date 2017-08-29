package org.schabi.newpipe.player;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.playlist.Playlist;

import java.util.List;

public class MediaSourceManager {

    private DynamicConcatenatingMediaSource source;

    private Playlist playlist;
    private List<MediaSource> sources;

    public MediaSourceManager(Playlist playlist) {
        this.source = new DynamicConcatenatingMediaSource();
        this.playlist = playlist;
    }
}
