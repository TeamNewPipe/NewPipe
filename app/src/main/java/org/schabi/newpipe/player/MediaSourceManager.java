package org.schabi.newpipe.player;

import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.events.PlayQueueMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class MediaSourceManager {
    private DynamicConcatenatingMediaSource sources;
    // indices maps media source index to play queue index
    // Invariant 1: all indices occur once only in this list
    private List<Integer> indices;

    private PlaybackListener playbackListener;

    private PlayQueue playQueue;
    private Subscription playQueueReactor;

    interface PlaybackListener {
        void block();
        void unblock();

        void resync();
        void sync(final StreamInfo info);
        MediaSource sourceOf(final StreamInfo info);
    }

    public MediaSourceManager(@NonNull final MediaSourceManager.PlaybackListener listener,
                              @NonNull final PlayQueue playQueue) {
        this.sources = new DynamicConcatenatingMediaSource();
        this.indices = Collections.synchronizedList(new ArrayList<Integer>());

        this.playbackListener = listener;
        this.playQueue = playQueue;

        playQueue.getEventBroadcast().subscribe(getReactor());
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

                switch (event.type()) {
                    case INIT:
                        break;
                    case APPEND:
                        break;
                    case SELECT:
                        break;
                    case REMOVE:
                    case SWAP:
                        break;
                    case NEXT:
                    default:
                        break;
                }

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
