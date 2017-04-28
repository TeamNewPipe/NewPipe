package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.ContextCompat;
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

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Base for <b>video</b> players
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class VideoPlayer extends BasePlayer implements SimpleExoPlayer.VideoListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener, ExoPlayer.EventListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    public static final boolean DEBUG = BasePlayer.DEBUG;
    public final String TAG;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String VIDEO_STREAMS_LIST = "video_streams_list";
    public static final String VIDEO_ONLY_AUDIO_STREAM = "video_only_audio_stream";
    public static final String INDEX_SEL_VIDEO_STREAM = "index_selected_video_stream";
    public static final String STARTED_FROM_NEWPIPE = "started_from_newpipe";

    private int selectedIndexStream;
    private ArrayList<VideoStream> videoStreamsList = new ArrayList<>();
    private AudioStream videoOnlyAudioStream;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    public static final int DEFAULT_CONTROLS_HIDE_TIME = 3000;  // 3 Seconds

    private boolean startedFromNewPipe = true;
    private boolean wasPlaying = false;

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

    public VideoPlayer(String debugTag, Context context) {
        super(context);
        this.TAG = debugTag;
        this.context = context;
    }

    public void setup(View rootView) {
        initViews(rootView);
        setup();
    }

    public void initViews(View rootView) {
        this.rootView = rootView;
        this.aspectRatioFrameLayout = (AspectRatioFrameLayout) rootView.findViewById(R.id.aspectRatioLayout);
        this.surfaceView = (SurfaceView) rootView.findViewById(R.id.surfaceView);
        this.surfaceForeground = rootView.findViewById(R.id.surfaceForeground);
        this.loadingPanel = rootView.findViewById(R.id.loading_panel);
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

    @Override
    public void initListeners() {
        super.initListeners();
        playbackSeekBar.setOnSeekBarChangeListener(this);
        fullScreenButton.setOnClickListener(this);
        qualityTextView.setOnClickListener(this);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        simpleExoPlayer.setVideoSurfaceView(surfaceView);
        simpleExoPlayer.setVideoListener(this);
    }

    @SuppressWarnings("unchecked")
    public void handleIntent(Intent intent) {
        super.handleIntent(intent);
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        selectedIndexStream = intent.getIntExtra(INDEX_SEL_VIDEO_STREAM, -1);

        Serializable serializable = intent.getSerializableExtra(VIDEO_STREAMS_LIST);

        if (serializable instanceof ArrayList) videoStreamsList = (ArrayList<VideoStream>) serializable;
        if (serializable instanceof Vector) videoStreamsList = new ArrayList<>((List<VideoStream>) serializable);

        Serializable audioStream = intent.getSerializableExtra(VIDEO_ONLY_AUDIO_STREAM);
        if (audioStream != null) videoOnlyAudioStream = (AudioStream) audioStream;

        startedFromNewPipe = intent.getBooleanExtra(STARTED_FROM_NEWPIPE, true);
        play(true);
    }


    public void play(boolean autoPlay) {
        playUrl(getSelectedVideoStream().url, MediaFormat.getSuffixById(getSelectedVideoStream().format), autoPlay);
    }

    @Override
    public void playUrl(String url, String format, boolean autoPlay) {
        if (DEBUG) Log.d(TAG, "play() called with: url = [" + url + "], autoPlay = [" + autoPlay + "]");
        qualityChanged = false;

        if (url == null || simpleExoPlayer == null) {
            RuntimeException runtimeException = new RuntimeException((url == null ? "Url " : "Player ") + " null");
            onError(runtimeException);
            throw runtimeException;
        }

        qualityPopupMenu.getMenu().removeGroup(qualityPopupMenuGroupId);
        buildQualityMenu(qualityPopupMenu);

        super.playUrl(url, format, autoPlay);
    }

    @Override
    public MediaSource buildMediaSource(String url, String overrideExtension) {
        MediaSource mediaSource = super.buildMediaSource(url, overrideExtension);
        if (!getSelectedVideoStream().isVideoOnly) return mediaSource;

        Uri audioUri = Uri.parse(videoOnlyAudioStream.url);
        return new MergingMediaSource(mediaSource, new ExtractorMediaSource(audioUri, cacheDataSourceFactory, extractorsFactory, null, null));
    }

    public void buildQualityMenu(PopupMenu popupMenu) {
        for (int i = 0; i < videoStreamsList.size(); i++) {
            VideoStream videoStream = videoStreamsList.get(i);
            popupMenu.getMenu().add(qualityPopupMenuGroupId, i, Menu.NONE, MediaFormat.getNameById(videoStream.format) + " " + videoStream.resolution);
        }
        qualityTextView.setText(getSelectedVideoStream().resolution);
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.setOnDismissListener(this);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

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
            simpleExoPlayer.seekTo(0);
        }
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

    @Override
    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");

        if (videoStartPos > 0) {
            playbackSeekBar.setProgress(videoStartPos);
            playbackCurrentTime.setText(getTimeString(videoStartPos));
            videoStartPos = -1;
        }

        playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        playbackEndTime.setText(getTimeString((int) simpleExoPlayer.getDuration()));

        super.onPrepared(playWhenReady);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (endScreen != null) endScreen.setImageBitmap(null);
    }

    @Override
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

    @Override
    public void onVideoPlayPauseRepeat() {
        if (DEBUG) Log.d(TAG, "onVideoPlayPauseRepeat() called");
        if (qualityChanged) {
            setVideoStartPos(0);
            play(true);
        } else super.onVideoPlayPauseRepeat();
    }

    @Override
    public void onThumbnailReceived(Bitmap thumbnail) {
        super.onThumbnailReceived(thumbnail);
        if (thumbnail != null) endScreen.setImageBitmap(thumbnail);
    }

    protected abstract void onFullScreenButtonClicked();

    @Override
    public void onFastRewind() {
        super.onFastRewind();
        showAndAnimateControl(R.drawable.ic_action_av_fast_rewind, true);
    }

    @Override
    public void onFastForward() {
        super.onFastForward();
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
        setVideoStartPos((int) simpleExoPlayer.getCurrentPosition());

        selectedIndexStream = menuItem.getItemId();
        if (!(getCurrentState() == STATE_COMPLETED)) play(wasPlaying);
        else qualityChanged = true;

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
        qualityTextView.setText(getSelectedVideoStream().resolution);
    }

    public void onQualitySelectorClicked() {
        if (DEBUG) Log.d(TAG, "onQualitySelectorClicked() called");
        qualityPopupMenu.show();
        isQualityPopupMenuVisible = true;
        animateView(getControlsRoot(), true, 300, 0);

        VideoStream videoStream = getSelectedVideoStream();
        qualityTextView.setText(MediaFormat.getNameById(videoStream.format) + " " + videoStream.resolution);
        wasPlaying = isPlaying();
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

    public boolean isQualityMenuVisible() {
        return isQualityPopupMenuVisible;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    public AspectRatioFrameLayout getAspectRatioFrameLayout() {
        return aspectRatioFrameLayout;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    public boolean wasPlaying() {
        return wasPlaying;
    }

    public VideoStream getSelectedVideoStream() {
        return videoStreamsList.get(selectedIndexStream);
    }

    public Uri getSelectedStreamUri() {
        return Uri.parse(getSelectedVideoStream().url);
    }

    public int getQualityPopupMenuGroupId() {
        return qualityPopupMenuGroupId;
    }

    public int getSelectedStreamIndex() {
        return selectedIndexStream;
    }

    public void setSelectedIndexStream(int selectedIndexStream) {
        this.selectedIndexStream = selectedIndexStream;
    }

    public void setAudioStream(AudioStream audioStream) {
        this.videoOnlyAudioStream = audioStream;
    }

    public AudioStream getAudioStream() {
        return videoOnlyAudioStream;
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
