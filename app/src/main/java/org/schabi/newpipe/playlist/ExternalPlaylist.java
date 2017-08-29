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
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ExternalPlaylist extends Playlist {

    private AtomicInteger pageNumber;

    private StreamingService service;

    public ExternalPlaylist(final PlayListInfoItem playlist) {
        super();
        service = getService(playlist.serviceId);
        pageNumber = new AtomicInteger(0);

        load(playlist);
    }

    private void load(final PlayListInfoItem playlist) {
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
                streams.addAll(extractPlaylistItems(playListInfo));
                changeBroadcast.onNext(streams);
            }
        };

        Maybe.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorComplete()
                .subscribe(onSuccess);
    }

    private List<PlaylistItem> extractPlaylistItems(final PlayListInfo info) {
        List<PlaylistItem> result = new ArrayList<>();
        for (final InfoItem stream : info.related_streams) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlaylistItem((StreamInfoItem) stream));
            }
        }
        return result;
    }

    @Override
    boolean isComplete() {
        return false;
    }

    @Override
    void load(int index) {
        while (streams.size() < index) {
            pageNumber.incrementAndGet();
        }
    }

    @Override
    Observable<StreamInfo> get(int index) {
        return null;
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }

}
