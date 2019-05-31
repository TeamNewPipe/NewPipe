package org.schabi.newpipe.player.mediasource;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;

public class LoadedMediaSource implements ManagedMediaSource {

    private final MediaSource source;
    private final PlayQueueItem stream;
    private final long expireTimestamp;

    public LoadedMediaSource(@NonNull final MediaSource source,
                             @NonNull final PlayQueueItem stream,
                             final long expireTimestamp) {
        this.source = source;
        this.stream = stream;
        this.expireTimestamp = expireTimestamp;
    }

    public PlayQueueItem getStream() {
        return stream;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() >= expireTimestamp;
    }

    @Override
    public void prepareSource(SourceInfoRefreshListener listener, @Nullable TransferListener mediaTransferListener) {
        source.prepareSource(listener, mediaTransferListener);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        source.maybeThrowSourceInfoRefreshError();
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        return source.createPeriod(id, allocator, startPositionUs);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        source.releasePeriod(mediaPeriod);
    }

    @Override
    public void releaseSource(SourceInfoRefreshListener listener) {
        source.releaseSource(listener);
    }

    @Override
    public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
        source.addEventListener(handler, eventListener);
    }

    @Override
    public void removeEventListener(MediaSourceEventListener eventListener) {
        source.removeEventListener(eventListener);
    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return newIdentity != stream || (isInterruptable && isExpired());
    }

    @Override
    public boolean isStreamEqual(@NonNull PlayQueueItem stream) {
        return this.stream == stream;
    }
}
