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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
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
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueAdapter;

import java.io.File;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;

/**
 * Base for the players, joining the common properties
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class BasePlayer implements Player.EventListener,
        AudioManager.OnAudioFocusChangeListener, PlaybackListener, AudioRendererEventListener {
    // TODO: Check api version for deprecated audio manager methods

    public static final boolean DEBUG = true;
    public static final String TAG = "BasePlayer";

    protected Context context;
    protected SharedPreferences sharedPreferences;
    protected AudioManager audioManager;

    protected BroadcastReceiver broadcastReceiver;
    protected IntentFilter intentFilter;

    protected PlayQueueAdapter playQueueAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String INTENT_TYPE = "intent_type";
    public static final String SINGLE_STREAM = "single";
    public static final String EXTERNAL_PLAYLIST = "external";
    public static final String INTERNAL_PLAYLIST = "internal";

    public static final String VIDEO_URL = "video_url";
    public static final String VIDEO_TITLE = "video_title";
    public static final String VIDEO_THUMBNAIL_URL = "video_thumbnail_url";
    public static final String START_POSITION = "start_position";
    public static final String CHANNEL_NAME = "channel_name";
    public static final String PLAYBACK_SPEED = "playback_speed";

    public static final String PLAY_QUEUE = "play_queue";
    public static final String RESTORE_QUEUE_INDEX = "restore_queue_index";
    public static final String RESTORE_WINDOW_POS = "restore_window_pos";

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    protected MediaSourceManager playbackManager;
    protected PlayQueue playQueue;

    private boolean isRecovery = false;
    private int queuePos = 0;
    private long videoPos = -1;

    protected StreamInfo currentInfo;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    public int FAST_FORWARD_REWIND_AMOUNT = 10000; // 10 Seconds
    public int PLAY_PREV_ACTIVATION_LIMIT = 5000; // 5 seconds
    public static final String CACHE_FOLDER_NAME = "exoplayer";

    protected SimpleExoPlayer simpleExoPlayer;
    protected boolean isPrepared = false;

    protected DefaultTrackSelector trackSelector;
    protected CacheDataSourceFactory cacheDataSourceFactory;
    protected final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
    protected final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    protected int PROGRESS_LOOP_INTERVAL = 500;
    protected Disposable progressUpdateReactor;

    //////////////////////////////////////////////////////////////////////////*/

    public BasePlayer(Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

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

    private void initExoPlayerCache() {
        if (cacheDataSourceFactory == null) {
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, Downloader.USER_AGENT, bandwidthMeter);
            File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdir();
            }

            if (DEBUG) Log.d(TAG, "initExoPlayerCache: cacheDir = " + cacheDir.getAbsolutePath());
            SimpleCache simpleCache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(64 * 1024 * 1024L));
            cacheDataSourceFactory = new CacheDataSourceFactory(simpleCache, dataSourceFactory, CacheDataSource.FLAG_BLOCK_ON_CACHE, 512 * 1024);
        }
    }

    public void initPlayer() {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");
        initExoPlayerCache();

        if (audioManager == null) {
            this.audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        }

        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector = new DefaultTrackSelector(trackSelectionFactory);

        DefaultLoadControl loadControl = new DefaultLoadControl();

        final RenderersFactory renderFactory = new DefaultRenderersFactory(context);
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(renderFactory, trackSelector, loadControl);
        simpleExoPlayer.setAudioDebugListener(this);
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(true);
    }

    public void initListeners() {}

    protected Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter(new Predicate<Long>() {
                    @Override
                    public boolean test(@NonNull Long aLong) throws Exception {
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

        // Resolve playback details
        if (intent.hasExtra(RESTORE_QUEUE_INDEX) && intent.hasExtra(START_POSITION)) {
            setRecovery(
                    intent.getIntExtra(RESTORE_QUEUE_INDEX, 0),
                    intent.getLongExtra(START_POSITION, 0)
            );
        }
        setPlaybackSpeed(intent.getFloatExtra(PLAYBACK_SPEED, getPlaybackSpeed()));

        // Re-initialization
        destroyPlayer();
        if (playQueue != null) playQueue.dispose();
        if (playbackManager != null) playbackManager.dispose();
        initPlayer();

        // Good to go...
        initPlayback(this, queue);
    }

    protected void initPlayback(@NonNull final PlaybackListener listener, @NonNull final PlayQueue queue) {
        playQueue = queue;
        playQueue.init();
        playbackManager = new MediaSourceManager(this, playQueue);

        if (playQueueAdapter != null) playQueueAdapter.dispose();
        playQueueAdapter = new PlayQueueAdapter(playQueue);
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
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) stopProgressLoop();
        if (audioManager != null) {
            audioManager.abandonAudioFocus(this);
            audioManager = null;
        }
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();

        if (playQueue != null) {
            playQueue.dispose();
            playQueue = null;
        }
        if (playbackManager != null) {
            playbackManager.dispose();
            playbackManager = null;
        }

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
    // AudioFocus
    //////////////////////////////////////////////////////////////////////////*/

    private static final int DUCK_DURATION = 1500;
    private static final float DUCK_AUDIO_TO = .2f;

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (DEBUG) Log.d(TAG, "onAudioFocusChange() called with: focusChange = [" + focusChange + "]");
        if (simpleExoPlayer == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                onAudioFocusGain();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                onAudioFocusLossCanDuck();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                onAudioFocusLoss();
                break;
        }
    }

    private boolean isResumeAfterAudioFocusGain() {
        return sharedPreferences != null && context != null
                && sharedPreferences.getBoolean(context.getString(R.string.resume_on_audio_focus_gain_key), false);
    }

    protected void onAudioFocusGain() {
        if (DEBUG) Log.d(TAG, "onAudioFocusGain() called");
        if (simpleExoPlayer != null) simpleExoPlayer.setVolume(DUCK_AUDIO_TO);
        animateAudio(DUCK_AUDIO_TO, 1f, DUCK_DURATION);

        if (isResumeAfterAudioFocusGain()) {
            simpleExoPlayer.setPlayWhenReady(true);
        }
    }

    protected void onAudioFocusLoss() {
        if (DEBUG) Log.d(TAG, "onAudioFocusLoss() called");
        simpleExoPlayer.setPlayWhenReady(false);
    }

    protected void onAudioFocusLossCanDuck() {
        if (DEBUG) Log.d(TAG, "onAudioFocusLossCanDuck() called");
        // Set the volume to 1/10 on ducking
        animateAudio(simpleExoPlayer.getVolume(), DUCK_AUDIO_TO, DUCK_DURATION);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioEnabled(DecoderCounters decoderCounters) {}

    @Override
    public void onAudioSessionId(int i) {
        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, i);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onAudioDecoderInitialized(String s, long l, long l1) {}

    @Override
    public void onAudioInputFormatChanged(Format format) {}

    @Override
    public void onAudioTrackUnderrun(int i, long l, long l1) {}

    @Override
    public void onAudioDisabled(DecoderCounters decoderCounters) {}

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
    // Repeat
    //////////////////////////////////////////////////////////////////////////*/

    public void onRepeatClicked() {
        if (DEBUG) Log.d(TAG, "onRepeatClicked() called");

        final int mode;

        switch (simpleExoPlayer.getRepeatMode()) {
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

        simpleExoPlayer.setRepeatMode(mode);
        if (DEBUG) Log.d(TAG, "onRepeatClicked() currentRepeatMode = " + simpleExoPlayer.getRepeatMode());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Timeline
    //////////////////////////////////////////////////////////////////////////*/

    private void refreshTimeline() {
        playbackManager.load();

        final int currentSourceIndex = playbackManager.getCurrentSourceIndex();

        // Sanity checks
        if (currentSourceIndex < 0) return;

        // Check if already playing correct window
        final boolean isCurrentWindowCorrect = simpleExoPlayer.getCurrentWindowIndex() == currentSourceIndex;

        // Check if on wrong window
        if (!isCurrentWindowCorrect) {
            final long startPos = currentInfo != null ? currentInfo.start_position : 0;
            if (DEBUG) Log.d(TAG, "Rewinding to correct window: " + currentSourceIndex + " at: " + getTimeString((int)startPos));
            simpleExoPlayer.seekTo(currentSourceIndex, startPos);
        }

        // Check if recovering
        if (isCurrentWindowCorrect && isRecovery && queuePos == playQueue.getIndex()) {
            // todo: figure out exactly why this is the case
            /* Rounding time to nearest second as certain media cannot guarantee a sub-second seek
             will complete and the player might get stuck in buffering state forever */
            final long roundedPos = (videoPos / 1000) * 1000;

            if (DEBUG) Log.d(TAG, "Rewinding to recovery window: " + currentSourceIndex + " at: " + getTimeString((int)roundedPos));
            simpleExoPlayer.seekTo(roundedPos);
            isRecovery = false;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        if (DEBUG) Log.d(TAG, "onTimelineChanged(), timeline size = " + timeline.getWindowCount());

        refreshTimeline();
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
                if (simpleExoPlayer.isCurrentWindowSeekable() &&
                        simpleExoPlayer.getCurrentPosition() >= simpleExoPlayer.getDuration()) {
                    changeState(STATE_COMPLETED);
                    isPrepared = false;
                }
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "onPlayerError() called with: error = [" + error + "]");
        playQueue.remove(playQueue.getIndex());
        onError(error);
    }

    @Override
    public void onPositionDiscontinuity() {
        // Refresh the playback if there is a transition to the next video
        final int newWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        final int newQueueIndex = playbackManager.getQueueIndexOf(newWindowIndex);
        if (DEBUG) Log.d(TAG, "onPositionDiscontinuity() called with: " +
                "window index = [" + newWindowIndex + "], queue index = [" + newQueueIndex + "]");

        // If the user selects a new track, then the discontinuity occurs after the index is changed.
        // Therefore, the only source that causes a discrepancy would be autoplay,
        // which can only offset the current track by +1.
        if (newQueueIndex != playQueue.getIndex()) playQueue.offsetIndex(+1);
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
    }

    @Override
    public void sync(@Nullable final StreamInfo info) {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Syncing...");

        refreshTimeline();

        if (info == null) return;

        currentInfo = info;
        initThumbnail(info.thumbnail_url);
    }

    @Override
    public void shutdown() {
        if (DEBUG) Log.d(TAG, "Shutting down...");

        playbackManager.dispose();
        playQueue.dispose();
        destroy();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    public abstract void onError(Exception exception);

    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        if (playWhenReady) audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
    }

    public abstract void onUpdateProgress(int currentProgress, int duration, int bufferPercent);

    public void onVideoPlayPause() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");

        if (!isPlaying()) audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        else audioManager.abandonAudioFocus(this);

        if (getCurrentState() == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) simpleExoPlayer.seekToDefaultPosition();
            else playQueue.setIndex(0);
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
        if (simpleExoPlayer == null || playQueue == null || currentInfo == null) return;
        if (DEBUG) Log.d(TAG, "onPlayPrevious() called");

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT milliseconds, restart current track.
        * Also restart the track if the current track is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT || playQueue.getIndex() == 0) {
            simpleExoPlayer.seekTo(currentInfo.start_position);
        } else {
            playQueue.offsetIndex(-1);
        }
    }

    public void onPlayNext() {
        if (playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayNext() called");

        playQueue.offsetIndex(+1);
    }

    public void seekBy(int milliSeconds) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: milliSeconds = [" + milliSeconds + "]");
        if (simpleExoPlayer == null || (isCompleted() && milliSeconds > 0) || ((milliSeconds < 0 && simpleExoPlayer.getCurrentPosition() == 0)))
            return;
        int progress = (int) (simpleExoPlayer.getCurrentPosition() + milliSeconds);
        if (progress < 0) progress = 0;
        simpleExoPlayer.seekTo(progress);
    }

    public boolean isPlaying() {
        return simpleExoPlayer.getPlaybackState() == Player.STATE_READY && simpleExoPlayer.getPlayWhenReady();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private final StringBuilder stringBuilder = new StringBuilder();
    private final Formatter formatter = new Formatter(stringBuilder, Locale.getDefault());
    private final NumberFormat speedFormatter = new DecimalFormat("0.##x");

    // todo: merge this into Localization
    public String getTimeString(int milliSeconds) {
        long seconds = (milliSeconds % 60000L) / 1000L;
        long minutes = (milliSeconds % 3600000L) / 60000L;
        long hours = (milliSeconds % 86400000L) / 3600000L;
        long days = (milliSeconds % (86400000L * 7L)) / 86400000L;

        stringBuilder.setLength(0);
        return days > 0 ? formatter.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds).toString()
                : hours > 0 ? formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
                : formatter.format("%02d:%02d", minutes, seconds).toString();
    }

    protected String formatSpeed(float speed) {
        return speedFormatter.format(speed);
    }

    protected void startProgressLoop() {
        if (progressUpdateReactor != null) progressUpdateReactor.dispose();
        progressUpdateReactor = getProgressReactor();
    }

    protected void stopProgressLoop() {
        if (progressUpdateReactor != null) progressUpdateReactor.dispose();
        progressUpdateReactor = null;
    }

    protected void tryDeleteCacheFiles(Context context) {
        File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);

        if (cacheDir.exists()) {
            try {
                if (cacheDir.isDirectory()) {
                    for (File file : cacheDir.listFiles()) {
                        try {
                            if (DEBUG) Log.d(TAG, "tryDeleteCacheFiles: " + file.getAbsolutePath() + " deleted = " + file.delete());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void triggerProgressUpdate() {
        onUpdateProgress(
                (int) simpleExoPlayer.getCurrentPosition(),
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage()
        );
    }

    public void animateAudio(final float from, final float to, int duration) {
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setFloatValues(from, to);
        valueAnimator.setDuration(duration);
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (simpleExoPlayer != null) simpleExoPlayer.setVolume(from);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (simpleExoPlayer != null) simpleExoPlayer.setVolume(to);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (simpleExoPlayer != null) simpleExoPlayer.setVolume(to);
            }
        });
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (simpleExoPlayer != null) simpleExoPlayer.setVolume(((float) animation.getAnimatedValue()));
            }
        });
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    public SimpleExoPlayer getPlayer() {
        return simpleExoPlayer;
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public int getCurrentState() {
        return currentState;
    }

    public long getVideoPos() {
        return videoPos;
    }

    public String getVideoUrl() {
        return currentInfo == null ? null : currentInfo.url;
    }

    public String getVideoTitle() {
        return currentInfo == null ? null : currentInfo.name;
    }

    public String getUploaderName() {
        return currentInfo == null ? null : currentInfo.uploader_name;
    }

    public boolean isCompleted() {
        return simpleExoPlayer != null && simpleExoPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isPrepared() {
        return isPrepared;
    }

    public void setPrepared(boolean prepared) {
        isPrepared = prepared;
    }

    public float getPlaybackSpeed() {
        return simpleExoPlayer.getPlaybackParameters().speed;
    }

    public void setPlaybackSpeed(float speed) {
        simpleExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1f));
    }

    public int getCurrentQueueIndex() {
        return playQueue != null ? playQueue.getIndex() : -1;
    }

    public int getCurrentResolutionTarget() {
        return trackSelector != null ? trackSelector.getParameters().maxVideoHeight : Integer.MAX_VALUE;
    }

    public long getPlayerCurrentPosition() {
        return simpleExoPlayer != null ? simpleExoPlayer.getCurrentPosition() : 0L;
    }

    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public boolean isPlayerReady() {
        return currentState == STATE_PLAYING || currentState == STATE_COMPLETED || currentState == STATE_PAUSED;
    }

    public boolean isProgressLoopRunning() {
        return progressUpdateReactor != null && !progressUpdateReactor.isDisposed();
    }

    public boolean getRecovery() {
        return isRecovery;
    }

    public void setRecovery(final int queuePos, final long windowPos) {
        if (DEBUG) Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        this.isRecovery = true;
        this.queuePos = queuePos;
        this.videoPos = windowPos;
    }
}
