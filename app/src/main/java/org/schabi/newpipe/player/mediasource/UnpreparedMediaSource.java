package org.schabi.newpipe.player.mediasource;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;

public class UnpreparedMediaSource implements ManagedMediaSource {

    private MediaSource source;

    public UnpreparedMediaSource(MediaSource source) {
        this.source = source;
    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull PlayQueueItem newIdentity, boolean isInterruptable) {
        return true;
    }

    @Override
    public boolean isStreamEqual(@NonNull PlayQueueItem stream) {
        return true;
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
    public void prepareSource(MediaSourceCaller caller, @Nullable TransferListener mediaTransferListener) {
        source.prepareSource(caller, mediaTransferListener);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        source.maybeThrowSourceInfoRefreshError();
    }

    @Override
    public void enable(MediaSourceCaller caller) {
        source.enable(caller);
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
    public void disable(MediaSourceCaller caller) {
        source.disable(caller);
    }

    @Override
    public void releaseSource(MediaSourceCaller caller) {
        source.releaseSource(caller);
    }
}
