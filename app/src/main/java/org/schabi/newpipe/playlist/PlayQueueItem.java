package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItem;

import java.util.concurrent.Callable;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class PlayQueueItem {

    final private String title;
    final private String url;
    final private int serviceId;
    final private int duration;

    private boolean isDone;
    private Throwable error;
    private Maybe<StreamInfo> stream;

    PlayQueueItem(final StreamInfoItem streamInfoItem) {
        this.title = streamInfoItem.getTitle();
        this.url = streamInfoItem.getLink();
        this.serviceId = streamInfoItem.service_id;
        this.duration = streamInfoItem.duration;

        this.isDone = false;
        this.stream = getInfo();
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public int getServiceId() {
        return serviceId;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isDone() {
        return isDone;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @NonNull
    public Maybe<StreamInfo> getStream() {
        return stream;
    }

    @NonNull
    private Maybe<StreamInfo> getInfo() {
        final StreamingService service = getService(serviceId);
        if (service == null) return Maybe.empty();

        final Callable<StreamInfo> task = new Callable<StreamInfo>() {
            @Override
            public StreamInfo call() throws Exception {
                final StreamExtractor extractor = service.getExtractorInstance(url);
                return StreamInfo.getVideoInfo(extractor);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                error = throwable;
            }
        };

        final Action onComplete = new Action() {
            @Override
            public void run() throws Exception {
                isDone = true;
            }
        };

        return Maybe.fromCallable(task)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(onError)
                .doOnComplete(onComplete)
                .retry(3)
                .cache();
    }

    private StreamingService getService(final int serviceId) {
        try {
            return NewPipe.getService(serviceId);
        } catch (ExtractionException e) {
            return null;
        }
    }
}
