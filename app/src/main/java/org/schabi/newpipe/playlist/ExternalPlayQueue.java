package org.schabi.newpipe.playlist;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.playlist.PlayListExtractor;
import org.schabi.newpipe.extractor.playlist.PlayListInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ExternalPlayQueue extends PlayQueue {
    private final String TAG = "ExternalPlayQueue@" + Integer.toHexString(hashCode());

    private boolean isComplete;

    private StreamingService service;
    private String playlistUrl;

    private AtomicInteger pageNumber;
    private Disposable fetchReactor;

    public ExternalPlayQueue(final String playlistUrl,
                             final PlayListInfo info,
                             final int currentPage,
                             final int index) {
        super(index, extractPlaylistItems(info));

        this.service = getService(info.service_id);

        this.isComplete = !info.hasNextPage;
        this.pageNumber = new AtomicInteger(currentPage + 1);

        this.playlistUrl = playlistUrl;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public PlayQueueItem get(int index) {
        if (index > getStreams().size() || getStreams().get(index) == null) return null;
        return getStreams().get(index);
    }

    @Override
    public void fetch() {
        if (isComplete) return;
        if (fetchReactor != null && !fetchReactor.isDisposed()) return;

        final Callable<PlayListInfo> task = new Callable<PlayListInfo>() {
            @Override
            public PlayListInfo call() throws Exception {
                PlayListExtractor extractor = service.getPlayListExtractorInstance(playlistUrl, pageNumber.get());
                return PlayListInfo.getInfo(extractor);
            }
        };

        final Consumer<PlayListInfo> onSuccess = new Consumer<PlayListInfo>() {
            @Override
            public void accept(PlayListInfo playListInfo) throws Exception {
                if (!playListInfo.hasNextPage) isComplete = true;

                append(extractPlaylistItems(playListInfo));
                pageNumber.incrementAndGet();
            }
        };

        fetchReactor = Maybe.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fetchReactor != null) fetchReactor.dispose();
    }

    private static List<PlayQueueItem> extractPlaylistItems(final PlayListInfo info) {
        List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : info.related_streams) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }
}
