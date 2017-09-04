package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class PlayQueueItem {

    final private String title;
    final private String url;
    final private int serviceId;
    final private long duration;

    private Throwable error;
    private Single<StreamInfo> stream;

    PlayQueueItem(final StreamInfoItem streamInfoItem) {
        this.title = streamInfoItem.name;
        this.url = streamInfoItem.url;
        this.serviceId = streamInfoItem.service_id;
        this.duration = streamInfoItem.duration;

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

    public long getDuration() {
        return duration;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @NonNull
    public Single<StreamInfo> getStream() {
        return stream;
    }

    @NonNull
    private Single<StreamInfo> getInfo() {
        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                error = throwable;
            }
        };

        return ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .doOnError(onError);
    }
}
