package org.schabi.newpipe.player.ui;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.RENDERER_UNAVAILABLE;
import static org.schabi.newpipe.player.Player.STATE_BUFFERING;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED_SEEK;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;
import static org.schabi.newpipe.player.helper.CustomPlaybackControls.seekMillis;
import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextResizeModeAndSaveToPrefs;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.BitmapCompat;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.player.DanmakuController;
import org.schabi.newpipe.player.LiveQualityController;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlaybackPresentationMode;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.DisplayPortion;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay;
import org.schabi.newpipe.util.image.ExtractorImageCompat;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class VideoPlayerUi extends PlayerUi implements SeekBar.OnSeekBarChangeListener,
        PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    private static final String TAG = VideoPlayerUi.class.getSimpleName();

    // time constants
    public static final long DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final long DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final long DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds
    public static final int SEEK_OVERLAY_DURATION = 450; // 450 millis

    private boolean tvDirectionalSeek;
    private final ChapterSeekBarController chapterController = new ChapterSeekBarController();
    // other constants (TODO remove playback speeds and use normal menu for popup, too)
    private static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private enum PlayButtonAction {
        PLAY, PAUSE, REPLAY
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected PlayerBinding binding;
    private final BottomPlayerControls bottomPlayerControls;
    private final PlayerUiTheme playerUiTheme;
    private final DanmakuController danmakuController;
    private final Handler controlsVisibilityHandler = new Handler(Looper.getMainLooper());
    private long lastTvBackPressedAt;
    private final PlayerQualityFeedback qualityFeedback;
    private final org.schabi.newpipe.player.video.PlayerVideoSurface videoSurface;
    @Nullable
    private Bitmap scaledEndScreenThumbnail;

    private static final int POPUP_MENU_ID_QUALITY = 69;
    private static final int AUTO_QUALITY_MENU_ITEM_ID = Integer.MAX_VALUE;
    private static final int BEST_QUALITY_MENU_ITEM_ID = Integer.MAX_VALUE - 1;
    private static final int POPUP_MENU_ID_AUDIO_TRACK = 70;
    private static final int POPUP_MENU_ID_PLAYBACK_SPEED = 79;
    private static final int POPUP_MENU_ID_CAPTION = 89;

    protected boolean isSomePopupMenuVisible = false;
    private boolean dismissingPopupMenus;
    private final View.OnAttachStateChangeListener popupAnchorListener =
            PlayerPopupMenu.detachListener(this::dismissPopupMenus);
    private PopupMenu qualityPopupMenu;
    private PopupMenu audioTrackPopupMenu;
    protected PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;
    private final PlayerPopupMenu playerPopupMenu = new PlayerPopupMenu();
    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/

    private GestureDetector gestureDetector;
    private BasePlayerGestureListener playerGestureListener;
    @Nullable
    private View.OnLayoutChangeListener onLayoutChangeListener = null;
    private int controlsBasePadding;
    private int topControlsBasePadding;

    @NonNull
    private final SeekbarPreviewThumbnailHolder seekbarPreviewThumbnailHolder =
            new SeekbarPreviewThumbnailHolder();
    protected VideoPlayerUi(@NonNull final Player player,
                            @NonNull final PlayerBinding playerBinding) {
        super(player);
        binding = playerBinding;
        videoSurface = org.schabi.newpipe.player.video.PlayerVideoSurface.forUi(this);
        binding.abRepeatButton.bind(player);
        binding.playbackInformationPanel.bind(player.getPlaybackInformation());
        binding.playbackInformationButton.setOnClickListener(
                view -> binding.playbackInformationPanel.toggle());
        bottomPlayerControls = new BottomPlayerControls(binding);
        playerUiTheme = new PlayerUiTheme(context, binding.playbackSeekBar);
        danmakuController = new DanmakuController(player, binding);
        qualityFeedback = new PlayerQualityFeedback(player);
        setupFromView();
        player.getCurrentStreamInfo().ifPresent(danmakuController::onMetadataChanged);
    }

    public void setupFromView() {
        initViews();
        initListeners();
        SponsorBlockContributionMenu.bind(binding.sponsorBlockContributeButton, player);
        PlayerTooltips.disable(binding.getRoot());
        PlayerControlHints.install(binding.getRoot());
        org.schabi.newpipe.themes.ThemeIcons.apply(binding.playPreviousButton, "previous");
        org.schabi.newpipe.themes.ThemeIcons.apply(binding.playNextButton, "next");
        org.schabi.newpipe.themes.ThemeIcons.apply(binding.shuffleButton, "shuffle");
        setupPlayerSeekOverlay();
    }

    private void initViews() {
        setupSubtitleView();

        binding.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));

        applyPlayerSeekBarColor();

        final ContextThemeWrapper themeWrapper = new ContextThemeWrapper(context,
                R.style.PlayerOverlayPopupMenu);

        qualityPopupMenu = new PopupMenu(themeWrapper, binding.qualityTextView);
        audioTrackPopupMenu = new PopupMenu(themeWrapper, binding.audioTrackTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, binding.playbackSpeed);
        captionPopupMenu = new PopupMenu(themeWrapper, binding.captionTextView);

        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

        binding.titleTextView.setSelected(true);
        binding.channelTextView.setSelected(true);

        // Prevent hiding of bottom sheet via swipe inside queue
        binding.itemsList.setNestedScrollingEnabled(false);
    }

    private void applyPlayerSeekBarColor() {
        playerUiTheme.refresh();
    }

    abstract BasePlayerGestureListener buildGestureListener();

    protected void initListeners() {
        binding.getRoot().addOnAttachStateChangeListener(popupAnchorListener);
        binding.qualityTextView.setOnClickListener(makeOnClickListener(this::onQualityClicked));
        binding.audioTrackTextView.setOnClickListener(
                makeOnClickListener(this::onAudioTracksClicked));
        binding.playbackSpeed.setOnClickListener(makeOnClickListener(this::onPlaybackSpeedClicked));

        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.captionTextView.setOnClickListener(makeOnClickListener(this::onCaptionClicked));
        binding.resizeTextView.setOnClickListener(makeOnClickListener(this::onResizeClicked));
        binding.danmakuToggle.setOnClickListener(makeOnClickListener(danmakuController::toggle));
        binding.playbackLiveSync.setOnClickListener(makeOnClickListener(player::seekToDefault));
        binding.playbackLiveSync.setOnLongClickListener(
                view -> PlayerLiveModeDialog.show(player, view));

        playerGestureListener = buildGestureListener();
        gestureDetector = new GestureDetector(context, playerGestureListener);
        binding.getRoot().setOnTouchListener(playerGestureListener);

        binding.repeatButton.setOnClickListener(v -> onRepeatClicked());
        binding.shuffleButton.setOnClickListener(v -> onShuffleClicked());

        binding.playPauseButton.setOnClickListener(makeOnClickListener(player::playPause));
        binding.playPreviousButton.setOnClickListener(
                makeOnClickListener(() -> PlayerNavigationButtons.previous(player)));
        binding.playNextButton.setOnClickListener(
                makeOnClickListener(() -> PlayerNavigationButtons.next(player)));

        binding.moreOptionsButton.setOnClickListener(
                makeOnClickListener(this::onMoreOptionsClicked));
        binding.share.setOnClickListener(makeOnClickListener(() -> {
            final PlayQueueItem currentItem = player.getCurrentItem();
            if (currentItem != null) {
                ShareUtils.shareText(context, currentItem.getTitle(),
                        player.getVideoUrlAtCurrentTime(),
                        ExtractorImageCompat.thumbnailImages(currentItem));
            }
        }));
        binding.share.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(context, player.getVideoUrlAtCurrentTime());
            return true;
        });
        binding.fullScreenButton.setOnClickListener(makeOnClickListener(() -> {
            player.setRecovery();
            NavigationHelper.playOnMainPlayer(context,
                    Objects.requireNonNull(player.getPlayQueue()), true);
        }));
        binding.playWithKodi.setOnClickListener(makeOnClickListener(this::onPlayWithKodiClicked));
        binding.openInBrowser.setOnClickListener(makeOnClickListener(this::onOpenInBrowserClicked));
        binding.openInBrowser.setOnLongClickListener(v -> {
            player.getCurrentStreamInfo().ifPresent(info -> ShareUtils.openUrlInBrowser(context,
                    org.schabi.newpipe.util.TimestampedUrl.atPosition(info.getOriginalUrl(),
                            info.getServiceId(), player.exoPlayerIsNull() ? -1L
                                    : player.getExoPlayer().getCurrentPosition(),
                            player.isLive())));
            return true;
        });
        binding.playerCloseButton.setOnClickListener(makeOnClickListener(() ->
                // set package to this app's package to prevent the intent from being seen outside
                context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER)
                        .setPackage(App.PACKAGE_NAME))
        ));
        binding.switchMute.setOnClickListener(makeOnClickListener(player::toggleMute));

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, (view, windowInsets) -> {
            final Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (!cutout.equals(Insets.NONE)) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            }
            return windowInsets;
        });

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays and fast_seek_overlay too. Without it they will be off-centered.
        onLayoutChangeListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    updateFullscreenOverlayInsets();
                    binding.playerOverlays.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                            v.getPaddingRight(), v.getPaddingBottom());

                    // If we added padding to the fast seek overlay, too, it would not go under the
                    // system ui. Instead we apply negative margins equal to the window insets of
                    // the opposite side, so that the view covers all of the player (overflowing on
                    // some sides) and its center coincides with the center of other controls.
                    final RelativeLayout.LayoutParams fastSeekParams = (RelativeLayout.LayoutParams)
                            binding.fastSeekOverlay.getLayoutParams();
                    fastSeekParams.leftMargin = -v.getPaddingRight();
                    fastSeekParams.topMargin = -v.getPaddingBottom();
                    fastSeekParams.rightMargin = -v.getPaddingLeft();
                    fastSeekParams.bottomMargin = -v.getPaddingTop();
                };
        binding.playbackControlRoot.addOnLayoutChangeListener(onLayoutChangeListener);
    }

    protected void deinitListeners() {
        qualityFeedback.reset();
        binding.getRoot().removeOnAttachStateChangeListener(popupAnchorListener);
        binding.qualityTextView.setOnClickListener(null);
        binding.audioTrackTextView.setOnClickListener(null);
        binding.playbackSpeed.setOnClickListener(null);
        binding.playbackSeekBar.setOnSeekBarChangeListener(null);
        binding.captionTextView.setOnClickListener(null);
        binding.resizeTextView.setOnClickListener(null);
        binding.danmakuToggle.setOnClickListener(null);
        binding.playbackLiveSync.setOnClickListener(null);
        binding.playbackLiveSync.setOnLongClickListener(null);

        binding.getRoot().setOnTouchListener(null);
        playerGestureListener = null;
        gestureDetector = null;

        binding.repeatButton.setOnClickListener(null);
        binding.shuffleButton.setOnClickListener(null);

        binding.playPauseButton.setOnClickListener(null);
        binding.playPreviousButton.setOnClickListener(null);
        binding.playNextButton.setOnClickListener(null);

        binding.moreOptionsButton.setOnClickListener(null);
        binding.sponsorBlockContributeButton.setOnClickListener(null);
        binding.moreOptionsButton.setOnLongClickListener(null);
        binding.share.setOnClickListener(null);
        binding.share.setOnLongClickListener(null);
        binding.fullScreenButton.setOnClickListener(null);
        binding.screenRotationButton.setOnClickListener(null);
        binding.playWithKodi.setOnClickListener(null);
        binding.openInBrowser.setOnClickListener(null);
        binding.openInBrowser.setOnLongClickListener(null);
        binding.playerCloseButton.setOnClickListener(null);
        binding.switchMute.setOnClickListener(null);

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, null);

        binding.playbackControlRoot.removeOnLayoutChangeListener(onLayoutChangeListener);
    }

    /**
     * Initializes the Fast-For/Backward overlay.
     */
    private void setupPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(forward -> seekMillis(player.getContext(), forward) / 1000)
                .performListener(new PlayerFastSeekOverlay.PerformListener() {

                    @Override
                    public void onDoubleTap() {
                        animate(binding.fastSeekOverlay, true, SEEK_OVERLAY_DURATION);
                    }

                    @Override
                    public void onDoubleTapEnd() {
                        animate(binding.fastSeekOverlay, false, SEEK_OVERLAY_DURATION);
                    }

                    @NonNull
                    @Override
                    public FastSeekDirection getFastSeekDirection(
                            @NonNull final DisplayPortion portion
                    ) {
                        if (player.exoPlayerIsNull()) {
                            // Abort seeking
                            playerGestureListener.endMultiDoubleTap();
                            return FastSeekDirection.NONE;
                        }
                        if (portion == DisplayPortion.LEFT) {
                            // Check if it's possible to rewind
                            // Small puffer to eliminate infinite rewind seeking
                            if (player.getExoPlayer().getCurrentPosition() < 500L) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.BACKWARD;
                        } else if (portion == DisplayPortion.RIGHT) {
                            // Check if it's possible to fast-forward
                            if (player.getCurrentState() == STATE_COMPLETED
                                    || player.getExoPlayer().getCurrentPosition()
                                    >= player.getExoPlayer().getDuration()) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.FORWARD;
                        }
                        /* portion == DisplayPortion.MIDDLE */
                        return FastSeekDirection.NONE;
                    }

                    @Override
                    public void seek(final boolean forward) {
                        playerGestureListener.keepInDoubleTapMode();
                        if (forward) {
                            player.fastForward();
                        } else {
                            player.fastRewind();
                        }
                    }
                });
        playerGestureListener.doubleTapControls(binding.fastSeekOverlay);
    }

    public void deinitPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(null)
                .performListener(null);
    }

    @Override
    public void setupAfterIntent() {
        super.setupAfterIntent();
        applyPlayerSeekBarColor();
        setupElementsVisibility();
        setupElementsSize(context.getResources());
        binding.getRoot().setVisibility(View.VISIBLE);
        if (shouldRequestPlaybackButtonFocus()) {
            binding.playPauseButton.requestFocus();
        }
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        setupVideoSurfaceIfNeeded();
    }

    @Override
    public void initPlayback() {
        super.initPlayback();

        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(player.getExoPlayer().getShuffleModeEnabled());

        // Set repeat button to the correct UI state
        setRepeatButton(player.getExoPlayer().getRepeatMode());

    }

    public abstract void removeViewFromParent();

    @Override
    public void destroyPlayer() {
        dismissPopupMenus();
        seekbarPreviewThumbnailHolder.cancelRequest();
        super.destroyPlayer();
        clearVideoSurface();
    }

    @Override
    public void destroy() {
        dismissPopupMenus();
        binding.abRepeatButton.clear();
        binding.playbackInformationPanel.clear();
        binding.playbackInformationButton.setOnClickListener(null);
        playerUiTheme.close();
        danmakuController.destroy();
        seekbarPreviewThumbnailHolder.clear();
        super.destroy();
        clearScaledEndScreenThumbnail();
        deinitPlayerSeekOverlay();
        deinitListeners();
        bottomPlayerControls.restore();
        videoSurface.close();
    }

    protected void setupElementsVisibility() {
        binding.themePlayer.setVisibility(View.GONE);
        setMuteButton(player.isMuted());
        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    protected abstract void setupElementsSize(Resources resources);

    protected void setupElementsSize(final int buttonsMinWidth,
                                     final int playerTopPad,
                                     final int controlsPad,
                                     final int buttonsPad,
                                     final int iconButtonsPad) {
        controlsBasePadding = controlsPad;
        topControlsBasePadding = playerTopPad;
        binding.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0);
        binding.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0);
        binding.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.audioTrackTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setMinimumWidth(buttonsMinWidth);
        binding.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.sleepTimerButton.setPadding(
                iconButtonsPad, iconButtonsPad, iconButtonsPad, iconButtonsPad);
        updateFullscreenOverlayInsets();
    }

    protected final void updateFullscreenOverlayInsets() {
        binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        bottomPlayerControls.applyWindowInsets(isFullscreen(),
                controlsBasePadding, topControlsBasePadding);
    }

    static int calculateTopControlsPadding(final boolean fullscreen,
                                           final int basePadding,
                                           final int statusBarInset,
                                           final int displayCutoutInset) {
        final int safeBasePadding = Math.max(basePadding, 0);
        if (!fullscreen) {
            return safeBasePadding;
        }
        return safeBasePadding + Math.max(Math.max(statusBarInset, displayCutoutInset), 0);
    }

    static int calculateControlsEdgePadding(final boolean fullscreen,
                                            final int basePadding,
                                            final int systemBarInset,
                                            final int displayCutoutInset) {
        final int safeBasePadding = Math.max(basePadding, 0);
        if (!fullscreen) {
            return safeBasePadding;
        }
        return safeBasePadding + Math.max(Math.max(systemBarInset, displayCutoutInset), 0);
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        videoSurface.onBroadcast(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            // When the orientation changes, the screen height might be smaller. If the end screen
            // thumbnail is not re-scaled, it can be larger than the current screen height and thus
            // enlarging the whole player. This causes the seekbar to be out of the visible area.
            updateEndScreenThumbnail(player.getThumbnail());
        }
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail
    //////////////////////////////////////////////////////////////////////////*/
    //region Thumbnail

    /**
     * Scale the player audio / end screen thumbnail down if necessary.
     * <p>
     * This is necessary when the thumbnail's height is larger than the device's height
     * and thus is enlarging the player's height
     * causing the bottom playback controls to be out of the visible screen.
     * </p>
     */
    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        updateEndScreenThumbnail(bitmap);
    }

    private void updateEndScreenThumbnail(@Nullable final Bitmap thumbnail) {
        if (thumbnail == null) {
            clearScaledEndScreenThumbnail();
            return;
        }

        final float endScreenHeight = calculateMaxEndScreenThumbnailHeight(thumbnail);
        final Bitmap endScreenBitmap;
        if (needsThumbnailScaling(thumbnail.getHeight(), endScreenHeight)) {
            final int targetHeight = Math.max(1, Math.round(endScreenHeight));
            final int targetWidth = Math.max(
                    1,
                    Math.round(thumbnail.getWidth()
                            * (targetHeight / (float) thumbnail.getHeight()))
            );
            endScreenBitmap = BitmapCompat.createScaledBitmap(
                    thumbnail,
                    targetWidth,
                    targetHeight,
                    null,
                    true);
        } else {
            endScreenBitmap = thumbnail;
        }

        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onThumbnailLoaded() called with: "
                    + "currentThumbnail = [" + thumbnail + "], "
                    + thumbnail.getWidth() + "x" + thumbnail.getHeight()
                    + ", scaled end screen height = " + endScreenHeight
                    + ", scaled end screen width = " + endScreenBitmap.getWidth());
        }

        clearScaledEndScreenThumbnail();
        if (endScreenBitmap != thumbnail) {
            scaledEndScreenThumbnail = endScreenBitmap;
        }
        binding.endScreen.setImageBitmap(endScreenBitmap);
    }

    static boolean needsThumbnailScaling(final int sourceHeight, final float targetHeight) {
        return targetHeight > 0 && targetHeight < sourceHeight;
    }

    private void clearScaledEndScreenThumbnail() {
        binding.endScreen.setImageDrawable(null);
        if (scaledEndScreenThumbnail != null && !scaledEndScreenThumbnail.isRecycled()) {
            scaledEndScreenThumbnail.recycle();
        }
        scaledEndScreenThumbnail = null;
    }

    protected abstract float calculateMaxEndScreenThumbnailHeight(@NonNull Bitmap bitmap);
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    @Override
    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {

        if (duration != binding.playbackSeekBar.getMax()) {
            setVideoDurationToControls(duration);
        }
        if (player.getCurrentState() != STATE_PAUSED) {
            updatePlayBackElementsCurrentDuration(currentProgress);
        }
        if (player.isLoading() || bufferPercent > 90) {
            binding.playbackSeekBar.setSecondaryProgress(
                    (int) (binding.playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!player.isLiveEdge());
        binding.playbackSeekBar.setEnabled(!player.isNormalLive());
    }

    /**
     * Sets the current duration into the corresponding elements.
     *
     * @param currentProgress the current progress, in milliseconds
     */
    private void updatePlayBackElementsCurrentDuration(final int currentProgress) {
        // Don't set seekbar progress while user is seeking
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            binding.playbackSeekBar.setProgress(currentProgress);
        }
        binding.playbackCurrentTime.setText(getTimeString(currentProgress));
    }

    /**
     * Sets the video duration time into all control components (e.g. seekbar).
     *
     * @param duration the video duration, in milliseconds
     */
    private void setVideoDurationToControls(final int duration) {
        binding.playbackEndTime.setText(getTimeString(duration));

        binding.playbackSeekBar.setMax(duration);
        // This is important for Android TVs otherwise it would apply the default from
        // setMax/Min methods which is (max - min) / 20
        binding.playbackSeekBar.setKeyProgressIncrement(
                PlayerHelper.retrieveSeekDurationFromPreferences(player));
    }

    @Override // seekbar listener
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        // Currently we don't need method execution when fromUser is false
        if (!fromUser) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }
        binding.currentDisplaySeek.setText(getTimeString(progress));
        chapterController.onProgressChanged(binding, progress);
        seekbarPreviewThumbnailHolder.requestBitmapAt(progress, bitmap -> {
            SeekbarPreviewThumbnailHelper.tryResizeAndSetSeekbarPreviewThumbnail(
                    player.getContext(), bitmap, binding.currentSeekbarPreviewThumbnail,
                    binding.subtitleView::getWidth);
            adjustSeekbarPreviewContainer();
        });
    }

    private void adjustSeekbarPreviewContainer() {
        try {
            // Should only be required when an error occurred before
            // and the layout was positioned in the center
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.NO_GRAVITY);

            // Calculate the current left position of seekbar progress in px
            // More info: https://stackoverflow.com/q/20493577
            final int currentSeekbarLeft =
                    binding.playbackSeekBar.getLeft()
                            + binding.playbackSeekBar.getPaddingLeft()
                            + binding.playbackSeekBar.getThumb().getBounds().left;

            // Calculate the (unchecked) left position of the container
            final int uncheckedContainerLeft =
                    currentSeekbarLeft - (binding.seekbarPreviewContainer.getWidth() / 2);

            // Fix the position so it's within the boundaries
            final int checkedContainerLeft = MathUtils.clamp(uncheckedContainerLeft,
                    0, binding.playbackWindowRoot.getWidth()
                            - binding.seekbarPreviewContainer.getWidth());

            // See also: https://stackoverflow.com/a/23249734
            final LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            binding.seekbarPreviewContainer.getLayoutParams());
            params.setMarginStart(checkedContainerLeft);
            binding.seekbarPreviewContainer.setLayoutParams(params);
        } catch (final Exception ex) {
            Log.e(TAG, "Failed to adjust seekbarPreviewContainer", ex);
            // Fallback - position in the middle
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.CENTER);
        }
    }

    @Override // seekbar listener
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            player.changeState(STATE_PAUSED_SEEK);
        }
        showControls(0);
        chapterController.onStartTrackingTouch(binding, seekBar.getProgress());
        animate(binding.currentDisplaySeek, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
    }

    @Override // seekbar listener
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        seekbarPreviewThumbnailHolder.cancelRequest();
        player.seekTo(seekBar.getProgress());
        if (player.getExoPlayer().getDuration() == seekBar.getProgress()) {
            player.getExoPlayer().play();
        }
        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        chapterController.hideTitle(binding);
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, false, 200, AnimationType.SCALE_AND_ALPHA);
        if (player.getCurrentState() == STATE_PAUSED_SEEK) {
            player.changeState(STATE_BUFFERING);
        }
        if (!player.isProgressLoopRunning()) {
            player.startProgressLoop();
        }
        showControlsThenHide();
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    //////////////////////////////////////////////////////////////////////////*/
    //region Controls showing / hiding

    public boolean isControlsVisible() {
        return binding != null && binding.playbackControlRoot.getVisibility() == View.VISIBLE;
    }

    public void showControlsThenHide() {
        tvDirectionalSeek = false;
        updateFullscreenOverlayInsets();
        applyPlayerSeekBarColor();
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }

        showOrHideButtons();
        showSystemUIPartially();

        final boolean touch = binding.playbackControlRoot.isInTouchMode();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, DEFAULT_CONTROLS_DURATION);
        animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.ALPHA, 0, () -> scheduleControlsAutoHide(touch));
    }

    public void showControls(final long duration) {
        tvDirectionalSeek = false;
        updateFullscreenOverlayInsets();
        applyPlayerSeekBarColor();
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        showOrHideButtons();
        showSystemUIPartially();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration);
        animate(binding.playbackControlRoot, true, duration);
    }

    private void scheduleControlsAutoHide(final boolean touch) {
        final long delay = PlayerControlsTimeout.delayMillis(context, player.getPrefs(), touch);
        if (delay >= 0) {
            hideControls(DEFAULT_CONTROLS_DURATION, delay);
        } else {
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
        }
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]");
        }

        showOrHideButtons();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            tvDirectionalSeek = false;
            showHideShadow(false, duration);
            animate(binding.playbackControlRoot, false, duration, AnimationType.ALPHA,
                    0, this::hideSystemUIIfNeeded);
        }, delay);
    }

    public void showHideShadow(final boolean show, final long duration) {
        animate(binding.playbackControlsShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerTopShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerBottomShadow, show, duration, AnimationType.ALPHA, 0, null);
    }

    protected void showOrHideButtons() {
        PlayerNavigationButtons.updateVisibility(player, binding);
    }

    protected void showSystemUIPartially() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected void hideSystemUIIfNeeded() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected boolean isAnyListViewOpen() {
        // only MainPlayerUi has list views for the queue and for segments, so overridden there
        return false;
    }

    public boolean isFullscreen() {
        // only MainPlayerUi can be in fullscreen, so overridden there
        return false;
    }

    /**
     * Update the play/pause button ({@link R.id.playPauseButton}) to reflect the action
     * that will be performed when the button is clicked..
     * @param action the action that is performed when the play/pause button is clicked
     */
    private void updatePlayPauseButton(final PlayButtonAction action) {
        final AppCompatImageButton button = binding.playPauseButton;
        switch (action) {
            case PLAY:
                button.setContentDescription(context.getString(R.string.play));
                button.setImageResource(R.drawable.ic_play_arrow);
                org.schabi.newpipe.themes.ThemeIcons.apply(button, "play");
                break;
            case PAUSE:
                button.setContentDescription(context.getString(R.string.pause));
                button.setImageResource(R.drawable.ic_pause);
                org.schabi.newpipe.themes.ThemeIcons.apply(button, "pause");
                break;
            case REPLAY:
                button.setContentDescription(context.getString(R.string.replay));
                button.setImageResource(R.drawable.ic_replay);
                org.schabi.newpipe.themes.ThemeIcons.apply(button, "replay");
                break;
        }
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states

    @Override
    public void onPrepared() {
        super.onPrepared();
        setVideoDurationToControls((int) player.getExoPlayer().getDuration());
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        binding.playbackSeekBar.setPlaybackActive(false);

        binding.surfaceView.clearAspectRatio();

        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0);

        binding.playbackSeekBar.setEnabled(false);
        applyPlayerSeekBarColor();

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animate(binding.loadingPanel, true, 0);
        animate(binding.surfaceForeground, true, 100);

        updatePlayPauseButton(PlayButtonAction.PLAY);
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onMediaItemTransition() {
        super.onMediaItemTransition();
        videoSurface.onMediaItemTransition();
        seekbarPreviewThumbnailHolder.cancelRequest();
        chapterController.clear(binding);
        binding.surfaceView.clearAspectRatio();
        danmakuController.reset();
        qualityFeedback.reset();
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        binding.playbackSeekBar.setPlaybackActive(true);

        updateStreamRelatedViews();
        danmakuController.start();

        binding.playbackSeekBar.setEnabled(!player.isNormalLive());
        applyPlayerSeekBarColor();

        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        chapterController.hideTitle(binding);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.PAUSE);
                    animatePlayButtons(true, 200);
                    if (shouldRequestPlaybackButtonFocus()) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.suspendForBuffering();
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        binding.loadingPanel.setVisibility(View.VISIBLE);
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onPaused() {
        super.onPaused();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.pause();

        // Don't let UI elements popup during double tap seeking. This state is entered sometimes
        // during seeking/loading. This if-else check ensures that the controls aren't popping up.
        if (!playerGestureListener.isDoubleTapping()) {
            showControls(400);
            binding.loadingPanel.setVisibility(View.GONE);

            animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                    () -> {
                        updatePlayPauseButton(PlayButtonAction.PLAY);
                        animatePlayButtons(true, 200);
                        if (shouldRequestPlaybackButtonFocus()) {
                        binding.playPauseButton.requestFocus();
                    }
                    });
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.suspendForBuffering();
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.pause();
        binding.danmakuOverlay.clearComments();

        animate(binding.playPauseButton, false, 0, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.REPLAY);
                    animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                });

        binding.getRoot().setKeepScreenOn(false);

        // When a (short) video ends the elements have to display the correct values - see #6180
        updatePlayBackElementsCurrentDuration(binding.playbackSeekBar.getMax());

        showControls(500);
        binding.screenRotationButton.setVisibility(isFullscreen() ? View.VISIBLE
                : binding.screenRotationButton.getVisibility());
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        binding.loadingPanel.setVisibility(View.GONE);
        animate(binding.surfaceForeground, true, 100);
    }

    static boolean shouldRequestPlaybackButtonFocus(final boolean tv,
                                                    final boolean fullscreen,
                                                    final boolean popup,
                                                    final boolean listOpen,
                                                    final boolean hasFocus) {
        return !listOpen && (!tv || ((fullscreen || popup) && !hasFocus));
    }

    protected boolean shouldRequestPlaybackButtonFocus() {
        return shouldRequestPlaybackButtonFocus(
                DeviceUtils.isTv(context),
                isFullscreen(),
                player.popupPlayerSelected(),
                isAnyListViewOpen(),
                binding.getRoot().getRootView().findFocus() != null);
    }

    private void animatePlayButtons(final boolean show, final long duration) {
        animate(binding.playPauseButton, show, duration, AnimationType.SCALE_AND_ALPHA);

        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        if (!show || PlayerNavigationButtons.useSeekButtons(player) || playQueue.getIndex() > 0) {
            animate(
                    binding.playPreviousButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
        if (!show || PlayerNavigationButtons.useSeekButtons(player)
                || playQueue.getIndex() + 1 < playQueue.getStreams().size()) {
            animate(
                    binding.playNextButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Repeat, shuffle, mute
    //////////////////////////////////////////////////////////////////////////*/
    //region Repeat, shuffle, mute

    public void onRepeatClicked() {
        if (DEBUG) {
            Log.d(TAG, "onRepeatClicked() called");
        }
        player.cycleNextRepeatMode();
    }

    public void onShuffleClicked() {
        if (DEBUG) {
            Log.d(TAG, "onShuffleClicked() called");
        }
        player.toggleShuffleModeEnabled();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);

        setRepeatButton(repeatMode);
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        setShuffleButton(shuffleModeEnabled);
    }

    @Override
    public void onMuteUnmuteChanged(final boolean isMuted) {
        super.onMuteUnmuteChanged(isMuted);
        setMuteButton(isMuted);
    }

    private void setMuteButton(final boolean isMuted) {
        binding.switchMute.setImageDrawable(AppCompatResources.getDrawable(context, isMuted
                ? R.drawable.ic_volume_off : R.drawable.ic_volume_up));
    }

    private void setShuffleButton(final boolean shuffled) {
        binding.shuffleButton.setImageAlpha(shuffled ? 255 : 77);
    }

    private void setRepeatButton(final int repeatMode) {
        final int resId = switch (repeatMode) {
            case REPEAT_MODE_ALL
                    -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_all;
            case REPEAT_MODE_ONE
                    -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_one;
            default -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_off;
        };
        binding.repeatButton.setImageResource(resId);
        org.schabi.newpipe.themes.ThemeIcons.apply(binding.repeatButton,
                repeatMode == REPEAT_MODE_ALL ? "repeatAll"
                        : repeatMode == REPEAT_MODE_ONE ? "repeatOne" : "repeatOff");
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Other player listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Other player listeners

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        binding.playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onRenderedFirstFrame() {
        super.onRenderedFirstFrame();
        restoreVideoAspectRatioFromPlayer();
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Metadata & stream related views
    //////////////////////////////////////////////////////////////////////////*/
    //region Metadata & stream related views

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        videoSurface.selectItem(info.getServiceId() + ":" + info.getUrl(), info.isSpherical());
        chapterController.setChapters(binding, info.getStreamSegments());
        binding.surfaceView.resetUserTransform();

        danmakuController.onMetadataChanged(info);
        updateStreamRelatedViews();

        binding.titleTextView.setText(info.getName());
        binding.channelTextView.setText(info.getUploaderName());
        binding.channelAvatarView.setContentDescription(info.getUploaderName());
        CoilHelper.INSTANCE.loadAvatar(binding.channelAvatarView,
                ExtractorImageCompat.uploaderAvatarImages(info));

        this.seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), info.getPreviewFrames());
        videoSurface.presentationChanged();
    }

    @Override
    public void onMetadataChanged(@NonNull final MediaItemTag tag) {
        super.onMetadataChanged(tag);
        binding.sponsorBlockContributeButton.setVisibility(View.GONE);
        videoSurface.selectItem(tag.getServiceId() + ":" + tag.getStreamUrl(), false);
        chapterController.clear(binding);
        danmakuController.reset();
        binding.surfaceView.resetUserTransform();
        binding.qualityTextView.setVisibility(View.GONE);
        binding.audioTrackTextView.setVisibility(View.GONE);
        DirectMediaPresentation.updateLiveControls(binding.playbackLiveSync,
                binding.playbackEndTime, tag.getStreamType());
        DirectMediaPresentation.update(binding.surfaceView, binding.endScreen,
                tag.getStreamType(), player.getPlaybackPresentationMode());
        updateEndScreenThumbnail(player.getThumbnail());
        buildPlaybackSpeedMenu();
        binding.playbackSpeed.setVisibility(View.VISIBLE);
        binding.titleTextView.setText(tag.getTitle());
        binding.channelTextView.setText(tag.getUploaderName());
        binding.channelAvatarView.setContentDescription(tag.getUploaderName());
        CoilHelper.INSTANCE.clearAvatar(binding.channelAvatarView);
        binding.channelAvatarView.setImageResource(R.drawable.placeholder_person);
        seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), Collections.emptyList());
        videoSurface.presentationChanged();
    }

    public void onPlaybackTimelineChanged() {
        ArchivePresentation.onTimelineChanged(player, binding);
    }

    protected void updateStreamRelatedViews() {
        SponsorBlockContributionMenu.bind(binding.sponsorBlockContributeButton, player);
        if (!player.getPlaybackPresentationMode().rendersVideo()) {
            binding.qualityTextView.setVisibility(View.GONE);
            binding.audioTrackTextView.setVisibility(View.GONE);
            binding.surfaceView.setVisibility(View.GONE);
            binding.endScreen.setVisibility(player.getPlaybackPresentationMode().allowsVisualizer()
                    ? View.GONE : View.VISIBLE);
            binding.playbackLiveSync.setVisibility(View.GONE);
            binding.playbackEndTime.setVisibility(View.VISIBLE);
            buildPlaybackSpeedMenu();
            binding.playbackSpeed.setVisibility(View.VISIBLE);
            return;
        }
        player.getCurrentStreamInfo().ifPresent(info -> {
            binding.qualityTextView.setVisibility(View.GONE);
            binding.audioTrackTextView.setVisibility(View.GONE);
            binding.playbackSpeed.setVisibility(View.GONE);

            binding.playbackEndTime.setVisibility(View.GONE);
            binding.playbackLiveSync.setVisibility(View.GONE);

            switch (info.getStreamType()) {
                case AUDIO_STREAM:
                case POST_LIVE_AUDIO_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;

                case AUDIO_LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    break;

                case LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    if (ArchivePresentation.isEnded(player.getExoPlayer())) {
                        ArchivePresentation.show(binding);
                    }
                    updateQualityLabel(null);
                    break;

                case VIDEO_STREAM:
                case POST_LIVE_STREAM:
                    ArchivePresentation.show(binding);
                    if (player.isLiveQualityPlayback()) {
                        updateQualityLabel(null);
                        break;
                    }
                    if (player.getCurrentMetadata() != null
                            && player.getCurrentMetadata().getMaybeQuality().isEmpty()
                            || (info.getVideoStreams().isEmpty()
                            && info.getVideoOnlyStreams().isEmpty())) {
                        break;
                    }

                    buildQualityMenu();
                    buildAudioTrackMenu();

                    binding.qualityTextView.setVisibility(View.VISIBLE);
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    // fallthrough
                default:
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;
            }

            buildPlaybackSpeedMenu();
            binding.playbackSpeed.setVisibility(View.VISIBLE);
        });
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup menus ("popup" means that they pop up, not that they belong to the popup player)

    private void buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return;
        }
        qualityPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_QUALITY);

        if (player.isLiveQualityPlayback()) {
            buildLiveQualityMenu();
            return;
        }

        final List<VideoStream> availableStreams = Optional.ofNullable(player.getCurrentMetadata())
                .flatMap(MediaItemTag::getMaybeQuality)
                .map(MediaItemTag.Quality::getSortedVideoStreams)
                .orElse(null);
        if (availableStreams == null) {
            return;
        }

        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                AUTO_QUALITY_MENU_ITEM_ID,
                Menu.NONE,
                R.string.auto);
        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                BEST_QUALITY_MENU_ITEM_ID,
                Menu.NONE,
                R.string.best_resolution);
        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.getResolution());
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
        updateQualityLabel(null);
    }

    private void buildLiveQualityMenu() {
        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                0,
                Menu.NONE,
                R.string.auto);

        final List<LiveQualityController.Option> options = player.getLiveQualityOptions();
        for (int index = 0; index < options.size(); index++) {
            qualityPopupMenu.getMenu().add(
                    POPUP_MENU_ID_QUALITY,
                    index + 1,
                    Menu.NONE,
                    options.get(index).getLabel());
        }

        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
        updateQualityLabel(null);
    }

    private void buildAudioTrackMenu() {
        buildAudioTrackMenu(false);
    }

    private void buildAudioTrackMenu(final boolean showAll) {
        if (audioTrackPopupMenu == null) {
            return;
        }
        audioTrackPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_AUDIO_TRACK);

        final List<AudioStream> availableStreams = Optional.ofNullable(player.getCurrentMetadata())
                .flatMap(MediaItemTag::getMaybeAudioTrack)
                .map(MediaItemTag.AudioTrack::getAudioStreams)
                .orElse(null);
        if (availableStreams == null || availableStreams.size() < 2) {
            return;
        }

        org.schabi.newpipe.player.ui.PlaybackLanguageMenu.audio(context,
                audioTrackPopupMenu.getMenu(), POPUP_MENU_ID_AUDIO_TRACK, availableStreams, showAll,
                () -> binding.audioTrackTextView.post(() -> {
                    buildAudioTrackMenu(true);
                    showPopupMenu(audioTrackPopupMenu, binding.audioTrackTextView);
                }));
        player.getSelectedAudioStream()
                .ifPresent(s -> binding.audioTrackTextView.setText(
                        Localization.audioTrackName(context, s)));
        binding.audioTrackTextView.setVisibility(View.VISIBLE);
        audioTrackPopupMenu.setOnMenuItemClickListener(this);
        audioTrackPopupMenu.setOnDismissListener(this);
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return;
        }
        playbackSpeedPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_PLAYBACK_SPEED);

        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            playbackSpeedPopupMenu.getMenu().add(POPUP_MENU_ID_PLAYBACK_SPEED, i, Menu.NONE,
                    formatSpeed(PLAYBACK_SPEEDS[i]));
        }
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private List<String> captionMenuLanguages = java.util.Collections.emptyList();

    private void buildCaptionMenu(@NonNull final List<String> availableLanguages) {
        captionMenuLanguages = availableLanguages;
        buildCaptionMenu(availableLanguages, false);
    }

    private void buildCaptionMenu(@NonNull final List<String> availableLanguages,
                                  final boolean showAll) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_CAPTION);

        captionPopupMenu.setOnDismissListener(this);
        PlaybackLanguageMenu.captions(context, captionPopupMenu.getMenu(), POPUP_MENU_ID_CAPTION,
                availableLanguages, showAll, binding.captionTextView, player,
                player::setCaptionPreference,
                () -> binding.captionTextView.post(() -> {
                    buildCaptionMenu(availableLanguages, true);
                    showPopupMenu(captionPopupMenu, binding.captionTextView);
                }));

        // apply caption language from previous user preference
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        // If user prefers to show no caption, then disable the renderer.
        // Otherwise, DefaultTrackSelector may automatically find an available caption
        // and display that.
        final String userPreferredLanguage = player.getCaptionPreference();
        if (userPreferredLanguage == null) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setRendererDisabled(textRendererIndex, true));
            return;
        }

        // Avoid repeat track-selector updates on track notifications.
        final List<String> selectedPreferredLanguages =
                player.getTrackSelector().getParameters().preferredTextLanguages;
        if (player.getTrackSelector().getParameters().getRendererDisabled(textRendererIndex)
                || !selectedPreferredLanguages.contains(userPreferredLanguage)) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setPreferredTextLanguages(userPreferredLanguage,
                            PlayerHelper.captionLanguageStemOf(userPreferredLanguage))
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false));
        }
    }

    protected abstract void onPlaybackSpeedClicked();

    private void onQualityClicked() {
        if (player.isLiveQualityPlayback()) {
            buildQualityMenu();
        }
        showPopupMenu(qualityPopupMenu, binding.qualityTextView);
        updateQualityLabel(null);
    }

    private void onAudioTracksClicked() {
        buildAudioTrackMenu();
        showPopupMenu(audioTrackPopupMenu, binding.audioTrackTextView);
    }

    /**
     * Called when an item of the quality selector or the playback speed selector is selected.
     */
    @Override
    public boolean onMenuItemClick(@NonNull final MenuItem menuItem) {
        if (DEBUG) {
            Log.d(TAG, "onMenuItemClick() called with: "
                    + "menuItem = [" + menuItem + "], "
                    + "menuItem.getItemId = [" + menuItem.getItemId() + "]");
        }

        if (menuItem.getGroupId() == POPUP_MENU_ID_QUALITY) {
            onQualityItemClick(menuItem);
            return true;
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_AUDIO_TRACK) {
            onAudioTrackItemClick(menuItem);
            return true;
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_PLAYBACK_SPEED) {
            final int speedIndex = menuItem.getItemId();
            final float speed = PLAYBACK_SPEEDS[speedIndex];

            player.setPlaybackSpeed(speed);
            binding.playbackSpeed.setText(formatSpeed(speed));
        }

        return false;
    }

    private void onQualityItemClick(@NonNull final MenuItem menuItem) {
        if (player.isLiveQualityPlayback()) {
            qualityFeedback.reset();
            final String label = player.selectLiveQuality(menuItem.getItemId());
            if (label != null) {
                binding.qualityTextView.setText(label);
            }
            return;
        }

        if (menuItem.getItemId() == AUTO_QUALITY_MENU_ITEM_ID) {
            if (!player.isAutoQualitySelected()) {
                player.setPlaybackQuality(context.getString(R.string.auto_resolution_key));
            }
            binding.qualityTextView.setText(R.string.auto);
            return;
        }
        if (menuItem.getItemId() == BEST_QUALITY_MENU_ITEM_ID) {
            player.setPlaybackQuality(context.getString(R.string.best_resolution_key));
            binding.qualityTextView.setText(R.string.best_resolution);
            return;
        }

        final int menuItemIndex = menuItem.getItemId();
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata == null || currentMetadata.getMaybeQuality().isEmpty()) {
            return;
        }

        final MediaItemTag.Quality quality = currentMetadata.getMaybeQuality().get();
        final List<VideoStream> availableStreams = quality.getSortedVideoStreams();
        final int selectedStreamIndex = quality.getSelectedVideoStreamIndex();
        if (!player.isAutoQualitySelected()
                && !quality.isAdaptive()
                && selectedStreamIndex == menuItemIndex) {
            return;
        }
        if (menuItemIndex < 0 || availableStreams.size() <= menuItemIndex) {
            return;
        }

        final String newResolution = availableStreams.get(menuItemIndex).getResolution();
        player.setPlaybackQuality(newResolution);
        binding.qualityTextView.setText(menuItem.getTitle());
    }

    private void onAudioTrackItemClick(@NonNull final MenuItem menuItem) {
        final int menuItemIndex = menuItem.getItemId();
        @Nullable final MediaItemTag currentMetadata = player.getCurrentMetadata();
        if (currentMetadata == null || currentMetadata.getMaybeAudioTrack().isEmpty()) {
            return;
        }

        final MediaItemTag.AudioTrack audioTrack =
                currentMetadata.getMaybeAudioTrack().get();
        final List<AudioStream> availableStreams = audioTrack.getAudioStreams();
        final int selectedStreamIndex = audioTrack.getSelectedAudioStreamIndex();
        if (selectedStreamIndex == menuItemIndex || availableStreams.size() <= menuItemIndex) {
            return;
        }

        final String newAudioTrack = availableStreams.get(menuItemIndex).getAudioTrackId();
        player.setAudioTrack(newAudioTrack);

        binding.audioTrackTextView.setText(menuItem.getTitle());
    }

    /**
     * Called when some popup menu is dismissed.
     */
    @Override
    public void onDismiss(@Nullable final PopupMenu menu) {
        if (dismissingPopupMenus) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]");
        }
        isSomePopupMenuVisible = false; //TODO check if this works
        updateQualityLabel(null);

        if (player.isPlaying()) {
            hideControls(DEFAULT_CONTROLS_DURATION, 0);
            hideSystemUIIfNeeded();
        }
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }
        buildCaptionMenu(captionMenuLanguages);
        showPopupMenu(captionPopupMenu, binding.captionTextView);
    }

    protected void showPopupMenu(final PopupMenu menu, final View anchor) {
        dismissPopupMenus();
        isSomePopupMenuVisible = playerPopupMenu.show(menu, anchor, () -> onDismiss(menu));
    }

    protected void dismissPopupMenus() {
        dismissingPopupMenus = true;
        try {
            playerPopupMenu.dismiss();
        } finally {
            isSomePopupMenuVisible = false;
            dismissingPopupMenus = false;
        }
    }

    @Override
    public void smoothStopForImmediateReusing() {
        dismissPopupMenus();
        super.smoothStopForImmediateReusing();
    }

    public boolean isSomePopupMenuVisible() {
        return isSomePopupMenuVisible;
    }
    //endregion

    private void updateQualityLabel(@Nullable final Tracks currentTracks) {
        if (player.isLiveQualityPlayback()) {
            binding.qualityTextView.setText(player.getCurrentLiveQualityLabel());
            binding.qualityTextView.setVisibility(
                    player.hasSelectableLiveQualities() ? View.VISIBLE : View.GONE);
            return;
        }

        if (!player.isAutoQualitySelected()) {
            player.getSelectedVideoStream()
                    .ifPresent(stream -> binding.qualityTextView.setText(stream.getResolution()));
            return;
        }

        final String playingResolution = player.exoPlayerIsNull() ? null
                : LiveQualityController.resolutionFromFormat(
                        player.getExoPlayer().getVideoFormat());
        final String currentResolution = playingResolution != null ? playingResolution
                : currentTracks == null
                ? player.getSelectedVideoStream()
                        .map(VideoStream::getResolution)
                        .orElse(null)
                : qualityFeedback.selectedAdaptiveResolution(currentTracks);
        binding.qualityTextView.setText(currentResolution == null
                ? context.getString(R.string.auto)
                : context.getString(R.string.auto) + " · " + currentResolution);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    @Override
    public void onTextTracksChanged(@NonNull final Tracks currentTracks) {
        super.onTextTracksChanged(currentTracks);
        updateQualityLabel(currentTracks);

        final boolean trackTypeTextSupported = !currentTracks.containsType(C.TRACK_TYPE_TEXT)
                || currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT, false);
        if (getPlayer().getTrackSelector().getCurrentMappedTrackInfo() == null
                || !trackTypeTextSupported) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        // Extract all loaded languages
        final List<Tracks.Group> textTracks = currentTracks
                .getGroups()
                .stream()
                .filter(trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.getType())
                .collect(Collectors.toList());
        final List<String> availableLanguages = textTracks.stream()
                .map(Tracks.Group::getMediaTrackGroup)
                .filter(textTrack -> textTrack.length > 0)
                .map(textTrack -> textTrack.getFormat(0).language)
                .collect(Collectors.toList());

        // Find selected text track
        final Optional<Format> selectedTracks = textTracks.stream()
                .filter(Tracks.Group::isSelected)
                .filter(info -> info.getMediaTrackGroup().length >= 1)
                .map(info -> info.getMediaTrackGroup().getFormat(0))
                .findFirst();

        // Build UI
        buildCaptionMenu(availableLanguages);
        if (player.getTrackSelector().getParameters().getRendererDisabled(
                player.getCaptionRendererIndex()) || selectedTracks.isEmpty()) {
            binding.captionTextView.setContentDescription(
                    context.getString(R.string.caption_setting_title) + ": "
                            + context.getString(R.string.caption_none));
            binding.captionTextView.setActivated(false);
            binding.captionTextView.setAlpha(0.7f);
        } else {
            binding.captionTextView.setContentDescription(
                    context.getString(R.string.caption_setting_title) + ": "
                            + selectedTracks.get().language);
            binding.captionTextView.setActivated(true);
            binding.captionTextView.setAlpha(1.0f);
        }
        binding.captionTextView.setVisibility(
                availableLanguages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onCues(@NonNull final List<Cue> cues) {
        super.onCues(cues);
        binding.subtitleView.setCues(cues);
    }

    private void setupSubtitleView() {
        setupSubtitleView(PlayerHelper.getCaptionScale(context));
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        binding.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(captionStyle);
    }

    /**
     *
     * @param captionScale Value returned by {@link PlayerHelper#getCaptionScale}.
     */
    protected abstract void setupSubtitleView(float captionScale);
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Click listeners

    /**
     * Create on-click listener which manages the player controls after the view on-click action.
     *
     * @param runnable The action to be executed.
     * @return The view click listener.
     */
    protected View.OnClickListener makeOnClickListener(@NonNull final Runnable runnable) {
        return v -> {
            tvDirectionalSeek = false;
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }

            runnable.run();

            // Manages the player controls after handling the view click.
            if (player.getCurrentState() == STATE_COMPLETED) {
                return;
            }
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
            showHideShadow(true, DEFAULT_CONTROLS_DURATION);
            animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                    AnimationType.ALPHA, 0, () -> {
                        if (player.getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible) {
                            if (v == binding.playPauseButton
                                    // Hide controls in fullscreen immediately
                                    || (v == binding.screenRotationButton && isFullscreen())) {
                                hideControls(0, 0);
                            } else {
                                scheduleControlsAutoHide(v.isInTouchMode());
                            }
                        }
                    });
        };
    }

    public boolean onKeyDown(final int keyCode) {
        resetTvSeekOnNavigation(keyCode);
        // Embedded playback must not consume navigation intended for the detail page.
        if (DeviceUtils.isTv(context) && !isFullscreen() && !player.popupPlayerSelected()) {
            return false;
        }
        if (videoSurface.handleKey(keyCode,
                isControlsVisible() || isAnyListViewOpen() || isSomePopupMenuVisible)) {
            return true;
        }
        if (DeviceUtils.isTv(context) && TvPlayerKeyPolicy.shouldSeek(
                keyCode, isControlsVisible() && !tvDirectionalSeek,
                isAnyListViewOpen() || isSomePopupMenuVisible) && player.getCurrentState()
                != org.schabi.newpipe.player.Player.STATE_BLOCKED) {
            if (TvPlayerKeyPolicy.isForwardSeek(keyCode)) {
                player.fastForward();
            } else {
                player.fastRewind();
            }
            showControlsThenHide();
            // These controls provide seek feedback. Further horizontal presses should
            // continue seeking until the user explicitly navigates into the controls.
            tvDirectionalSeek = true;
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (DeviceUtils.isTv(context)) {
                    final long now = android.os.SystemClock.elapsedRealtime();
                    if (TvPlayerKeyPolicy.shouldCloseOnSecondBack(lastTvBackPressedAt, now)) {
                        lastTvBackPressedAt = 0L;
                        context.sendBroadcast(
                                new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER)
                                        .setPackage(App.PACKAGE_NAME));
                        return true;
                    }
                    lastTvBackPressedAt = now;
                    if (isControlsVisible()) {
                        hideControls(0, 0);
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if ((binding.getRoot().hasFocus() && !binding.playbackControlRoot.hasFocus())
                        || isAnyListViewOpen()) {
                    // do not interfere with focus in playlist and play queue etc.
                    break;
                }

                if (player.getCurrentState() == org.schabi.newpipe.player.Player.STATE_BLOCKED) {
                    return true;
                }

                if (isControlsVisible()) {
                    scheduleControlsAutoHide(false);
                } else {
                    binding.playPauseButton.requestFocus();
                    showControlsThenHide();
                    showSystemUIPartially();
                    return true;
                }
                break;
            default:
                break; // ignore other keys
        }

        return false;
    }

    protected void resetTvSeekOnNavigation(final int keyCode) {
        if (!TvPlayerKeyPolicy.isSeekKey(keyCode)) {
            tvDirectionalSeek = false;
        }
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) {
            Log.d(TAG, "onMoreOptionsClicked() called");
        }

        final boolean isMoreControlsVisible =
                binding.secondaryControls.getVisibility() == View.VISIBLE;

        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                isMoreControlsVisible ? 0 : 180);
        animate(binding.secondaryControls, !isMoreControlsVisible, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA, 0, () -> {
                    // Fix for a ripple effect on background drawable.
                    // When view returns from GONE state it takes more milliseconds than returning
                    // from INVISIBLE state. And the delay makes ripple background end to fast
                    if (isMoreControlsVisible) {
                        binding.secondaryControls.setVisibility(View.INVISIBLE);
                    }
                });
        showControls(DEFAULT_CONTROLS_DURATION);
    }

    private void onPlayWithKodiClicked() {
        if (player.getCurrentMetadata() != null) {
            player.pause();
            KoreUtils.playWithKore(context, Uri.parse(player.getVideoUrl()));
        }
    }

    private void onOpenInBrowserClicked() {
        player.getCurrentStreamInfo().ifPresent(streamInfo ->
                ShareUtils.openUrlInBrowser(player.getContext(), streamInfo.getOriginalUrl()));
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size

    protected void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        binding.surfaceView.setResizeMode(resizeMode);
        binding.resizeTextView.setText(PlayerHelper.resizeTypeOf(context, resizeMode));
    }

    void onResizeClicked() {
        binding.surfaceView.resetUserTransform();
        setResizeMode(nextResizeModeAndSaveToPrefs(player, binding.surfaceView.getResizeMode()));
    }

    @Override
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        super.onVideoSizeChanged(videoSize);
        applyVideoAspectRatio(videoSize);
        if (videoSize.width <= 0 || videoSize.height <= 0) {
            return;
        }
        updateQualityLabel(null);
        qualityFeedback.onVideoSizeChanged(videoSize, binding.getRoot());
    }

    private void applyVideoAspectRatio(@NonNull final VideoSize videoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            PlayerVideoGeometry.apply(binding.surfaceView, videoSize);
        }
    }

    private void restoreVideoAspectRatioFromPlayer() {
        final ExoPlayer exoPlayer = player.getExoPlayer();
        if (exoPlayer != null) {
            applyVideoAspectRatio(exoPlayer.getVideoSize());
        }
    }

    static float calculateDisplayAspectRatio(final int width,
                                             final int height,
                                             final int unappliedRotationDegrees,
                                             final float pixelWidthHeightRatio) {
        return PlayerVideoGeometry.calculate(width, height, unappliedRotationDegrees,
                pixelWidthHeightRatio);
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // SurfaceHolderCallback helpers
    //////////////////////////////////////////////////////////////////////////*/
    //region SurfaceHolderCallback helpers

    /**
     * Connects the video surface to the exo player. This can be called anytime without the risk for
     * issues to occur, since the player will run just fine when no surface is connected. Therefore
     * the video surface will be setup only when all of these conditions are true: it is not already
     * setup (this just prevents wasting resources to setup the surface again), there is an exo
     * player, the root view is attached to a parent and the surface view is valid/unreleased (the
     * latter two conditions prevent "The surface has been released" errors). So this function can
     * be called many times and even while the UI is in unready states.
     */
    public void setupVideoSurfaceIfNeeded() {
        videoSurface.setup();
    }

    protected final void restoreVideoSurfaceAfterResume() {
        restoreVideoSurfaceAfterLayoutTransition();
    }

    public final void restoreVideoSurfaceAfterLayoutTransition() {
        if (!player.getPlaybackPresentationMode().rendersVideo()) {
            return;
        }
        setupVideoSurfaceIfNeeded();
        binding.videoSurfaceContainer.post(() -> {
            restoreVideoAspectRatioFromPlayer();
            videoSurface.restore();
        });
    }

    private void clearVideoSurface() {
        videoSurface.clear();
    }

    public View getRenderedVideoSurface() {
        return videoSurface.activeVideoView();
    }

    public boolean isSphericalVideo() {
        return videoSurface.isSpherical();
    }

    public boolean handleSphericalTouch(final View source, final MotionEvent event) {
        return videoSurface.handleTouch(source, event);
    }

    @Override
    public void onPlaybackPresentationModeChanged(@NonNull final PlaybackPresentationMode mode) {
        videoSurface.presentationChanged();
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    public PlayerBinding getBinding() {
        return binding;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }
    //endregion
}
