package org.schabi.newpipe.player.mediasource;

import android.os.Looper;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.IOException;
import java.util.List;

public final class DeferredMediaSource implements MediaSource {

    public interface Callback {
        MediaSource sourceOf(final StreamInfo info);
    }

    final private PlayQueueItem stream;
    final private Callback callback;

    private StreamInfo info;
    private MediaSource mediaSource;

    private ExoPlayer exoPlayer;
    private boolean isTopLevel;
    private Listener listener;

    public DeferredMediaSource(final PlayQueueItem stream, final Callback callback) {
        this.stream = stream;
        this.callback = callback;
    }

    @Override
    public void prepareSource(ExoPlayer exoPlayer, boolean isTopLevelSource, Listener listener) {
        this.exoPlayer = exoPlayer;
        this.isTopLevel = isTopLevelSource;
        this.listener = listener;

        listener.onSourceInfoRefreshed(new SinglePeriodTimeline(C.TIME_UNSET, false), null);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (mediaSource != null) {
            mediaSource.maybeThrowSourceInfoRefreshError();
        }
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId mediaPeriodId, Allocator allocator) {
        // This must be called on a non-main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new UnsupportedOperationException("Source preparation is blocking, it must be run on non-UI thread.");
        }

        info = stream.getStream().blockingGet();

        mediaSource = callback.sourceOf(info);
        mediaSource.prepareSource(exoPlayer, isTopLevel, listener);

        return mediaSource.createPeriod(mediaPeriodId, allocator);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        mediaSource.releasePeriod(mediaPeriod);
    }

    @Override
    public void releaseSource() {
        if (mediaSource != null) mediaSource.releaseSource();
        info = null;
        mediaSource = null;
    }
}
