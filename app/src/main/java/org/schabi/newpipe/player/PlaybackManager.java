package org.schabi.newpipe.player;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueEvent;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.schedulers.Schedulers;

public class PlaybackManager {

    private static final int WINDOW_SIZE = 5;

    private DynamicConcatenatingMediaSource mediaSource;
    private List<PlayQueueItem> queueSource;
    private int sourceIndex;

    private PlaybackListener listener;
    private PlayQueue playQueue;

    private Subscription playQueueReactor;

    interface PlaybackListener {
        void block();
        void unblock();

        void sync(final StreamInfo info);
        MediaSource sourceOf(final StreamInfo info);
    }

    public PlaybackManager(@NonNull final PlaybackListener listener,
                           @NonNull final PlayQueue playQueue) {
        this.mediaSource = new DynamicConcatenatingMediaSource();
        this.queueSource = Collections.synchronizedList(new ArrayList<PlayQueueItem>(10));
        this.sourceIndex = 0;

        this.listener = listener;
        this.playQueue = playQueue;

        playQueue.getPlayQueueFlowable().subscribe(getReactor());
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
        listener.block();
        this.mediaSource.removeMediaSource(0);
        this.mediaSource.addMediaSource(0, newSource);
        listener.unblock();
    }

    public void refreshMedia(final int newMediaIndex) {
        if (newMediaIndex == sourceIndex) return;

        if (newMediaIndex == sourceIndex + 1) {
            playQueue.incrementIndex();
            mediaSource.removeMediaSource(0);
            queueSource.remove(0);
        } else {
            //something went wrong
            reload();
        }
    }

    private void removeCurrent() {
        listener.block();
        mediaSource.removeMediaSource(0);
        queueSource.remove(0);
        listener.unblock();
    }

    private Subscription loaderReactor;

    private void load() {
        if (mediaSource.getSize() < WINDOW_SIZE && queueSource.size() < WINDOW_SIZE)
            load(mediaSource.getSize());
    }

    private void load(final int from) {
        clear(from);

        if (loaderReactor != null) loaderReactor.cancel();

        List<Maybe<StreamInfo>> maybes = new ArrayList<>();
        for (int i = from; i < WINDOW_SIZE; i++) {
            final int index = playQueue.getIndex() + i;
            final PlayQueueItem item = playQueue.get(index);

            if (queueSource.size() > i) queueSource.set(i, item);
            else queueSource.add(item);

            maybes.add(item.getStream());
        }

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
                tryUnblock();
                loaderReactor.request(1);
            }

            @Override
            public void onError(Throwable t) {
                playQueue.remove(queueSource.size());
            }

            @Override
            public void onComplete() {
                if (loaderReactor != null) loaderReactor.cancel();
                loaderReactor = null;
            }
        };
    }

    private void tryUnblock() {
        if (mediaSource.getSize() > 0 && queueSource.size() > 0) listener.unblock();
    }

    private void init() {
        listener.block();
        load();
    }

    private void clear(int from) {
        while (mediaSource.getSize() > from) {
            queueSource.remove(from);
            mediaSource.removeMediaSource(from);
        }
    }

    private void clear() {
        listener.block();
        clear(0);
        listener.unblock();
    }

    private Subscriber<PlayQueueEvent> getReactor() {
        return new Subscriber<PlayQueueEvent>() {
            @Override
            public void onSubscribe(@NonNull Subscription d) {
                if (playQueueReactor != null) playQueueReactor.cancel();
                playQueueReactor = d;
                playQueueReactor.request(1);
            }

            @Override
            public void onNext(@NonNull PlayQueueEvent event) {
                if (playQueue.getStreams().size() - playQueue.getIndex() < WINDOW_SIZE && !playQueue.isComplete()) {
                    listener.block();
                    playQueue.fetch();
                }

                switch (event) {
                    case INIT:
                        init();
                        break;
                    case APPEND:
                        load();
                        break;
                    case REMOVE_CURRENT:
                        removeCurrent();
                        load();
                        break;
                    case SELECT:
                        reload();
                        break;
                    case REMOVE:
                    case SWAP:
                        load(1);
                        break;
                    case CLEAR:
                        clear();
                        break;
                    case NEXT:
                    default:
                        break;
                }

                tryUnblock();
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
