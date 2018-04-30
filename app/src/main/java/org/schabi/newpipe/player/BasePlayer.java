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
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.MediaSessionManager;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediasource.FailedMediaSource;
import org.schabi.newpipe.player.playback.BasePlayerMediaSession;
import org.schabi.newpipe.player.playback.CustomTrackSelector;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
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

    protected PlayQueue playQueue;
    protected PlayQueueAdapter playQueueAdapter;

    protected MediaSourceManager playbackManager;

    protected StreamInfo currentInfo;
    protected PlayQueueItem currentItem;

    protected Toast errorToast;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    protected final static int FAST_FORWARD_REWIND_AMOUNT_MILLIS = 10000; // 10 Seconds
    protected final static int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    protected final static int PROGRESS_LOOP_INTERVAL_MILLIS = 500;
    protected final static int RECOVERY_SKIP_THRESHOLD_MILLIS = 3000; // 3 seconds

    protected CustomTrackSelector trackSelector;
    protected PlayerDataSource dataSource;

    protected SimpleExoPlayer simpleExoPlayer;
    protected AudioReactor audioReactor;
    protected MediaSessionManager mediaSessionManager;

    private boolean isPrepared = false;
    private boolean isSynchronizing = false;

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
        if (simpleExoPlayer == null) initPlayer(/*playOnInit=*/true);
        initListeners();
    }

    public void initPlayer(final boolean playOnReady) {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");

        if (databaseUpdateReactor != null) databaseUpdateReactor.dispose();
        databaseUpdateReactor = new CompositeDisposable();

        final String userAgent = Downloader.USER_AGENT;
        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        dataSource = new PlayerDataSource(context, userAgent, bandwidthMeter);

        final TrackSelection.Factory trackSelectionFactory =
                PlayerHelper.getQualitySelector(context, bandwidthMeter);
        trackSelector = new CustomTrackSelector(trackSelectionFactory);

        final LoadControl loadControl = new LoadController(context);
        final RenderersFactory renderFactory = new DefaultRenderersFactory(context);
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(renderFactory, trackSelector, loadControl);
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(playOnReady);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));

        audioReactor = new AudioReactor(context, simpleExoPlayer);
        mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                new BasePlayerMediaSession(this));
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

        // Good to go...
        initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, /*playOnInit=*/true);
    }

    protected void initPlayback(@NonNull final PlayQueue queue,
                                @Player.RepeatMode final int repeatMode,
                                final float playbackSpeed,
                                final float playbackPitch,
                                final boolean playOnReady) {
        destroyPlayer();
        initPlayer(playOnReady);
        setRepeatMode(repeatMode);
        setPlaybackParameters(playbackSpeed, playbackPitch);

        playQueue = queue;
        playQueue.init();
        if (playbackManager != null) playbackManager.dispose();
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
        if (audioReactor != null) audioReactor.dispose();
        if (playbackManager != null) playbackManager.dispose();
        if (databaseUpdateReactor != null) databaseUpdateReactor.dispose();

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();
        unregisterBroadcastReceiver();

        trackSelector = null;
        simpleExoPlayer = null;
        mediaSessionManager = null;
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
                onPause();
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

    public static final int STATE_PREFLIGHT = -1;
    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    protected int currentState = STATE_PREFLIGHT;

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
        if (simpleExoPlayer == null) return;
        onUpdateProgress(
                Math.max((int) simpleExoPlayer.getCurrentPosition(), 0),
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage()
        );
    }

    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
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
        if (playQueue == null) return;

        switch (reason) {
            case Player.TIMELINE_CHANGE_REASON_RESET: // called after #block
            case Player.TIMELINE_CHANGE_REASON_PREPARED: // called after #unblock
            case Player.TIMELINE_CHANGE_REASON_DYNAMIC: // called after playlist changes
                // Ensures MediaSourceManager#update is complete
                final boolean isPlaylistStable = timeline.getWindowCount() == playQueue.size();
                // Ensure dynamic/livestream timeline changes does not cause negative position
                if (isPlaylistStable && !isCurrentWindowValid() && !isSynchronizing) {
                    if (DEBUG) Log.d(TAG, "Playback - negative time position reached, " +
                            "clamping to default position.");
                    seekToDefault();
                }
                break;
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
                maybeCorrectSeekPosition();
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                    break;
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case Player.STATE_ENDED: // 4
                changeState(STATE_COMPLETED);
                isPrepared = false;
                break;
        }
    }

    private void maybeCorrectSeekPosition() {
        if (playQueue == null || simpleExoPlayer == null || currentInfo == null) return;

        final int currentSourceIndex = playQueue.getIndex();
        final PlayQueueItem currentSourceItem = playQueue.getItem();
        if (currentSourceItem == null) return;

        final long recoveryPositionMillis = currentSourceItem.getRecoveryPosition();
        final boolean isCurrentWindowCorrect =
                simpleExoPlayer.getCurrentPeriodIndex() == currentSourceIndex;
        final long presetStartPositionMillis = currentInfo.getStartPosition() * 1000;

        if (recoveryPositionMillis != PlayQueueItem.RECOVERY_UNSET && isCurrentWindowCorrect) {
            // Is recovering previous playback?
            if (DEBUG) Log.d(TAG, "Playback - Rewinding to recovery time=" +
                    "[" + getTimeString((int)recoveryPositionMillis) + "]");
            seekTo(recoveryPositionMillis);
            playQueue.unsetRecovery(currentSourceIndex);

        } else if (isSynchronizing && isLive()) {
            if (DEBUG) Log.d(TAG, "Playback - Synchronizing livestream to default time");
            // Is still synchronizing?
            seekToDefault();

        } else if (isSynchronizing && presetStartPositionMillis > 0L) {
            if (DEBUG) Log.d(TAG, "Playback - Seeking to preset start " +
                    "position=[" + presetStartPositionMillis + "]");
            // Has another start position?
            seekTo(presetStartPositionMillis);
            currentInfo.setStartPosition(0);
        }

        isSynchronizing = false;
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

    private void processSourceError(final IOException error) {
        if (simpleExoPlayer == null || playQueue == null) return;
        setRecovery();

        final Throwable cause = error.getCause();
        if (cause instanceof BehindLiveWindowException) {
            reload();
        } else if (cause instanceof UnknownHostException) {
            playQueue.error(/*isNetworkProblem=*/true);
        } else if (isCurrentWindowValid()) {
            playQueue.error(/*isTransitioningToBadStream=*/true);
        } else if (cause instanceof FailedMediaSource.MediaSourceResolutionException) {
            playQueue.error(/*recoverableWithNoAvailableStream=*/false);
        } else if (cause instanceof FailedMediaSource.StreamInfoLoadException) {
            playQueue.error(/*recoverableIfLoadFailsWhenNetworkIsFine=*/false);
        } else {
            playQueue.error(/*noIdeaWhatHappenedAndLetUserChooseWhatToDo=*/true);
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
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (simpleExoPlayer == null || isLive() || !isPlaying()) return false;

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        final long currentDurationMillis = simpleExoPlayer.getDuration();
        return currentDurationMillis - currentPositionMillis < timeToEndMillis;
    }

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
    }

    @Override
    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item,
                                      @Nullable final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackSynchronize() called with " +
                (info != null ? "available" : "null") + " info, " +
                "item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");
        if (simpleExoPlayer == null || playQueue == null) return;

        final boolean onPlaybackInitial = currentItem == null;
        final boolean hasPlayQueueItemChanged = currentItem != item;
        final boolean hasStreamInfoChanged = currentInfo != info;

        final int currentPlayQueueIndex = playQueue.indexOf(item);
        final int currentPlaylistIndex = simpleExoPlayer.getCurrentWindowIndex();
        final int currentPlaylistSize = simpleExoPlayer.getCurrentTimeline().getWindowCount();

        // when starting playback on the last item when not repeating, maybe auto queue
        if (info != null && currentPlayQueueIndex == playQueue.size() - 1 &&
                getRepeatMode() == Player.REPEAT_MODE_OFF &&
                PlayerHelper.isAutoQueueEnabled(context)) {
            final PlayQueue autoQueue = PlayerHelper.autoQueueOf(info, playQueue.getStreams());
            if (autoQueue != null) playQueue.append(autoQueue.getStreams());
        }
        // If nothing to synchronize
        if (!hasPlayQueueItemChanged && !hasStreamInfoChanged) {
            return;
        }

        currentItem = item;
        currentInfo = info;
        if (hasPlayQueueItemChanged) {
            // updates only to the stream info should not trigger another view count
            registerView();
            initThumbnail(info == null ? item.getThumbnailUrl() : info.getThumbnailUrl());
        }
        onMetadataChanged(item, info, currentPlayQueueIndex, hasPlayQueueItemChanged);

        // Check if on wrong window
        if (currentPlayQueueIndex != playQueue.getIndex()) {
            Log.e(TAG, "Playback - Play Queue may be desynchronized: item " +
                    "index=[" + currentPlayQueueIndex + "], " +
                    "queue index=[" + playQueue.getIndex() + "]");

            // Check if bad seek position
        } else if ((currentPlaylistSize > 0 && currentPlayQueueIndex >= currentPlaylistSize) ||
                currentPlayQueueIndex < 0) {
            Log.e(TAG, "Playback - Trying to seek to invalid " +
                    "index=[" + currentPlayQueueIndex + "] with " +
                    "playlist length=[" + currentPlaylistSize + "]");

            // If not playing correct stream, change window position and sets flag
            // for synchronizing once window position is corrected
            // @see maybeCorrectSeekPosition()
        } else if (currentPlaylistIndex != currentPlayQueueIndex || onPlaybackInitial ||
                !isPlaying()) {
            if (DEBUG) Log.d(TAG, "Playback - Rewinding to correct" +
                    " index=[" + currentPlayQueueIndex + "]," +
                    " from=[" + currentPlaylistIndex + "], size=[" + currentPlaylistSize + "].");
            isSynchronizing = true;
            simpleExoPlayer.seekToDefaultPosition(currentPlayQueueIndex);
        }
    }

    abstract protected void onMetadataChanged(@NonNull final PlayQueueItem item,
                                              @Nullable final StreamInfo info,
                                              final int newPlayQueueIndex,
                                              final boolean hasPlayQueueItemChanged);

    @Nullable
    @Override
    public MediaSource sourceOf(PlayQueueItem item, StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM || streamType == StreamType.LIVE_STREAM)) {
            return null;
        }

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

    public void onPlay() {
        if (DEBUG) Log.d(TAG, "onPlay() called");
        if (audioReactor == null || playQueue == null || simpleExoPlayer == null) return;

        audioReactor.requestAudioFocus();

        if (getCurrentState() == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        simpleExoPlayer.setPlayWhenReady(true);
    }

    public void onPause() {
        if (DEBUG) Log.d(TAG, "onPause() called");
        if (audioReactor == null || simpleExoPlayer == null) return;

        audioReactor.abandonAudioFocus();
        simpleExoPlayer.setPlayWhenReady(false);
    }

    public void onPlayPause() {
        if (DEBUG) Log.d(TAG, "onPlayPause() called");

        if (!isPlaying()) {
            onPlay();
        } else {
            onPause();
        }
    }

    public void onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called");
        seekBy(-FAST_FORWARD_REWIND_AMOUNT_MILLIS);
    }

    public void onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called");
        seekBy(FAST_FORWARD_REWIND_AMOUNT_MILLIS);
    }

    public void onPlayPrevious() {
        if (simpleExoPlayer == null || playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayPrevious() called");

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
        * restart current track. Also restart the track if the current track
        * is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS ||
                playQueue.getIndex() == 0) {
            seekToDefault();
            playQueue.offsetIndex(0);
        } else {
            savePlaybackState();
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
            savePlaybackState();
        }
        playQueue.setIndex(index);
    }

    public void seekTo(long positionMillis) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]");
        if (simpleExoPlayer == null || positionMillis < 0 ||
                positionMillis > simpleExoPlayer.getDuration()) return;
        simpleExoPlayer.seekTo(positionMillis);
    }

    public void seekBy(long offsetMillis) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]");
        seekTo(simpleExoPlayer.getCurrentPosition() + offsetMillis);
    }

    public boolean isCurrentWindowValid() {
        return simpleExoPlayer != null && simpleExoPlayer.getDuration() >= 0
                && simpleExoPlayer.getCurrentPosition() >= 0;
    }

    public void seekToDefault() {
        if (simpleExoPlayer != null) {
            simpleExoPlayer.seekToDefaultPosition();
        }
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
            playbackManager.dispose();
        }

        if (playQueue != null) {
            playbackManager = new MediaSourceManager(this, playQueue);
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

        if (simpleExoPlayer.getCurrentPosition() > RECOVERY_SKIP_THRESHOLD_MILLIS &&
                simpleExoPlayer.getCurrentPosition() <
                        simpleExoPlayer.getDuration() - RECOVERY_SKIP_THRESHOLD_MILLIS) {
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

    /** Checks if the current playback is a livestream AND is playing at or beyond the live edge */
    public boolean isLiveEdge() {
        if (simpleExoPlayer == null || !isLive()) return false;

        final Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
        final int currentWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        if (currentTimeline.isEmpty() || currentWindowIndex < 0 ||
                currentWindowIndex >= currentTimeline.getWindowCount()) {
            return false;
        }

        Timeline.Window timelineWindow = new Timeline.Window();
        currentTimeline.getWindow(currentWindowIndex, timelineWindow);
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer.getCurrentPosition();
    }

    public boolean isLive() {
        if (simpleExoPlayer == null) return false;
        try {
            return simpleExoPlayer.isCurrentWindowDynamic();
        } catch (@NonNull IndexOutOfBoundsException ignored) {
            // Why would this even happen =(
            return false;
        }
    }

    public boolean isPlaying() {
        final int state = simpleExoPlayer.getPlaybackState();
        return (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                && simpleExoPlayer.getPlayWhenReady();
    }

    @Player.RepeatMode
    public int getRepeatMode() {
        return simpleExoPlayer.getRepeatMode();
    }

    public void setRepeatMode(@Player.RepeatMode final int repeatMode) {
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

    public boolean isPrepared() {
        return isPrepared;
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
