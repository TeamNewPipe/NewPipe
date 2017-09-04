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
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
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
import org.schabi.newpipe.playlist.PlayQueue;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base for the players, joining the common properties
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class BasePlayer implements Player.EventListener,
        AudioManager.OnAudioFocusChangeListener, MediaSourceManager.PlaybackListener {
    // TODO: Check api version for deprecated audio manager methods

    public static final boolean DEBUG = false;
    public static final String TAG = "BasePlayer";

    protected Context context;
    protected SharedPreferences sharedPreferences;
    protected AudioManager audioManager;

    protected BroadcastReceiver broadcastReceiver;
    protected IntentFilter intentFilter;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String VIDEO_URL = "video_url";
    public static final String VIDEO_TITLE = "video_title";
    public static final String VIDEO_THUMBNAIL_URL = "video_thumbnail_url";
    public static final String START_POSITION = "start_position";
    public static final String CHANNEL_NAME = "channel_name";
    public static final String PLAYBACK_SPEED = "playback_speed";

    protected Bitmap videoThumbnail = null;
    protected String videoUrl = "";
    protected String videoTitle = "";
    protected String videoThumbnailUrl = "";
    protected long videoStartPos = -1;
    protected String uploaderName = "";

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist
    //////////////////////////////////////////////////////////////////////////*/

    protected MediaSourceManager playbackManager;
    protected PlayQueue playQueue;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    public int FAST_FORWARD_REWIND_AMOUNT = 10000; // 10 Seconds
    public static final String CACHE_FOLDER_NAME = "exoplayer";

    protected SimpleExoPlayer simpleExoPlayer;
    protected boolean isPrepared = false;

    protected MediaSource mediaSource;
    protected CacheDataSourceFactory cacheDataSourceFactory;
    protected final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
    protected final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    protected int PROGRESS_LOOP_INTERVAL = 100;
    protected AtomicBoolean isProgressLoopRunning = new AtomicBoolean();
    protected Handler progressLoop;
    protected Runnable progressUpdate;

    //////////////////////////////////////////////////////////////////////////*/

    public BasePlayer(Context context) {
        this.context = context;
        this.progressLoop = new Handler();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));

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
        DefaultTrackSelector defaultTrackSelector = new DefaultTrackSelector(trackSelectionFactory);
        DefaultLoadControl loadControl = new DefaultLoadControl();

        final RenderersFactory renderFactory = new DefaultRenderersFactory(context);
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(renderFactory, defaultTrackSelector, loadControl);
        simpleExoPlayer.addListener(this);
    }

    public void initListeners() {
        progressUpdate = new Runnable() {
            @Override
            public void run() {
                //if(DEBUG) Log.d(TAG, "progressUpdate run() called");
                onUpdateProgress((int) simpleExoPlayer.getCurrentPosition(), (int) simpleExoPlayer.getDuration(), simpleExoPlayer.getBufferedPercentage());
                if (isProgressLoopRunning.get()) progressLoop.postDelayed(this, PROGRESS_LOOP_INTERVAL);
            }
        };
    }

    public void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        videoUrl = intent.getStringExtra(VIDEO_URL);
        videoTitle = intent.getStringExtra(VIDEO_TITLE);
        videoThumbnailUrl = intent.getStringExtra(VIDEO_THUMBNAIL_URL);
        videoStartPos = intent.getLongExtra(START_POSITION, -1L);
        uploaderName = intent.getStringExtra(CHANNEL_NAME);
        setPlaybackSpeed(intent.getFloatExtra(PLAYBACK_SPEED, getPlaybackSpeed()));

        initThumbnail();
        //play(getSelectedVideoStream(), true);
    }

    public void initThumbnail() {
        if (DEBUG) Log.d(TAG, "initThumbnail() called");
        videoThumbnail = null;
        if (videoThumbnailUrl == null || videoThumbnailUrl.isEmpty()) return;
        ImageLoader.getInstance().resume();
        ImageLoader.getInstance().loadImage(videoThumbnailUrl, new SimpleImageLoadingListener() {
            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                if (simpleExoPlayer == null) return;
                if (DEBUG)
                    Log.d(TAG, "onLoadingComplete() called with: imageUri = [" + imageUri + "], view = [" + view + "], loadedImage = [" + loadedImage + "]");
                videoThumbnail = loadedImage;
                onThumbnailReceived(loadedImage);
            }
        });
    }

    public void playUrl(String url, String format, boolean autoPlay) {
        if (DEBUG) {
            Log.d(TAG, "play() called with: url = [" + url + "], autoPlay = [" + autoPlay + "]");
        }

        if (url == null || simpleExoPlayer == null) {
            RuntimeException runtimeException = new RuntimeException((url == null ? "Url " : "Player ") + " null");
            onError(runtimeException);
            throw runtimeException;
        }

        changeState(STATE_LOADING);

        isPrepared = false;

        if (simpleExoPlayer.getPlaybackState() != Player.STATE_IDLE) simpleExoPlayer.stop();
        if (videoStartPos > 0) simpleExoPlayer.seekTo(videoStartPos);
        simpleExoPlayer.prepare(mediaSource);
        simpleExoPlayer.setPlayWhenReady(autoPlay);
    }

    public void destroyPlayer() {
        if (DEBUG) Log.d(TAG, "destroyPlayer() called");
        if (simpleExoPlayer != null) {
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (progressLoop != null && isProgressLoopRunning.get()) stopProgressLoop();
        if (audioManager != null) {
            audioManager.abandonAudioFocus(this);
            audioManager = null;
        }
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();
        unregisterBroadcastReceiver();
        videoThumbnail = null;
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

        if (isResumeAfterAudioFocusGain()) simpleExoPlayer.setPlayWhenReady(true);
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
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_LOADING = 123;
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
            case STATE_LOADING:
                onLoading();
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

    public void onLoading() {
        if (DEBUG) Log.d(TAG, "onLoading() called");
        if (!isProgressLoopRunning.get()) startProgressLoop();
    }

    public void onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called");
        if (!isProgressLoopRunning.get()) startProgressLoop();
    }

    public void onBuffering() {
    }

    public void onPaused() {
        if (isProgressLoopRunning.get()) stopProgressLoop();
    }

    public void onPausedSeek() {
    }

    public void onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called");
        if (isProgressLoopRunning.get()) stopProgressLoop();

        if (currentRepeatMode == RepeatMode.REPEAT_ONE) {
            changeState(STATE_LOADING);
            simpleExoPlayer.seekTo(0);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Repeat
    //////////////////////////////////////////////////////////////////////////*/

    protected RepeatMode currentRepeatMode = RepeatMode.REPEAT_DISABLED;

    public enum RepeatMode {
        REPEAT_DISABLED,
        REPEAT_ONE,
        REPEAT_ALL
    }

    public void onRepeatClicked() {
        if (DEBUG) Log.d(TAG, "onRepeatClicked() called");
        // TODO: implement repeat all when playlist is implemented

        // Switch the modes between DISABLED and REPEAT_ONE, till playlist is implemented
        setCurrentRepeatMode(getCurrentRepeatMode() == RepeatMode.REPEAT_DISABLED ?
                RepeatMode.REPEAT_ONE :
                RepeatMode.REPEAT_DISABLED);

        if (DEBUG) Log.d(TAG, "onRepeatClicked() currentRepeatMode = " + getCurrentRepeatMode().name());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (DEBUG) Log.d(TAG, "onLoadingChanged() called with: isLoading = [" + isLoading + "]");

        if (!isLoading && getCurrentState() == STATE_PAUSED && isProgressLoopRunning.get()) stopProgressLoop();
        else if (isLoading && !isProgressLoopRunning.get()) startProgressLoop();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG)
            Log.d(TAG, "onPlayerStateChanged() called with: playWhenReady = [" + playWhenReady + "], playbackState = [" + playbackState + "]");
        if (getCurrentState() == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "onPlayerStateChanged() currently on PausedSeek");
            return;
        }

        switch (playbackState) {
            case Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case Player.STATE_BUFFERING: // 2
                if (isPrepared && getCurrentState() != STATE_LOADING) changeState(STATE_BUFFERING);
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
                changeState(STATE_COMPLETED);
                isPrepared = false;
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "onPlayerError() called with: error = [" + error + "]");
        playbackManager.report(error);

        onError(error);
    }

    @Override
    public void onPositionDiscontinuity() {
        int newIndex = simpleExoPlayer.getCurrentWindowIndex();
        if (playbackManager.getCurrentSourceIndex() != newIndex) playbackManager.refresh(newIndex);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    private int windowIndex;
    private long windowPos;

    @Override
    public void block() {
        Log.d(TAG, "Blocking...");

        if (currentState != STATE_PLAYING) return;

        simpleExoPlayer.stop();
        windowIndex = simpleExoPlayer.getCurrentWindowIndex();
        windowPos = Math.max(0, simpleExoPlayer.getContentPosition());

        changeState(STATE_BUFFERING);
    }

    @Override
    public void unblock() {
        Log.d(TAG, "Unblocking...");

        if (currentState != STATE_BUFFERING) return;

        if (windowIndex != playbackManager.getCurrentSourceIndex()) {
            windowIndex = playbackManager.getCurrentSourceIndex();
            windowPos = 0;
        }

        simpleExoPlayer.prepare(playbackManager.getMediaSource());
        simpleExoPlayer.seekTo(windowIndex, windowPos);
        simpleExoPlayer.setPlayWhenReady(true);
        changeState(STATE_PLAYING);
    }

    @Override
    public void sync(final int windowIndex, final long windowPos, final StreamInfo info) {
        Log.d(TAG, "Syncing...");

        videoUrl = info.url;
        videoThumbnailUrl = info.thumbnail_url;
        videoTitle = info.name;

        if (simpleExoPlayer.getCurrentWindowIndex() != windowIndex) {
            Log.w(TAG, "Rewinding to correct window");
            simpleExoPlayer.seekTo(windowIndex, windowPos);
        } else {
            simpleExoPlayer.seekTo(windowPos);
        }
    }

    @Override
    public void init() {
        Log.d(TAG, "Initializing...");

        if (simpleExoPlayer.getPlaybackState() != Player.STATE_IDLE) simpleExoPlayer.stop();
        simpleExoPlayer.prepare(playbackManager.getMediaSource());
        simpleExoPlayer.seekToDefaultPosition();
        simpleExoPlayer.setPlayWhenReady(true);
    }

    @Override
    public MediaSource sourceOf(final StreamInfo info) {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    public void onError(Exception exception){
        destroy();
    }

    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        if (playWhenReady) audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
    }

    public abstract void onUpdateProgress(int currentProgress, int duration, int bufferPercent);

    public void onVideoPlayPause() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");

        if (currentState == STATE_COMPLETED) {
            onVideoPlayPauseRepeat();
            return;
        }

        if (!isPlaying()) audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        else audioManager.abandonAudioFocus(this);

        simpleExoPlayer.setPlayWhenReady(!isPlaying());
    }

    public void onVideoPlayPauseRepeat() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPauseRepeat() called");
        changeState(STATE_LOADING);
        setVideoStartPos(0);
        simpleExoPlayer.seekTo(0);
        simpleExoPlayer.setPlayWhenReady(true);
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    public void onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called");
        seekBy(-FAST_FORWARD_REWIND_AMOUNT);
    }

    public void onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called");
        seekBy(FAST_FORWARD_REWIND_AMOUNT);
    }

    public void onThumbnailReceived(Bitmap thumbnail) {
        if (DEBUG) Log.d(TAG, "onThumbnailReceived() called with: thumbnail = [" + thumbnail + "]");
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
        progressLoop.removeCallbacksAndMessages(null);
        isProgressLoopRunning.set(true);
        progressLoop.post(progressUpdate);
    }

    protected void stopProgressLoop() {
        isProgressLoopRunning.set(false);
        progressLoop.removeCallbacksAndMessages(null);
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
        onUpdateProgress((int) simpleExoPlayer.getCurrentPosition(), (int) simpleExoPlayer.getDuration(), simpleExoPlayer.getBufferedPercentage());
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

    public RepeatMode getCurrentRepeatMode() {
        return currentRepeatMode;
    }

    public void setCurrentRepeatMode(RepeatMode mode) {
        currentRepeatMode = mode;
    }

    public int getCurrentState() {
        return currentState;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public long getVideoStartPos() {
        return videoStartPos;
    }

    public void setVideoStartPos(long videoStartPos) {
        this.videoStartPos = videoStartPos;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
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

    public Bitmap getVideoThumbnail() {
        return videoThumbnail;
    }

    public void setVideoThumbnail(Bitmap videoThumbnail) {
        this.videoThumbnail = videoThumbnail;
    }

    public String getVideoThumbnailUrl() {
        return videoThumbnailUrl;
    }

    public void setVideoThumbnailUrl(String videoThumbnailUrl) {
        this.videoThumbnailUrl = videoThumbnailUrl;
    }

    public float getPlaybackSpeed() {
        return simpleExoPlayer.getPlaybackParameters().speed;
    }

    public void setPlaybackSpeed(float speed) {
        simpleExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1f));
    }
}
