package org.schabi.newpipe.player.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.QueueItemMenuUtil.openPopupMenu;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimizeOnExitAction;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.video.VideoSize;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.info_list.StreamSegmentItem;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MainPlayerUi extends VideoPlayerUi implements View.OnLayoutChangeListener {
    private static final String TAG = MainPlayerUi.class.getSimpleName();

    // see the Javadoc of calculateMaxEndScreenThumbnailHeight for information
    private static final int DETAIL_ROOT_MINIMUM_HEIGHT = 85; // dp
    private static final int DETAIL_TITLE_TEXT_SIZE_TV = 16; // sp
    private static final int DETAIL_TITLE_TEXT_SIZE_TABLET = 15; // sp

    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    private boolean fragmentIsVisible = false;

    private ContentObserver settingsContentObserver;

    private PlayQueueAdapter playQueueAdapter;
    private StreamSegmentAdapter segmentAdapter;
    private boolean isQueueVisible = false;
    private boolean areSegmentsVisible = false;

    // fullscreen player
    private ItemTouchHelper itemTouchHelper;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor, setup, destroy

    public MainPlayerUi(@NonNull final Player player,
                        @NonNull final PlayerBinding playerBinding) {
        super(player, playerBinding);
    }

    /**
     * Open fullscreen on tablets where the option to have the main player start automatically in
     * fullscreen mode is on. Rotating the device to landscape is already done in {@link
     * VideoDetailFragment#openVideoPlayer(boolean)} when the thumbnail is clicked, and that's
     * enough for phones, but not for tablets since the mini player can be also shown in landscape.
     */
    private void directlyOpenFullscreenIfNeeded() {
        if (PlayerHelper.isStartMainPlayerFullscreenEnabled(player.getService())
                && DeviceUtils.isTablet(player.getService())
                && PlayerHelper.globalScreenOrientationLocked(player.getService())) {
            player.getFragmentListener().ifPresent(
                    PlayerServiceEventListener::onScreenRotationButtonClicked);
        }
    }

    @Override
    public void setupAfterIntent() {
        // needed for tablets, check the function for a better explanation
        directlyOpenFullscreenIfNeeded();

        super.setupAfterIntent();

        initVideoPlayer();
        // Android TV: without it focus will frame the whole player
        binding.playPauseButton.requestFocus();

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

    @Override
    protected void initListeners() {
        super.initListeners();

        binding.screenRotationButton.setOnClickListener(makeOnClickListener(() -> {
            // Only if it's not a vertical video or vertical video but in landscape with locked
            // orientation a screen orientation can be changed automatically
            if (!isVerticalVideo || (isLandscape() && globalScreenOrientationLocked(context))) {
                player.getFragmentListener()
                        .ifPresent(PlayerServiceEventListener::onScreenRotationButtonClicked);
            } else {
                toggleFullscreen();
            }
        }));
        binding.queueButton.setOnClickListener(v -> onQueueClicked());
        binding.segmentsButton.setOnClickListener(v -> onSegmentsClicked());

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
    protected void deinitListeners() {
        super.deinitListeners();

        binding.queueButton.setOnClickListener(null);
        binding.segmentsButton.setOnClickListener(null);
        binding.addToPlaylistButton.setOnClickListener(null);

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
    }

    @Override
    public void removeViewFromParent() {
        // view was added to fragment
        final ViewParent parent = binding.getRoot().getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(binding.getRoot());
        }
    }

    @Override
    public void destroy() {
        super.destroy();

        // Exit from fullscreen when user closes the player via notification
        if (isFullscreen) {
            toggleFullscreen();
        }

        removeViewFromParent();
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    @Override
    public void smoothStopForImmediateReusing() {
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
        binding.switchMute.setVisibility(View.VISIBLE);
        binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        // Top controls have a large minHeight which is allows to drag the player
        // down in fullscreen mode (just larger area to make easy to locate by finger)
        binding.topControls.setClickable(true);
        binding.topControls.setFocusable(true);

        binding.titleTextView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
        binding.channelTextView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        setupElementsSize(
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_min_width),
                resources.getDimensionPixelSize(R.dimen.player_main_top_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_padding)
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
            onFragmentStopped();
        } else if (VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED.equals(intent.getAction())) {
            // Restore video source when user returns to the fragment
            fragmentIsVisible = true;
            player.useVideoSource(true);

            // When a user returns from background, the system UI will always be shown even if
            // controls are invisible: hide it in that case
            if (!isControlsVisible()) {
                hideSystemUIIfNeeded();
            }
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
                    player.useVideoSource(false);
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

        if (areSegmentsVisible) {
            segmentAdapter.selectSegmentAt(getNearestStreamSegmentPosition(currentProgress));
        }
        if (isQueueVisible) {
            updateQueueTime(currentProgress);
        }
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        checkLandscape();
    }

    @Override
    public void onCompleted() {
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

        final boolean showQueue = playQueue.getStreams().size() > 1;
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
            getParentActivity().map(Activity::getWindow).ifPresent(window -> {
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
                final int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                window.getDecorView().setSystemUiVisibility(visibility);
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
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
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
        final float captionRatioInverse = 20f + 4f * (1.0f - captionScale);
        binding.subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_PX, minimumLength / captionRatioInverse);
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
            binding.itemsListPanel.getLayoutParams().height =
                    height - binding.itemsListPanel.getTop();
        }
    }

    private void setInitialGestureValues() {
        if (player.getAudioReactor() != null) {
            final float currentVolumeNormalized = (float) player.getAudioReactor().getVolume()
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
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        showHideKodiButton();
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
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        showOrHideButtons();
    }

    private void onQueueClicked() {
        isQueueVisible = true;

        hideSystemUIIfNeeded();
        buildQueue();

        binding.itemsListHeaderTitle.setVisibility(View.GONE);
        binding.itemsListHeaderDuration.setVisibility(View.VISIBLE);
        binding.shuffleButton.setVisibility(View.VISIBLE);
        binding.repeatButton.setVisibility(View.VISIBLE);
        binding.addToPlaylistButton.setVisibility(View.VISIBLE);

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
                            videoUrl, currentItem.getThumbnailUrl());
                }
            }
        };
    }

    private int getNearestStreamSegmentPosition(final long playbackPosition) {
        //noinspection SimplifyOptionalCallChains
        if (!player.getCurrentStreamInfo().isPresent()) {
            return 0;
        }

        int nearestPosition = 0;
        final List<StreamSegment> segments = player.getCurrentStreamInfo()
                .get()
                .getStreamSegments();

        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getStartTimeSeconds() * 1000L > playbackPosition) {
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
        int before = 0;
        int after = 0;

        final List<PlayQueueItem> streams = playQueue.getStreams();
        final int nStreams = streams.size();

        for (int i = 0; i < nStreams; i++) {
            if (i < currentStream) {
                before += streams.get(i).getDuration();
            } else {
                after += streams.get(i).getDuration();
            }
        }

        before *= 1000;
        after *= 1000;

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
        return isVerticalVideo;
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Click listeners

    @Override
    protected void onPlaybackSpeedClicked() {
        final AppCompatActivity activity = getParentActivity().orElse(null);
        if (activity == null) {
            return;
        }

        PlaybackParameterDialog.newInstance(player.getPlaybackSpeed(), player.getPlaybackPitch(),
                player.getPlaybackSkipSilence(), player::setPlaybackParameters)
                .show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public boolean onKeyDown(final int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_SPACE && isFullscreen) {
            player.playPause();
            if (player.isPlaying()) {
                hideControls(0, 0);
            }
            return true;
        }
        return super.onKeyDown(keyCode);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Video size, orientation, fullscreen
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size, orientation, fullscreen

    private void setupScreenRotationButton() {
        binding.screenRotationButton.setVisibility(globalScreenOrientationLocked(context)
                || isVerticalVideo || DeviceUtils.isTablet(context)
                ? View.VISIBLE : View.GONE);
        binding.screenRotationButton.setImageDrawable(AppCompatResources.getDrawable(context,
                isFullscreen ? R.drawable.ic_fullscreen_exit
                        : R.drawable.ic_fullscreen));
    }

    @Override
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        super.onVideoSizeChanged(videoSize);
        isVerticalVideo = videoSize.width < videoSize.height;

        if (globalScreenOrientationLocked(context)
                && isFullscreen
                && isLandscape() == isVerticalVideo
                && !DeviceUtils.isTv(context)
                && !DeviceUtils.isTablet(context)) {
            // set correct orientation
            player.getFragmentListener().ifPresent(
                    PlayerServiceEventListener::onScreenRotationButtonClicked);
        }

        setupScreenRotationButton();
    }

    public void toggleFullscreen() {
        if (DEBUG) {
            Log.d(TAG, "toggleFullscreen() called");
        }
        final PlayerServiceEventListener fragmentListener = player.getFragmentListener()
                .orElse(null);
        if (fragmentListener == null || player.exoPlayerIsNull()) {
            return;
        }

        isFullscreen = !isFullscreen;
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

        binding.titleTextView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
        binding.channelTextView.setVisibility(isFullscreen ? View.VISIBLE : View.GONE);
        binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        setupScreenRotationButton();
    }

    public void checkLandscape() {
        // check if landscape is correct
        final boolean videoInLandscapeButNotInFullscreen = isLandscape()
                && !isFullscreen
                && !player.isAudioOnly();
        final boolean notPaused = player.getCurrentState() != STATE_COMPLETED
                && player.getCurrentState() != STATE_PAUSED;

        if (videoInLandscapeButNotInFullscreen
                && notPaused
                && !DeviceUtils.isTablet(context)) {
            toggleFullscreen();
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    public Optional<AppCompatActivity> getParentActivity() {
        final ViewParent rootParent = binding.getRoot().getParent();
        if (rootParent instanceof ViewGroup) {
            final Context activity = ((ViewGroup) rootParent).getContext();
            if (activity instanceof AppCompatActivity) {
                return Optional.of((AppCompatActivity) activity);
            }
        }
        return Optional.empty();
    }

    public boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature
        // while DisplayMetrics from app context doesn't
        return DeviceUtils.isLandscape(
                getParentActivity().map(Context.class::cast).orElse(player.getService()));
    }
    //endregion
}
