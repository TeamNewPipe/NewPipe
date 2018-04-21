package org.schabi.newpipe.player.playqueue;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.Serializable;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class PlayQueueItem implements Serializable {
    public final static long RECOVERY_UNSET = Long.MIN_VALUE;
    private final static String EMPTY_STRING = "";

    @NonNull final private String title;
    @NonNull final private String url;
    final private int serviceId;
    final private long duration;
    @NonNull final private String thumbnailUrl;
    @NonNull final private String uploader;
    @NonNull final private StreamType streamType;

    private long recoveryPosition;
    private Throwable error;

    PlayQueueItem(@NonNull final StreamInfo info) {
        this(info.getName(), info.getUrl(), info.getServiceId(), info.getDuration(),
                info.getThumbnailUrl(), info.getUploaderName(), info.getStreamType());

        if (info.getStartPosition() > 0)
            setRecoveryPosition(info.getStartPosition() * 1000);
    }

    PlayQueueItem(@NonNull final StreamInfoItem item) {
        this(item.getName(), item.getUrl(), item.getServiceId(), item.getDuration(),
                item.getThumbnailUrl(), item.getUploaderName(), item.getStreamType());
    }

    private PlayQueueItem(@Nullable final String name, @Nullable final String url,
                          final int serviceId, final long duration,
                          @Nullable final String thumbnailUrl, @Nullable final String uploader,
                          @NonNull final StreamType streamType) {
        this.title = name != null ? name : EMPTY_STRING;
        this.url = url != null ? url : EMPTY_STRING;
        this.serviceId = serviceId;
        this.duration = duration;
        this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : EMPTY_STRING;
        this.uploader = uploader != null ? uploader : EMPTY_STRING;
        this.streamType = streamType;

        this.recoveryPosition = RECOVERY_UNSET;
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

    @NonNull
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @NonNull
    public String getUploader() {
        return uploader;
    }

    @NonNull
    public StreamType getStreamType() {
        return streamType;
    }

    public long getRecoveryPosition() {
        return recoveryPosition;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @NonNull
    public Single<StreamInfo> getStream() {
        return ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> error = throwable);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Item States, keep external access out
    ////////////////////////////////////////////////////////////////////////////

    /*package-private*/ void setRecoveryPosition(final long recoveryPosition) {
        this.recoveryPosition = recoveryPosition;
    }
}
