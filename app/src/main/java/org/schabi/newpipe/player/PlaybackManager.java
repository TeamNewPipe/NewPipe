package org.schabi.newpipe.player;

import android.util.Log;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.events.PlayQueueEvent;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;

public class PlaybackManager {
    private final String TAG = "PlaybackManager@" + Integer.toHexString(hashCode());

    private static final int WINDOW_SIZE = 3;

    private DynamicConcatenatingMediaSource mediaSource;
    private List<StreamInfo> syncInfos;

    private int sourceIndex;

    private PlaybackListener listener;
    private PlayQueue playQueue;

    private Subscription playQueueReactor;

    public boolean prepared = false;

    interface PlaybackListener {
        void block();
        void unblock();

        void resync();
        void sync(final StreamInfo info);
        MediaSource sourceOf(final StreamInfo info);
    }

    public PlaybackManager(@NonNull final PlaybackListener listener,
                           @NonNull final PlayQueue playQueue) {
        this.mediaSource = new DynamicConcatenatingMediaSource();
        this.syncInfos = Collections.synchronizedList(new ArrayList<StreamInfo>());
        this.sourceIndex = 0;

        this.listener = listener;
        this.playQueue = playQueue;

        playQueue.getEventBroadcast().subscribe(getReactor());
    }

    @NonNull
    public DynamicConcatenatingMediaSource getMediaSource() {
        return mediaSource;
    }

    private void reload() {
        listener.block();
        load(0);
    }

    public void changeSource(final MediaSource newSource) {
        this.mediaSource.removeMediaSource(0);
        this.mediaSource.addMediaSource(0, newSource);
    }

    public void refreshMedia(final int newMediaIndex) {
        if (newMediaIndex == sourceIndex) return;

        if (newMediaIndex == sourceIndex + 1) {
            playQueue.incrementIndex();
            mediaSource.removeMediaSource(0);
            syncInfos.remove(0);
        } else {
            //something went wrong
            Log.e(TAG, "Refresh media failed, reloading.");
            reload();
        }
    }

    private void removeCurrent() {
        mediaSource.removeMediaSource(0);
        syncInfos.remove(0);
    }

    private Subscription loaderReactor;

    private void load() {
        if (mediaSource.getSize() < WINDOW_SIZE) load(mediaSource.getSize());
    }

    private void load(final int from) {
        // Fetch queue items
        //todo fix out of bound
        final int index = playQueue.getIndex();

        List<Maybe<StreamInfo>> maybes = new ArrayList<>();
        for (int i = from; i < WINDOW_SIZE; i++) {
            final PlayQueueItem item = playQueue.get(index + i);

            maybes.add(item.getStream());
        }

        // Stop loading and clear pending media sources
        if (loaderReactor != null) loaderReactor.cancel();
        clear(from);

        // Start sequential loading of media sources
        Maybe.concat(maybes).subscribe(getSubscriber());
    }

    private Subscriber<StreamInfo> getSubscriber() {
        return new Subscriber<StreamInfo>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (loaderReactor != null) loaderReactor.cancel();
                loaderReactor = s;
                s.request(1);
            }

            @Override
            public void onNext(StreamInfo streamInfo) {
                mediaSource.addMediaSource(listener.sourceOf(streamInfo));
                syncInfos.add(streamInfo);
                tryUnblock();
                loaderReactor.request(1);
            }

            @Override
            public void onError(Throwable t) {
                playQueue.remove(playQueue.getIndex());
            }

            @Override
            public void onComplete() {
                if (loaderReactor != null) loaderReactor.cancel();
                loaderReactor = null;
            }
        };
    }

    private void tryUnblock() {
        if (mediaSource.getSize() > 0) listener.unblock();
    }

    private void init() {
        listener.block();
        load();
    }

    private void clear(int from) {
        while (mediaSource.getSize() > from) {
            mediaSource.removeMediaSource(from);
            syncInfos.remove(from);
        }
    }

    private Subscriber<PlayQueueMessage> getReactor() {
        return new Subscriber<PlayQueueMessage>() {
            @Override
            public void onSubscribe(@NonNull Subscription d) {
                if (playQueueReactor != null) playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull PlayQueueMessage event) {
                if (playQueue.getStreams().size() - playQueue.getIndex() < WINDOW_SIZE && !playQueue.isComplete()) {
                    listener.block();
                    playQueue.fetch();
                }

                switch (event.type()) {
                    case INIT:
                        init();
                        break;
                    case APPEND:
                        load();
                        break;
                    case SELECT:
                        reload();
                        break;
                    case REMOVE:
                    case SWAP:
                        load(1);
                        break;
                    case NEXT:
                    default:
                        break;
                }

                tryUnblock();
                if (!syncInfos.isEmpty()) listener.sync(syncInfos.get(0));
                if (playQueueReactor != null) playQueueReactor.request(1);
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }

            @Override
            public void onComplete() {
                dispose();
            }
        };
    }

    public void dispose() {
        if (playQueueReactor != null) playQueueReactor.cancel();
        playQueueReactor = null;
    }
}
