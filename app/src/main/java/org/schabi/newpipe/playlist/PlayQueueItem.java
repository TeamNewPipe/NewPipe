package org.schabi.newpipe.playlist;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.Serializable;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class PlayQueueItem implements Serializable {
    final public static int DEFAULT_QUALITY = Integer.MIN_VALUE;
    final public static long RECOVERY_UNSET = Long.MIN_VALUE;

    final private String title;
    final private String url;
    final private int serviceId;
    final private long duration;
    final private String thumbnailUrl;
    final private String uploader;

    private int qualityIndex;
    private long recoveryPosition;
    private Throwable error;

    private transient Single<StreamInfo> stream;

    PlayQueueItem(@NonNull final StreamInfo info) {
        this(info.name, info.url, info.service_id, info.duration, info.thumbnail_url, info.uploader_name);
        this.stream = Single.just(info);
    }

    PlayQueueItem(@NonNull final StreamInfoItem item) {
        this(item.name, item.url, item.service_id, item.duration, item.thumbnail_url, item.uploader_name);
    }

    private PlayQueueItem(final String name, final String url, final int serviceId,
                          final long duration, final String thumbnailUrl, final String uploader) {
        this.title = name;
        this.url = url;
        this.serviceId = serviceId;
        this.duration = duration;
        this.thumbnailUrl = thumbnailUrl;
        this.uploader = uploader;

        resetQualityIndex();
        resetRecoveryPosition();
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getUploader() {
        return uploader;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @NonNull
    public Single<StreamInfo> getStream() {
        return stream == null ? stream = getInfo() : stream;
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
                .doOnError(onError);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Item States
    ////////////////////////////////////////////////////////////////////////////

    public int getQualityIndex() {
        return qualityIndex;
    }

    public long getRecoveryPosition() {
        return recoveryPosition;
    }

    public void setQualityIndex(int qualityIndex) {
        this.qualityIndex = qualityIndex;
    }

    public void setRecoveryPosition(long recoveryPosition) {
        this.recoveryPosition = recoveryPosition;
    }

    public void resetQualityIndex() {
        this.qualityIndex = DEFAULT_QUALITY;
    }

    public void resetRecoveryPosition() {
        this.recoveryPosition = RECOVERY_UNSET;
    }
}
