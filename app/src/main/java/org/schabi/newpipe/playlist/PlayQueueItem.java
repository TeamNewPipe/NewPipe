package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.NewPipe;
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

    private String title;
    private String url;
    private int serviceId;
    private int duration;

    private boolean isDone;
    private Throwable error;
    private Maybe<StreamInfo> stream;

    public PlayQueueItem(final StreamInfoItem streamInfoItem) {
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

    public void load() {
        stream.subscribe();
    }

    @NonNull
    private Maybe<StreamInfo> getInfo() {
        final Callable<StreamInfo> task = new Callable<StreamInfo>() {
            @Override
            public StreamInfo call() throws Exception {
                final StreamExtractor extractor = NewPipe.getService(serviceId).getExtractorInstance(url);
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
                .onErrorComplete()
                .doOnComplete(onComplete)
                .cache();
    }
}
