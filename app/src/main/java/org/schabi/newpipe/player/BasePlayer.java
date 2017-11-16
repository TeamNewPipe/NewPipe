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
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.CacheFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueAdapter;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;

import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;

/**
 * Base for the players, joining the common properties
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class BasePlayer implements Player.EventListener, PlaybackListener {

    public static final boolean DEBUG = true;
    public static final String TAG = "BasePlayer";

    protected Context context;

    protected BroadcastReceiver broadcastReceiver;
    protected IntentFilter intentFilter;

    protected PlayQueueAdapter playQueueAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String REPEAT_MODE = "repeat_mode";
    public static final String PLAYBACK_PITCH = "playback_pitch";
    public static final String PLAYBACK_SPEED = "playback_speed";
    public static final String PLAYBACK_QUALITY = "playback_quality";
    public static final String PLAY_QUEUE = "play_queue";
    public static final String APPEND_ONLY = "append_only";

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    protected static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    protected static final float[] PLAYBACK_PITCHES = {0.8f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.2f};

    protected MediaSourceManager playbackManager;
    protected PlayQueue playQueue;

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

    protected SimpleExoPlayer simpleExoPlayer;
    protected AudioReactor audioReactor;

    protected boolean isPrepared = false;

    protected DefaultTrackSelector trackSelector;
    protected DataSource.Factory cacheDataSourceFactory;
    protected DefaultExtractorsFactory extractorsFactory;

    protected Disposable progressUpdateReactor;

    //////////////////////////////////////////////////////////////////////////*/

    public BasePlayer(Context context) {
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
    }

    public void setup() {
        if (simpleExoPlayer == null) initPlayer();
        initListeners();
    }

    public void initPlayer() {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");

        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        final AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        final LoadControl loadControl = new LoadController(context);
        final RenderersFactory renderFactory = new DefaultRenderersFactory(context);

        trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        extractorsFactory = new DefaultExtractorsFactory();
        cacheDataSourceFactory = new CacheFactory(context);

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(renderFactory, trackSelector, loadControl);
        audioReactor = new AudioReactor(context, simpleExoPlayer);

        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(true);
    }

    public void initListeners() {}

    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter(new Predicate<Long>() {
                    @Override
                    public boolean test(Long aLong) throws Exception {
                        return isProgressLoopRunning();
                    }
                })
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        triggerProgressUpdate();
                    }
                });
    }

    public void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        // Resolve play queue
        if (!intent.hasExtra(PLAY_QUEUE)) return;
        final Serializable playQueueCandidate = intent.getSerializableExtra(PLAY_QUEUE);
        if (!(playQueueCandidate instanceof PlayQueue)) return;
        final PlayQueue queue = (PlayQueue) playQueueCandidate;

        // Resolve append intents
        if (intent.getBooleanExtra(APPEND_ONLY, false) && playQueue != null) {
            playQueue.append(queue.getStreams());
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

    public void initThumbnail(final String url) {
        if (DEBUG) Log.d(TAG, "initThumbnail() called");
        if (url == null || url.isEmpty()) return;
        ImageLoader.getInstance().resume();
        ImageLoader.getInstance().loadImage(url, new SimpleImageLoadingListener() {
            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                if (simpleExoPlayer == null) return;
                if (DEBUG) Log.d(TAG, "onLoadingComplete() called with: imageUri = [" + imageUri + "], view = [" + view + "], loadedImage = [" + loadedImage + "]");
                onThumbnailReceived(loadedImage);
            }
        });
    }

    public void onThumbnailReceived(Bitmap thumbnail) {
        if (DEBUG) Log.d(TAG, "onThumbnailReceived() called with: thumbnail = [" + thumbnail + "]");
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
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();
        clearThumbnailCache();
        unregisterBroadcastReceiver();

        trackSelector = null;
        simpleExoPlayer = null;
    }

    public MediaSource buildMediaSource(String url, String overrideExtension) {
        if (DEBUG) {
            Log.d(TAG, "buildMediaSource() called with: url = [" + url + "], overrideExtension = [" + overrideExtension + "]");
        }
        Uri uri = Uri.parse(url);
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        MediaSource mediaSource;
        switch (type) {
            case C.TYPE_SS:
                mediaSource = new SsMediaSource(uri, cacheDataSourceFactory, new DefaultSsChunkSource.Factory(cacheDataSourceFactory), null, null);
                break;
            case C.TYPE_DASH:
                mediaSource = new DashMediaSource(uri, cacheDataSourceFactory, new DefaultDashChunkSource.Factory(cacheDataSourceFactory), null, null);
                break;
            case C.TYPE_HLS:
                mediaSource = new HlsMediaSource(uri, cacheDataSourceFactory, null, null);
                break;
            case C.TYPE_OTHER:
                mediaSource = new ExtractorMediaSource(uri, cacheDataSourceFactory, extractorsFactory, null, null);
                break;
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
        return mediaSource;
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
                if (isPlaying()) simpleExoPlayer.setPlayWhenReady(false);
                break;
        }
    }

    public void unregisterBroadcastReceiver() {
        if (broadcastReceiver != null && context != null) {
            context.unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
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
    }

    public void onBuffering() {
    }

    public void onPaused() {
        if (isProgressLoopRunning()) stopProgressLoop();
    }

    public void onPausedSeek() {
    }

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

        if (playQueue == null) return;

        setRecovery();
        if (playQueue.isShuffled()) {
            playQueue.unshuffle();
        } else {
            playQueue.shuffle();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    //////////////////////////////////////////////////////////////////////////*/

    private void recover() {
        final int currentSourceIndex = playQueue.getIndex();
        final PlayQueueItem currentSourceItem = playQueue.getItem();

        // Check if already playing correct window
        final boolean isCurrentWindowCorrect =
                simpleExoPlayer.getCurrentWindowIndex() == currentSourceIndex;

        // Check if recovering
        if (isCurrentWindowCorrect && currentSourceItem != null) {
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

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        if (DEBUG) Log.d(TAG, "onTimelineChanged(), timeline size = " + timeline.getWindowCount());

        if (playbackManager != null) {
            playbackManager.load();
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        if (DEBUG) Log.d(TAG, "onTracksChanged(), track group size = " + trackGroups.length);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        if (DEBUG) Log.d(TAG, "playbackParameters(), speed: " + playbackParameters.speed + ", pitch: " + playbackParameters.pitch);
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (DEBUG) Log.d(TAG, "onLoadingChanged() called with: isLoading = [" + isLoading + "]");

        if (!isLoading && getCurrentState() == STATE_PAUSED && isProgressLoopRunning()) stopProgressLoop();
        else if (isLoading && !isProgressLoopRunning()) startProgressLoop();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG)
            Log.d(TAG, "onPlayerStateChanged() called with: playWhenReady = [" + playWhenReady + "], playbackState = [" + playbackState + "]");
        if (getCurrentState() == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "onPlayerStateChanged() is currently blocked");
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
                recover();
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
                if (isCurrentWindowValid() && simpleExoPlayer.getCurrentPosition() >= simpleExoPlayer.getDuration()) {
                    changeState(STATE_COMPLETED);
                    isPrepared = false;
                }
                break;
        }
    }

    /**
     * Processes the exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
     * There are multiple types of errors: <br><br>
     *
     * {@link ExoPlaybackException#TYPE_SOURCE TYPE_SOURCE}: <br><br>
     * If the current {@link com.google.android.exoplayer2.Timeline.Window window} is valid,
     * then we know the error is produced by transitioning into a bad window, therefore we report
     * an error to the play queue based on if the current error can be skipped.
     *
     * This is done because ExoPlayer reports the source exceptions before window is
     * transitioned on seamless playback. Because player error causes ExoPlayer to go
     * back to {@link Player#STATE_IDLE STATE_IDLE}, we reset and prepare the media source
     * again to resume playback.
     *
     * In the event that this error is produced during a valid stream playback, we save the
     * current position so the playback may be recovered and resumed manually by the user. This
     * happens only if the playback is {@link #RECOVERY_SKIP_THRESHOLD} milliseconds until complete.
     * <br><br>
     *
     * {@link ExoPlaybackException#TYPE_UNEXPECTED TYPE_UNEXPECTED}: <br><br>
     * If a runtime error occurred, then we can try to recover it by restarting the playback
     * after setting the timestamp recovery. <br><br>
     *
     * {@link ExoPlaybackException#TYPE_RENDERER TYPE_RENDERER}: <br><br>
     * If the renderer failed, treat the error as unrecoverable.
     *
     * @see Player.EventListener#onPlayerError(ExoPlaybackException)
     *  */
    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "onPlayerError() called with: error = [" + error + "]");
        if (errorToast != null) {
            errorToast.cancel();
            errorToast = null;
        }

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                if (simpleExoPlayer.getCurrentPosition() <
                        simpleExoPlayer.getDuration() - RECOVERY_SKIP_THRESHOLD) {
                    setRecovery();
                }
                playQueue.error(isCurrentWindowValid());
                showStreamError(error);
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                showRecoverableError(error);
                setRecovery();
                reload();
                break;
            default:
                showUnrecoverableError(error);
                shutdown();
                break;
        }
    }

    @Override
    public void onPositionDiscontinuity() {
        // Refresh the playback if there is a transition to the next video
        final int newWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        if (DEBUG) Log.d(TAG, "onPositionDiscontinuity() called with window index = [" + newWindowIndex + "]");

        // If the user selects a new track, then the discontinuity occurs after the index is changed.
        // Therefore, the only source that causes a discrepancy would be gapless transition,
        // which can only offset the current track by +1.
        if (newWindowIndex == playQueue.getIndex() + 1) {
            playQueue.offsetIndex(+1);
        }
        playbackManager.load();
    }

    @Override
    public void onRepeatModeChanged(int i) {
        if (DEBUG) Log.d(TAG, "onRepeatModeChanged() called with: mode = [" + i + "]");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void block() {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Blocking...");

        currentItem = null;
        currentInfo = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override
    public void unblock(final MediaSource mediaSource) {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Unblocking...");

        if (getCurrentState() == STATE_BLOCKED) changeState(STATE_BUFFERING);

        simpleExoPlayer.prepare(mediaSource);
        simpleExoPlayer.seekToDefaultPosition();
    }

    @Override
    public void sync(@NonNull final PlayQueueItem item,
                     @Nullable final StreamInfo info) {
        if (currentItem == item && currentInfo == info) return;
        currentItem = item;
        currentInfo = info;

        if (DEBUG) Log.d(TAG, "Syncing...");
        if (simpleExoPlayer == null) return;

        // Check if on wrong window
        final int currentSourceIndex = playQueue.indexOf(item);
        if (currentSourceIndex != playQueue.getIndex()) {
            Log.e(TAG, "Play Queue may be desynchronized: item index=[" + currentSourceIndex +
                    "], queue index=[" + playQueue.getIndex() + "]");
        } else if (simpleExoPlayer.getCurrentWindowIndex() != currentSourceIndex || !isPlaying()) {
            final long startPos = info != null ? info.start_position : 0;
            if (DEBUG) Log.d(TAG, "Rewinding to correct window: " + currentSourceIndex + " at: " + getTimeString((int)startPos));
            simpleExoPlayer.seekTo(currentSourceIndex, startPos);
        }

        initThumbnail(info == null ? item.getThumbnailUrl() : info.thumbnail_url);
    }

    @Override
    public void shutdown() {
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

    public abstract void onUpdateProgress(int currentProgress, int duration, int bufferPercent);

    public void onVideoPlayPause() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");

        if (!isPlaying()) {
            audioReactor.requestAudioFocus();
        } else {
            audioReactor.abandonAudioFocus();
        }

        if (getCurrentState() == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                simpleExoPlayer.seekToDefaultPosition();
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

        playQueue.offsetIndex(+1);
    }

    public void onSelected(final PlayQueueItem item) {
        final int index = playQueue.indexOf(item);
        if (index == -1) return;

        if (playQueue.getIndex() == index) {
            simpleExoPlayer.seekToDefaultPosition();
        } else {
            playQueue.setIndex(index);
        }
    }

    public void seekBy(int milliSeconds) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: milliSeconds = [" + milliSeconds + "]");
        if (simpleExoPlayer == null || (isCompleted() && milliSeconds > 0) || ((milliSeconds < 0 && simpleExoPlayer.getCurrentPosition() == 0)))
            return;
        int progress = (int) (simpleExoPlayer.getCurrentPosition() + milliSeconds);
        if (progress < 0) progress = 0;
        simpleExoPlayer.seekTo(progress);
    }

    public boolean isCurrentWindowValid() {
        return simpleExoPlayer != null && simpleExoPlayer.getDuration() >= 0
                && simpleExoPlayer.getCurrentPosition() >= 0;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void reload() {
        if (playbackManager != null) {
            playbackManager.reset();
            playbackManager.load();
        }
    }

    protected void clearThumbnailCache() {
        ImageLoader.getInstance().clearMemoryCache();
    }

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
        return simpleExoPlayer.getPlaybackState() == Player.STATE_READY && simpleExoPlayer.getPlayWhenReady();
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
