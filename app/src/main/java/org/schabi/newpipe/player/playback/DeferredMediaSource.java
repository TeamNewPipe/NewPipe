package org.schabi.newpipe.player.playback;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * DeferredMediaSource is specifically designed to allow external control over when
 * the source metadata are loaded while being compatible with ExoPlayer's playlists.
 *
 * This media source follows the structure of how NewPipeExtractor's
 * {@link org.schabi.newpipe.extractor.stream.StreamInfoItem} is converted into
 * {@link org.schabi.newpipe.extractor.stream.StreamInfo}. Once conversion is complete,
 * this media source behaves identically as any other native media sources.
 * */
public final class DeferredMediaSource implements MediaSource {
    private final String TAG = "DeferredMediaSource@" + Integer.toHexString(hashCode());

    /**
     * This state indicates the {@link DeferredMediaSource} has just been initialized or reset.
     * The source must be prepared and loaded again before playback.
     * */
    public final static int STATE_INIT = 0;
    /**
     * This state indicates the {@link DeferredMediaSource} has been prepared and is ready to load.
     * */
    public final static int STATE_PREPARED = 1;
    /**
     * This state indicates the {@link DeferredMediaSource} has been loaded without errors and
     * is ready for playback.
     * */
    public final static int STATE_LOADED = 2;

    public interface Callback {
        /**
         * Player-specific {@link com.google.android.exoplayer2.source.MediaSource} resolution
         * from a given StreamInfo.
         * */
        MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info);
    }

    private PlayQueueItem stream;
    private Callback callback;
    private int state;

    private MediaSource mediaSource;

    /* Custom internal objects */
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

    /**
     * Returns the current state of the {@link DeferredMediaSource}.
     *
     * @see DeferredMediaSource#STATE_INIT
     * @see DeferredMediaSource#STATE_PREPARED
     * @see DeferredMediaSource#STATE_LOADED
     * */
    public int state() {
        return state;
    }

    /**
     * Parameters are kept in the class for delayed preparation.
     * */
    @Override
    public void prepareSource(ExoPlayer exoPlayer, boolean isTopLevelSource, Listener listener) {
        this.exoPlayer = exoPlayer;
        this.listener = listener;
        this.state = STATE_PREPARED;
    }

    /**
     * Externally controlled loading. This method fully prepares the source to be used
     * like any other native {@link com.google.android.exoplayer2.source.MediaSource}.
     *
     * Ideally, this should be called after this source has entered PREPARED state and
     * called once only.
     *
     * If loading fails here, an error will be propagated out and result in an
     * {@link com.google.android.exoplayer2.ExoPlaybackException ExoPlaybackException},
     * which is delegated to the player.
     * */
    public synchronized void load() {
        if (stream == null) {
            Log.e(TAG, "Stream Info missing, media source loading terminated.");
            return;
        }
        if (state != STATE_PREPARED || loader != null) return;

        Log.d(TAG, "Loading: [" + stream.getTitle() + "] with url: " + stream.getUrl());

        final Function<StreamInfo, MediaSource> onReceive = new Function<StreamInfo, MediaSource>() {
            @Override
            public MediaSource apply(StreamInfo streamInfo) throws Exception {
                return onStreamInfoReceived(stream, streamInfo);
            }
        };

        final Consumer<MediaSource> onSuccess = new Consumer<MediaSource>() {
            @Override
            public void accept(MediaSource mediaSource) throws Exception {
                onMediaSourceReceived(mediaSource);
            }
        };

        final Consumer<Throwable> onError = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                onStreamInfoError(throwable);
            }
        };

        loader = stream.getStream()
                .observeOn(Schedulers.io())
                .map(onReceive)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSuccess, onError);
    }

    private MediaSource onStreamInfoReceived(@NonNull final PlayQueueItem item,
                                             @NonNull final StreamInfo info) throws Exception {
        if (callback == null) {
            throw new Exception("No available callback for resolving stream info.");
        }

        final MediaSource mediaSource = callback.sourceOf(item, info);

        if (mediaSource == null) {
            throw new Exception("Unable to resolve source from stream info. URL: " + stream.getUrl() +
                    ", audio count: " + info.audio_streams.size() +
                    ", video count: " + info.video_only_streams.size() + info.video_streams.size());
        }

        return mediaSource;
    }

    private void onMediaSourceReceived(final MediaSource mediaSource) throws Exception {
        if (exoPlayer == null || listener == null || mediaSource == null) {
            throw new Exception("MediaSource loading failed. URL: " + stream.getUrl());
        }

        Log.d(TAG, " Loaded: [" + stream.getTitle() + "] with url: " + stream.getUrl());
        state = STATE_LOADED;

        this.mediaSource = mediaSource;
        this.mediaSource.prepareSource(exoPlayer, false, listener);
    }

    private void onStreamInfoError(final Throwable throwable) {
        Log.e(TAG, "Loading error:", throwable);
        error = throwable;
        state = STATE_LOADED;
    }

    /**
     * Delegate all errors to the player after {@link #load() load} is complete.
     *
     * Specifically, this method is called after an exception has occurred during loading or
     * {@link com.google.android.exoplayer2.source.MediaSource#prepareSource(ExoPlayer, boolean, Listener) prepareSource}.
     * */
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

    /**
     * Releases the media period (buffers).
     *
     * This may be called after {@link #releaseSource releaseSource}.
     * */
    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        mediaSource.releasePeriod(mediaPeriod);
    }

    /**
     * Cleans up all internal custom objects creating during loading.
     *
     * This method is called when the parent {@link com.google.android.exoplayer2.source.MediaSource}
     * is released or when the player is stopped.
     *
     * This method should not release or set null the resources passed in through the constructor.
     * This method should not set null the internal {@link com.google.android.exoplayer2.source.MediaSource}.
     * */
    @Override
    public void releaseSource() {
        if (mediaSource != null) {
            mediaSource.releaseSource();
        }
        if (loader != null) {
            loader.dispose();
        }

        /* Do not set mediaSource as null here as it may be called through releasePeriod */
        loader = null;
        exoPlayer = null;
        listener = null;
        error = null;

        state = STATE_INIT;
    }
}
