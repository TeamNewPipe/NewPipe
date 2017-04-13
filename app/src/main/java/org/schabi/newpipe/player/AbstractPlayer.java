package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
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
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common properties of the players
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class AbstractPlayer implements StateInterface, SeekBar.OnSeekBarChangeListener, View.OnClickListener, ExoPlayer.EventListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener, SimpleExoPlayer.VideoListener {
    public static final boolean DEBUG = false;
    public final String TAG;

    protected Context context;
    private SharedPreferences sharedPreferences;

    private static int currentState = -1;
    public static final String ACTION_UPDATE_THUMB = "org.schabi.newpipe.player.AbstractPlayer.UPDATE_THUMBNAIL";

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String VIDEO_URL = "video_url";
    public static final String VIDEO_STREAMS_LIST = "video_streams_list";
    public static final String VIDEO_TITLE = "video_title";
    public static final String INDEX_SEL_VIDEO_STREAM = "index_selected_video_stream";
    public static final String START_POSITION = "start_position";
    public static final String CHANNEL_NAME = "channel_name";
    public static final String STARTED_FROM_NEWPIPE = "started_from_newpipe";

    private String videoUrl = "";
    private int videoStartPos = -1;
    private String videoTitle = "";
    private Bitmap videoThumbnail;
    private String channelName = "";
    private int selectedIndexStream;
    private ArrayList<VideoStream> videoStreamsList;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    public static final int FAST_FORWARD_REWIND_AMOUNT = 10000; // 10 Seconds
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 3000;  // 3 Seconds
    public static final String CACHE_FOLDER_NAME = "exoplayer";

    private boolean startedFromNewPipe = true;
    private boolean isPrepared = false;
    private boolean wasPlaying = false;
    private SimpleExoPlayer simpleExoPlayer;

    @SuppressWarnings("FieldCanBeLocal")
    private MediaSource videoSource;
    private static CacheDataSourceFactory cacheDataSourceFactory;
    private static final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
    private static final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    private AtomicBoolean isProgressLoopRunning = new AtomicBoolean();
    private Handler progressLoop;
    private Runnable progressUpdate;

    /*//////////////////////////////////////////////////////////////////////////
    // Repeat
    //////////////////////////////////////////////////////////////////////////*/

    private RepeatMode currentRepeatMode = RepeatMode.REPEAT_DISABLED;

    public enum RepeatMode {
        REPEAT_DISABLED,
        REPEAT_ONE,
        REPEAT_ALL
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View rootView;

    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private SurfaceView surfaceView;
    private View surfaceForeground;

    private View loadingPanel;
    private ImageView endScreen;
    private ImageView controlAnimationView;

    private View controlsRoot;
    private TextView currentDisplaySeek;

    private View bottomControlsRoot;
    private SeekBar playbackSeekBar;
    private TextView playbackCurrentTime;
    private TextView playbackEndTime;

    private View topControlsRoot;
    private TextView qualityTextView;
    private ImageButton fullScreenButton;

    private ValueAnimator controlViewAnimator;

    private boolean isQualityPopupMenuVisible = false;
    private boolean qualityChanged = false;
    private int qualityPopupMenuGroupId = 69;
    private PopupMenu qualityPopupMenu;

    ///////////////////////////////////////////////////////////////////////////

    public AbstractPlayer(String debugTag, Context context) {
        this.TAG = debugTag;
        this.context = context;
        this.progressLoop = new Handler();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (cacheDataSourceFactory == null) {
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getPackageName()), bandwidthMeter);
            File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
            if (!cacheDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdir();
            }

            Log.d(TAG, "buildMediaSource: cacheDir = " + cacheDir.getAbsolutePath());
            SimpleCache simpleCache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(64 * 1024 * 1024L));
            cacheDataSourceFactory = new CacheDataSourceFactory(simpleCache, dataSourceFactory, CacheDataSource.FLAG_BLOCK_ON_CACHE, 512 * 1024);
        }
    }

    public void setup(View rootView) {
        initViews(rootView);
        initListeners();
        if (simpleExoPlayer == null) initPlayer();
        else {
            simpleExoPlayer.addListener(this);
            simpleExoPlayer.setVideoListener(this);
            simpleExoPlayer.setVideoSurfaceView(surfaceView);
        }
    }

    public void initViews(View rootView) {
        this.rootView = rootView;
        this.aspectRatioFrameLayout = (AspectRatioFrameLayout) rootView.findViewById(R.id.aspectRatioLayout);
        this.surfaceView = (SurfaceView) rootView.findViewById(R.id.surfaceView);
        this.surfaceForeground = rootView.findViewById(R.id.surfaceForeground);
        this.loadingPanel = rootView.findViewById(R.id.loadingPanel);
        this.endScreen = (ImageView) rootView.findViewById(R.id.endScreen);
        this.controlAnimationView = (ImageView) rootView.findViewById(R.id.controlAnimationView);
        this.controlsRoot = rootView.findViewById(R.id.playbackControlRoot);
        this.currentDisplaySeek = (TextView) rootView.findViewById(R.id.currentDisplaySeek);
        this.playbackSeekBar = (SeekBar) rootView.findViewById(R.id.playbackSeekBar);
        this.playbackCurrentTime = (TextView) rootView.findViewById(R.id.playbackCurrentTime);
        this.playbackEndTime = (TextView) rootView.findViewById(R.id.playbackEndTime);
        this.bottomControlsRoot = rootView.findViewById(R.id.bottomControls);
        this.topControlsRoot = rootView.findViewById(R.id.topControls);
        this.qualityTextView = (TextView) rootView.findViewById(R.id.qualityTextView);
        this.fullScreenButton = (ImageButton) rootView.findViewById(R.id.fullScreenButton);

        //this.aspectRatioFrameLayout.setAspectRatio(16.0f / 9.0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        this.playbackSeekBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);

        this.qualityPopupMenu = new PopupMenu(context, qualityTextView);

        ((ProgressBar) this.loadingPanel.findViewById(R.id.progressBarLoadingPanel)).getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);

    }

    public void initListeners() {
        progressUpdate = new Runnable() {
            @Override
            public void run() {
                //if(DEBUG) Log.d(TAG, "progressUpdate run() called");
                onUpdateProgress((int) simpleExoPlayer.getCurrentPosition(), (int) simpleExoPlayer.getDuration(), simpleExoPlayer.getBufferedPercentage());
                if (isProgressLoopRunning.get()) progressLoop.postDelayed(this, 100);
            }
        };

        playbackSeekBar.setOnSeekBarChangeListener(this);
        fullScreenButton.setOnClickListener(this);
        qualityTextView.setOnClickListener(this);
    }

    public void initPlayer() {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");

        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        DefaultTrackSelector defaultTrackSelector = new DefaultTrackSelector(trackSelectionFactory);
        DefaultLoadControl loadControl = new DefaultLoadControl();

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, defaultTrackSelector, loadControl);
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setVideoListener(this);
        simpleExoPlayer.setVideoSurfaceView(surfaceView);
    }

    @SuppressWarnings("unchecked")
    public void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        selectedIndexStream = intent.getIntExtra(INDEX_SEL_VIDEO_STREAM, -1);

        Serializable serializable = intent.getSerializableExtra(VIDEO_STREAMS_LIST);

        if (serializable instanceof ArrayList) videoStreamsList = (ArrayList<VideoStream>) serializable;
        if (serializable instanceof Vector) videoStreamsList = new ArrayList<>((List<VideoStream>) serializable);

        videoUrl = intent.getStringExtra(VIDEO_URL);
        videoTitle = intent.getStringExtra(VIDEO_TITLE);
        videoStartPos = intent.getIntExtra(START_POSITION, -1);
        channelName = intent.getStringExtra(CHANNEL_NAME);
        startedFromNewPipe = intent.getBooleanExtra(STARTED_FROM_NEWPIPE, true);
        try {
            videoThumbnail = ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail;
        } catch (Exception e) {
            e.printStackTrace();
        }

        playVideo(getSelectedStreamUri(), true);
    }

    public void playVideo(Uri videoURI, boolean autoPlay) {
        if (DEBUG) Log.d(TAG, "playVideo() called with: videoURI = [" + videoURI + "], autoPlay = [" + autoPlay + "]");

        if (videoURI == null || simpleExoPlayer == null) {
            onError();
            return;
        }

        isPrepared = false;
        qualityChanged = false;

        qualityPopupMenu.getMenu().removeGroup(qualityPopupMenuGroupId);
        buildQualityMenu(qualityPopupMenu);

        videoSource = buildMediaSource(videoURI, MediaFormat.getSuffixById(videoStreamsList.get(selectedIndexStream).format));

        if (simpleExoPlayer.getPlaybackState() != ExoPlayer.STATE_IDLE) simpleExoPlayer.stop();
        if (videoStartPos > 0) simpleExoPlayer.seekTo(videoStartPos);
        simpleExoPlayer.prepare(videoSource);
        simpleExoPlayer.setPlayWhenReady(autoPlay);
        changeState(STATE_LOADING);
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        if (simpleExoPlayer != null) {
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (progressLoop != null) stopProgressLoop();
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        if (DEBUG) Log.d(TAG, "buildMediaSource() called with: uri = [" + uri + "], overrideExtension = [" + overrideExtension + "]");
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, cacheDataSourceFactory, new DefaultSsChunkSource.Factory(cacheDataSourceFactory), null, null);
            case C.TYPE_DASH:
                return new DashMediaSource(uri, cacheDataSourceFactory, new DefaultDashChunkSource.Factory(cacheDataSourceFactory), null, null);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, cacheDataSourceFactory, null, null);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, cacheDataSourceFactory, extractorsFactory, null, null);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    public void buildQualityMenu(PopupMenu popupMenu) {
        for (int i = 0; i < videoStreamsList.size(); i++) {
            VideoStream videoStream = videoStreamsList.get(i);
            popupMenu.getMenu().add(qualityPopupMenuGroupId, i, Menu.NONE, MediaFormat.getNameById(videoStream.format) + " " + videoStream.resolution);
        }
        qualityTextView.setText(videoStreamsList.get(selectedIndexStream).resolution);
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.setOnDismissListener(this);

    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
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

    @Override
    public void onLoading() {
        if (DEBUG) Log.d(TAG, "onLoading() called");

        if (!isProgressLoopRunning.get()) startProgressLoop();

        showAndAnimateControl(-1, true);
        playbackSeekBar.setEnabled(true);
        playbackSeekBar.setProgress(0);

        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-, so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);

        animateView(endScreen, false, 0, 0);
        loadingPanel.setBackgroundColor(Color.BLACK);
        animateView(loadingPanel, true, 0, 0);
        animateView(surfaceForeground, true, 100, 0);
    }

    @Override
    public void onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called");
        if (!isProgressLoopRunning.get()) startProgressLoop();
        showAndAnimateControl(-1, true);
        loadingPanel.setVisibility(View.GONE);
        animateView(controlsRoot, true, 500, 0, new Runnable() {
            @Override
            public void run() {
                animateView(controlsRoot, false, 500, DEFAULT_CONTROLS_HIDE_TIME, true);
            }
        });
        animateView(currentDisplaySeek, false, 200, 0);
    }

    @Override
    public void onBuffering() {
        if (DEBUG) Log.d(TAG, "onBuffering() called");
        loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        animateView(loadingPanel, true, 500, 0);
    }

    @Override
    public void onPaused() {
        if (DEBUG) Log.d(TAG, "onPaused() called");
        animateView(controlsRoot, true, 500, 100);
        loadingPanel.setVisibility(View.GONE);
    }

    @Override
    public void onPausedSeek() {
        if (DEBUG) Log.d(TAG, "onPausedSeek() called");
        showAndAnimateControl(-1, true);
    }

    @Override
    public void onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called");

        if (isProgressLoopRunning.get()) stopProgressLoop();

        if (videoThumbnail != null) endScreen.setImageBitmap(videoThumbnail);
        animateView(controlsRoot, true, 500, 0);
        animateView(endScreen, true, 800, 0);
        animateView(currentDisplaySeek, false, 200, 0);
        loadingPanel.setVisibility(View.GONE);

        playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        playbackSeekBar.setProgress(playbackSeekBar.getMax());
        playbackSeekBar.setEnabled(false);
        playbackEndTime.setText(getTimeString(playbackSeekBar.getMax()));
        playbackCurrentTime.setText(playbackEndTime.getText());
        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-, so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);

        animateView(surfaceForeground, true, 100, 0);

        if (currentRepeatMode == RepeatMode.REPEAT_ONE) {
            changeState(STATE_LOADING);
            getPlayer().seekTo(0);
        }
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
    public void onLoadingChanged(boolean isLoading) {
        if (DEBUG) Log.d(TAG, "onLoadingChanged() called with: isLoading = [" + isLoading + "]");

        if (!isLoading && getCurrentState() == STATE_PAUSED && isProgressLoopRunning.get()) stopProgressLoop();
        else if (isLoading && !isProgressLoopRunning.get()) startProgressLoop();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG) Log.d(TAG, "onPlayerStateChanged() called with: playWhenReady = [" + playWhenReady + "], playbackState = [" + playbackState + "]");
        if (getCurrentState() == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "onPlayerStateChanged() currently on PausedSeek");
            return;
        }

        switch (playbackState) {
            case ExoPlayer.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case ExoPlayer.STATE_BUFFERING: // 2
                if (isPrepared && getCurrentState() != STATE_LOADING) changeState(STATE_BUFFERING);
                break;
            case ExoPlayer.STATE_READY: //3
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                    break;
                }
                if (currentState == STATE_PAUSED_SEEK) break;
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case ExoPlayer.STATE_ENDED: // 4
                changeState(STATE_COMPLETED);
                isPrepared = false;
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "onPlayerError() called with: error = [" + error + "]");
        onError();
    }

    @Override
    public void onPositionDiscontinuity() {
        if (DEBUG) Log.d(TAG, "onPositionDiscontinuity() called");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: width / height = [" + width + " / " + height + " = " + (((float) width) / height) + "], unappliedRotationDegrees = [" + unappliedRotationDegrees + "], pixelWidthHeightRatio = [" + pixelWidthHeightRatio + "]");
        }
        aspectRatioFrameLayout.setAspectRatio(((float) width) / height);
    }

    @Override
    public void onRenderedFirstFrame() {
        animateView(surfaceForeground, false, 100, 0);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    public abstract void onError();

    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");

        if (videoStartPos > 0) {
            playbackSeekBar.setProgress(videoStartPos);
            playbackCurrentTime.setText(getTimeString(videoStartPos));
            videoStartPos = -1;
        }

        playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        playbackEndTime.setText(getTimeString((int) simpleExoPlayer.getDuration()));

        changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
    }

    public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
        if (!isPrepared) return;
        if (currentState != STATE_PAUSED) {
            if (currentState != STATE_PAUSED_SEEK) playbackSeekBar.setProgress(currentProgress);
            playbackCurrentTime.setText(getTimeString(currentProgress));
        }
        if (simpleExoPlayer.isLoading() || bufferPercent > 90) {
            playbackSeekBar.setSecondaryProgress((int) (playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "updateProgress() called with: isVisible = " + isControlsVisible() + ", currentProgress = [" + currentProgress + "], duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
    }

    public void onUpdateThumbnail(Intent intent) {
        if (DEBUG) Log.d(TAG, "onUpdateThumbnail() called with: intent = [" + intent + "]");
        if (!intent.getStringExtra(VIDEO_URL).equals(videoUrl)) return;
        videoThumbnail = ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail;
    }

    public void onVideoPlayPause() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");
        if (currentState == STATE_COMPLETED) {
            changeState(STATE_LOADING);
            if (qualityChanged) playVideo(getSelectedStreamUri(), true);
            simpleExoPlayer.seekTo(0);
            return;
        }
        simpleExoPlayer.setPlayWhenReady(!isPlaying());
    }

    public void onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called");
        seekBy(-FAST_FORWARD_REWIND_AMOUNT);
        showAndAnimateControl(R.drawable.ic_action_av_fast_rewind, true);
    }

    public void onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called");
        seekBy(FAST_FORWARD_REWIND_AMOUNT);
        showAndAnimateControl(R.drawable.ic_action_av_fast_forward, true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick related
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(View v) {
        if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
        if (v.getId() == fullScreenButton.getId()) {
            onFullScreenButtonClicked();
        } else if (v.getId() == qualityTextView.getId()) {
            onQualitySelectorClicked();
        }
    }

    /**
     * Called when an item of the quality selector is selected
     */
    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        if (DEBUG) Log.d(TAG, "onMenuItemClick() called with: menuItem = [" + menuItem + "], menuItem.getItemId = [" + menuItem.getItemId() + "]");
        if (selectedIndexStream == menuItem.getItemId()) return true;
        setVideoStartPos((int) getPlayer().getCurrentPosition());

        if (!(getCurrentState() == STATE_COMPLETED)) playVideo(Uri.parse(getVideoStreamsList().get(menuItem.getItemId()).url), wasPlaying);
        else qualityChanged = true;

        selectedIndexStream = menuItem.getItemId();
        qualityTextView.setText(menuItem.getTitle());
        return true;
    }

    /**
     * Called when the quality selector is dismissed
     */
    @Override
    public void onDismiss(PopupMenu menu) {
        if (DEBUG) Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]");
        isQualityPopupMenuVisible = false;
        qualityTextView.setText(videoStreamsList.get(selectedIndexStream).resolution);
    }

    public abstract void onFullScreenButtonClicked();

    public void onQualitySelectorClicked() {
        if (DEBUG) Log.d(TAG, "onQualitySelectorClicked() called");
        qualityPopupMenu.show();
        isQualityPopupMenuVisible = true;
        animateView(getControlsRoot(), true, 300, 0);

        VideoStream videoStream = videoStreamsList.get(selectedIndexStream);
        qualityTextView.setText(MediaFormat.getNameById(videoStream.format) + " " + videoStream.resolution);
        wasPlaying = isPlaying();
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
    // SeekBar Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (DEBUG && fromUser) Log.d(TAG, "onProgressChanged() called with: seekBar = [" + seekBar + "], progress = [" + progress + "]");
        //if (fromUser) playbackCurrentTime.setText(getTimeString(progress));
        if (fromUser) currentDisplaySeek.setText(getTimeString(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (DEBUG) Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        if (getCurrentState() != STATE_PAUSED_SEEK) changeState(STATE_PAUSED_SEEK);

        wasPlaying = isPlaying();
        if (isPlaying()) simpleExoPlayer.setPlayWhenReady(false);

        animateView(controlsRoot, true, 0, 0);
        animateView(currentDisplaySeek, true, 300, 0);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (DEBUG) Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");

        simpleExoPlayer.seekTo(seekBar.getProgress());
        if (wasPlaying || simpleExoPlayer.getDuration() == seekBar.getProgress()) simpleExoPlayer.setPlayWhenReady(true);

        playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animateView(currentDisplaySeek, false, 200, 0);

        if (getCurrentState() == STATE_PAUSED_SEEK) changeState(STATE_BUFFERING);
        if (!isProgressLoopRunning.get()) startProgressLoop();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private static final StringBuilder stringBuilder = new StringBuilder();
    private static final Formatter formatter = new Formatter(stringBuilder, Locale.getDefault());

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

    public boolean isControlsVisible() {
        return controlsRoot != null && controlsRoot.getVisibility() == View.VISIBLE;
    }

    /**
     * Show a animation, and depending on goneOnEnd, will stay on the screen or be gone
     *
     * @param drawableId the drawable that will be used to animate, pass -1 to clear any animation that is visible
     * @param goneOnEnd  will set the animation view to GONE on the end of the animation
     */
    public void showAndAnimateControl(final int drawableId, final boolean goneOnEnd) {
        if (DEBUG) Log.d(TAG, "showAndAnimateControl() called with: drawableId = [" + drawableId + "], goneOnEnd = [" + goneOnEnd + "]");
        if (controlViewAnimator != null && controlViewAnimator.isRunning()) {
            if (DEBUG) Log.d(TAG, "showAndAnimateControl: controlViewAnimator.isRunning");
            controlViewAnimator.end();
        }

        if (drawableId == -1) {
            if (controlAnimationView.getVisibility() == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1f)
                ).setDuration(300);
                controlViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        controlAnimationView.setVisibility(View.GONE);
                    }
                });
                controlViewAnimator.start();
            }
            return;
        }

        float scaleFrom = goneOnEnd ? 1f : 1f, scaleTo = goneOnEnd ? 1.8f : 1.4f;
        float alphaFrom = goneOnEnd ? 1f : 0f, alphaTo = goneOnEnd ? 0f : 1f;


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        );
        controlViewAnimator.setDuration(goneOnEnd ? 1000 : 500);
        controlViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (goneOnEnd) controlAnimationView.setVisibility(View.GONE);
                else controlAnimationView.setVisibility(View.VISIBLE);
            }
        });


        controlAnimationView.setVisibility(View.VISIBLE);
        controlAnimationView.setImageDrawable(ContextCompat.getDrawable(context, drawableId));
        controlViewAnimator.start();
    }

    public void animateView(View view, boolean enterOrExit, long duration, long delay) {
        animateView(view, enterOrExit, duration, delay, null, false);
    }

    public void animateView(View view, boolean enterOrExit, long duration, long delay, boolean hideUi) {
        animateView(view, enterOrExit, duration, delay, null, hideUi);
    }

    public void animateView(final View view, final boolean enterOrExit, long duration, long delay, final Runnable execOnEnd) {
        animateView(view, enterOrExit, duration, delay, execOnEnd, false);
    }

    /**
     * Animate the view
     *
     * @param view        view that will be animated
     * @param enterOrExit true to enter, false to exit
     * @param duration    how long the animation will take, in milliseconds
     * @param delay       how long the animation will wait to start, in milliseconds
     * @param execOnEnd   runnable that will be executed when the animation ends
     * @param hideUi      need to hide ui when animation ends,
     *                    just a helper for classes extending this
     */
    public void animateView(final View view, final boolean enterOrExit, long duration, long delay, final Runnable execOnEnd, boolean hideUi) {
        if (DEBUG) {
            Log.d(TAG, "animateView() called with: view = [" + view + "], enterOrExit = [" + enterOrExit + "], duration = [" + duration + "], delay = [" + delay + "], execOnEnd = [" + execOnEnd + "]");
        }
        if (view.getVisibility() == View.VISIBLE && enterOrExit) {
            if (DEBUG) Log.d(TAG, "animateView() view was already visible > view = [" + view + "]");
            view.animate().setListener(null).cancel();
            view.setVisibility(View.VISIBLE);
            view.setAlpha(1f);
            if (execOnEnd != null) execOnEnd.run();
            return;
        } else if ((view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE) && !enterOrExit) {
            if (DEBUG) Log.d(TAG, "animateView() view was already gone > view = [" + view + "]");
            view.animate().setListener(null).cancel();
            view.setVisibility(View.GONE);
            view.setAlpha(0f);
            if (execOnEnd != null) execOnEnd.run();
            return;
        }

        view.animate().setListener(null).cancel();
        view.setVisibility(View.VISIBLE);

        if (view == controlsRoot) {
            if (enterOrExit) {
                view.animate().alpha(1f).setDuration(duration).setStartDelay(delay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (execOnEnd != null) execOnEnd.run();
                            }
                        }).start();
            } else {
                view.animate().alpha(0f)
                        .setDuration(duration).setStartDelay(delay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.setVisibility(View.GONE);
                                if (execOnEnd != null) execOnEnd.run();
                            }
                        })
                        .start();
            }
            return;
        }

        if (enterOrExit) {
            view.setAlpha(0f);
            view.setScaleX(.8f);
            view.setScaleY(.8f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setStartDelay(delay)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (execOnEnd != null) execOnEnd.run();
                        }
                    }).start();
        } else {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.animate().alpha(0f).scaleX(.8f).scaleY(.8f).setDuration(duration).setStartDelay(delay)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setVisibility(View.GONE);
                            if (execOnEnd != null) execOnEnd.run();
                        }
                    })
                    .start();
        }
    }

    private void seekBy(int milliSeconds) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: milliSeconds = [" + milliSeconds + "]");
        if (simpleExoPlayer == null) return;
        int progress = (int) (simpleExoPlayer.getCurrentPosition() + milliSeconds);
        simpleExoPlayer.seekTo(progress);
    }

    public boolean isPlaying() {
        return simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_READY && simpleExoPlayer.getPlayWhenReady();
    }

    public boolean isQualityMenuVisible() {
        return isQualityPopupMenuVisible;
    }

    private void startProgressLoop() {
        progressLoop.removeCallbacksAndMessages(null);
        isProgressLoopRunning.set(true);
        progressLoop.post(progressUpdate);
    }

    private void stopProgressLoop() {
        isProgressLoopRunning.set(false);
        progressLoop.removeCallbacksAndMessages(null);
    }

    public void tryDeleteCacheFiles(Context context) {
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

    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    public SimpleExoPlayer getPlayer() {
        return simpleExoPlayer;
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public AspectRatioFrameLayout getAspectRatioFrameLayout() {
        return aspectRatioFrameLayout;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    public RepeatMode getCurrentRepeatMode() {
        return currentRepeatMode;
    }

    public void setCurrentRepeatMode(RepeatMode mode) {
        currentRepeatMode = mode;
    }

    public boolean wasPlaying() {
        return wasPlaying;
    }

    public int getCurrentState() {
        return currentState;
    }

    public Uri getSelectedStreamUri() {
        return Uri.parse(videoStreamsList.get(selectedIndexStream).url);
    }

    public int getQualityPopupMenuGroupId() {
        return qualityPopupMenuGroupId;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public int getVideoStartPos() {
        return videoStartPos;
    }

    public void setVideoStartPos(int videoStartPos) {
        this.videoStartPos = videoStartPos;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public Bitmap getVideoThumbnail() {
        return videoThumbnail;
    }

    public void setVideoThumbnail(Bitmap videoThumbnail) {
        this.videoThumbnail = videoThumbnail;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public int getSelectedIndexStream() {
        return selectedIndexStream;
    }

    public void setSelectedIndexStream(int selectedIndexStream) {
        this.selectedIndexStream = selectedIndexStream;
    }

    public ArrayList<VideoStream> getVideoStreamsList() {
        return videoStreamsList;
    }

    public void setVideoStreamsList(ArrayList<VideoStream> videoStreamsList) {
        this.videoStreamsList = videoStreamsList;
    }

    public boolean isStartedFromNewPipe() {
        return startedFromNewPipe;
    }

    public void setStartedFromNewPipe(boolean startedFromNewPipe) {
        this.startedFromNewPipe = startedFromNewPipe;
    }

    public View getRootView() {
        return rootView;
    }

    public void setRootView(View rootView) {
        this.rootView = rootView;
    }

    public View getLoadingPanel() {
        return loadingPanel;
    }

    public ImageView getEndScreen() {
        return endScreen;
    }

    public ImageView getControlAnimationView() {
        return controlAnimationView;
    }

    public View getControlsRoot() {
        return controlsRoot;
    }

    public View getBottomControlsRoot() {
        return bottomControlsRoot;
    }

    public SeekBar getPlaybackSeekBar() {
        return playbackSeekBar;
    }

    public TextView getPlaybackCurrentTime() {
        return playbackCurrentTime;
    }

    public TextView getPlaybackEndTime() {
        return playbackEndTime;
    }

    public View getTopControlsRoot() {
        return topControlsRoot;
    }

    public TextView getQualityTextView() {
        return qualityTextView;
    }

    public ImageButton getFullScreenButton() {
        return fullScreenButton;
    }

    public PopupMenu getQualityPopupMenu() {
        return qualityPopupMenu;
    }

    public View getSurfaceForeground() {
        return surfaceForeground;
    }

    public TextView getCurrentDisplaySeek() {
        return currentDisplaySeek;
    }
}
