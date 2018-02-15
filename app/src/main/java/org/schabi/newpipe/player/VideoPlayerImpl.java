package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.List;

import static org.schabi.newpipe.player.MainPlayerService.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.isUsingOldPlayer;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

@SuppressWarnings({"unused", "WeakerAccess"})
public class VideoPlayerImpl extends VideoPlayer implements View.OnLayoutChangeListener {
    private TextView titleTextView;
    private TextView channelTextView;
    private TextView volumeTextView;
    private TextView brightnessTextView;
    private TextView resizingIndicator;
    private ImageButton queueButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private ImageButton screenRotationButton;

    private ImageButton playPauseButton;
    private ImageButton playPreviousButton;
    private ImageButton playNextButton;

    private RelativeLayout queueLayout;
    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private boolean queueVisible;
    private PlayerType playerType = PlayerType.VIDEO;

    // We can use audioOnly variable with any type of video. It tells to the player to disable video stream
    private boolean audioOnly = false;
    private boolean isFullscreen = false;
    private boolean shouldUpdateOnProgress;

    private ImageButton moreOptionsButton;

    private Bitmap cachedImage;

    private MainPlayerService service;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;
    private GestureDetector gestureDetector;

    // Popup
    private WindowManager.LayoutParams windowLayoutParams;
    private WindowManager windowManager;
    static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    static final String POPUP_SAVED_X = "popup_saved_x";
    static final String POPUP_SAVED_Y = "popup_saved_y";
    private static final int MINIMUM_SHOW_EXTRA_WIDTH_DP = 300;
    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;
    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;
    // Popup end


    @Override
    public void handleIntent(Intent intent) {
        if (intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) == null)
            return;

        PlayerType oldPlayerType = playerType;
        choosePlayerTypeFromIntent(intent);
        audioOnly = audioPlayerSelected();
        // We need to setup audioOnly before super()
        super.handleIntent(intent);

        if (intent.getBooleanExtra(APPEND_ONLY, false) && oldPlayerType != playerType && playQueue != null) {
            // BasePlayer will not rebuild player if append mode is used. But if playerType changes we should rebuild it
            rebuildPlayer();
        }

        service.resetNotification();
        if (service.getBigNotRemoteView() != null)
            service.getBigNotRemoteView().setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        if (service.getNotRemoteView() != null)
            service.getNotRemoteView().setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        service.startForeground(NOTIFICATION_ID, service.getNotBuilder().build());
        setupElementsVisibility();

        if (audioPlayerSelected()) {
            service.removeViewFromParent();
        } else if (popupPlayerSelected()) {
            getRootView().setVisibility(View.VISIBLE);
            initPopup();
        } else {
            getRootView().setVisibility(View.VISIBLE);
            initVideoPlayer();
        }
    }

    VideoPlayerImpl(final MainPlayerService service) {
        super("VideoPlayerImpl", service);
        this.service = service;
        this.shouldUpdateOnProgress = true;
        this.windowManager = (WindowManager) service.getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void initViews(View rootView) {
        super.initViews(rootView);
        this.titleTextView = rootView.findViewById(R.id.titleTextView);
        this.channelTextView = rootView.findViewById(R.id.channelTextView);
        this.volumeTextView = rootView.findViewById(R.id.volumeTextView);
        this.brightnessTextView = rootView.findViewById(R.id.brightnessTextView);
        this.queueButton = rootView.findViewById(R.id.queueButton);
        this.repeatButton = rootView.findViewById(R.id.repeatButton);
        this.shuffleButton = rootView.findViewById(R.id.shuffleButton);
        this.screenRotationButton = rootView.findViewById(R.id.screenRotationButton);
        this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
        this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
        this.playNextButton = rootView.findViewById(R.id.playNextButton);
        this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
        this.resizingIndicator = rootView.findViewById(R.id.resizing_indicator);

        getRootView().setKeepScreenOn(true);
    }

    public void setupElementsVisibility() {
        boolean globalOrientationLocked = !(android.provider.Settings.System.getInt(service.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
        if (popupPlayerSelected()) {
            getFullScreenButton().setVisibility(View.VISIBLE);
            screenRotationButton.setVisibility(View.GONE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.GONE);
            getQualityTextView().setVisibility(View.VISIBLE);
            queueButton.setVisibility(View.GONE);
            moreOptionsButton.setVisibility(View.GONE);
            getTopControls().setOrientation(LinearLayout.HORIZONTAL);
            getPrimaryControls().getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
            getSecondaryControls().setVisibility(View.VISIBLE);
        } else {
            getFullScreenButton().setVisibility(View.GONE);
            screenRotationButton.setVisibility(globalOrientationLocked? View.VISIBLE : View.GONE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
            getQualityTextView().setVisibility(isInFullscreen() ? View.VISIBLE : View.INVISIBLE);
            moreOptionsButton.setVisibility(View.VISIBLE);
            getTopControls().setOrientation(LinearLayout.VERTICAL);
            getPrimaryControls().getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;
            getSecondaryControls().setVisibility(View.GONE);
            moreOptionsButton.setImageDrawable(service.getResources().getDrawable(
                    R.drawable.ic_expand_more_white_24dp));
        }
    }

    @Override
    public void initListeners() {
        super.initListeners();

        PlayerGestureListener listener = new PlayerGestureListener(this, service);
        gestureDetector = new GestureDetector(context, listener);
        getRootView().setOnTouchListener(listener);
        getRootView().addOnLayoutChangeListener(this);

        queueButton.setOnClickListener(this);
        repeatButton.setOnClickListener(this);
        shuffleButton.setOnClickListener(this);

        playPauseButton.setOnClickListener(this);
        playPreviousButton.setOnClickListener(this);
        playNextButton.setOnClickListener(this);
        screenRotationButton.setOnClickListener(this);
        moreOptionsButton.setOnClickListener(this);
    }

    public Activity getParentActivity() {
        // ! instanceof ViewGroup means that view was added via windowManager for Popup
        if (getRootView().getParent() == null || !(getRootView().getParent() instanceof ViewGroup))
            return null;

        ViewGroup parent = (ViewGroup) getRootView().getParent();
        return (Activity) parent.getContext();
    }

    @Override
    protected void setupSubtitleView(@NonNull SubtitleView view,
                                     @NonNull String captionSizeKey) {
        if (popupPlayerSelected()) {
            float captionRatio = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION;
            if (captionSizeKey.equals(service.getString(R.string.smaller_caption_size_key))) {
                captionRatio *= 0.9;
            } else if (captionSizeKey.equals(service.getString(R.string.larger_caption_size_key))) {
                captionRatio *= 1.1;
            }
            view.setFractionalTextSize(captionRatio);
        } else {
            final float captionRatioInverse;
            if (captionSizeKey.equals(service.getString(R.string.smaller_caption_size_key))) {
                captionRatioInverse = 22f;
            }
            else if (captionSizeKey.equals(service.getString(R.string.larger_caption_size_key))) {
                captionRatioInverse = 18f;
            }
            else {
                captionRatioInverse = 20f;
            }

            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,
                                  (float) minimumLength / captionRatioInverse);
        }
    }

    @Override
    public void onLayoutChange(final View view, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (popupPlayerSelected()) {
            float widthDp = Math.abs(right - left) / service.getResources().getDisplayMetrics().density;
            final int visibility = widthDp > MINIMUM_SHOW_EXTRA_WIDTH_DP ? View.VISIBLE : View.GONE;
            getSecondaryControls().setVisibility(visibility);
        }
        else if (videoPlayerSelected()
                && !isInFullscreen()
                && getAspectRatioFrameLayout().getMeasuredHeight() > service.getResources().getDisplayMetrics().heightPixels * 0.8) {
            // Resize mode is ZOOM probably. In this mode video will grow down and it will be weird. So let's open it in fullscreen
            onFullScreenButtonClicked();
        }
    }

    @Override
    protected int nextResizeMode(int currentResizeMode) {
        switch (currentResizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                return AspectRatioFrameLayout.RESIZE_MODE_FILL;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                return videoPlayerSelected()? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT;
            default:
                return AspectRatioFrameLayout.RESIZE_MODE_FIT;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onRepeatModeChanged(int i) {
        super.onRepeatModeChanged(i);
        setRepeatModeRemote(service.getNotRemoteView(), i);
        updatePlaybackButtons();
        updatePlayback();
        service.updateNotification(-1);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        updatePlayback();
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        super.onPlayerError(error);

        if (fragmentListener != null && !popupPlayerSelected())
            fragmentListener.onPlayerError(error);
    }

    @Override
    public void sync(@NonNull final PlayQueueItem item, @Nullable final StreamInfo info) {
        super.sync(item, info);
        titleTextView.setText(getVideoTitle());
        channelTextView.setText(getUploaderName());
        updateMetadata();
    }

    @Override
    public void onShuffleClicked() {
        super.onShuffleClicked();
        updatePlaybackButtons();
        updatePlayback();
    }

    @Override
    public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent);
        updateProgress(currentProgress, duration, bufferPercent);

        if (!shouldUpdateOnProgress || getCurrentState() == BasePlayer.STATE_COMPLETED || getCurrentState() == BasePlayer.STATE_PAUSED || getPlayQueue() == null)
            return;

        service.resetNotification();
        if (service.getBigNotRemoteView() != null) {
            service.getBigNotRemoteView().setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            service.getBigNotRemoteView().setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration));
        }
        if (service.getNotRemoteView() != null) {
            service.getNotRemoteView().setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
        }
        service.updateNotification(-1);
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        if (!audioOnly)
            return super.sourceOf(item, info);
        else {
            final int index = ListHelper.getDefaultAudioFormat(context, info.audio_streams);
            if (index < 0 || index >= info.audio_streams.size())
                return null;

            final AudioStream audio = info.audio_streams.get(index);
            return buildMediaSource(audio.getUrl(), MediaFormat.getSuffixById(audio.format));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player Overrides
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onFullScreenButtonClicked() {
        if (DEBUG)
            Log.d(TAG, "onFullScreenButtonClicked() called");

        if (popupPlayerSelected()) {
            setRecovery();
            service.removeViewFromParent();
            Intent intent;
            if (!isUsingOldPlayer(service)) {
                intent = NavigationHelper.getPlayerIntent(
                        context,
                        MainActivity.class,
                        this.getPlayQueue(),
                        this.getRepeatMode(),
                        this.getPlaybackSpeed(),
                        this.getPlaybackPitch(),
                        this.getPlaybackQuality()
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
                intent.putExtra(Constants.KEY_URL, getVideoUrl());
                intent.putExtra(Constants.KEY_TITLE, getVideoTitle());
                intent.putExtra(BasePlayer.AUTO_PLAY, true);
            } else {
                intent = new Intent(service, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                        .putExtra(PlayVideoActivity.STREAM_URL, getSelectedVideoStream().getUrl())
                        .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                        .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
            }
            context.startActivity(intent);
        } else {
            if (fragmentListener == null)
                return;

            playerInFullscreenNow(!isInFullscreen());
            getQualityTextView().setVisibility(isInFullscreen() ? View.VISIBLE : View.GONE);
            fragmentListener.onFullScreenButtonClicked(isInFullscreen());

            // When user presses back button in landscape mode and in fullscreen and uses ZOOM mode a video can be larger than screen. Prevent it like this
            if (getAspectRatioFrameLayout().getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM && !isInFullscreen() && service.isLandScape())
                onResizeClicked();
        }

    }

    public void onPlayBackgroundButtonClicked() {
        if (DEBUG)
            Log.d(TAG, "onPlayBackgroundButtonClicked() called");

        if (getPlayer() == null)
            return;

        setRecovery();
        final Intent intent = NavigationHelper.getPlayerIntent(
                context,
                MainPlayerService.class,
                this.getPlayQueue(),
                this.getRepeatMode(),
                this.getPlaybackSpeed(),
                this.getPlaybackPitch(),
                this.getPlaybackQuality()
        );
        context.startService(intent);

        ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
        destroy();
    }


    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == playPauseButton.getId()) {
            useVideoSource(true);
            onVideoPlayPause();

        } else if (v.getId() == playPreviousButton.getId()) {
            onPlayPrevious();

        } else if (v.getId() == playNextButton.getId()) {
            onPlayNext();

        } else if (v.getId() == screenRotationButton.getId()) {
            onScreenRotationClicked();

        } else if (v.getId() == queueButton.getId()) {
            onQueueClicked();
            return;

        } else if (v.getId() == repeatButton.getId()) {
            onRepeatClicked();
            return;

        } else if (v.getId() == shuffleButton.getId()) {
            onShuffleClicked();
            return;

        } else if (v.getId() == moreOptionsButton.getId()) {
            onMoreOptionsClicked();
        }

        if (getCurrentState() != STATE_COMPLETED) {
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            animateView(getControlsRoot(), true, 300, 0, () -> {
                if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                    hideControls(300, DEFAULT_CONTROLS_HIDE_TIME);
                }
            });
        }
    }

    private void onQueueClicked() {
        if (playQueue.getIndex() < 0)
            return;

        queueVisible = true;

        buildQueue();
        updatePlaybackButtons();

        getControlsRoot().setVisibility(View.INVISIBLE);
        queueLayout.setVisibility(View.VISIBLE);

        itemsList.scrollToPosition(playQueue.getIndex());

        if (playQueue.getStreams().size() > 4 && !isInFullscreen())
            onFullScreenButtonClicked();
    }

    private void onQueueClosed() {
        queueLayout.setVisibility(View.GONE);
        queueVisible = false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG)
            Log.d(TAG, "onMoreOptionsClicked() called");

        // Don't use animateView. It gives unexpected result when switching from main to popup
        if (getSecondaryControls().getVisibility() == View.VISIBLE) {
            moreOptionsButton.setImageDrawable(service.getResources().getDrawable(
                    R.drawable.ic_expand_more_white_24dp));
            getSecondaryControls().setVisibility(View.GONE);
        } else {
            moreOptionsButton.setImageDrawable(service.getResources().getDrawable(
                    R.drawable.ic_expand_less_white_24dp));
            getSecondaryControls().setVisibility(View.VISIBLE);
        }
        showControls(300);
    }

    private void onScreenRotationClicked() {
        if (DEBUG)
            Log.d(TAG, "onScreenRotationClicked() called");

        service.toggleOrientation();
        onMoreOptionsClicked();
        showControlsThenHide();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        if (wasPlaying()) {
            hideControls(100, 0);
        }
    }

    @Override
    public void onDismiss(PopupMenu menu) {
        super.onDismiss(menu);
        if (isPlaying())
            hideControls(300, 0);
    }

    @Override
    protected int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
        return videoPlayerSelected()?
                ListHelper.getDefaultResolutionIndex(context, sortedVideos) :
                ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
    }

    @Override
    protected int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                             final String playbackQuality) {
        return videoPlayerSelected() ?
                ListHelper.getDefaultResolutionIndex(context, sortedVideos, playbackQuality) :
                ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos, playbackQuality);
    }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

    private void animatePlayButtons(final boolean show, final int duration) {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if (playQueue.getIndex() > 0)
            animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if (playQueue.getIndex() + 1 < playQueue.getStreams().size())
            animateView(playNextButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
    }

    @Override
    public void changeState(int state) {
        super.changeState(state);
        updatePlayback();
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        playPauseButton.setImageResource(R.drawable.ic_pause_white);
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(true);
        service.setControlsOpacity(77);
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(true);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(true, 200);
        });
        checkLandscape();
        getRootView().setKeepScreenOn(true);
        service.getLockManager().acquireWifiAndCpu();
        service.resetNotification();
        service.setControlsOpacity(255);
        service.updateNotification(R.drawable.ic_pause_white);
        service.startForeground(NOTIFICATION_ID, service.getNotBuilder().build());
    }

    @Override
    public void onPaused() {
        super.onPaused();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
            animatePlayButtons(true, 200);
        });
        getRootView().setKeepScreenOn(false);
        service.getLockManager().releaseWifiAndCpu();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        if (!videoPlayerSelected()) {
            service.stopForeground(false);
        } else {
            service.stopForeground(true);
            service.getNotificationManager().cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(true);
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }


    @Override
    public void onCompleted() {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_replay_white);
            animatePlayButtons(true, 300);
        });

        getRootView().setKeepScreenOn(false);
        showControls(100);
        service.getLockManager().releaseWifiAndCpu();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        if (audioPlayerSelected()) {
            service.stopForeground(false);
        } else {
            service.stopForeground(true);
            service.getNotificationManager().cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void shutdown() {
        if (DEBUG)
            Log.d(TAG, "Shutting down...");
        // Override it because we don't want playerImpl destroyed
    }

    @Override
    public void destroy() {
        super.destroy();
        cachedImage = null;
        setRootView(null);
        service.stopForeground(true);
        service.getNotificationManager().cancel(NOTIFICATION_ID);
        stopActivityBinding();
    }

    @Override
    public void initThumbnail(final String url) {
        service.resetNotification();
        if (service.getNotRemoteView() != null)
            service.getNotRemoteView().setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        if (service.getBigNotRemoteView() != null)
            service.getBigNotRemoteView().setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        service.updateNotification(-1);
        super.initThumbnail(url);
    }

    @Override
    public void onThumbnailReceived(Bitmap thumbnail) {
        super.onThumbnailReceived(thumbnail);
        if (thumbnail != null) {
            cachedImage = thumbnail;
            // rebuild notification here since remote view does not release bitmaps, causing memory leaks
            service.resetNotification();

            if (service.getNotRemoteView() != null)
                service.getNotRemoteView().setImageViewBitmap(R.id.notificationCover, thumbnail);
            if (service.getBigNotRemoteView() != null)
                service.getBigNotRemoteView().setImageViewBitmap(R.id.notificationCover, thumbnail);

            service.updateNotification(-1);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void setupBroadcastReceiver(IntentFilter intentFilter) {
        super.setupBroadcastReceiver(intentFilter);
        if (DEBUG)
            Log.d(TAG, "setupBroadcastReceiver() called with: intentFilter = [" + intentFilter + "]");

        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_OPEN_CONTROLS);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(ACTION_PLAY_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_NEXT);
        intentFilter.addAction(ACTION_FAST_REWIND);
        intentFilter.addAction(ACTION_FAST_FORWARD);

        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
    }

    @Override
    public void onBroadcastReceived(Intent intent) {
        super.onBroadcastReceived(intent);
        if (intent == null || intent.getAction() == null)
            return;

        if (DEBUG)
            Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");

        switch (intent.getAction()) {
            case ACTION_CLOSE:
                service.onDestroy();
                break;
            case ACTION_PLAY_NEXT:
                onPlayNext();
                break;
            case ACTION_PLAY_PREVIOUS:
                onPlayPrevious();
                break;
            case ACTION_FAST_FORWARD:
                onFastForward();
                break;
            case ACTION_FAST_REWIND:
                onFastRewind();
                break;
            case ACTION_PLAY_PAUSE:
                onVideoPlayPause();
                break;
            case ACTION_REPEAT:
                onRepeatClicked();
                break;
            case Intent.ACTION_SCREEN_ON:
                shouldUpdateOnProgress = true;
                // Interrupt playback only when screen turns on and user is watching video in fragment
                if (backgroundPlaybackEnabledInSettings() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                shouldUpdateOnProgress = false;
                // Interrupt playback only when screen turns off with video working
                if (backgroundPlaybackEnabledInSettings() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(false);
                break;
        }
        service.resetNotification();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void choosePlayerTypeFromIntent(Intent intent) {
        // If you want to open popup from the app just include Constants.POPUP_ONLY into an extra
        if (intent.getBooleanExtra(BasePlayer.AUDIO_ONLY, false)) {
            playerType = PlayerType.AUDIO;
        } else if (intent.getBooleanExtra(Constants.POPUP_ONLY, false)
                || intent.getStringExtra(Constants.KEY_URL) != null) {
            playerType = PlayerType.POPUP;
        } else {
            playerType = PlayerType.VIDEO;
        }
    }

    public boolean backgroundPlaybackEnabledInSettings() {
        return PreferenceManager.getDefaultSharedPreferences(service).getBoolean(service.getString(R.string.continue_in_background_key), false);
    }

    public void selectAudioPlayer(boolean justAudio) {
        playerType = justAudio ? PlayerType.AUDIO : PlayerType.VIDEO;
    }

    public boolean audioPlayerSelected() {
        return playerType == PlayerType.AUDIO;
    }

    public boolean videoPlayerSelected() {
        return playerType == PlayerType.VIDEO;
    }

    public boolean popupPlayerSelected() {
        return playerType == PlayerType.POPUP;
    }


    public void playerInFullscreenNow(boolean fullscreen) {
        isFullscreen = fullscreen;
    }

    public boolean isInFullscreen() {
        return isFullscreen;
    }

    @Override
    public void showControlsThenHide() {
        if (queueVisible)
            return;

        showOrHideButtons();
        super.showControlsThenHide();
    }

    @Override
    public void showControls(long duration) {
        if (queueVisible)
            return;

        showOrHideButtons();
        super.showControls(duration);
    }

    @Override
    public void hideControls(final long duration, long delay) {
        if (DEBUG)
            Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");

        showOrHideButtons();

        getControlsVisibilityHandler().removeCallbacksAndMessages(null);
        getControlsVisibilityHandler().postDelayed(() ->
                        animateView(getControlsRoot(), false, duration, 0, () -> {
                            if (getRootView() == null || getRootView().getContext() == null || !isInFullscreen())
                                return;

                            Activity parent = getParentActivity();
                            if (parent == null)
                                return;

                            Window window = parent.getWindow();

                            if (android.os.Build.VERSION.SDK_INT >= 16) {
                                int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT)
                                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                                window.getDecorView().setSystemUiVisibility(visibility);
                            }
                            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        })
                , delay);
    }

    private void showOrHideButtons() {
        if (playQueue == null)
            return;

        if (playQueue.getIndex() == 0)
            playPreviousButton.setVisibility(View.INVISIBLE);
        else
            playPreviousButton.setVisibility(View.VISIBLE);

        if (playQueue.getIndex() + 1 == playQueue.getStreams().size())
            playNextButton.setVisibility(View.INVISIBLE);
        else
            playNextButton.setVisibility(View.VISIBLE);

        if (playQueue.getStreams().size() <= 1 || popupPlayerSelected())
            queueButton.setVisibility(View.GONE);
        else
            queueButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideSystemUIIfNeeded() {
        if(fragmentListener != null)
            fragmentListener.hideSystemUIIfNeeded();
    }

    private void updatePlaybackButtons() {
        if (repeatButton == null || shuffleButton == null ||
                simpleExoPlayer == null || playQueue == null)
            return;

        service.setRepeatModeButton(repeatButton, getRepeatMode());
        service.setShuffleButton(shuffleButton, playQueue.isShuffled());
    }

    public void checkLandscape() {
        Activity parent = getParentActivity();
        if (parent != null && service.isLandScape() && !isInFullscreen() && getCurrentState() != STATE_COMPLETED && videoPlayerSelected())
            onFullScreenButtonClicked();
    }

    public void setupScreenRotationButton(boolean visible) {
        if(!videoPlayerSelected())
            return;

        screenRotationButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void buildQueue() {
        queueLayout = getRootView().findViewById(R.id.playQueuePanel);

        ImageButton itemsListCloseButton = getRootView().findViewById(R.id.playQueueClose);

        itemsList = getRootView().findViewById(R.id.playQueue);
        itemsList.setAdapter(playQueueAdapter);
        itemsList.setClickable(true);
        itemsList.setLongClickable(true);

        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(getQueueScrollListener());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        playQueueAdapter.setSelectedListener(getOnSelectedListener());

        itemsListCloseButton.setOnClickListener(view ->
                onQueueClosed()
        );
    }

    public void useVideoSource(boolean video) {
        // Return when: old value of audioOnly equals to the new value, audio player is selected,
        // video player is selected AND fragment is not shown
        if (playQueue == null
                || audioOnly == !video
                || audioPlayerSelected()
                || (video && videoPlayerSelected() && fragmentListener.isPaused()))
            return;

        boolean shouldStartPlaying = true;
        if (getPlayer() != null)
            shouldStartPlaying = isPlaying();

        audioOnly = !video;
        setRecovery();
        // Here we are restarting playback. This method will be called with audioOnly setting - sourceOf(final PlayQueueItem item, final StreamInfo info)
        initPlayback(playQueue);
        getPlayer().setPlayWhenReady(shouldStartPlaying);
    }

    private OnScrollBelowItemsListener getQueueScrollListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(RecyclerView recyclerView) {
                if (playQueue != null && !playQueue.isComplete()) {
                    playQueue.fetch();
                } else if (itemsList != null) {
                    itemsList.clearOnScrollListeners();
                }
            }
        };
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()) {
                    return false;
                }

                final int sourceIndex = source.getLayoutPosition();
                final int targetIndex = target.getLayoutPosition();
                playQueue.move(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            }
        };
    }

    private void rebuildPlayer() {
        setRecovery();
        // Here we are restarting playback. This method will be called with audioOnly setting - sourceOf(final PlayQueueItem item, final StreamInfo info)
        PlayQueue oldQueue = playQueue;
        // Re-initialization
        destroyPlayer();
        initPlayer();
        initPlayback(oldQueue);
        getPlayer().setPlayWhenReady(true);
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(PlayQueueItem item, View view) {
                onSelected(item);
            }

            @Override
            public void held(PlayQueueItem item, View view) {
                final int index = playQueue.indexOf(item);
                if (index != -1)
                    playQueue.remove(index);
            }

            @Override
            public void onStartDrag(PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null)
                    itemTouchHelper.startDrag(viewHolder);
            }
        };
    }

    protected void setRepeatModeRemote(final RemoteViews remoteViews, final int repeatMode) {
        final String methodName = "setImageResource";

        if (remoteViews == null)
            return;

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_all);
                break;
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG)
            Log.d(TAG, "initPopup() called");

        updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(service);
        final float defaultSize = service.getResources().getDimension(R.dimen.popup_default_width);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        popupWidth = popupRememberSizeAndPos ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_PHONE : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        windowLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) getMinimumVideoHeight(popupWidth),
                layoutParamType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        windowLayoutParams.x = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        windowLayoutParams.y = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        checkPositionBounds();

        getLoadingPanel().setMinimumWidth(windowLayoutParams.width);
        getLoadingPanel().setMinimumHeight(windowLayoutParams.height);

        service.removeViewFromParent();
        windowManager.addView(service.getView(), windowLayoutParams);

        if (getAspectRatioFrameLayout().getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            onResizeClicked();
    }

    private void initVideoPlayer() {
        service.getView().setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Popup utils
    //////////////////////////////////////////////////////////////////////////*/

    public void updateViewLayout(View view, WindowManager.LayoutParams windowLayoutParams) {
        if (view.getParent() == null)
            return;

        windowManager.updateViewLayout(view, windowLayoutParams);
    }

    public void checkPositionBounds() {
        if (windowLayoutParams.x > screenWidth - windowLayoutParams.width)
            windowLayoutParams.x = (int) (screenWidth - windowLayoutParams.width);
        if (windowLayoutParams.x < 0)
            windowLayoutParams.x = 0;
        if (windowLayoutParams.y > screenHeight - windowLayoutParams.height)
            windowLayoutParams.y = (int) (screenHeight - windowLayoutParams.height);
        if (windowLayoutParams.y < 0)
            windowLayoutParams.y = 0;
    }

    public void savePositionAndSize() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, windowLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, windowLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, windowLayoutParams.width).apply();
    }

    public void updateScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        if (DEBUG)
            Log.d(TAG, "updateScreenSize() called > screenWidth = " + screenWidth + ", screenHeight = " + screenHeight);

        popupWidth = service.getResources().getDimension(R.dimen.popup_default_width);
        popupHeight = getMinimumVideoHeight(popupWidth);

        minimumWidth = service.getResources().getDimension(R.dimen.popup_minimum_width);
        minimumHeight = getMinimumVideoHeight(minimumWidth);

        maximumWidth = screenWidth;
        maximumHeight = screenHeight;
    }

    public void updatePopupSize(WindowManager.LayoutParams windowLayoutParams, int width, int height) {
        if (DEBUG)
            Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

        width = (int) (width > maximumWidth ? maximumWidth : width < minimumWidth ? minimumWidth : width);

        if (height == -1)
            height = (int) getMinimumVideoHeight(width);
        else
            height = (int) (height > maximumHeight ? maximumHeight : height < minimumHeight ? minimumHeight : height);

        windowLayoutParams.width = width;
        windowLayoutParams.height = height;
        popupWidth = width;
        popupHeight = height;

        if (DEBUG)
            Log.d(TAG, "updatePopupSize() updated values:  width = [" + width + "], height = [" + height + "]");

        updateViewLayout(getRootView(), windowLayoutParams);
    }

    private float getMinimumVideoHeight(float width) {
        //if (DEBUG) Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], returned: " + height);
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    ///////////////////////////////////////////////////////////////////////////
    // Manipulations with listener
    ///////////////////////////////////////////////////////////////////////////

    public void setFragmentListener(PlayerServiceEventListener listener) {
        fragmentListener = listener;
        updateMetadata();
        updatePlayback();
        triggerProgressUpdate();
    }

    public void removeFragmentListener(PlayerServiceEventListener listener) {
        if (fragmentListener == listener) {
            fragmentListener = null;
        }
    }

    /*package-private*/ void setActivityListener(PlayerEventListener listener) {
        activityListener = listener;
        updateMetadata();
        triggerProgressUpdate();
    }

    /*package-private*/ void removeActivityListener(PlayerEventListener listener) {
        if (activityListener == listener) {
            activityListener = null;
        }
    }

    private void updateMetadata() {
        if (fragmentListener != null && currentInfo != null && !popupPlayerSelected()) {
            fragmentListener.onMetadataUpdate(currentInfo);
        }
        if (activityListener != null && currentInfo != null) {
            activityListener.onMetadataUpdate(currentInfo);
        }
    }

    private void updatePlayback() {
        if (fragmentListener != null && simpleExoPlayer != null && playQueue != null && !popupPlayerSelected()) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void updateProgress(int currentProgress, int duration, int bufferPercent) {
        if (fragmentListener != null && !popupPlayerSelected()) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    private void stopActivityBinding() {
        if (fragmentListener != null) {
            fragmentListener.onServiceStopped();
            fragmentListener = null;
        }
        if (activityListener != null) {
            activityListener.onServiceStopped();
            activityListener = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Getters / setters
    ///////////////////////////////////////////////////////////////////////////

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public TextView getChannelTextView() {
        return channelTextView;
    }

    public TextView getVolumeTextView() {
        return volumeTextView;
    }

    public TextView getBrightnessTextView() {
        return brightnessTextView;
    }

    public ImageButton getRepeatButton() {
        return repeatButton;
    }

    @SuppressWarnings("WeakerAccess")
    public TextView getResizingIndicator() {
        return resizingIndicator;
    }

    public Bitmap getCachedImage() {
        return cachedImage;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }

    public WindowManager.LayoutParams getWindowLayoutParams() {
        return windowLayoutParams;
    }

    public float getScreenWidth() {
        return screenWidth;
    }

    public float getScreenHeight() {
        return screenHeight;
    }

    public float getPopupWidth() {
        return popupWidth;
    }

    public float getPopupHeight() {
        return popupHeight;
    }

    public void setPopupWidth(float width) {
        popupWidth = width;
    }

    public void setPopupHeight(float height) {
        popupHeight = height;
    }
}