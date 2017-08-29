package org.schabi.newpipe.playlist;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlayListExtractor;
import org.schabi.newpipe.extractor.playlist.PlayListInfo;
import org.schabi.newpipe.extractor.playlist.PlayListInfoItem;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ExternalPlayQueue extends PlayQueue {

    private final static int LOAD_PROXIMITY = 10;

    private boolean isComplete;

    private AtomicInteger pageNumber;

    private StreamingService service;

    private PlayListInfoItem playlist;

    public ExternalPlayQueue(final PlayListInfoItem playlist) {
        super();
        this.service = getService(playlist.serviceId);
        this.pageNumber = new AtomicInteger(0);
        this.playlist = playlist;

        fetch();
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void load(int index, boolean loadNeighbors) {
        if (index > streams.size() || streams.get(index) == null) return;

        streams.get(index).load();

        if (loadNeighbors) {
            int leftBound = index - LOAD_BOUND >= 0 ? index - LOAD_BOUND : 0;
            int rightBound = index + LOAD_BOUND < streams.size() ? index + LOAD_BOUND : streams.size() - 1;

            for (int i = leftBound; i < rightBound; i++) {
                final PlayQueueItem item = streams.get(i);
                if (item != null) item.load();
            }
        }
    }

    @Override
    public Maybe<StreamInfo> get(int index) {
        if (index > streams.size() || streams.get(index) == null) return Maybe.empty();
        return streams.get(index).getStream();
    }


    public synchronized void fetch() {
        final int page = pageNumber.getAndIncrement();

        final Callable<PlayListInfo> task = new Callable<PlayListInfo>() {
            @Override
            public PlayListInfo call() throws Exception {
                PlayListExtractor extractor = service.getPlayListExtractorInstance(playlist.getLink(), page);
                return PlayListInfo.getInfo(extractor);
            }
        };

        final Consumer<PlayListInfo> onSuccess = new Consumer<PlayListInfo>() {
            @Override
            public void accept(PlayListInfo playListInfo) throws Exception {
                if (!playListInfo.hasNextPage) isComplete = true;

                streams.addAll(extractPlaylistItems(playListInfo));
                notifyChange();
            }
        };

        Maybe.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorComplete()
                .subscribe(onSuccess);
    }

    private List<PlayQueueItem> extractPlaylistItems(final PlayListInfo info) {
        List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : info.related_streams) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }
}
