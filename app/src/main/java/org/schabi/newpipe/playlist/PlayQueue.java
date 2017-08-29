package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PlayQueue {
    private final String TAG = "PlayQueue@" + Integer.toHexString(hashCode());

    final int LOAD_BOUND = 2;

    protected List<PlayQueueItem> streams;
    private BehaviorSubject<List<PlayQueueItem>> changeBroadcast;

    PlayQueue() {
        streams = Collections.synchronizedList(new ArrayList<PlayQueueItem>());
        changeBroadcast = BehaviorSubject.create();
    }

    @NonNull
    public List<PlayQueueItem> getStreams() {
        return streams;
    }

    public void notifyChange() {
        changeBroadcast.onNext(streams);
    }

    public abstract boolean isComplete();

    public abstract void load(int index, boolean loadNeighbors);

    public abstract Maybe<StreamInfo> get(int index);
}

