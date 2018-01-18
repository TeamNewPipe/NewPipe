package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
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
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.schabi.newpipe.player.MainPlayerService.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.isUsingOldPlayer;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

@SuppressWarnings({"unused", "WeakerAccess"})
public class VideoPlayerImpl extends VideoPlayer {
    private TextView titleTextView;
    private TextView channelTextView;
    private TextView volumeTextView;
    private TextView brightnessTextView;
    private TextView resizingIndicator;
    private ImageButton queueButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private ImageButton screenRotationButton;
    private Space spaceBeforeFullscreenButton;

    private ImageButton playPauseButton;
    private ImageButton playPreviousButton;
    private ImageButton playNextButton;

    private RelativeLayout queueLayout;
    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private boolean queueVisible;
    public boolean audioOnly = false;
    private boolean isAudioPlayerSelected = false;
    public boolean isPopupPlayerSelected = false;
    private boolean isFullscreen = false;
    private boolean shouldUpdateOnProgress;

    private ImageButton moreOptionsButton;
    public int moreOptionsPopupMenuGroupId = 89;
    public PopupMenu moreOptionsPopupMenu;

    Bitmap cachedImage;

    private MainPlayerService service;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;

    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;

    @Override
    public void handleIntent(Intent intent) {
        if(intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) == null) return;

        selectAudioPlayer(intent.getBooleanExtra(BasePlayer.AUDIO_ONLY, false));
        audioOnly = audioPlayerSelected();
        // We need to setup audioOnly before super()
        super.handleIntent(intent);

        service.resetNotification();
        if (service.bigNotRemoteView != null) service.bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        if (service.notRemoteView != null) service.notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        service.startForeground(NOTIFICATION_ID, service.notBuilder.build());
        setupElementsVisibility();

        if(!audioPlayerSelected())
            getRootView().setVisibility(View.VISIBLE);
        else
            service.removeViewFromParent();
    }

    VideoPlayerImpl(final MainPlayerService service) {
        super("VideoPlayerImpl", service);
        this.service = service;
        this.shouldUpdateOnProgress = true;
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
        this.moreOptionsPopupMenu = new PopupMenu(context, moreOptionsButton);
        this.moreOptionsPopupMenu.getMenuInflater().inflate(R.menu.menu_videooptions, moreOptionsPopupMenu.getMenu());
        this.resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
        this.spaceBeforeFullscreenButton = rootView.findViewById(R.id.spaceBeforeFullscreenButton);

        getFullScreenButton().setOnClickListener(v -> onFullScreenButtonClicked());
        titleTextView.setSelected(true);
        channelTextView.setSelected(true);

        getRootView().setKeepScreenOn(true);
    }

    public void setupElementsVisibility() {
        if (popupPlayerSelected()) {
            getFullScreenButton().setVisibility(View.VISIBLE);
            screenRotationButton.setVisibility(View.GONE);
            getRootView().findViewById(R.id.titleAndChannel).setVisibility(View.GONE);
            getQualityTextView().setVisibility(View.VISIBLE);
            spaceBeforeFullscreenButton.setVisibility(View.VISIBLE);
        } else {
            getFullScreenButton().setVisibility(View.GONE);
            screenRotationButton.setVisibility(View.VISIBLE);
            getRootView().findViewById(R.id.titleAndChannel).setVisibility(View.VISIBLE);
            getQualityTextView().setVisibility(isInFullscreen()? View.VISIBLE : View.INVISIBLE);
            spaceBeforeFullscreenButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void initListeners() {
        super.initListeners();

        PlayerGestureListener listener = new PlayerGestureListener(this, service);
        service.gestureDetector = new GestureDetector(context, listener);
        service.gestureDetector.setIsLongpressEnabled(true);
        getRootView().setOnTouchListener(listener);

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
        if(getRootView().getParent() == null || !(getRootView().getParent() instanceof ViewGroup)) return null;

        ViewGroup parent = (ViewGroup) getRootView().getParent();
        return (Activity) parent.getContext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onRepeatModeChanged(int i) {
        super.onRepeatModeChanged(i);
        setRepeatModeRemote(service.notRemoteView, i);
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

        if(fragmentListener != null && !popupPlayerSelected())
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

        if (!shouldUpdateOnProgress || getCurrentState() == BasePlayer.STATE_COMPLETED || getCurrentState() == BasePlayer.STATE_PAUSED || getPlayQueue() == null) return;
        service.resetNotification();
        if (service.bigNotRemoteView != null) {
            service.bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            service.bigNotRemoteView.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration));
        }
        if (service.notRemoteView != null) {
            service.notRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
        }
        service.updateNotification(-1);
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        if(!audioOnly)
            return super.sourceOf(item, info);
        else {
            final int index = ListHelper.getDefaultAudioFormat(context, info.audio_streams);
            if (index < 0 || index >= info.audio_streams.size()) return null;

            final AudioStream audio = info.audio_streams.get(index);
            return buildMediaSource(audio.getUrl(), MediaFormat.getSuffixById(audio.format));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player Overrides
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onFullScreenButtonClicked() {
        if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");

        if(popupPlayerSelected()) {
            setRecovery();
            getPlayer().setPlayWhenReady(false);
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
                intent.putExtra(VideoPlayer.STARTED_FROM_NEWPIPE, isStartedFromNewPipe());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
                intent.putExtra(Constants.KEY_URL, getVideoUrl());
                intent.putExtra(Constants.KEY_TITLE, getVideoTitle());
                intent.putExtra(VideoDetailFragment.AUTO_PLAY, PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.autoplay_through_intent_key), false));
            } else {
                intent = new Intent(service, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                        .putExtra(PlayVideoActivity.STREAM_URL, getSelectedVideoStream().getUrl())
                        .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                        .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
            }
            context.startActivity(intent);
        }
        else {
            if(fragmentListener == null) return;

            playerInFullscreenNow(!isInFullscreen());
            getQualityTextView().setVisibility(isInFullscreen()? View.VISIBLE : View.GONE);
            fragmentListener.onFullScreenButtonClicked(isInFullscreen());
        }

    }

    public void onPlayBackgroundButtonClicked() {
        if (DEBUG) Log.d(TAG, "onPlayBackgroundButtonClicked() called");
        if (getPlayer() == null) return;

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
        if(playQueue.getIndex() < 0) return;

        queueVisible = true;

        buildQueue();
        updatePlaybackButtons();

        getControlsRoot().setVisibility(View.INVISIBLE);
        queueLayout.setVisibility(View.VISIBLE);

        itemsList.scrollToPosition(playQueue.getIndex());

        if(playQueue.getStreams().size() > 4 && !isInFullscreen())
            onFullScreenButtonClicked();
    }

    private void onQueueClosed() {
        queueLayout.setVisibility(View.GONE);
        queueVisible = false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");
        buildMoreOptionsMenu();

        try {
            Field[] fields = moreOptionsPopupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(moreOptionsPopupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper
                            .getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod(
                            "setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        moreOptionsPopupMenu.show();
        isSomePopupMenuVisible = true;
        showControls(300);
    }

    private void onScreenRotationClicked() {
        if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
        service.toggleOrientation();
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
        if (isPlaying()) hideControls(300, 0);
    }

    @Override
    protected int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
        return ListHelper.getDefaultResolutionIndex(context, sortedVideos);
    }

    @Override
    protected int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                             final String playbackQuality) {
        return ListHelper.getDefaultResolutionIndex(context, sortedVideos, playbackQuality);
    }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

    private void animatePlayButtons(final boolean show, final int duration) {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if(playQueue.getIndex() > 0)
            animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        if(playQueue.getIndex() + 1 < playQueue.getStreams().size())
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
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, new Runnable() {
            @Override
            public void run() {
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
                animatePlayButtons(true, 200);
            }
        });
        checkLandscape();
        getRootView().setKeepScreenOn(true);
        getSurfaceView().setVisibility(View.VISIBLE);
        service.lockManager.acquireWifiAndCpu();
        service.resetNotification();
        service.setControlsOpacity(255);
        service.updateNotification(R.drawable.ic_pause_white);
        service.startForeground(NOTIFICATION_ID, service.notBuilder.build());
    }

    @Override
    public void onPaused() {
        super.onPaused();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
            animatePlayButtons(true, 200);
        });
        getRootView().setKeepScreenOn(false);
        service.lockManager.releaseWifiAndCpu();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        if(!videoPlayerSelected()) {
            service.stopForeground(false);
        } else {
            service.stopForeground(true);
            service.notificationManager.cancel(NOTIFICATION_ID);
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
        service.lockManager.releaseWifiAndCpu();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        if(audioPlayerSelected()) {
            service.stopForeground(false);
        } else {
            service.stopForeground(true);
            service.notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void shutdown() {
        if (DEBUG) Log.d(TAG, "Shutting down...");
        // Override it because we don't want playerImpl destroyed
    }

    @Override
    public void destroy() {
        super.destroy();
        cachedImage = null;
        setRootView(null);
        service.stopForeground(true);
        service.notificationManager.cancel(NOTIFICATION_ID);
        stopActivityBinding();
    }

    @Override
    public void initThumbnail(final String url) {
        service.resetNotification();
        if (service.notRemoteView != null) service.notRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        if (service.bigNotRemoteView != null) service.bigNotRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        service. updateNotification(-1);
        super.initThumbnail(url);
    }

    @Override
    public void onThumbnailReceived(Bitmap thumbnail) {
        super.onThumbnailReceived(thumbnail);
        if (thumbnail != null) {
            cachedImage = thumbnail;
            // rebuild notification here since remote view does not release bitmaps, causing memory leaks
            service.notBuilder = service.createNotification();

            if (service.notRemoteView != null) service.notRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);
            if (service.bigNotRemoteView != null) service.bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);

            service.updateNotification(-1);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void setupBroadcastReceiver(IntentFilter intentFilter) {
        super.setupBroadcastReceiver(intentFilter);
        if (DEBUG) Log.d(TAG, "setupBroadcastReceiver() called with: intentFilter = [" + intentFilter + "]");
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
        if (intent == null || intent.getAction() == null) return;
        if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
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
                if(backgroundPlaybackEnabledInSettings() && !audioPlayerSelected() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                shouldUpdateOnProgress = false;
                // Interrupt playback only when screen turns off with video working
                Log.d(TAG, "onBroadcastReceived: "+backgroundPlaybackEnabledInSettings());
                Log.d(TAG, "onBroadcastReceived: "+!audioPlayerSelected());
                Log.d(TAG, "onBroadcastReceived: "+(getPlayer() != null));
                Log.d(TAG, "onBroadcastReceived: "+(isPlaying() || getPlayer().isLoading()));

                if(backgroundPlaybackEnabledInSettings() && !audioPlayerSelected() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(false);
                break;
        }
        service.resetNotification();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public boolean backgroundPlaybackEnabledInSettings() {
        return service.defaultPreferences.getBoolean(service.getString(R.string.continue_in_background_key), false);
    }

    public void selectAudioPlayer(boolean background) {
        isAudioPlayerSelected = background;
    }

    public boolean audioPlayerSelected() {
        return isAudioPlayerSelected;
    }

    public boolean videoPlayerSelected() {
        return !popupPlayerSelected() && !audioPlayerSelected();
    }

    public boolean popupPlayerSelected() {
        return isPopupPlayerSelected;
    }


    public void playerInFullscreenNow(boolean fullscreen) {
        isFullscreen = fullscreen;
    }

    public boolean isInFullscreen() {
        return isFullscreen;
    }

    @Override
    public void showControlsThenHide() {
        if (queueVisible) return;

        showOrHideButtons();
        super.showControlsThenHide();
    }

    @Override
    public void showControls(long duration) {
        if (queueVisible) return;
        showOrHideButtons();
        super.showControls(duration);
    }

    @Override
    public void hideControls(final long duration, long delay) {
        if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
        showOrHideButtons();

        getControlsVisibilityHandler().removeCallbacksAndMessages(null);
        getControlsVisibilityHandler().postDelayed(() ->
                        animateView(getControlsRoot(), false, duration, 0, () -> {
                            if(getRootView() == null || getRootView().getContext() == null || !isInFullscreen()) return;

                            Activity parent = getParentActivity();
                            if(parent == null) return;

                            Window window = parent.getWindow();

                            if (android.os.Build.VERSION.SDK_INT >= 16) {
                                int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                                window.getDecorView().setSystemUiVisibility(visibility);
                            }
                            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        })
                , delay);
    }
    private  void showOrHideButtons(){
        if(playQueue == null) return;

        if(playQueue.getIndex() == 0)
            playPreviousButton.setVisibility(View.INVISIBLE);
        else
            playPreviousButton.setVisibility(View.VISIBLE);

        if(playQueue.getIndex() + 1 == playQueue.getStreams().size())
            playNextButton.setVisibility(View.INVISIBLE);
        else
            playNextButton.setVisibility(View.VISIBLE);

        if(playQueue.getStreams().size() <= 1 || popupPlayerSelected())
            queueButton.setVisibility(View.GONE);
        else
            queueButton.setVisibility(View.VISIBLE);
    }

    private void updatePlaybackButtons() {
        if (repeatButton == null || shuffleButton == null ||
                simpleExoPlayer == null || playQueue == null) return;

        service.setRepeatModeButton(repeatButton, getRepeatMode());
        service.setShuffleButton(shuffleButton, playQueue.isShuffled());
    }

    public void checkLandscape() {
        Activity parent = getParentActivity();
        if(parent != null && service.isLandScape() && !isInFullscreen() && getCurrentState() != STATE_COMPLETED && !isAudioPlayerSelected)
            onFullScreenButtonClicked();
    }

    private void buildMoreOptionsMenu() {
        if (moreOptionsPopupMenu == null) return;
        moreOptionsPopupMenu.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.toggleOrientation:
                    onScreenRotationClicked();
                    break;
                case R.id.switchPopup:
                    onFullScreenButtonClicked();
                    break;
                case R.id.switchBackground:
                    onPlayBackgroundButtonClicked();
                    break;
            }
            return false;
        });
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
        if(playQueue == null || audioOnly == !video || audioPlayerSelected()) return;

        boolean shouldStartPlaying = true;
        if(getPlayer() != null)
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
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
        };
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
                if (index != -1) playQueue.remove(index);
            }

            @Override
            public void onStartDrag(PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
            }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Popup utils
    //////////////////////////////////////////////////////////////////////////*/

    public void checkPositionBounds() {
        if (service.windowLayoutParams.x > service.screenWidth - service.windowLayoutParams.width)
            service.windowLayoutParams.x = (int) (service.screenWidth - service.windowLayoutParams.width);
        if (service.windowLayoutParams.x < 0) service.windowLayoutParams.x = 0;
        if (service.windowLayoutParams.y > service.screenHeight - service.windowLayoutParams.height)
            service.windowLayoutParams.y = (int) (service.screenHeight - service.windowLayoutParams.height);
        if (service.windowLayoutParams.y < 0) service.windowLayoutParams.y = 0;
    }

    public void savePositionAndSize() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, service.windowLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, service.windowLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, service.windowLayoutParams.width).apply();
    }

    float getMinimumVideoHeight(float width) {
        //if (DEBUG) Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], returned: " + height);
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    public void updateScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        service.windowManager.getDefaultDisplay().getMetrics(metrics);

        service.screenWidth = metrics.widthPixels;
        service.screenHeight = metrics.heightPixels;
        if (DEBUG) Log.d(TAG, "updateScreenSize() called > screenWidth = " + service.screenWidth + ", screenHeight = " + service.screenHeight);

        service.popupWidth = service.getResources().getDimension(R.dimen.popup_default_width);
        service.popupHeight = getMinimumVideoHeight(service.popupWidth);

        minimumWidth = service.getResources().getDimension(R.dimen.popup_minimum_width);
        minimumHeight = getMinimumVideoHeight(minimumWidth);

        maximumWidth = service.screenWidth;
        maximumHeight = service.screenHeight;
    }

    public void updatePopupSize(int width, int height) {
        if (DEBUG) Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

        width = (int) (width > maximumWidth ? maximumWidth : width < minimumWidth ? minimumWidth : width);

        if (height == -1) height = (int) getMinimumVideoHeight(width);
        else height = (int) (height > maximumHeight ? maximumHeight : height < minimumHeight ? minimumHeight : height);

        service.windowLayoutParams.width = width;
        service.windowLayoutParams.height = height;
        service.popupWidth = width;
        service.popupHeight = height;

        if (DEBUG) Log.d(TAG, "updatePopupSize() updated values:  width = [" + width + "], height = [" + height + "]");
        service.windowManager.updateViewLayout(getRootView(), service.windowLayoutParams);
    }

    protected void setRepeatModeRemote(final RemoteViews remoteViews, final int repeatMode) {
        final String methodName = "setImageResource";

        if (remoteViews == null) return;

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
        updatePlayback();
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
    // Getters
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
}