package org.schabi.newpipe.player.mediasource;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.IOException;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public final class DeferredMediaSource implements MediaSource {
    private final String TAG = "DeferredMediaSource@" + Integer.toHexString(hashCode());

    private int state = -1;

    public final static int STATE_INIT = 0;
    public final static int STATE_PREPARED = 1;
    public final static int STATE_LOADED = 2;
    public final static int STATE_DISPOSED = 3;

    public interface Callback {
        MediaSource sourceOf(final StreamInfo info);
    }

    private PlayQueueItem stream;
    private Callback callback;

    private MediaSource mediaSource;

    private Disposable loader;

    private ExoPlayer exoPlayer;
    private Listener listener;
    private Throwable error;

    public DeferredMediaSource(@NonNull final PlayQueueItem stream,
                               @NonNull final Callback callback) {
        this.stream = stream;
        this.callback = callback;
        this.state = STATE_INIT;
    }

    @Override
    public void prepareSource(ExoPlayer exoPlayer, boolean isTopLevelSource, Listener listener) {
        this.exoPlayer = exoPlayer;
        this.listener = listener;
        this.state = STATE_PREPARED;
    }

    public int state() {
        return state;
    }

    public synchronized void load() {
        if (state != STATE_PREPARED || stream == null || loader != null) return;
        Log.d(TAG, "Loading: [" + stream.getTitle() + "] with url: " + stream.getUrl());

        final Consumer<StreamInfo> onSuccess = new Consumer<StreamInfo>() {
            @Override
            public void accept(StreamInfo streamInfo) throws Exception {
                if (exoPlayer == null && listener == null) {
                    error = new Throwable("Stream info loading failed. URL: " + stream.getUrl());
                } else {
                    Log.d(TAG, " Loaded: [" + stream.getTitle() + "] with url: " + stream.getUrl());

                    mediaSource = callback.sourceOf(streamInfo);
                    mediaSource.prepareSource(exoPlayer, false, listener);
                    state = STATE_LOADED;
                }
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.e(TAG, "Loading error:", throwable);
                error = throwable;
                state = STATE_LOADED;
            }
        };

        loader = stream.getStream()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, onError);
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (error != null) {
            throw new IOException(error);
        }

        if (mediaSource != null) {
            mediaSource.maybeThrowSourceInfoRefreshError();
        }
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId mediaPeriodId, Allocator allocator) {
        return mediaSource.createPeriod(mediaPeriodId, allocator);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        if (mediaSource == null) {
            Log.e(TAG, "releasePeriod() called when media source is null, memory leak may have occurred.");
        } else {
            mediaSource.releasePeriod(mediaPeriod);
        }
    }

    @Override
    public void releaseSource() {
        state = STATE_DISPOSED;

        if (mediaSource != null) {
            mediaSource.releaseSource();
        }
        if (loader != null) {
            loader.dispose();
        }

        /* Do not set mediaSource as null here as it may be called through releasePeriod */
        stream = null;
        callback = null;
        exoPlayer = null;
        listener = null;
    }
}
