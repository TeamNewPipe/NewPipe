package org.schabi.newpipe.player.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.QueueItemMenuUtil.openPopupMenu;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimizeOnExitAction;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.ui.SubtitleView;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.info_list.StreamSegmentItem;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningNoteDialog;
import org.schabi.newpipe.learning.LearningNoteManager;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlaybackPresentationMode;
import org.schabi.newpipe.player.boost.VolumeBoostDialog;
import org.schabi.newpipe.player.equalizer.EqualizerDialog;
import org.schabi.newpipe.player.equalizer.EqualizerState;
import org.schabi.newpipe.player.video.VideoAdjustmentDialog;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener;
import org.schabi.newpipe.player.helper.ChannelSkipDialog;
import org.schabi.newpipe.player.helper.ChannelSkipPreferences;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerRotationMode;
import org.schabi.newpipe.player.helper.SleepTimer;
import org.schabi.newpipe.player.helper.SleepTimerDialog;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.EdgeToEdgeHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class MainPlayerUi extends VideoPlayerUi implements View.OnLayoutChangeListener {
    private final TabletAutoFullscreen tabletAutoFullscreen = new TabletAutoFullscreen();
    private static final String TAG = MainPlayerUi.class.getSimpleName();

    // see the Javadoc of calculateMaxEndScreenThumbnailHeight for information
    private static final int DETAIL_ROOT_MINIMUM_HEIGHT = 85; // dp
    private static final int DETAIL_TITLE_TEXT_SIZE_TV = 16; // sp
    private static final int DETAIL_TITLE_TEXT_SIZE_TABLET = 15; // sp

    private boolean isFullscreen = false;
    private final PlayerAmbientMode ambientMode;
    private boolean touchLocked;
    private FullscreenOrientationPolicy.VideoContentOrientation videoContentOrientation =
            FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN;
    @Nullable
    private String videoOrientationStreamKey;
    private boolean fragmentIsVisible = false;

    private ContentObserver settingsContentObserver;

    private PlayQueueAdapter playQueueAdapter;
    private StreamSegmentAdapter segmentAdapter;
    private boolean isQueueVisible = false;
    private boolean areSegmentsVisible = false;

    // fullscreen player
    private ItemTouchHelper itemTouchHelper;
    private final CompositeDisposable learningNoteDisposables = new CompositeDisposable();
    @Nullable
    private PlayerNoteButton noteButton;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor, setup, destroy

    public MainPlayerUi(@NonNull final Player player,
                        @NonNull final PlayerBinding playerBinding) {
        super(player, playerBinding);
        ambientMode = new PlayerAmbientMode(binding);
        binding.touchLockButton.setOnClickListener(view -> setTouchLocked(true));
        binding.touchUnlockButton.setOnClickListener(view -> {
            setTouchLocked(false);
            showControlsThenHide();
            binding.touchLockButton.requestFocus();
        });
    }

    /**
     * Preserve TV and tablet startup behavior. Phone startup requests are applied by the detail
     * fragment after attachment, with orientation determined by the video dimensions.
     */
    private void directlyOpenFullscreenIfNeeded() {
        if (DeviceUtils.isTv(context)
                && player.getPlaybackPresentationMode().rendersVideo()) {
            // TV cannot rotate, and replay can reuse an already-expanded bottom sheet.
            // Apply fullscreen for each video intent rather than relying on a sheet callback.
            setFullscreen(true);
            return;
        }
        if (PlayerHelper.isStartMainPlayerFullscreenEnabled(player.getService())
                && DeviceUtils.isTablet(player.getService())
                && PlayerHelper.globalScreenOrientationLocked(player.getService())) {
            player.getFragmentListener().ifPresent(
                    listener -> listener.onScreenRotationButtonClicked(true));
        }
    }

    @Override
    public void setupAfterIntent() {
        super.setupAfterIntent();

        initVideoPlayer();
        directlyOpenFullscreenIfNeeded();
        // A reused UI must retry attachment after the intent installs the new queue, so an
        // explicit startup fullscreen request cannot be consumed by the previous video.
        player.getFragmentListener().ifPresent(PlayerServiceEventListener::onViewCreated);
        // Replaying the current item can reuse the engine and its retained SurfaceView.
        restoreVideoSurfaceAfterLayoutTransition();
        if (shouldRequestPlaybackButtonFocus()) {
            binding.playPauseButton.requestFocus();
        }

        // Note: This is for automatically playing (when "Resume playback" is off), see #6179
        if (player.getPlayWhenReady()) {
            player.play();
        } else {
            player.pause();
        }
    }

    @Override
    BasePlayerGestureListener buildGestureListener() {
        return new MainPlayerGestureListener(this);
    }

    private androidx.appcompat.app.AlertDialog videoAdjustmentDialog;

    @Override
    protected void initListeners() {
        super.initListeners();

        binding.screenRotationButton.setOnClickListener(
                makeOnClickListener(this::toggleFullscreenWithOrientation));
        binding.queueButton.setOnClickListener(v -> onQueueClicked());
        binding.commentsButton.setOnClickListener(v -> onCommentsClicked());
        binding.segmentsButton.setOnClickListener(v -> onSegmentsClicked());
        binding.sleepTimerButton.setOnClickListener(v ->
                getParentActivity().ifPresent(activity -> SleepTimerDialog.show(activity, player)));
        binding.equalizerButton.setOnClickListener(v ->
                getParentActivity().ifPresent(activity -> EqualizerDialog.show(
                        activity, EqualizerDialog.forPlayer(player))));
        binding.volumeBoostButton.setOnClickListener(v ->
                getParentActivity().ifPresent(activity ->
                        VolumeBoostDialog.show(activity, player)));
        binding.videoAdjustmentsButton.setOnClickListener(v ->
                getParentActivity().ifPresent(activity -> {
                    if (videoAdjustmentDialog == null || !videoAdjustmentDialog.isShowing()) {
                        videoAdjustmentDialog = VideoAdjustmentDialog.show(
                                activity, player.getVideoAdjustments());
                    }
                }));
        binding.listenModeButton.setOnClickListener(v -> {
            final boolean listening = player.getPlaybackPresentationMode()
                    == PlaybackPresentationMode.LISTEN_VISUALIZER;
            player.setPlaybackPresentationMode(listening
                    ? PlaybackPresentationMode.VIDEO
                    : PlaybackPresentationMode.LISTEN_VISUALIZER);
        });
        if (noteButton != null) {
            noteButton.close();
        }
        noteButton = new PlayerNoteButton(binding.learningNoteButton,
                () -> player.exoPlayerIsNull() ? 0L : player.getExoPlayer().getCurrentPosition(),
                this::showLearningNoteDialog, this::showVideoNotes);

        binding.addToPlaylistButton.setOnClickListener(v ->
                getParentActivity().map(FragmentActivity::getSupportFragmentManager)
                        .ifPresent(fragmentManager ->
                                PlaylistDialog.showForPlayQueue(player, fragmentManager)));

        settingsContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(final boolean selfChange) {
                setupScreenRotationButton();
            }
        };
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);

        binding.getRoot().addOnLayoutChangeListener(this);

        binding.moreOptionsButton.setOnLongClickListener(v -> {
            player.getFragmentListener()
                    .ifPresent(PlayerServiceEventListener::onMoreOptionsLongClicked);
            hideControls(0, 0);
            hideSystemUIIfNeeded();
            return true;
        });
    }


    @Override
    public void onEqualizerStateChanged(@NonNull final EqualizerState state,
                                        final boolean operational) {
        binding.equalizerButton.setActivated(state.isEnabled());
        binding.equalizerButton.setAlpha(state.isEnabled() && operational ? 1.0f : 0.7f);
    }

    @Override
    public void onVolumeBoostChanged(final int gainMb, final boolean operational) {
        final boolean enabled = gainMb > 0;
        binding.volumeBoostButton.setActivated(enabled);
        binding.volumeBoostButton.setAlpha(enabled && operational ? 1.0f : 0.7f);
        binding.volumeBoostButton.setContentDescription(enabled
                ? context.getString(R.string.volume_boost_active, gainMb / 100)
                : context.getString(R.string.volume_boost));
    }

    @Override
    public void showSponsorBlockSkipButton(@NonNull final String label,
                                           @NonNull final Runnable onClick) {
        binding.sponsorBlockSkipButton.setText(label);
        binding.sponsorBlockSkipButton.setOnClickListener(v -> onClick.run());
        binding.sponsorBlockSkipButton.setVisibility(View.VISIBLE);
        binding.sponsorBlockSkipButton.bringToFront();
    }

    @Override
    public void hideSponsorBlockSkipButton() {
        binding.sponsorBlockSkipButton.setVisibility(View.GONE);
        binding.sponsorBlockSkipButton.setOnClickListener(null);
    }

    @Override
    public void updateSponsorBlockSeekBarMarkers(
            @NonNull final List<SponsorBlockSegment> segments,
            final long durationMillis) {
        binding.sponsorBlockSeekBarMarkers.setPadding(
                binding.playbackSeekBar.getPaddingLeft(),
                0,
                binding.playbackSeekBar.getPaddingRight(),
                0);
        binding.sponsorBlockSeekBarMarkers.setSegments(segments, durationMillis);
    }

    @Override
    public void clearSponsorBlockSeekBarMarkers() {
        binding.sponsorBlockSeekBarMarkers.clearSegments();
    }

    @Override
    protected void deinitListeners() {
        super.deinitListeners();

        binding.queueButton.setOnClickListener(null);
        binding.commentsButton.setOnClickListener(null);
        binding.segmentsButton.setOnClickListener(null);
        binding.sleepTimerButton.setOnClickListener(null);
        binding.equalizerButton.setOnClickListener(null);
        binding.volumeBoostButton.setOnClickListener(null);
        binding.videoAdjustmentsButton.setOnClickListener(null);
        binding.listenModeButton.setOnClickListener(null);
        binding.addToPlaylistButton.setOnClickListener(null);
        if (noteButton != null) {
            noteButton.close();
            noteButton = null;
        }
        hideSponsorBlockSkipButton();
        clearSponsorBlockSeekBarMarkers();

        context.getContentResolver().unregisterContentObserver(settingsContentObserver);

        binding.getRoot().removeOnLayoutChangeListener(this);
    }

    @Override
    public void initPlayback() {
        super.initPlayback();

        if (playQueueAdapter != null) {
            playQueueAdapter.dispose();
        }
        playQueueAdapter = new PlayQueueAdapter(context,
                Objects.requireNonNull(player.getPlayQueue()));
        segmentAdapter = new StreamSegmentAdapter(getStreamSegmentListener());

        // Make sure video and text tracks are enabled if the user is in the app, in the case user
        // switched from background player to main player
        player.useVideoAndSubtitles(fragmentIsVisible);
        if (DeviceUtils.isTv(context)) {
            directlyOpenFullscreenIfNeeded();
        }
    }

    @Override
    public void removeViewFromParent() {
        // view was added to fragment
        final ViewParent parent = binding.getRoot().getParent();
        if (parent instanceof ViewGroup) {
            binding.videoSurfaceContainer.prepareForReparent();
            ((ViewGroup) parent).removeView(binding.getRoot());
        }
    }

    @Override
    public void destroy() {
        ambientMode.clear();
        binding.getRoot().configure(false, 60_000);
        getParentActivity().ifPresent(FullscreenCommentsSidebar::hide);
        if (videoAdjustmentDialog != null) {
            videoAdjustmentDialog.dismiss();
            videoAdjustmentDialog = null;
        }
        setTouchLocked(false);
        super.destroy();

        // Exit from fullscreen when user closes the player via notification
        if (isFullscreen) {
            toggleFullscreen();
        }

        removeViewFromParent();
    }

    @Override
    public void destroyPlayer() {
        binding.getRoot().configure(false, 60_000);
        super.destroyPlayer();

        learningNoteDisposables.clear();

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    @Override
    public void smoothStopForImmediateReusing() {
        binding.getRoot().configure(false, 60_000);
        getParentActivity().ifPresent(FullscreenCommentsSidebar::hide);
        setTouchLocked(false);
        super.smoothStopForImmediateReusing();
        // Android TV will handle back button in case controls will be visible
        // (one more additional unneeded click while the player is hidden)
        hideControls(0, 0);
        closeItemsList();
    }

    private void initVideoPlayer() {
        // restore last resize mode
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(player));
        binding.getRoot().setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    @Override
    protected void setupElementsVisibility() {
        super.setupElementsVisibility();
        binding.touchLockButton.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);

        closeItemsList();
        showHideKodiButton();
        binding.fullScreenButton.setVisibility(View.GONE);
        setupScreenRotationButton();
        binding.resizeTextView.setVisibility(View.VISIBLE);
        binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
        binding.moreOptionsButton.setVisibility(View.VISIBLE);
        binding.topControls.setOrientation(LinearLayout.VERTICAL);
        binding.primaryControls.getLayoutParams().width = MATCH_PARENT;
        binding.secondaryControls.setVisibility(View.INVISIBLE);
        binding.moreOptionsButton.setImageDrawable(AppCompatResources.getDrawable(context,
                R.drawable.ic_expand_more));
        binding.share.setVisibility(View.VISIBLE);
        binding.openInBrowser.setVisibility(View.VISIBLE);
        binding.sleepTimerButton.setVisibility(View.VISIBLE);
        binding.equalizerButton.setVisibility(View.VISIBLE);
        binding.volumeBoostButton.setVisibility(View.VISIBLE);
        binding.videoAdjustmentsButton.setVisibility(View.VISIBLE);
        binding.equalizerButton.setActivated(player.getEqualizerState().isEnabled());
        onVolumeBoostChanged(player.getVolumeBoostGainMb(),
                player.isVolumeBoostOperational());
        binding.listenModeButton.setVisibility(View.VISIBLE);
        binding.audioVisualizer.setAudioProcessor(player.getVisualizerAudioProcessor());
        updateListenModeUi(player.getPlaybackPresentationMode());
        updateLearningNoteButtonVisibility(player.getCurrentStreamInfo().orElse(null));
        binding.switchMute.setVisibility(View.VISIBLE);
        binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        // Top controls have a large minHeight which is allows to drag the player
        // down in fullscreen mode (just larger area to make easy to locate by finger)
        binding.topControls.setClickable(true);
        binding.topControls.setFocusable(true);

        binding.metadataView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);

        // Reset workaround changes from popup player
        binding.audioTrackTextView.setMaxWidth(context.getResources().getDimensionPixelSize(
                R.dimen.player_audio_track_max_width));
        updateSleepTimerButton(player.getSleepTimerMode(),
                player.getSleepTimerRemainingMillis());
    }

    @Override
    public void onPlaybackPresentationModeChanged(
            @NonNull final PlaybackPresentationMode mode) {
        super.onPlaybackPresentationModeChanged(mode);
        updateListenModeUi(mode);
        updateStreamRelatedViews();
        if (mode.rendersVideo()) {
            setupVideoSurfaceIfNeeded();
        }
        updateAmbientMode();
        player.getFragmentListener().ifPresent(
                listener -> listener.onPlaybackPresentationModeChanged(mode));
    }

    private void updateListenModeUi(@NonNull final PlaybackPresentationMode mode) {
        final boolean listening = mode == PlaybackPresentationMode.LISTEN_VISUALIZER;
        binding.listenModeButton.setActivated(listening);
        org.schabi.newpipe.themes.ThemePlayerView.updatePresentation(binding, listening);
        if (listening) {
            binding.endScreen.setVisibility(View.GONE);
        }
        binding.listenModeButton.setContentDescription(context.getString(listening
                ? R.string.exit_listen_mode : R.string.enter_listen_mode));
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        setupElementsSize(
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_min_width),
                resources.getDimensionPixelSize(R.dimen.player_main_top_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_icon_buttons_padding)
        );
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            // Close it because when changing orientation from portrait
            // (in fullscreen mode) the size of queue layout can be larger than the screen size
            closeItemsList();
        } else if (ACTION_PLAY_PAUSE.equals(intent.getAction())) {
            // Ensure that we have audio-only stream playing when a user
            // started to play from notification's play button from outside of the app
            if (!fragmentIsVisible) {
                onFragmentStopped();
            }
        } else if (VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED.equals(intent.getAction())) {
            fragmentIsVisible = false;
            binding.getRoot().configure(false, 60_000);
            onFragmentStopped();
        } else if (VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED.equals(intent.getAction())) {
            // Restore video source when user returns to the fragment
            fragmentIsVisible = true;
            player.useVideoAndSubtitles(true);
            // The fragment's first resumed frame can precede this asynchronous broadcast.
            // Reconcile fullscreen after leaving temporary audio-only playback, before hiding
            // system bars or restoring the surface with the previous fullscreen dimensions.
            player.getFragmentListener().ifPresent(
                    PlayerServiceEventListener::onVideoPlaybackResumed);
            restoreVideoSurfaceAfterResume();

            // When a user returns from background, the system UI will always be shown even if
            // controls are invisible: hide it in that case
            if (!isControlsVisible()) {
                hideSystemUIIfNeeded();
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && fragmentIsVisible) {
            restoreVideoSurfaceAfterResume();
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment binding
    //////////////////////////////////////////////////////////////////////////*/
    //region Fragment binding

    @Override
    public void onFragmentListenerSet() {
        super.onFragmentListenerSet();
        fragmentIsVisible = true;
        // Apply window insets because Android will not do it when orientation changes
        // from landscape to portrait
        if (!isFullscreen) {
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        }
        binding.itemsListPanel.setPadding(0, 0, 0, 0);
        player.getFragmentListener().ifPresent(PlayerServiceEventListener::onViewCreated);
    }

    /**
     * This will be called when a user goes to another app/activity, turns off a screen.
     * We don't want to interrupt playback and don't want to see notification so
     * next lines of code will enable audio-only playback only if needed
     */
    private void onFragmentStopped() {
        if (player.isPlaying() || player.isLoading()) {
            switch (getMinimizeOnExitAction(context)) {
                case MINIMIZE_ON_EXIT_MODE_BACKGROUND:
                    player.useVideoAndSubtitlesForTemporaryBackground();
                    break;
                case MINIMIZE_ON_EXIT_MODE_POPUP:
                    getParentActivity().ifPresent(activity -> {
                        player.setRecovery();
                        NavigationHelper.playOnPopupPlayer(activity, player.getPlayQueue(), true);
                    });
                    break;
                case MINIMIZE_ON_EXIT_MODE_NONE: default:
                    player.pause();
                    break;
            }
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states

    @Override
    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent);
        updateScreenDimming();

        if (areSegmentsVisible) {
            segmentAdapter.selectSegmentAt(getNearestStreamSegmentPosition(currentProgress));
        }
        if (isQueueVisible) {
            updateQueueTime(currentProgress);
        }
    }

    @Override
    public void onSleepTimerChanged(@NonNull final SleepTimer.Mode mode,
                                    final long remainingMillis,
                                    final boolean fadeOutEnabled) {
        super.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled);
        updateSleepTimerButton(mode, remainingMillis);
    }

    private void updateSleepTimerButton(@NonNull final SleepTimer.Mode mode,
                                        final long remainingMillis) {
        if (mode == SleepTimer.Mode.NONE) {
            binding.sleepTimerButton.setContentDescription(context.getString(R.string.sleep_timer));
            binding.sleepTimerButton.setActivated(false);
            binding.sleepTimerCountdown.setVisibility(View.GONE);
            return;
        }

        if (remainingMillis == SleepTimer.REMAINING_TIME_UNSET) {
            binding.sleepTimerCountdown.setText(R.string.sleep_timer_on_short);
        } else {
            binding.sleepTimerCountdown.setText(Localization.getDurationString(
                    (remainingMillis + 999L) / 1_000L));
        }
        final String status = SleepTimerDialog.getStatusText(context, mode, remainingMillis);
        binding.sleepTimerButton.setContentDescription(context.getString(
                R.string.sleep_timer_active_description, status));
        binding.sleepTimerButton.setActivated(true);
        binding.sleepTimerCountdown.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        checkLandscape();
        updateScreenDimming();
    }

    private void updateScreenDimming() {
        final var preferences = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context);
        long delaySeconds = 60;
        try {
            delaySeconds = Long.parseLong(preferences.getString(
                    context.getString(R.string.playback_dimming_delay_key), "60"));
        } catch (final NumberFormatException ignored) {
            // Retain the default if a restored preference is invalid.
        }
        final boolean enabled = preferences.getBoolean(
                context.getString(R.string.playback_dimming_key), false);
        binding.getRoot().configure(enabled && isFullscreen && fragmentIsVisible
                && player.isPlaying() && player.getPlaybackPresentationMode().rendersVideo()
                && !isAnyListViewOpen() && !isControlsVisible(),
                Math.min(600, Math.max(60, delaySeconds)) * 1000);
    }

    @Override
    public void onPaused() {
        binding.getRoot().configure(false, 60_000);
        super.onPaused();
    }

    @Override
    public void onBlocked() {
        binding.getRoot().configure(false, 60_000);
        ambientMode.setThumbnail(null);
        updateAmbientMode();
        super.onBlocked();
    }

    @Override
    public void onCompleted() {
        binding.getRoot().configure(false, 60_000);
        super.onCompleted();
        if (isFullscreen) {
            toggleFullscreen();
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    //////////////////////////////////////////////////////////////////////////*/
    //region Controls showing / hiding

    @Override
    protected void showOrHideButtons() {
        super.showOrHideButtons();
        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final boolean showQueue = !playQueue.getStreams().isEmpty();
        final boolean showSegment = !player.getCurrentStreamInfo()
                .map(StreamInfo::getStreamSegments)
                .map(List::isEmpty)
                .orElse(/*no stream info=*/true);

        binding.queueButton.setVisibility(showQueue ? View.VISIBLE : View.GONE);
        binding.queueButton.setAlpha(showQueue ? 1.0f : 0.0f);
        binding.segmentsButton.setVisibility(showSegment ? View.VISIBLE : View.GONE);
        binding.segmentsButton.setAlpha(showSegment ? 1.0f : 0.0f);
    }

    @Override
    public void showSystemUIPartially() {
        if (isFullscreen) {
            getParentActivity().ifPresent(activity -> {
                EdgeToEdgeHelper.showSystemBars(activity);
                EdgeToEdgeHelper.setLightSystemBars(activity, false);
                binding.getRoot().post(this::updateFullscreenOverlayInsets);
            });
        }
    }

    @Override
    public void hideSystemUIIfNeeded() {
        player.getFragmentListener().ifPresent(PlayerServiceEventListener::hideSystemUiIfNeeded);
    }

    /**
     * Calculate the maximum allowed height for the {@link R.id.endScreen}
     * to prevent it from enlarging the player.
     * <p>
     * The calculating follows these rules:
     * <ul>
     * <li>
     *     Show at least stream title and content creator on TVs and tablets when in landscape
     *     (always the case for TVs) and not in fullscreen mode. This requires to have at least
     *     {@link #DETAIL_ROOT_MINIMUM_HEIGHT} free space for {@link R.id.detail_root} and
     *     additional space for the stream title text size ({@link R.id.detail_title_root_layout}).
     *     The text size is {@link #DETAIL_TITLE_TEXT_SIZE_TABLET} on tablets and
     *     {@link #DETAIL_TITLE_TEXT_SIZE_TV} on TVs, see {@link R.id.titleTextView}.
     * </li>
     * <li>
     *     Otherwise, the max thumbnail height is the screen height.
     * </li>
     * </ul>
     *
     * @param bitmap the bitmap that needs to be resized to fit the end screen
     * @return the maximum height for the end screen thumbnail
     */
    @Override
    protected float calculateMaxEndScreenThumbnailHeight(@NonNull final Bitmap bitmap) {
        final int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        if (DeviceUtils.isTv(context) && !isFullscreen()) {
            final int videoInfoHeight = DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context)
                    + DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TV, context);
            return Math.min(bitmap.getHeight(), screenHeight - videoInfoHeight);
        } else if (DeviceUtils.isTablet(context) && isLandscape() && !isFullscreen()) {
            final int videoInfoHeight = DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context)
                    + DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TABLET, context);
            return Math.min(bitmap.getHeight(), screenHeight - videoInfoHeight);
        } else { // fullscreen player: max height is the device height
            return Math.min(bitmap.getHeight(), screenHeight);
        }
    }

    private void showHideKodiButton() {
        // show kodi button if it supports the current service and it is enabled in settings
        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        binding.playWithKodi.setVisibility(playQueue != null && playQueue.getItem() != null
                && KoreUtils.shouldShowPlayWithKodi(context, playQueue.getItem().getServiceId())
                ? View.VISIBLE : View.GONE);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    @Override
    protected void setupSubtitleView(final float captionScale) {
        binding.subtitleView.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/
    //region Gestures

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    public void onLayoutChange(final View view, final int l, final int t, final int r, final int b,
                               final int ol, final int ot, final int or, final int ob) {
        if (l != ol || t != ot || r != or || b != ob) {
            // Use a smaller value to be consistent across screen orientations, and to make usage
            // easier. Multiply by 3/4 to ensure the user does not need to move the finger up to the
            // screen border, in order to reach the maximum volume/brightness.
            final int width = r - l;
            final int height = b - t;
            final int min = Math.min(width, height);
            final int maxGestureLength = (int) (min * 0.75);

            if (DEBUG) {
                Log.d(TAG, "maxGestureLength = " + maxGestureLength);
            }

            binding.volumeProgressBar.setMax(maxGestureLength);
            binding.brightnessProgressBar.setMax(maxGestureLength);

            setInitialGestureValues();
            ChapterPanelLayout.apply(binding.itemsListPanel, width, height, areSegmentsVisible);
        }
    }

    private void setInitialGestureValues() {
        if (player.getAudioReactor() != null) {
            final float currentVolumeNormalized = player.isIndependentPlayerVolumeEnabled()
                    ? player.getIndependentPlayerVolume()
                    : (float) player.getAudioReactor().getVolume()
                            / player.getAudioReactor().getMaxVolume();
            binding.volumeProgressBar.setProgress(
                    (int) (binding.volumeProgressBar.getMax() * currentVolumeNormalized));
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    //////////////////////////////////////////////////////////////////////////*/
    //region Play queue, segments and streams

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        ambientMode.setThumbnail(bitmap);
        updateAmbientMode();
    }

    private void updateAmbientMode() {
        ambientMode.update(
                isFullscreen,
                player.getPlaybackPresentationMode().rendersVideo(),
                isSphericalVideo(),
                binding.videoSurfaceContainer.getMiniPlayerMode());
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        ambientMode.setThumbnail(null);
        super.onMetadataChanged(info);
        updateAmbientMode();
        updateVideoContentIdentity(info.getServiceId(), info.getUrl());
        binding.openInBrowser.setVisibility(View.VISIBLE);
        showHideKodiButton();
        updateLearningNoteButtonVisibility(info);
        if (areSegmentsVisible) {
            if (segmentAdapter.setItems(info)) {
                final int adapterPosition = getNearestStreamSegmentPosition(
                        player.getExoPlayer().getCurrentPosition());
                segmentAdapter.selectSegmentAt(adapterPosition);
                binding.itemsList.scrollToPosition(adapterPosition);
            } else {
                closeItemsList();
            }
        }
    }

    @Override
    public void onMetadataChanged(@NonNull final MediaItemTag tag) {
        ambientMode.setThumbnail(null);
        super.onMetadataChanged(tag);
        updateAmbientMode();
        updateVideoContentIdentity(tag.getServiceId(), tag.getStreamUrl());
        binding.openInBrowser.setVisibility(View.GONE);
        binding.playWithKodi.setVisibility(View.GONE);
        binding.learningNoteButton.setVisibility(View.GONE);
    }

    @Override
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        showOrHideButtons();
    }

    private void onQueueClicked() {
        isQueueVisible = true;
        areSegmentsVisible = false;
        ChapterPanelLayout.apply(binding.itemsListPanel, binding.playerVideoContent.getWidth(),
                binding.playerVideoContent.getHeight(), false);

        hideSystemUIIfNeeded();
        buildQueue();

        binding.itemsListHeaderTitle.setVisibility(View.GONE);
        binding.itemsListHeaderDuration.setVisibility(View.VISIBLE);
        binding.shuffleButton.setVisibility(View.VISIBLE);
        binding.repeatButton.setVisibility(View.VISIBLE);
        binding.addToPlaylistButton.setVisibility(
                org.schabi.newpipe.player.playqueue.DirectMediaQueuePolicy
                        .canSaveToLibrary(player.getPlayQueue()) ? View.VISIBLE : View.GONE);

        hideControls(0, 0);
        binding.itemsListPanel.requestFocus();
        animate(binding.itemsListPanel, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA);

        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue != null) {
            binding.itemsList.scrollToPosition(playQueue.getIndex());
        }

        updateQueueTime((int) player.getExoPlayer().getCurrentPosition());
    }

    private void buildQueue() {
        binding.itemsList.setAdapter(playQueueAdapter);
        binding.itemsList.setClickable(true);
        binding.itemsList.setLongClickable(true);

        binding.itemsList.clearOnScrollListeners();
        binding.itemsList.addOnScrollListener(getQueueScrollListener());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(binding.itemsList);

        playQueueAdapter.setSelectedListener(getOnSelectedListener());

        binding.itemsListClose.setOnClickListener(view -> closeItemsList());
    }

    private void onSegmentsClicked() {
        areSegmentsVisible = true;
        isQueueVisible = false;
        ChapterPanelLayout.apply(binding.itemsListPanel, binding.playerVideoContent.getWidth(),
                binding.playerVideoContent.getHeight(), true);

        hideSystemUIIfNeeded();
        buildSegments();

        binding.itemsListHeaderTitle.setVisibility(View.VISIBLE);
        binding.itemsListHeaderDuration.setVisibility(View.GONE);
        binding.shuffleButton.setVisibility(View.GONE);
        binding.repeatButton.setVisibility(View.GONE);
        binding.addToPlaylistButton.setVisibility(View.GONE);

        hideControls(0, 0);
        binding.itemsListPanel.requestFocus();
        animate(binding.itemsListPanel, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA);

        final int adapterPosition = getNearestStreamSegmentPosition(
                player.getExoPlayer().getCurrentPosition());
        segmentAdapter.selectSegmentAt(adapterPosition);
        binding.itemsList.scrollToPosition(adapterPosition);
    }

    private void buildSegments() {
        binding.itemsList.setAdapter(segmentAdapter);
        binding.itemsList.setClickable(true);
        binding.itemsList.setLongClickable(true);

        binding.itemsList.clearOnScrollListeners();
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }

        player.getCurrentStreamInfo().ifPresent(segmentAdapter::setItems);

        binding.shuffleButton.setVisibility(View.GONE);
        binding.repeatButton.setVisibility(View.GONE);
        binding.addToPlaylistButton.setVisibility(View.GONE);
        binding.itemsListClose.setOnClickListener(view -> closeItemsList());
    }

    public void closeItemsList() {
        if (isQueueVisible || areSegmentsVisible) {
            isQueueVisible = false;
            areSegmentsVisible = false;

            if (itemTouchHelper != null) {
                itemTouchHelper.attachToRecyclerView(null);
            }

            animate(binding.itemsListPanel, false, DEFAULT_CONTROLS_DURATION,
                    AnimationType.SLIDE_AND_ALPHA, 0, () ->
                        // Even when queueLayout is GONE it receives touch events
                        // and ruins normal behavior of the app. This line fixes it
                        binding.itemsListPanel.setTranslationY(
                                -binding.itemsListPanel.getHeight() * 5.0f));

            // clear focus, otherwise a white rectangle remains on top of the player
            binding.itemsListClose.clearFocus();
            binding.playPauseButton.requestFocus();
        }
    }

    private OnScrollBelowItemsListener getQueueScrollListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(final RecyclerView recyclerView) {
                @Nullable final PlayQueue playQueue = player.getPlayQueue();
                if (playQueue != null && !playQueue.isComplete()) {
                    playQueue.fetch();
                } else if (binding != null) {
                    binding.itemsList.clearOnScrollListeners();
                }
            }
        };
    }

    private StreamSegmentAdapter.StreamSegmentListener getStreamSegmentListener() {
        return new StreamSegmentAdapter.StreamSegmentListener() {
            @Override
            public void onItemClick(@NonNull final StreamSegmentItem item, final int seconds) {
                segmentAdapter.selectSegment(item);
                player.seekTo(seconds * 1000L);
                player.triggerProgressUpdate();
            }

            @Override
            public void onItemLongClick(@NonNull final StreamSegmentItem item, final int seconds) {
                @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
                if (currentMetadata == null
                        || currentMetadata.getServiceId() != YouTube.getServiceId()) {
                    return;
                }

                final PlayQueueItem currentItem = player.getCurrentItem();
                if (currentItem != null) {
                    String videoUrl = player.getVideoUrl();
                    videoUrl += ("&t=" + seconds);
                    ShareUtils.shareText(context, currentItem.getTitle(),
                            videoUrl, ExtractorImageCompat.thumbnailImages(currentItem));
                }
            }
        };
    }

    private int getNearestStreamSegmentPosition(final long playbackPosition) {
        final List<StreamSegment> segments = player.getCurrentStreamInfo()
                .map(StreamInfo::getStreamSegments)
                .orElse(Collections.emptyList());

        int nearestPosition = 0;
        for (final var segment : segments) {
            if (segment.getStartTimeSeconds() * 1000L > playbackPosition) {
                break;
            }
            nearestPosition++;
        }
        return Math.max(0, nearestPosition - 1);
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new PlayQueueItemTouchCallback() {
            @Override
            public void onMove(final int sourceIndex, final int targetIndex) {
                @Nullable final PlayQueue playQueue = player.getPlayQueue();
                if (playQueue != null) {
                    playQueue.move(sourceIndex, targetIndex);
                }
            }

            @Override
            public void onSwiped(final int index) {
                @Nullable final PlayQueue playQueue = player.getPlayQueue();
                if (playQueue != null && index != -1) {
                    playQueue.remove(index);
                }
            }
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(final PlayQueueItem item, final View view) {
                player.selectQueueItem(item);
            }

            @Override
            public void held(final PlayQueueItem item, final View view) {
                @Nullable final PlayQueue playQueue = player.getPlayQueue();
                @Nullable final AppCompatActivity parentActivity = getParentActivity().orElse(null);
                if (playQueue != null && parentActivity != null && playQueue.indexOf(item) != -1) {
                    openPopupMenu(player.getPlayQueue(), item, view, true,
                            parentActivity.getSupportFragmentManager(), context);
                }
            }

            @Override
            public void onStartDrag(final PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        };
    }

    private void updateQueueTime(final int currentTime) {
        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final int currentStream = playQueue.getIndex();
        final List<PlayQueueItem> streams = playQueue.getStreams();

        final long before = streams.subList(0, currentStream).stream()
                .collect(Collectors.summingLong(PlayQueueItem::getDuration)) * 1000;

        final long after = streams.subList(currentStream, streams.size()).stream()
                .collect(Collectors.summingLong(PlayQueueItem::getDuration)) * 1000;

        binding.itemsListHeaderDuration.setText(
                String.format("%s/%s",
                        getTimeString(currentTime + before),
                        getTimeString(before + after)
                ));
    }

    @Override
    protected boolean isAnyListViewOpen() {
        return isQueueVisible || areSegmentsVisible;
    }

    @Override
    public boolean isFullscreen() {
        return isFullscreen;
    }

    public boolean isVerticalVideo() {
        return videoContentOrientation
                == FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT;
    }

    public FullscreenOrientationPolicy.VideoContentOrientation getVideoContentOrientation() {
        return videoContentOrientation;
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Click listeners

    @Override
    protected void onPlaybackSpeedClicked() {
        getParentActivity().ifPresent(activity ->
                PlaybackParameterDialog.newInstance(player.getPlaybackSpeed(),
                                player.getPlaybackPitch(), player.getPlaybackSkipSilence(),
                                player.getPlaybackSkipSilenceAvailable(),
                                player::setPlaybackParameters)
                        .withChannelSkippingAction(player.getCurrentStreamInfo()
                                .filter(info -> info.getStreamType() == StreamType.VIDEO_STREAM
                                        && ChannelSkipPreferences.isAvailable(info))
                                .<Runnable>map(info -> () -> ChannelSkipDialog.show(activity, info,
                                        () -> player.onChannelSkipSettingsChanged(info)))
                                .orElse(null))
                        .show(activity.getSupportFragmentManager(), null));
    }

    @Override
    public boolean onKeyDown(final int keyCode) {
        resetTvSeekOnNavigation(keyCode);
        if (binding.getRoot().wakeOnInteraction()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isFullscreen && DeviceUtils.isTv(context)
                && binding.getRoot().isShown() && !binding.getRoot().hasFocus()
                && getParentActivity().map(activity -> activity.hasWindowFocus()).orElse(false)) {
            showControls(0);
            if (binding.playPauseButton.requestFocus()) {
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isFullscreen && DeviceUtils.isTv(context)
                && binding.getRoot().hasFocus() && !isControlsVisible()
                && !isAnyListViewOpen() && player.getCurrentMetadata() != null) {
            final boolean opened = getParentActivity().map(activity ->
                    player.getCurrentMetadata().getMaybeStreamInfo().map(info ->
                            TvRecommendations.show(activity, info)).orElse(false)).orElse(false);
            if (opened) {
                return true;
            }
        }
        if (TvPlayerFocusPolicy.shouldMoveToActions(
                keyCode, isFullscreen, DeviceUtils.isTv(context))
                && binding.playbackControlRoot.isShown()
                && (binding.playPauseButton.hasFocus() || binding.playPreviousButton.hasFocus()
                || binding.playNextButton.hasFocus())) {
            if (binding.commentsButton.getVisibility() == View.VISIBLE
                    && binding.commentsButton.requestFocus()) {
                return true;
            }
            if (binding.queueButton.getVisibility() == View.VISIBLE
                    && binding.queueButton.requestFocus()) {
                return true;
            }
            if (binding.moreOptionsButton.getVisibility() == View.VISIBLE
                    && binding.moreOptionsButton.requestFocus()) {
                return true;
            }
            if (binding.qualityTextView.getVisibility() == View.VISIBLE
                    && binding.qualityTextView.requestFocus()) {
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && isFullscreen) {
            if (DeviceUtils.isTv(context) && isAnyListViewOpen()) {
                closeItemsList();
            } else if (DeviceUtils.isTv(context) && isControlsVisible()) {
                hideControls(0, 0);
            } else {
                toggleFullscreenWithOrientation();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE && isFullscreen) {
            player.playPause();
            if (player.isPlaying()) {
                hideControls(0, 0);
            }
            return true;
        }
        return super.onKeyDown(keyCode);
    }

    public boolean canUseDirectionalShortcuts() {
        return isFullscreen && binding.getRoot().isShown()
                && !isControlsVisible() && !isAnyListViewOpen() && !touchLocked
                && getParentActivity().map(activity -> activity.hasWindowFocus()
                        && (activity.getCurrentFocus() == null || binding.getRoot().hasFocus()))
                        .orElse(false);
    }

    public boolean isTouchLocked() {
        return touchLocked;
    }

    /**
     * Called before activity shortcuts and focused views can handle a remote key.
     * @param event the remote key press or release
     * @return whether the lock consumed this event
     */
    public boolean dispatchTouchLockedKeyEvent(final KeyEvent event) {
        if (!touchLocked) {
            return false;
        }
        // Allow PlaybackDimmingLayout to consume the first wake press before
        // Unlock processes another confirmation key.
        if (binding.touchUnlockButton.getVisibility() == View.VISIBLE) {
            binding.touchUnlockButton.requestFocus();
        }
        return binding.getRoot().dispatchKeyEvent(event)
                || binding.touchLockOverlay.dispatchKeyEvent(event);
    }

    private void setTouchLocked(final boolean locked) {
        touchLocked = locked && isFullscreen;
        binding.touchLockOverlay.setVisibility(touchLocked ? View.VISIBLE : View.GONE);
        if (touchLocked) {
            final boolean autoHide = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.player_touch_unlock_auto_hide_key),
                            true);
            binding.touchLockOverlay.startLocked(autoHide);
        } else {
            binding.touchLockOverlay.stopLocked();
        }
        binding.playbackControlRoot.setImportantForAccessibility(touchLocked
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (touchLocked) {
            closeItemsList();
            hideControls(0, 0);
            binding.touchUnlockButton.requestFocus();
        }
    }

    @Override
    public void showControls(final long duration) {
        if (canShowControls()) {
            super.showControls(duration);
        }
    }

    @Override
    public void showControlsThenHide() {
        if (canShowControls()) {
            super.showControlsThenHide();
        }
    }

    private boolean canShowControls() {
        return !touchLocked && getParentActivity().map(activity ->
                Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                        || !activity.isInPictureInPictureMode()).orElse(true);
    }

    /** @return the decoder's display ratio, falling back to the last measured surface ratio */
    public float getVideoAspectRatio() {
        if (isSphericalVideo() && binding.videoSurfaceContainer.getHeight() > 0) {
            return (float) binding.videoSurfaceContainer.getWidth()
                    / binding.videoSurfaceContainer.getHeight();
        }
        if (player.getExoPlayer() != null) {
            final VideoSize size = player.getExoPlayer().getVideoSize();
            final float ratio = calculateDisplayAspectRatio(size.width, size.height,
                    size.unappliedRotationDegrees, size.pixelWidthHeightRatio);
            if (ratio > 0.0f) {
                return ratio;
            }
        }
        return binding.surfaceView.getVideoAspectRatio();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Video size, orientation, fullscreen
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size, orientation, fullscreen


    private void onCommentsClicked() {
        if (!isFullscreen) {
            return;
        }
        player.getCurrentStreamInfo().ifPresent(info ->
                getParentActivity().ifPresent(activity -> {
                    if (org.schabi.newpipe.player.LiveChatRepository.supports(info)) {
                        FullscreenCommentsSidebar.showLiveChat(activity, info.getServiceId(),
                                info.getUrl(), info.getName());
                    } else {
                        FullscreenCommentsSidebar.show(activity, info.getServiceId(),
                                info.getUrl(), info.getName());
                    }
                    hideControls(0, 0);
                }));
    }

    private void setupScreenRotationButton() {
        binding.screenRotationButton.setVisibility(View.VISIBLE);
        binding.screenRotationButton.setImageDrawable(AppCompatResources.getDrawable(context,
                isFullscreen ? R.drawable.ic_fullscreen_exit
                        : R.drawable.ic_fullscreen));
    }

    private void updateVideoContentIdentity(final int serviceId,
                                            @Nullable final String streamUrl) {
        final String streamKey = serviceId + ":" + String.valueOf(streamUrl);
        if (Objects.equals(videoOrientationStreamKey, streamKey)) {
            return;
        }
        getParentActivity().ifPresent(FullscreenCommentsSidebar::hide);
        videoOrientationStreamKey = streamKey;
        tabletAutoFullscreen.onNewVideo();
        videoContentOrientation = FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN;
        setupScreenRotationButton();
    }

    private void updateVideoContentIdentityFromCurrentItem() {
        final PlayQueueItem currentItem = player.getCurrentItem();
        if (currentItem != null) {
            updateVideoContentIdentity(currentItem.getServiceId(), currentItem.getUrl());
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        super.onVideoSizeChanged(videoSize);
        if (videoSize.width <= 0 || videoSize.height <= 0) {
            // Disabling the renderer on screen-off reports 0x0. Keep the last known orientation
            // until valid metadata returns, especially for portrait fullscreen videos.
            return;
        }

        updateVideoContentIdentityFromCurrentItem();
        final FullscreenOrientationPolicy.VideoContentOrientation previousContentOrientation =
                videoContentOrientation;
        videoContentOrientation =
                FullscreenOrientationPolicy.classifyVideoContentOrientation(
                        videoSize.width,
                        videoSize.height,
                        videoSize.pixelWidthHeightRatio);

        final boolean portraitVideo = videoContentOrientation
                == FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT;
        final boolean orientationSpecificContent =
                videoContentOrientation
                        == FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT
                        || videoContentOrientation
                        == FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE;
        if (!DeviceUtils.isTv(context) && !DeviceUtils.isTablet(context)
                && (previousContentOrientation != videoContentOrientation
                    || globalScreenOrientationLocked(context)
                    && PlayerRotationMode.get(context) != PlayerRotationMode.FIXED
                    && isFullscreen && orientationSpecificContent
                    && isLandscape() == portraitVideo)) {
            // The detail fragment knows about pending fullscreen requests. It can update an
            // in-flight landscape request when metadata identifies a portrait video.
            player.getFragmentListener().ifPresent(
                    listener -> listener.onVideoContentOrientationChanged());
        }

        setupScreenRotationButton();
        updateAmbientMode();
        // PiP may already have params cached from the previous video. Refresh the aspect ratio
        // on a valid decoder-size change even if the player holder bounds do not change.
        getParentActivity().filter(org.schabi.newpipe.MainActivity.class::isInstance)
                .map(org.schabi.newpipe.MainActivity.class::cast)
                .ifPresent(org.schabi.newpipe.MainActivity::refreshNativePipAspectRatio);
        if (player.isPlaying()) {
            checkLandscape();
        }
    }

    public void toggleFullscreen() {
        if (DEBUG) {
            Log.d(TAG, "toggleFullscreen() called");
        }
        setFullscreen(!isFullscreen);
    }

    /**
     * Applies the requested fullscreen state without toggling an already-correct state.
     * Configuration changes may deliver more than one callback, so orientation-driven
     * fullscreen synchronization must be idempotent.
     *
     * @param fullscreen whether the player should be fullscreen
     */
    public void setFullscreen(final boolean fullscreen) {
        if (isFullscreen == fullscreen) {
            return;
        }

        final PlayerServiceEventListener fragmentListener = player.getFragmentListener()
                .orElse(null);
        if (fragmentListener == null || player.exoPlayerIsNull()) {
            return;
        }

        isFullscreen = fullscreen;
        updateScreenDimming();
        updateAmbientMode();
        if (!fullscreen) {
            setTouchLocked(false);
        }
        binding.touchLockButton.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        binding.commentsButton.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        if (!fullscreen) {
            getParentActivity().ifPresent(FullscreenCommentsSidebar::hide);
        }
        if (isFullscreen) {
            // Android needs tens milliseconds to send new insets but a user is able to see
            // how controls changes it's position from `0` to `nav bar height` padding.
            // So just hide the controls to hide this visual inconsistency
            hideControls(0, 0);
        } else {
            // Apply window insets because Android will not do it when orientation changes
            // from landscape to portrait (open vertical video to reproduce)
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        }
        fragmentListener.onFullscreenStateChanged(isFullscreen);

        binding.metadataView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
        binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        updateFullscreenOverlayInsets();
        setupScreenRotationButton();
    }

    /**
     * Applies explicit fullscreen intent while preserving the video's content orientation.
     * Exiting fullscreen releases the player's rotation lock so browsing follows Android.
     */
    public void toggleFullscreenWithOrientation() {
        final boolean targetFullscreen = !isFullscreen();
        final PlayerServiceEventListener listener = player.getFragmentListener().orElse(null);
        if (listener != null) {
            listener.onManualFullscreenButtonClicked(targetFullscreen);
        } else {
            setFullscreen(targetFullscreen);
        }
    }

    static boolean shouldUseScreenRotationAction(
            final FullscreenOrientationPolicy.VideoContentOrientation contentOrientation,
            final boolean landscape,
            final boolean targetFullscreen) {
        return FullscreenOrientationPolicy.shouldUseOrientationAction(
                contentOrientation, landscape, targetFullscreen);
    }


    public boolean isAutomaticFullscreenAllowed(final Context playerContext) {
        return tabletAutoFullscreen.isAllowed(playerContext);
    }

    public void onManualFullscreenChange(final int orientation) {
        updateVideoContentIdentityFromCurrentItem();
        tabletAutoFullscreen.onManualChange(orientation);
    }

    public void checkLandscape() {
        updateVideoContentIdentityFromCurrentItem();
        final Context playerContext = getParentContext().orElse(player.getService());
        final int orientation = playerContext.getResources().getConfiguration().orientation;
        // Playback resumption and video-size updates also call checkLandscape().
        // Honor an explicit fullscreen exit even if the phone stays in landscape.
        final boolean userExitedFullscreen = player.getFragmentListener()
                .map(PlayerServiceEventListener::shouldSuppressAutomaticFullscreenInLandscape)
                .orElse(false);
        if (!userExitedFullscreen && shouldEnterFullscreenForConfiguration(
                orientation,
                isFullscreen,
                player.isAudioOnly(),
                !isAutomaticFullscreenAllowed(playerContext)
                        || DeviceUtils.isTv(playerContext)
                        || DeviceUtils.isDesktopMode(playerContext)
                        || getParentActivity().map(DeviceUtils::isInMultiWindow).orElse(false),
                videoContentOrientation)) {
            setFullscreen(true);
        }
    }

    static boolean shouldEnterFullscreenForConfiguration(
            final int orientation,
            final boolean fullscreen,
            final boolean audioOnly,
            final boolean tablet,
            final FullscreenOrientationPolicy.VideoContentOrientation contentOrientation) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
                && !fullscreen
                && !audioOnly
                && !tablet
                && FullscreenOrientationPolicy.supportsAutomaticFullscreen(contentOrientation);
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    private Optional<Context> getParentContext() {
        return Optional.ofNullable(binding.getRoot().getParent())
                .filter(ViewGroup.class::isInstance)
                .map(parent -> ((ViewGroup) parent).getContext());
    }

    public Optional<AppCompatActivity> getParentActivity() {
        return getParentContext()
                .filter(AppCompatActivity.class::isInstance)
                .map(AppCompatActivity.class::cast);
    }

    public boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature
        // while DisplayMetrics from app context doesn't
        return DeviceUtils.isLandscape(getParentContext().orElse(player.getService()));
    }

    private void showLearningNoteDialog(final long timestampMillis) {
        player.getCurrentStreamInfo().ifPresent(info -> {
            if (org.schabi.newpipe.util.StreamTypeUtil.isLiveStream(info.getStreamType())) {
                Toast.makeText(context, R.string.learning_note_live_unavailable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            getParentActivity().ifPresent(activity -> LearningNoteDialog.showWithSeek(
                    activity,
                    timestampMillis,
                    null,
                    position -> player.getCurrentStreamInfo()
                            .filter(current -> current.getServiceId() == info.getServiceId()
                                    && current.getUrl().equals(info.getUrl()))
                            .ifPresent(current -> player.seekTo(position)),
                    (timestamp, text) -> learningNoteDisposables.add(
                            new LearningNoteManager(context).create(info, timestamp, text)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            note -> Toast.makeText(
                                                    context,
                                                    R.string.learning_note_saved,
                                                    Toast.LENGTH_SHORT
                                            ).show(),
                                            error -> Toast.makeText(
                                                    context,
                                                    R.string.learning_note_save_error,
                                                    Toast.LENGTH_SHORT
                                            ).show()
                                    )
                    )
            ));
        });
    }

    private void showVideoNotes(final long timestamp) {
        player.getCurrentStreamInfo().ifPresent(info -> {
            learningNoteDisposables.add(new LearningNoteManager(context).ensureStream(info)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(streamId -> player.getCurrentStreamInfo()
                                    .filter(current -> current.getServiceId() == info.getServiceId()
                                            && current.getUrl().equals(info.getUrl()))
                                    .ifPresent(current -> getParentActivity().ifPresent(activity ->
                                            org.schabi.newpipe.learning.VideoNotesDialog.show(
                                                    activity.getSupportFragmentManager(), streamId,
                                                    info.getName(), timestamp))),
                            error -> Toast.makeText(context, R.string.learning_note_save_error,
                                    Toast.LENGTH_SHORT).show()));
        });
    }

    private void updateLearningNoteButtonVisibility(@Nullable final StreamInfo info) {
        binding.learningNoteButton.setVisibility(shouldShowLearningNoteButton(
                LearningMode.areNotesEnabled(context),
                info) ? View.VISIBLE : View.GONE);
    }

    static boolean shouldShowLearningNoteButton(final boolean notesEnabled,
                                                @Nullable final StreamInfo info) {
        return notesEnabled && info != null
                && !org.schabi.newpipe.util.StreamTypeUtil.isLiveStream(info.getStreamType());
    }
    //endregion
}
