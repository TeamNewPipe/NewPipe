package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public abstract class Playlist {
    private final String TAG = "Playlist@" + Integer.toHexString(hashCode());

    private final int LOAD_BOUND = 2;

    List<PlaylistItem> streams;
    PublishSubject<List<PlaylistItem>> changeBroadcast;

    Playlist() {
        streams = Collections.synchronizedList(new ArrayList<PlaylistItem>());
        changeBroadcast = PublishSubject.create();
    }

    @NonNull
    public PublishSubject<List<PlaylistItem>> getChangeBroadcast() {
        return changeBroadcast;
    }

    abstract boolean isComplete();

    abstract void load(int index);

    abstract Observable<StreamInfo> get(int index);
}

