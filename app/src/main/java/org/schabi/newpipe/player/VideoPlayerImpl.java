/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * PopupVideoPlayer.java is part of NewPipe
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
import android.annotation.SuppressLint;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.AnticipateInterpolator;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nostra13.universalimageloader.core.assist.FailReason;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.*;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.*;

import java.util.List;

import static android.content.Context.WINDOW_SERVICE;
import static org.schabi.newpipe.player.MainPlayer.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.util.AnimationUtils.Type.SLIDE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.animateRotation;
import static org.schabi.newpipe.util.AnimationUtils.animateView;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;

/**
 * Unified UI for all players
 *
 * @author mauriciocolli
 */

public class VideoPlayerImpl extends VideoPlayer
        implements View.OnLayoutChangeListener,
        PlaybackParameterDialog.Callback,
        View.OnLongClickListener{
    private static final String TAG = ".VideoPlayerImpl";

    private final float MAX_GESTURE_LENGTH = 0.75f;

    private TextView titleTextView;
    private TextView channelTextView;
    private RelativeLayout volumeRelativeLayout;
    private ProgressBar volumeProgressBar;
    private ImageView volumeImageView;
    private RelativeLayout brightnessRelativeLayout;
    private ProgressBar brightnessProgressBar;
    private ImageView brightnessImageView;
    private TextView resizingIndicator;
    private ImageButton queueButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private ImageButton playWithKodi;
    private ImageButton openInBrowser;
    private ImageButton fullscreenButton;

    private ImageButton playPauseButton;
    private ImageButton playPreviousButton;
    private ImageButton playNextButton;

    private RelativeLayout queueLayout;
    private ImageButton itemsListCloseButton;
    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private boolean queueVisible;
    private MainPlayer.PlayerType playerType = MainPlayer.PlayerType.VIDEO;

    private ImageButton moreOptionsButton;
    private ImageButton shareButton;

    private View primaryControls;
    private View secondaryControls;

    private int maxGestureLength;

    private boolean audioOnly = false;
    private boolean isFullscreen = false;
    boolean shouldUpdateOnProgress;

    private MainPlayer service;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;
    private GestureDetector gestureDetector;
    private SharedPreferences defaultPreferences;
    @NonNull
    final private AudioPlaybackResolver resolver;

    private int cachedDuration;
    private String cachedDurationString;

    // Popup
    private WindowManager.LayoutParams popupLayoutParams;
    public WindowManager windowManager;

    private View closingOverlayView;
    private View closeOverlayView;
    private FloatingActionButton closeOverlayButton;

    public boolean isPopupClosing = false;

    static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    static final String POPUP_SAVED_X = "popup_saved_x";
    static final String POPUP_SAVED_Y = "popup_saved_y";
    private static final int MINIMUM_SHOW_EXTRA_WIDTH_DP = 300;
    private static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    private static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;
    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;
    // Popup end


    @Override
    public void handleIntent(Intent intent) {
        if (intent.getStringExtra(VideoPlayer.PLAY_QUEUE_KEY) == null) return;

        MainPlayer.PlayerType oldPlayerType = playerType;
        choosePlayerTypeFromIntent(intent);
        audioOnly = audioPlayerSelected();

        // We need to setup audioOnly before super(), see "sourceOf"
        super.handleIntent(intent);

        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            setRecovery();
            reload();
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
            initPopupCloseOverlay();
        } else {
            getRootView().setVisibility(View.VISIBLE);
            initVideoPlayer();
        }

        onPlay();
    }

    VideoPlayerImpl(final MainPlayer service) {
        super("MainPlayer" + TAG, service);
        this.service = service;
        this.shouldUpdateOnProgress = true;
        this.windowManager = (WindowManager) service.getSystemService(WINDOW_SERVICE);
        this.defaultPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        this.resolver = new AudioPlaybackResolver(context, dataSource);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void initViews(View rootView) {
        super.initViews(rootView);
        this.titleTextView = rootView.findViewById(R.id.titleTextView);
        this.channelTextView = rootView.findViewById(R.id.channelTextView);
        this.volumeRelativeLayout = rootView.findViewById(R.id.volumeRelativeLayout);
        this.volumeProgressBar = rootView.findViewById(R.id.volumeProgressBar);
        this.volumeImageView = rootView.findViewById(R.id.volumeImageView);
        this.brightnessRelativeLayout = rootView.findViewById(R.id.brightnessRelativeLayout);
        this.brightnessProgressBar = rootView.findViewById(R.id.brightnessProgressBar);
        this.brightnessImageView = rootView.findViewById(R.id.brightnessImageView);
        this.resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
        this.queueButton = rootView.findViewById(R.id.queueButton);
        this.repeatButton = rootView.findViewById(R.id.repeatButton);
        this.shuffleButton = rootView.findViewById(R.id.shuffleButton);
        this.playWithKodi = rootView.findViewById(R.id.playWithKodi);
        this.openInBrowser = rootView.findViewById(R.id.openInBrowser);
        this.fullscreenButton = rootView.findViewById(R.id.fullScreenButton);

        this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
        this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
        this.playNextButton = rootView.findViewById(R.id.playNextButton);

        this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
        this.primaryControls = rootView.findViewById(R.id.primaryControls);
        this.secondaryControls = rootView.findViewById(R.id.secondaryControls);
        this.shareButton = rootView.findViewById(R.id.share);

        this.queueLayout = rootView.findViewById(R.id.playQueuePanel);
        this.itemsListCloseButton = rootView.findViewById(R.id.playQueueClose);
        this.itemsList = rootView.findViewById(R.id.playQueue);

        closingOverlayView = rootView.findViewById(R.id.closingOverlay);

        titleTextView.setSelected(true);
        channelTextView.setSelected(true);
    }

    @Override
    protected void setupSubtitleView(@NonNull SubtitleView view,
                                     final float captionScale,
                                     @NonNull final CaptionStyleCompat captionStyle) {
        if (popupPlayerSelected()) {
            float captionRatio = (captionScale - 1f) / 5f + 1f;
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        } else {
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            final float captionRatioInverse = 20f + 4f * (1f - captionScale);
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) minimumLength / captionRatioInverse);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        }
    }

    private void setupElementsVisibility() {
        if (popupPlayerSelected()) {
            fullscreenButton.setVisibility(View.VISIBLE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.GONE);
            queueButton.setVisibility(View.GONE);
            moreOptionsButton.setVisibility(View.GONE);
            getTopControlsRoot().setOrientation(LinearLayout.HORIZONTAL);
            primaryControls.getLayoutParams().width = LinearLayout.LayoutParams.WRAP_CONTENT;
            secondaryControls.setAlpha(1f);
            secondaryControls.setVisibility(View.VISIBLE);
            secondaryControls.setTranslationY(0);
            shareButton.setVisibility(View.GONE);
            playWithKodi.setVisibility(View.GONE);
            openInBrowser.setVisibility(View.GONE);
        } else {
            fullscreenButton.setVisibility(View.GONE);
            getRootView().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
            moreOptionsButton.setVisibility(View.VISIBLE);
            getTopControlsRoot().setOrientation(LinearLayout.VERTICAL);
            primaryControls.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;
            secondaryControls.setVisibility(View.GONE);
            moreOptionsButton.setImageDrawable(service.getResources().getDrawable(
                    R.drawable.ic_expand_more_white_24dp));
            shareButton.setVisibility(View.VISIBLE);
            playWithKodi.setVisibility(
                    defaultPreferences.getBoolean(service.getString(R.string.show_play_with_kodi_key), false) ? View.VISIBLE : View.GONE);
            openInBrowser.setVisibility(View.VISIBLE);
        }
        if (!isInFullscreen()) {
            titleTextView.setVisibility(View.GONE);
            channelTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setVisibility(View.VISIBLE);
            channelTextView.setVisibility(View.VISIBLE);
        }

        animateRotation(moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    @Override
    public void initListeners() {
        super.initListeners();

        PlayerGestureListener listener = new PlayerGestureListener(this, service);
        gestureDetector = new GestureDetector(context, listener);
        getRootView().setOnTouchListener(listener);

        queueButton.setOnClickListener(this);
        repeatButton.setOnClickListener(this);
        shuffleButton.setOnClickListener(this);

        playPauseButton.setOnClickListener(this);
        playPreviousButton.setOnClickListener(this);
        playNextButton.setOnClickListener(this);

        moreOptionsButton.setOnClickListener(this);
        moreOptionsButton.setOnLongClickListener(this);
        shareButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        playWithKodi.setOnClickListener(this);
        openInBrowser.setOnClickListener(this);

        getRootView().addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob) {
                // Use smaller value to be consistent between screen orientations
                // (and to make usage easier)
                int width = r - l, height = b - t;
                int min = Math.min(width, height);
                maxGestureLength = (int) (min * MAX_GESTURE_LENGTH);

                if (DEBUG) Log.d(TAG, "maxGestureLength = " + maxGestureLength);

                volumeProgressBar.setMax(maxGestureLength);
                brightnessProgressBar.setMax(maxGestureLength);

                setInitialGestureValues();
                queueLayout.getLayoutParams().height = min - queueLayout.getTop();
            }
        });
    }

    public AppCompatActivity getParentActivity() {
        // ! instanceof ViewGroup means that view was added via windowManager for Popup
        if (getRootView() == null || getRootView().getParent() == null || !(getRootView().getParent() instanceof ViewGroup))
            return null;

        ViewGroup parent = (ViewGroup) getRootView().getParent();
        return (AppCompatActivity) parent.getContext();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // View
    //////////////////////////////////////////////////////////////////////////*/

    private void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
        final int shuffleAlpha = shuffled ? 255 : 77;
        shuffleButton.setImageAlpha(shuffleAlpha);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Playback Parameters Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackParameterChanged(float playbackTempo, float playbackPitch, boolean playbackSkipSilence) {
        setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence);
    }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onRepeatModeChanged(int i) {
        super.onRepeatModeChanged(i);
        updatePlaybackButtons();
        updatePlayback();
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onShuffleClicked() {
        super.onShuffleClicked();
        updatePlaybackButtons();
        updatePlayback();
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        super.onPlayerError(error);

        if (fragmentListener != null)
            fragmentListener.onPlayerError(error);
    }

    protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        super.onMetadataChanged(tag);

        titleTextView.setText(tag.getMetadata().getName());
        channelTextView.setText(tag.getMetadata().getUploaderName());

        service.resetNotification();
        service.updateNotification(-1);
        updateMetadata();
    }

    @Override
    public void onPlaybackShutdown() {
        if (DEBUG) Log.d(TAG, "onPlaybackShutdown() called");
        // Override it because we don't want playerImpl destroyed
    }

    @Override
    public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent);

        updateProgress(currentProgress, duration, bufferPercent);

        if (!shouldUpdateOnProgress || getCurrentState() == BasePlayer.STATE_COMPLETED
                || getCurrentState() == BasePlayer.STATE_PAUSED || getPlayQueue() == null)
            return;

        if (service.getBigNotRemoteView() != null) {
            if (cachedDuration != duration) {
                cachedDuration = duration;
                cachedDurationString = getTimeString(duration);
            }
            service.getBigNotRemoteView()
                    .setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            service.getBigNotRemoteView()
                    .setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + cachedDurationString);
        }
        if (service.getNotRemoteView() != null) {
            service.getNotRemoteView()
                    .setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
        }
        service.updateNotification(-1);
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        // For LiveStream or video/popup players we can use super() method
        // but not for audio player
        if (!audioOnly)
            return super.sourceOf(item, info);
        else {
            return resolver.resolve(info);
        }
    }

    @Override
    public void onPlayPrevious() {
        super.onPlayPrevious();
        triggerProgressUpdate();
    }

    @Override
    public void onPlayNext() {
        super.onPlayNext();
        triggerProgressUpdate();
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void toggleFullscreen() {
        if (DEBUG) Log.d(TAG, "toggleFullscreen() called");
        if (simpleExoPlayer == null || getCurrentMetadata() == null) return;

        if (popupPlayerSelected()) {
            setRecovery();
            service.removeViewFromParent();
            Intent intent = NavigationHelper.getPlayerIntent(
                    service,
                    MainActivity.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackSkipSilence(),
                    null,
                    true
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Constants.KEY_SERVICE_ID, getCurrentMetadata().getMetadata().getServiceId());
            intent.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
            intent.putExtra(Constants.KEY_URL, getVideoUrl());
            intent.putExtra(Constants.KEY_TITLE, getVideoTitle());
            intent.putExtra(VideoDetailFragment.AUTO_PLAY, true);
            context.startActivity(intent);
        } else {
            if (fragmentListener == null) return;

            isFullscreen = !isFullscreen;
            setControlsWidth();
            fragmentListener.onFullscreenStateChanged(isInFullscreen());
            // When user presses back button in landscape mode and in fullscreen and uses ZOOM mode
            // a video can be larger than screen. Prevent it like this
            if (getAspectRatioFrameLayout().getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    && !isInFullscreen()
                    && service.isLandscape())
                onResizeClicked();
        }

        if (!isInFullscreen()) {
            titleTextView.setVisibility(View.GONE);
            channelTextView.setVisibility(View.GONE);
        } else {
            titleTextView.setVisibility(View.VISIBLE);
            channelTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == playPauseButton.getId()) {
            onPlayPause();

        } else if (v.getId() == playPreviousButton.getId()) {
            onPlayPrevious();

        } else if (v.getId() == playNextButton.getId()) {
            onPlayNext();

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

        } else if (v.getId() == shareButton.getId()) {
            onShareClicked();

        } else if (v.getId() == playWithKodi.getId()) {
            onPlayWithKodiClicked();

        } else if (v.getId() == openInBrowser.getId()) {
            onOpenInBrowserClicked();

        } else if (v.getId() == fullscreenButton.getId()) {
            toggleFullscreen();

        }

        if (getCurrentState() != STATE_COMPLETED) {
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            animateView(getControlsRoot(), true, DEFAULT_CONTROLS_DURATION, 0, () -> {
                if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                    if (v.getId() == playPauseButton.getId()) hideControls(0, 0);
                    else hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                }
            });
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v.getId() == moreOptionsButton.getId() && isInFullscreen()) {
            fragmentListener.onMoreOptionsLongClicked();
            hideControls(0, 0);
            hideSystemUIIfNeeded();
        }
        return true;
    }

    private void onQueueClicked() {
        queueVisible = true;

        buildQueue();
        updatePlaybackButtons();

        getControlsRoot().setVisibility(View.INVISIBLE);
        animateView(queueLayout, SLIDE_AND_ALPHA, /*visible=*/true,
                DEFAULT_CONTROLS_DURATION);

        itemsList.scrollToPosition(playQueue.getIndex());
    }

    private void onQueueClosed() {
        animateView(queueLayout, SLIDE_AND_ALPHA, /*visible=*/false,
                DEFAULT_CONTROLS_DURATION);
        queueVisible = false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");

        final boolean isMoreControlsVisible = secondaryControls.getVisibility() == View.VISIBLE;

        animateRotation(moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                isMoreControlsVisible ? 0 : 180);
        animateView(secondaryControls, SLIDE_AND_ALPHA, !isMoreControlsVisible,
                DEFAULT_CONTROLS_DURATION);
        showControls(DEFAULT_CONTROLS_DURATION);
    }

    private void onShareClicked() {
        // share video at the current time (youtube.com/watch?v=ID&t=SECONDS)
        ShareUtils.shareUrl(service,
                getVideoTitle(),
                getVideoUrl() + "&t=" + getPlaybackSeekBar().getProgress() / 1000);
    }

    private void onPlayWithKodiClicked() {
        if (getCurrentMetadata() == null) return;

        try {
            NavigationHelper.playWithKore(getParentActivity(), Uri.parse(
                    getCurrentMetadata().getMetadata().getUrl().replace("https", "http")));
        } catch (Exception e) {
            if (DEBUG) Log.i(TAG, "Failed to start kore", e);
            showInstallKoreDialog(getParentActivity());
        }
    }

    private void onOpenInBrowserClicked() {
        if (getCurrentMetadata() == null) return;

        ShareUtils.openUrlInBrowser(getParentActivity(), getCurrentMetadata().getMetadata().getOriginalUrl());
    }

    private static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {
                });
        builder.create().show();
    }

    @Override
    public void onPlaybackSpeedClicked() {
        if (videoPlayerSelected()) {
            // It hides status bar in fullscreen mode
            hideSystemUIIfNeeded();

            PlaybackParameterDialog
                    .newInstance(getPlaybackSpeed(), getPlaybackPitch(), getPlaybackSkipSilence(), this)
                    .show(getParentActivity().getSupportFragmentManager(), null);
        } else {
            super.onPlaybackSpeedClicked();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        if (wasPlaying()) showControlsThenHide();
    }

    @Override
    public void onDismiss(PopupMenu menu) {
        super.onDismiss(menu);
        if (isPlaying()) hideControls(DEFAULT_CONTROLS_DURATION, 0);
    }

    @Override
    public void onLayoutChange(final View view, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (popupPlayerSelected()) {
            float widthDp = Math.abs(right - left) / service.getResources().getDisplayMetrics().density;
            final int visibility = widthDp > MINIMUM_SHOW_EXTRA_WIDTH_DP ? View.VISIBLE : View.GONE;
            secondaryControls.setVisibility(visibility);
        } else if (videoPlayerSelected()
                && !isInFullscreen()
                && getAspectRatioFrameLayout().getMeasuredHeight() > service.getResources().getDisplayMetrics().heightPixels * 0.8) {
            // Resize mode is ZOOM probably. In this mode video will grow down and it will be weird.
            // So let's open it in fullscreen
            toggleFullscreen();
        }
    }

    @Override
    protected int nextResizeMode(int currentResizeMode) {
        final int newResizeMode;
        switch (currentResizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            default:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }

        storeResizeMode(newResizeMode);
        return newResizeMode;
    }

    private void storeResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
        defaultPreferences.edit()
                .putInt(service.getString(R.string.last_resize_mode), resizeMode)
                .apply();
    }

    @Override
    protected VideoPlaybackResolver.QualityResolver getQualityResolver() {
        return new VideoPlaybackResolver.QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(List<VideoStream> sortedVideos) {
                return videoPlayerSelected() ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(List<VideoStream> sortedVideos,
                                                  String playbackQuality) {
                return videoPlayerSelected() ? getResolutionIndex(context, sortedVideos, playbackQuality)
                        : getPopupResolutionIndex(context, sortedVideos, playbackQuality);
            }
        };
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
        getRootView().setKeepScreenOn(false);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        getRootView().setKeepScreenOn(true);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(true, 200);
        });

        updateWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);
        checkLandscape();
        getRootView().setKeepScreenOn(true);

        service.getLockManager().acquireWifiAndCpu();
        service.resetNotification();
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

        updateWindowFlags(IDLE_WINDOW_FLAGS);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);

        // Remove running notification when user don't want music (or video in popup) to be played in background
        if (!minimizeOnPopupEnabled() && !backgroundPlaybackEnabled() && videoPlayerSelected())
            service.stopForeground(true);

        getRootView().setKeepScreenOn(false);

        service.getLockManager().releaseWifiAndCpu();
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        animatePlayButtons(false, 100);
        getRootView().setKeepScreenOn(true);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_play_arrow_white);
    }


    @Override
    public void onCompleted() {
        animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
            playPauseButton.setImageResource(R.drawable.ic_replay_white);
            animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
        });
        getRootView().setKeepScreenOn(false);

        updateWindowFlags(IDLE_WINDOW_FLAGS);

        service.resetNotification();
        service.updateNotification(R.drawable.ic_replay_white);

        service.getLockManager().releaseWifiAndCpu();

        super.onCompleted();
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

        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
    }

    @Override
    public void onBroadcastReceived(Intent intent) {
        super.onBroadcastReceived(intent);
        if (intent == null || intent.getAction() == null)
            return;

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
                onPlayPause();
                break;
            case ACTION_REPEAT:
                onRepeatClicked();
                break;
            case Intent.ACTION_SCREEN_ON:
                shouldUpdateOnProgress = true;
                // Interrupt playback only when screen turns on and user is watching video in fragment
                if (backgroundPlaybackEnabled() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                shouldUpdateOnProgress = false;
                // Interrupt playback only when screen turns off with video working
                if (backgroundPlaybackEnabled() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                    useVideoSource(false);
                break;
        }
        service.resetNotification();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        super.onLoadingComplete(imageUri, view, loadedImage);
        // rebuild notification here since remote view does not release bitmaps,
        // causing memory leaks
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        super.onLoadingFailed(imageUri, view, failReason);
        service.resetNotification();
        service.updateNotification(-1);
    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {
        super.onLoadingCancelled(imageUri, view);
        service.resetNotification();
        service.updateNotification(-1);
    }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

    private void setInitialGestureValues() {
        if (getAudioReactor() != null) {
            final float currentVolumeNormalized = (float) getAudioReactor().getVolume() / getAudioReactor().getMaxVolume();
            volumeProgressBar.setProgress((int) (volumeProgressBar.getMax() * currentVolumeNormalized));
        }
    }

    private void choosePlayerTypeFromIntent(Intent intent) {
        // If you want to open popup from the app just include Constants.POPUP_ONLY into an extra
        if (intent.getIntExtra(PLAYER_TYPE, PLAYER_TYPE_VIDEO) == PLAYER_TYPE_AUDIO) {
            playerType = MainPlayer.PlayerType.AUDIO;
        } else if (intent.getIntExtra(PLAYER_TYPE, PLAYER_TYPE_VIDEO) == PLAYER_TYPE_POPUP) {
            playerType = MainPlayer.PlayerType.POPUP;
        } else {
            playerType = MainPlayer.PlayerType.VIDEO;
        }
    }

    public boolean backgroundPlaybackEnabled() {
        return PlayerHelper.getMinimizeOnExitAction(service) == MINIMIZE_ON_EXIT_MODE_BACKGROUND;
    }

    public boolean minimizeOnPopupEnabled() {
        return PlayerHelper.getMinimizeOnExitAction(service) == PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
    }

    public boolean audioPlayerSelected() {
        return playerType == MainPlayer.PlayerType.AUDIO;
    }

    public boolean videoPlayerSelected() {
        return playerType == MainPlayer.PlayerType.VIDEO;
    }

    public boolean popupPlayerSelected() {
        return playerType == MainPlayer.PlayerType.POPUP;
    }

    private int distanceFromCloseButton(MotionEvent popupMotionEvent) {
        final int closeOverlayButtonX = closeOverlayButton.getLeft() + closeOverlayButton.getWidth() / 2;
        final int closeOverlayButtonY = closeOverlayButton.getTop() + closeOverlayButton.getHeight() / 2;

        float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
        float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

        return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2) + Math.pow(closeOverlayButtonY - fingerY, 2));
    }

    private float getClosingRadius() {
        final int buttonRadius = closeOverlayButton.getWidth() / 2;
        // 20% wider than the button itself
        return buttonRadius * 1.2f;
    }

    public boolean isInsideClosingRadius(MotionEvent popupMotionEvent) {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
    }

    public boolean isInFullscreen() {
        return isFullscreen;
    }

    @Override
    public void showControlsThenHide() {
        if (queueVisible) return;

        showOrHideButtons();
        showSystemUIPartially();
        super.showControlsThenHide();
    }

    @Override
    public void showControls(long duration) {
        if (queueVisible) return;

        showOrHideButtons();
        showSystemUIPartially();
        super.showControls(duration);
    }

    @Override
    public void hideControls(final long duration, long delay) {
        if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");

        showOrHideButtons();

        getControlsVisibilityHandler().removeCallbacksAndMessages(null);
        getControlsVisibilityHandler().postDelayed(() ->
                        animateView(getControlsRoot(), false, duration, 0,
                                this::hideSystemUIIfNeeded),
                /*delayMillis=*/delay
        );
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

    private void showSystemUIPartially() {
        if (isInFullscreen()) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            getParentActivity().getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
    }

    @Override
    public void hideSystemUIIfNeeded() {
        if (fragmentListener != null)
            fragmentListener.hideSystemUIIfNeeded();
    }

    private void setControlsWidth() {
        Point size = new Point();
        // This method will give a correct size of a usable area of a window.
        // It doesn't include NavigationBar, notches, etc.
        getRootView().getDisplay().getSize(size);

        int width = isFullscreen ? size.x : ViewGroup.LayoutParams.MATCH_PARENT;
        primaryControls.getLayoutParams().width = width;
        primaryControls.requestLayout();
        secondaryControls.getLayoutParams().width = width;
        secondaryControls.requestLayout();
        getBottomControlsRoot().getLayoutParams().width = width;
        getBottomControlsRoot().requestLayout();
    }

    private void updatePlaybackButtons() {
        if (repeatButton == null || shuffleButton == null ||
                simpleExoPlayer == null || playQueue == null) return;

        setRepeatModeButton(repeatButton, getRepeatMode());
        setShuffleButton(shuffleButton, playQueue.isShuffled());
    }

    public void checkLandscape() {
        AppCompatActivity parent = getParentActivity();
        if (parent != null && service.isLandscape() != isInFullscreen()
                && getCurrentState() != STATE_COMPLETED && videoPlayerSelected())
            toggleFullscreen();
    }

    private void buildQueue() {
        itemsList.setAdapter(playQueueAdapter);
        itemsList.setClickable(true);
        itemsList.setLongClickable(true);

        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(getQueueScrollListener());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        playQueueAdapter.setSelectedListener(getOnSelectedListener());

        itemsListCloseButton.setOnClickListener(view -> onQueueClosed());
    }

    public void useVideoSource(boolean video) {
        // Return when: old value of audioOnly equals to the new value, audio player is selected,
        // video player is selected AND fragment is not shown
        if (playQueue == null
                || audioOnly == !video
                || audioPlayerSelected()
                || (video && videoPlayerSelected() && fragmentListener.isFragmentStopped()))
            return;

        audioOnly = !video;
        setRecovery();
        reload();
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
        return new PlayQueueItemTouchCallback() {
            @Override
            public void onMove(int sourceIndex, int targetIndex) {
                if (playQueue != null) playQueue.move(sourceIndex, targetIndex);
            }

            @Override
            public void onSwiped(int index) {
                if (index != -1) playQueue.remove(index);
            }
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
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");

        updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(service);
        final float defaultSize = service.getResources().getDimension(R.dimen.popup_default_width);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        popupWidth = popupRememberSizeAndPos ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_PHONE :
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        popupLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) getMinimumVideoHeight(popupWidth),
                layoutParamType,
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT);
        popupLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        popupLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        popupLayoutParams.x = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        popupLayoutParams.y = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        checkPopupPositionBounds();

        getLoadingPanel().setMinimumWidth(popupLayoutParams.width);
        getLoadingPanel().setMinimumHeight(popupLayoutParams.height);

        service.removeViewFromParent();
        windowManager.addView(service.getView(), popupLayoutParams);

        if (getAspectRatioFrameLayout().getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            onResizeClicked();
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) Log.d(TAG, "initPopupCloseOverlay() called");
        closeOverlayView = View.inflate(service, R.layout.player_popup_close_overlay, null);
        closeOverlayButton = closeOverlayView.findViewById(R.id.closeButton);

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_PHONE :
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        WindowManager.LayoutParams closeOverlayLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParamType,
                flags,
                PixelFormat.TRANSLUCENT);
        closeOverlayLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        closeOverlayLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        closeOverlayButton.setVisibility(View.GONE);
        windowManager.addView(closeOverlayView, closeOverlayLayoutParams);
    }

    private void initVideoPlayer() {
        service.getView().setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Popup utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * @see #checkPopupPositionBounds(float, float)
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean checkPopupPositionBounds() {
        return checkPopupPositionBounds(screenWidth, screenHeight);
    }

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary that goes from (0,0) to (boundaryWidth,
     * boundaryHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed and {@code true} is returned
     * to represent this change.
     *
     * @return if the popup was out of bounds and have been moved back to it
     */
    public boolean checkPopupPositionBounds(final float boundaryWidth, final float boundaryHeight) {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: boundaryWidth = ["
                    + boundaryWidth + "], boundaryHeight = [" + boundaryHeight + "]");
        }

        if (popupLayoutParams.x < 0) {
            popupLayoutParams.x = 0;
            return true;
        } else if (popupLayoutParams.x > boundaryWidth - popupLayoutParams.width) {
            popupLayoutParams.x = (int) (boundaryWidth - popupLayoutParams.width);
            return true;
        }

        if (popupLayoutParams.y < 0) {
            popupLayoutParams.y = 0;
            return true;
        } else if (popupLayoutParams.y > boundaryHeight - popupLayoutParams.height) {
            popupLayoutParams.y = (int) (boundaryHeight - popupLayoutParams.height);
            return true;
        }

        return false;
    }

    public void savePositionAndSize() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, popupLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, popupLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, popupLayoutParams.width).apply();
    }

    private float getMinimumVideoHeight(float width) {
        //if (DEBUG) Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], returned: " + height);
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
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

    public void updatePopupSize(int width, int height) {
        if (DEBUG) Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

        if (popupLayoutParams == null || windowManager == null || getParentActivity() != null || getRootView().getParent() == null)
            return;

        width = (int) (width > maximumWidth ? maximumWidth : width < minimumWidth ? minimumWidth : width);

        if (height == -1) height = (int) getMinimumVideoHeight(width);
        else height = (int) (height > maximumHeight ? maximumHeight : height < minimumHeight ? minimumHeight : height);

        popupLayoutParams.width = width;
        popupLayoutParams.height = height;
        popupWidth = width;
        popupHeight = height;

        if (DEBUG) Log.d(TAG, "updatePopupSize() updated values:  width = [" + width + "], height = [" + height + "]");
        windowManager.updateViewLayout(getRootView(), popupLayoutParams);
    }

    private void updateWindowFlags(final int flags) {
        if (popupLayoutParams == null || windowManager == null || getParentActivity() != null || getRootView().getParent() == null)
            return;

        popupLayoutParams.flags = flags;
        windowManager.updateViewLayout(getRootView(), popupLayoutParams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Misc
    //////////////////////////////////////////////////////////////////////////*/

    public void closePopup() {
        if (DEBUG) Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        if (isPopupClosing) return;
        isPopupClosing = true;

        savePlaybackState();
        windowManager.removeView(getRootView());

        animateOverlayAndFinishService();
    }

    private void animateOverlayAndFinishService() {
        final int targetTranslationY = (int) (closeOverlayButton.getRootView().getHeight() - closeOverlayButton.getY());

        closeOverlayButton.animate().setListener(null).cancel();
        closeOverlayButton.animate()
                .setInterpolator(new AnticipateInterpolator())
                .translationY(targetTranslationY)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        end();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        end();
                    }

                    private void end() {
                        windowManager.removeView(closeOverlayView);

                        service.onDestroy();
                    }
                }).start();
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
        if (fragmentListener != null && getCurrentMetadata() != null) {
            fragmentListener.onMetadataUpdate(getCurrentMetadata().getMetadata());
        }
        if (activityListener != null && getCurrentMetadata() != null) {
            activityListener.onMetadataUpdate(getCurrentMetadata().getMetadata());
        }
    }

    private void updatePlayback() {
        if (fragmentListener != null && simpleExoPlayer != null && playQueue != null) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void updateProgress(int currentProgress, int duration, int bufferPercent) {
        if (fragmentListener != null) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    void stopActivityBinding() {
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

    public RelativeLayout getVolumeRelativeLayout() {
        return volumeRelativeLayout;
    }

    public ProgressBar getVolumeProgressBar() {
        return volumeProgressBar;
    }

    public ImageView getVolumeImageView() {
        return volumeImageView;
    }

    public RelativeLayout getBrightnessRelativeLayout() {
        return brightnessRelativeLayout;
    }

    public ProgressBar getBrightnessProgressBar() {
        return brightnessProgressBar;
    }

    public ImageView getBrightnessImageView() {
        return brightnessImageView;
    }

    public ImageButton getPlayPauseButton() {
        return playPauseButton;
    }

    public int getMaxGestureLength() {
        return maxGestureLength;
    }

    public TextView getResizingIndicator() {
        return resizingIndicator;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }

    public WindowManager.LayoutParams getPopupLayoutParams() {
        return popupLayoutParams;
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

    public View getCloseOverlayButton() {
        return closeOverlayButton;
    }

    public View getCloseOverlayView() {
        return closeOverlayView;
    }

    public View getClosingOverlayView() {
        return closingOverlayView;
    }
}
