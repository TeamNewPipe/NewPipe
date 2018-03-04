/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BasePlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.history.HistoryRecordManager;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playback.CustomTrackSelector;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueAdapter;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.util.SerializedCache;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_PERIOD_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;

/**
 * Base for the players, joining the common properties
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class BasePlayer implements
        Player.EventListener, PlaybackListener, ImageLoadingListener {

    public static final boolean DEBUG = true;
    @NonNull public static final String TAG = "BasePlayer";

    @NonNull final protected Context context;

    @NonNull final protected BroadcastReceiver broadcastReceiver;
    @NonNull final protected IntentFilter intentFilter;

    @NonNull final protected HistoryRecordManager recordManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String REPEAT_MODE = "repeat_mode";
    public static final String PLAYBACK_PITCH = "playback_pitch";
    public static final String PLAYBACK_SPEED = "playback_speed";
    public static final String PLAYBACK_QUALITY = "playback_quality";
    public static final String PLAY_QUEUE_KEY = "play_queue_key";
    public static final String APPEND_ONLY = "append_only";
    public static final String SELECT_ON_APPEND = "select_on_append";

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    protected static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    protected static final float[] PLAYBACK_PITCHES = {0.8f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.2f};

    protected PlayQueue playQueue;
    protected PlayQueueAdapter playQueueAdapter;

    protected MediaSourceManager playbackManager;

    protected StreamInfo currentInfo;
    protected PlayQueueItem currentItem;

    protected Toast errorToast;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    protected final static int FAST_FORWARD_REWIND_AMOUNT = 10000; // 10 Seconds
    protected final static int PLAY_PREV_ACTIVATION_LIMIT = 5000; // 5 seconds
    protected final static int PROGRESS_LOOP_INTERVAL = 500;
    protected final static int RECOVERY_SKIP_THRESHOLD = 3000; // 3 seconds

    protected CustomTrackSelector trackSelector;
    protected PlayerDataSource dataSource;

    protected SimpleExoPlayer simpleExoPlayer;
    protected AudioReactor audioReactor;

    protected boolean isPrepared = false;

    protected Disposable progressUpdateReactor;
    protected CompositeDisposable databaseUpdateReactor;

    //////////////////////////////////////////////////////////////////////////*/

    public BasePlayer(@NonNull final Context context) {
        this.context = context;

        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBroadcastReceived(intent);
            }
        };
        this.intentFilter = new IntentFilter();
        setupBroadcastReceiver(intentFilter);
        context.registerReceiver(broadcastReceiver, intentFilter);

        this.recordManager = new HistoryRecordManager(context);
    }

    public void setup() {
        if (simpleExoPlayer == null) initPlayer();
        initListeners();
    }

    public void initPlayer() {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");

        if (databaseUpdateReactor != null) databaseUpdateReactor.dispose();
        databaseUpdateReactor = new CompositeDisposable();

        final String userAgent = Downloader.USER_AGENT;
        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        dataSource = new PlayerDataSource(context, userAgent, bandwidthMeter);

        final AdaptiveTrackSelection.Factory trackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector = new CustomTrackSelector(trackSelectionFactory);

        final LoadControl loadControl = new LoadController(context);
        final RenderersFactory renderFactory = new DefaultRenderersFactory(context);
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(renderFactory, trackSelector, loadControl);
        audioReactor = new AudioReactor(context, simpleExoPlayer);

        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(true);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));
    }

    public void initListeners() {}

    public void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        // Resolve play queue
        if (!intent.hasExtra(PLAY_QUEUE_KEY)) return;
        final String intentCacheKey = intent.getStringExtra(PLAY_QUEUE_KEY);
        final PlayQueue queue = SerializedCache.getInstance().take(intentCacheKey, PlayQueue.class);
        if (queue == null) return;

        // Resolve append intents
        if (intent.getBooleanExtra(APPEND_ONLY, false) && playQueue != null) {
            int sizeBeforeAppend = playQueue.size();
            playQueue.append(queue.getStreams());

            if (intent.getBooleanExtra(SELECT_ON_APPEND, false) &&
                    queue.getStreams().size() > 0) {
                playQueue.setIndex(sizeBeforeAppend);
            }

            return;
        }

        final int repeatMode = intent.getIntExtra(REPEAT_MODE, getRepeatMode());
        final float playbackSpeed = intent.getFloatExtra(PLAYBACK_SPEED, getPlaybackSpeed());
        final float playbackPitch = intent.getFloatExtra(PLAYBACK_PITCH, getPlaybackPitch());

        // Re-initialization
        destroyPlayer();
        initPlayer();
        setRepeatMode(repeatMode);
        setPlaybackParameters(playbackSpeed, playbackPitch);

        // Good to go...
        initPlayback(queue);
    }

    protected void initPlayback(final PlayQueue queue) {
        playQueue = queue;
        playQueue.init();
        playbackManager = new MediaSourceManager(this, playQueue);

        if (playQueueAdapter != null) playQueueAdapter.dispose();
        playQueueAdapter = new PlayQueueAdapter(context, playQueue);
    }

    public void destroyPlayer() {
        if (DEBUG) Log.d(TAG, "destroyPlayer() called");
        if (simpleExoPlayer != null) {
            simpleExoPlayer.removeListener(this);
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) stopProgressLoop();
        if (playQueue != null) playQueue.dispose();
        if (playbackManager != null) playbackManager.dispose();
        if (audioReactor != null) audioReactor.abandonAudioFocus();
        if (databaseUpdateReactor != null) databaseUpdateReactor.dispose();

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();
        clearThumbnailCache();
        unregisterBroadcastReceiver();

        trackSelector = null;
        simpleExoPlayer = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    //////////////////////////////////////////////////////////////////////////*/

    public void initThumbnail(final String url) {
        if (DEBUG) Log.d(TAG, "Thumbnail - initThumbnail() called");
        if (url == null || url.isEmpty()) return;
        ImageLoader.getInstance().resume();
        ImageLoader.getInstance().loadImage(url, this);
    }

    @Override
    public void onLoadingStarted(String imageUri, View view) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingStarted() called on: " +
                "imageUri = [" + imageUri + "], view = [" + view + "]");
    }

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        Log.e(TAG, "Thumbnail - onLoadingFailed() called on imageUri = [" + imageUri + "]",
                failReason.getCause());
    }

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingComplete() called with: " +
                "imageUri = [" + imageUri + "], view = [" + view + "], " +
                "loadedImage = [" + loadedImage + "]");
    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingCancelled() called with: " +
                "imageUri = [" + imageUri + "], view = [" + view + "]");
    }

    protected void clearThumbnailCache() {
        ImageLoader.getInstance().clearMemoryCache();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // MediaSource Building
    //////////////////////////////////////////////////////////////////////////*/

    public MediaSource buildLiveMediaSource(@NonNull final String sourceUrl,
                                            @C.ContentType final int type) {
        if (DEBUG) {
            Log.d(TAG, "buildLiveMediaSource() called with: url = [" + sourceUrl +
                    "], content type = [" + type + "]");
        }
        if (dataSource == null) return null;

        final Uri uri = Uri.parse(sourceUrl);
        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().createMediaSource(uri);
            case C.TYPE_DASH:
                return dataSource.getLiveDashMediaSourceFactory().createMediaSource(uri);
            case C.TYPE_HLS:
                return dataSource.getLiveHlsMediaSourceFactory().createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    public MediaSource buildMediaSource(@NonNull final String sourceUrl,
                                        @NonNull final String cacheKey,
                                        @NonNull final String overrideExtension) {
        if (DEBUG) {
            Log.d(TAG, "buildMediaSource() called with: url = [" + sourceUrl +
                    "], cacheKey = [" + cacheKey + "]" +
                    "], overrideExtension = [" + overrideExtension + "]");
        }
        if (dataSource == null) return null;

        final Uri uri = Uri.parse(sourceUrl);
        @C.ContentType final int type = TextUtils.isEmpty(overrideExtension) ?
                Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);

        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().createMediaSource(uri);
            case C.TYPE_DASH:
                return dataSource.getDashMediaSourceFactory().createMediaSource(uri);
            case C.TYPE_HLS:
                return dataSource.getHlsMediaSourceFactory().createMediaSource(uri);
            case C.TYPE_OTHER:
                return dataSource.getExtractorMediaSourceFactory(cacheKey).createMediaSource(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Add your action in the intentFilter
     *
     * @param intentFilter intent filter that will be used for register the receiver
     */
    protected void setupBroadcastReceiver(IntentFilter intentFilter) {
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    }

    public void onBroadcastReceived(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                if (isPlaying()) onVideoPlayPause();
                break;
        }
    }

    public void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (final IllegalArgumentException unregisteredException) {
            Log.e(TAG, "Broadcast receiver already unregistered.", unregisteredException);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    protected int currentState = -1;

    public void changeState(int state) {
        if (DEBUG) Log.d(TAG, "changeState() called with: state = [" + state + "]");
        currentState = state;
        switch (state) {
            case STATE_BLOCKED:
                onBlocked();
                break;
            case STATE_PLAYING:
                onPlaying();
                break;
            case STATE_BUFFERING:
                onBuffering();
                break;
            case STATE_PAUSED:
                onPaused();
                break;
            case STATE_PAUSED_SEEK:
                onPausedSeek();
                break;
            case STATE_COMPLETED:
                onCompleted();
                break;
        }
    }

    public void onBlocked() {
        if (DEBUG) Log.d(TAG, "onBlocked() called");
        if (!isProgressLoopRunning()) startProgressLoop();
    }

    public void onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called");
        if (!isProgressLoopRunning()) startProgressLoop();
        if (!isCurrentWindowValid()) seekToDefault();
    }

    public void onBuffering() {}

    public void onPaused() {
        if (isProgressLoopRunning()) stopProgressLoop();
    }

    public void onPausedSeek() {}

    public void onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called");
        if (playQueue.getIndex() < playQueue.size() - 1) playQueue.offsetIndex(+1);
        if (isProgressLoopRunning()) stopProgressLoop();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    //////////////////////////////////////////////////////////////////////////*/

    public void onRepeatClicked() {
        if (DEBUG) Log.d(TAG, "onRepeatClicked() called");

        final int mode;

        switch (getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                mode = Player.REPEAT_MODE_ONE;
                break;
            case Player.REPEAT_MODE_ONE:
                mode = Player.REPEAT_MODE_ALL;
                break;
            case Player.REPEAT_MODE_ALL:
            default:
                mode = Player.REPEAT_MODE_OFF;
                break;
        }

        setRepeatMode(mode);
        if (DEBUG) Log.d(TAG, "onRepeatClicked() currentRepeatMode = " + getRepeatMode());
    }

    public void onShuffleClicked() {
        if (DEBUG) Log.d(TAG, "onShuffleClicked() called");

        if (simpleExoPlayer == null) return;
        simpleExoPlayer.setShuffleModeEnabled(!simpleExoPlayer.getShuffleModeEnabled());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Progress Updates
    //////////////////////////////////////////////////////////////////////////*/

    public abstract void onUpdateProgress(int currentProgress, int duration, int bufferPercent);

    protected void startProgressLoop() {
        if (progressUpdateReactor != null) progressUpdateReactor.dispose();
        progressUpdateReactor = getProgressReactor();
    }

    protected void stopProgressLoop() {
        if (progressUpdateReactor != null) progressUpdateReactor.dispose();
        progressUpdateReactor = null;
    }

    public void triggerProgressUpdate() {
        onUpdateProgress(
                (int) simpleExoPlayer.getCurrentPosition(),
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage()
        );
    }


    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter(ignored -> isProgressLoopRunning())
                .subscribe(ignored -> triggerProgressUpdate());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest,
                                  @Player.TimelineChangeReason final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onTimelineChanged() called with " +
                (manifest == null ? "no manifest" : "available manifest") + ", " +
                "timeline size = [" + timeline.getWindowCount() + "], " +
                "reason = [" + reason + "]");

        switch (reason) {
            case Player.TIMELINE_CHANGE_REASON_RESET: // called after #block
            case Player.TIMELINE_CHANGE_REASON_PREPARED: // called after #unblock
            case Player.TIMELINE_CHANGE_REASON_DYNAMIC: // called after playlist changes
                if (playQueue != null && playbackManager != null &&
                        // ensures MediaSourceManager#update is complete
                        timeline.getWindowCount() == playQueue.size()) {
                    playbackManager.load();
                }
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onTracksChanged(), " +
                "track group size = " + trackGroups.length);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - playbackParameters(), " +
                "speed: " + playbackParameters.speed + ", " +
                "pitch: " + playbackParameters.pitch);
    }

    @Override
    public void onLoadingChanged(final boolean isLoading) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onLoadingChanged() called with: " +
                "isLoading = [" + isLoading + "]");

        if (!isLoading && getCurrentState() == STATE_PAUSED && isProgressLoopRunning()) {
            stopProgressLoop();
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: " +
                "playWhenReady = [" + playWhenReady + "], " +
                "playbackState = [" + playbackState + "]");

        if (getCurrentState() == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerStateChanged() is currently blocked");
            return;
        }

        switch (playbackState) {
            case Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case Player.STATE_READY: //3
                maybeRecover();
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                    break;
                }
                if (currentState == STATE_PAUSED_SEEK) break;
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case Player.STATE_ENDED: // 4
                // Ensure the current window has actually ended
                // since single windows that are still loading may produce an ended state
                if (isCurrentWindowValid() &&
                        simpleExoPlayer.getCurrentPosition() >= simpleExoPlayer.getDuration()) {
                    changeState(STATE_COMPLETED);
                    isPrepared = false;
                }
                break;
        }
    }

    private void maybeRecover() {
        final int currentSourceIndex = playQueue.getIndex();
        final PlayQueueItem currentSourceItem = playQueue.getItem();

        // Check if already playing correct window
        final boolean isCurrentPeriodCorrect =
                simpleExoPlayer.getCurrentPeriodIndex() == currentSourceIndex;

        // Check if recovering
        if (isCurrentPeriodCorrect && currentSourceItem != null) {
            /* Recovering with sub-second position may cause a long buffer delay in ExoPlayer,
             * rounding this position to the nearest second will help alleviate this.*/
            final long position = currentSourceItem.getRecoveryPosition();

            /* Skip recovering if the recovery position is not set.*/
            if (position == PlayQueueItem.RECOVERY_UNSET) return;

            if (DEBUG) Log.d(TAG, "Rewinding to recovery window: " + currentSourceIndex +
                    " at: " + getTimeString((int)position));
            simpleExoPlayer.seekTo(currentSourceItem.getRecoveryPosition());
            playQueue.unsetRecovery(currentSourceIndex);
        }
    }

    /**
     * Processes the exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
     * There are multiple types of errors: <br><br>
     *
     * {@link ExoPlaybackException#TYPE_SOURCE TYPE_SOURCE}: <br><br>
     *
     * {@link ExoPlaybackException#TYPE_UNEXPECTED TYPE_UNEXPECTED}: <br><br>
     * If a runtime error occurred, then we can try to recover it by restarting the playback
     * after setting the timestamp recovery. <br><br>
     *
     * {@link ExoPlaybackException#TYPE_RENDERER TYPE_RENDERER}: <br><br>
     * If the renderer failed, treat the error as unrecoverable.
     *
     * @see #processSourceError(IOException)
     * @see Player.EventListener#onPlayerError(ExoPlaybackException)
     *  */
    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerError() called with: " +
                "error = [" + error + "]");
        if (errorToast != null) {
            errorToast.cancel();
            errorToast = null;
        }

        savePlaybackState();

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                processSourceError(error.getSourceException());
                showStreamError(error);
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                showRecoverableError(error);
                setRecovery();
                reload();
                break;
            default:
                showUnrecoverableError(error);
                onPlaybackShutdown();
                break;
        }
    }

    /**
     * Processes {@link ExoPlaybackException} tagged with {@link ExoPlaybackException#TYPE_SOURCE}.
     * <br><br>
     * If the current {@link com.google.android.exoplayer2.Timeline.Window window} is valid,
     * then we know the error is produced by transitioning into a bad window, therefore we report
     * an error to the play queue based on if the current error can be skipped.
     * <br><br>
     * This is done because ExoPlayer reports the source exceptions before window is
     * transitioned on seamless playback. Because player error causes ExoPlayer to go
     * back to {@link Player#STATE_IDLE STATE_IDLE}, we reset and prepare the media source
     * again to resume playback.
     * <br><br>
     * In the event that this error is produced during a valid stream playback, we save the
     * current position so the playback may be recovered and resumed manually by the user. This
     * happens only if the playback is {@link #RECOVERY_SKIP_THRESHOLD} milliseconds until complete.
     * <br><br>
     * In the event of livestreaming being lagged behind for any reason, most notably pausing for
     * too long, a {@link BehindLiveWindowException} will be produced. This will trigger a reload
     * instead of skipping or removal.
     * */
    private void processSourceError(final IOException error) {
        if (simpleExoPlayer == null || playQueue == null) return;

        if (simpleExoPlayer.getCurrentPosition() <
                simpleExoPlayer.getDuration() - RECOVERY_SKIP_THRESHOLD) {
            setRecovery();
        }

        final Throwable cause = error.getCause();
        if (cause instanceof BehindLiveWindowException) {
            reload();
        } else if (cause instanceof UnknownHostException) {
            playQueue.error(/*isNetworkProblem=*/true);
        } else {
            playQueue.error(isCurrentWindowValid());
        }
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with " +
                "reason = [" + reason + "]");
        // Refresh the playback if there is a transition to the next video
        final int newPeriodIndex = simpleExoPlayer.getCurrentPeriodIndex();

        /* Discontinuity reasons!! Thank you ExoPlayer lords */
        switch (reason) {
            case DISCONTINUITY_REASON_PERIOD_TRANSITION:
                if (newPeriodIndex == playQueue.getIndex()) {
                    registerView();
                } else {
                    playQueue.offsetIndex(+1);
                }
            case DISCONTINUITY_REASON_SEEK:
            case DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
            case DISCONTINUITY_REASON_INTERNAL:
                break;
        }
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: " +
                "mode = [" + reason + "]");
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: " +
                "mode = [" + shuffleModeEnabled + "]");
        if (playQueue == null) return;
        if (shuffleModeEnabled) {
            playQueue.shuffle();
        } else {
            playQueue.unshuffle();
        }
    }

    @Override
    public void onSeekProcessed() {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onSeekProcessed() called");
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPlaybackBlock() {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackBlock() called");

        currentItem = null;
        currentInfo = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackUnblock() called");

        if (getCurrentState() == STATE_BLOCKED) changeState(STATE_BUFFERING);

        simpleExoPlayer.prepare(mediaSource);
        seekToDefault();
    }

    @Override
    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item,
                                      @Nullable final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackSynchronize() called with " +
                (info != null ? "available" : "null") + " info, " +
                "item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");

        final boolean hasPlayQueueItemChanged = currentItem != item;
        final boolean hasStreamInfoChanged = currentInfo != info;
        if (!hasPlayQueueItemChanged && !hasStreamInfoChanged) {
            return; // Nothing to synchronize
        }

        currentItem = item;
        currentInfo = info;
        if (hasPlayQueueItemChanged) {
            // updates only to the stream info should not trigger another view count
            registerView();
            initThumbnail(info == null ? item.getThumbnailUrl() : info.getThumbnailUrl());
        }

        final int currentSourceIndex = playQueue.indexOf(item);
        onMetadataChanged(item, info, currentSourceIndex, hasPlayQueueItemChanged);

        // Check if on wrong window
        if (simpleExoPlayer == null) return;
        if (currentSourceIndex != playQueue.getIndex()) {
            Log.e(TAG, "Play Queue may be desynchronized: item index=[" + currentSourceIndex +
                    "], queue index=[" + playQueue.getIndex() + "]");

            // on metadata changed
        } else if (simpleExoPlayer.getCurrentWindowIndex() != currentSourceIndex || !isPlaying()) {
            final long startPos = info != null ? info.start_position : 0;
            if (DEBUG) Log.d(TAG, "Rewinding to correct window=[" + currentSourceIndex + "]," +
                    " at=[" + getTimeString((int)startPos) + "]," +
                    " from=[" + simpleExoPlayer.getCurrentWindowIndex() + "].");
            simpleExoPlayer.seekTo(currentSourceIndex, startPos);
        }
    }

    abstract protected void onMetadataChanged(@NonNull final PlayQueueItem item,
                                              @Nullable final StreamInfo info,
                                              final int newPlayQueueIndex,
                                              final boolean hasPlayQueueItemChanged);

    @Nullable
    @Override
    public MediaSource sourceOf(PlayQueueItem item, StreamInfo info) {
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(info.getHlsUrl(), C.TYPE_HLS);
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(info.getDashMpdUrl(), C.TYPE_DASH);
        }

        return null;
    }

    @Override
    public void onPlaybackShutdown() {
        if (DEBUG) Log.d(TAG, "Shutting down...");
        destroy();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    public void showStreamError(Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_stream_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    public void showRecoverableError(Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_recoverable_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    public void showUnrecoverableError(Exception exception) {
        exception.printStackTrace();

        if (errorToast != null) {
            errorToast.cancel();
        }
        errorToast = Toast.makeText(context, R.string.player_unrecoverable_failure, Toast.LENGTH_SHORT);
        errorToast.show();
    }

    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        if (playWhenReady) audioReactor.requestAudioFocus();
        changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
    }

    public void onVideoPlayPause() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");

        if (!isPlaying()) {
            audioReactor.requestAudioFocus();
        } else {
            audioReactor.abandonAudioFocus();
        }

        if (getCurrentState() == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        simpleExoPlayer.setPlayWhenReady(!isPlaying());
    }

    public void onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called");
        seekBy(-FAST_FORWARD_REWIND_AMOUNT);
    }

    public void onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called");
        seekBy(FAST_FORWARD_REWIND_AMOUNT);
    }

    public void onPlayPrevious() {
        if (simpleExoPlayer == null || playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayPrevious() called");

        savePlaybackState();

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT milliseconds, restart current track.
        * Also restart the track if the current track is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT || playQueue.getIndex() == 0) {
            final long startPos = currentInfo == null ? 0 : currentInfo.start_position;
            simpleExoPlayer.seekTo(startPos);
        } else {
            playQueue.offsetIndex(-1);
        }
    }

    public void onPlayNext() {
        if (playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayNext() called");

        savePlaybackState();

        playQueue.offsetIndex(+1);
    }

    public void onSelected(final PlayQueueItem item) {
        if (playQueue == null || simpleExoPlayer == null) return;

        final int index = playQueue.indexOf(item);
        if (index == -1) return;

        if (playQueue.getIndex() == index && simpleExoPlayer.getCurrentWindowIndex() == index) {
            seekToDefault();
        } else {
            playQueue.setIndex(index);
        }
    }

    public void seekBy(int milliSeconds) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: milliSeconds = [" + milliSeconds + "]");
        if (simpleExoPlayer == null || (isCompleted() && milliSeconds > 0) ||
                ((milliSeconds < 0 && simpleExoPlayer.getCurrentPosition() == 0))) {
            return;
        }

        int progress = (int) (simpleExoPlayer.getCurrentPosition() + milliSeconds);
        if (progress < 0) progress = 0;
        simpleExoPlayer.seekTo(progress);
    }

    public boolean isCurrentWindowValid() {
        return simpleExoPlayer != null && simpleExoPlayer.getDuration() >= 0
                && simpleExoPlayer.getCurrentPosition() >= 0;
    }

    public void seekToDefault() {
        if (simpleExoPlayer != null) simpleExoPlayer.seekToDefaultPosition();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void registerView() {
        if (databaseUpdateReactor == null || currentInfo == null) return;
        databaseUpdateReactor.add(recordManager.onViewed(currentInfo).onErrorComplete()
                .subscribe(
                        ignored -> {/* successful */},
                        error -> Log.e(TAG, "Player onViewed() failure: ", error)
                ));
    }

    protected void reload() {
        if (playbackManager != null) {
            playbackManager.reset();
            playbackManager.load();
        }
    }

    protected void savePlaybackState(final StreamInfo info, final long progress) {
        if (info == null || databaseUpdateReactor == null) return;
        final Disposable stateSaver = recordManager.saveStreamState(info, progress)
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorComplete()
                .subscribe(
                        ignored -> {/* successful */},
                        error -> Log.e(TAG, "savePlaybackState() failure: ", error)
                );
        databaseUpdateReactor.add(stateSaver);
    }

    private void savePlaybackState() {
        if (simpleExoPlayer == null || currentInfo == null) return;

        if (simpleExoPlayer.getCurrentPosition() > RECOVERY_SKIP_THRESHOLD &&
                simpleExoPlayer.getCurrentPosition() <
                        simpleExoPlayer.getDuration() - RECOVERY_SKIP_THRESHOLD) {
            savePlaybackState(currentInfo, simpleExoPlayer.getCurrentPosition());
        }
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    public SimpleExoPlayer getPlayer() {
        return simpleExoPlayer;
    }

    public AudioReactor getAudioReactor() {
        return audioReactor;
    }

    public int getCurrentState() {
        return currentState;
    }

    public String getVideoUrl() {
        return currentItem == null ? context.getString(R.string.unknown_content) : currentItem.getUrl();
    }

    public String getVideoTitle() {
        return currentItem == null ? context.getString(R.string.unknown_content) : currentItem.getTitle();
    }

    public String getUploaderName() {
        return currentItem == null ? context.getString(R.string.unknown_content) : currentItem.getUploader();
    }

    public boolean isCompleted() {
        return simpleExoPlayer != null && simpleExoPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isPlaying() {
        final int state = simpleExoPlayer.getPlaybackState();
        return (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                && simpleExoPlayer.getPlayWhenReady();
    }

    public int getRepeatMode() {
        return simpleExoPlayer.getRepeatMode();
    }

    public void setRepeatMode(final int repeatMode) {
        simpleExoPlayer.setRepeatMode(repeatMode);
    }

    public float getPlaybackSpeed() {
        return getPlaybackParameters().speed;
    }

    public float getPlaybackPitch() {
        return getPlaybackParameters().pitch;
    }

    public void setPlaybackSpeed(float speed) {
        setPlaybackParameters(speed, getPlaybackPitch());
    }

    public void setPlaybackPitch(float pitch) {
        setPlaybackParameters(getPlaybackSpeed(), pitch);
    }

    public PlaybackParameters getPlaybackParameters() {
        final PlaybackParameters defaultParameters = new PlaybackParameters(1f, 1f);
        if (simpleExoPlayer == null) return defaultParameters;
        final PlaybackParameters parameters = simpleExoPlayer.getPlaybackParameters();
        return parameters == null ? defaultParameters : parameters;
    }

    public void setPlaybackParameters(float speed, float pitch) {
        simpleExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, pitch));
    }

    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public PlayQueueAdapter getPlayQueueAdapter() {
        return playQueueAdapter;
    }

    public boolean isPlayerReady() {
        return currentState == STATE_PLAYING || currentState == STATE_COMPLETED || currentState == STATE_PAUSED;
    }

    public boolean isProgressLoopRunning() {
        return progressUpdateReactor != null && !progressUpdateReactor.isDisposed();
    }

    public void setRecovery() {
        if (playQueue == null || simpleExoPlayer == null) return;

        final int queuePos = playQueue.getIndex();
        final long windowPos = simpleExoPlayer.getCurrentPosition();

        if (windowPos > 0 && windowPos <= simpleExoPlayer.getDuration()) {
            setRecovery(queuePos, windowPos);
        }
    }

    public void setRecovery(final int queuePos, final long windowPos) {
        if (playQueue.size() <= queuePos) return;

        if (DEBUG) Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        playQueue.setRecovery(queuePos, windowPos);
    }
}
