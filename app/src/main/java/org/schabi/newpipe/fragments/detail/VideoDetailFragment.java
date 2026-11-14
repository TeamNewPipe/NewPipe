package org.schabi.newpipe.fragments.detail;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.helper.PlayerHelper.isClearingQueueConfirmationRequired;
import static org.schabi.newpipe.util.DependentPreferenceHelper.getResumePlaybackEnabled;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;
import static org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams;
import static org.schabi.newpipe.util.NavigationHelper.openPlayQueue;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.evernote.android.state.State;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import org.schabi.newpipe.learning.LearningPlaylistNavigation;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningContentManager;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.views.MiniPlayerCorners;
import org.schabi.newpipe.cast.FCastManager;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.dearrow.DeArrowService;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException;
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.EmptyFragment;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.chat.LiveChatFragment;
import org.schabi.newpipe.player.LiveChatRepository;
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.media.LocalMediaThumbnailLoader;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.player.PlaybackPresentationMode;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerIntentType;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerRotationMode;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.playqueue.TimestampedPlayQueue;
import org.schabi.newpipe.player.ui.FullscreenCommentsSidebar;
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.StartFullscreenRequest;
import org.schabi.newpipe.player.ui.TvFullscreenFocusGuard;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.AiSListContentHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.EdgeToEdgeHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.GridTitleDisplayPolicy;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.TvRelatedSidebar;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import coil3.util.CoilUtils;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        PlayerServiceExtendedEventListener,
        OnKeyDownListener {
    public static final String KEY_SWITCHING_PLAYERS = "switching_players";

    private static final float MAX_OVERLAY_ALPHA = 0.9f;
    private static final float MAX_PLAYER_HEIGHT = 0.7f;
    private static final int LEGACY_PLAYER_COLLAPSE_MODE =
            CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX;
    private static final int PINNED_PLAYER_COLLAPSE_MODE =
            CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN;
    private static final int LEGACY_DETAIL_SCROLL_FLAGS =
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL;
    private static final int PINNED_DETAIL_SCROLL_FLAGS =
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED;

    public static final String ACTION_SHOW_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER";
    public static final String ACTION_HIDE_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER";
    public static final String ACTION_PLAYER_STARTED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED";
    public static final String ACTION_VIDEO_FRAGMENT_RESUMED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED";
    public static final String ACTION_VIDEO_FRAGMENT_STOPPED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED";

    private static final String COMMENTS_TAB_TAG = VideoDetailNavigationMapper.COMMENTS_TAB_TAG;
    private static final String RELATED_TAB_TAG = VideoDetailNavigationMapper.RELATED_TAB_TAG;
    private static final String DESCRIPTION_TAB_TAG =
            VideoDetailNavigationMapper.DESCRIPTION_TAB_TAG;
    private static final String NOTES_TAB_TAG = VideoDetailNavigationMapper.NOTES_TAB_TAG;
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    // tabs
    private boolean showComments;
    private boolean showRelatedItems;
    private boolean showDescription;
    private String selectedTabTag;
    private boolean tabSettingsChanged = false;
    private boolean showDislikes = true;
    private boolean pinVideoWhileScrolling = false;
    private int lastAppBarVerticalOffset = Integer.MAX_VALUE; // prevents useless updates
    @Nullable
    private StreamInfo currentInfo = null;
    @Nullable
    private PlayQueueItem currentLocalItem = null;
    private FragmentVideoDetailBinding binding;
    private org.schabi.newpipe.sponsorblock.FullVideoLabelBadge sponsorshipBadge;
    private NavigationBarView detailNavigation;
    private MiniPlayerCorners miniPlayerCorners;
    private TvFullscreenFocusGuard tvFullscreenFocusGuard;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences, key) -> {
                if (getString(R.string.rounded_mini_player_key).equals(key)) {
                    if (miniPlayerCorners != null) {
                        miniPlayerCorners.setEnabled(sharedPreferences.getBoolean(key, true));
                    }
                } else if (getString(R.string.show_comments_key).equals(key)) {
                    showComments = sharedPreferences.getBoolean(key, true);
                    tabSettingsChanged = true;
                } else if (getString(R.string.show_next_video_key).equals(key)) {
                    showRelatedItems = sharedPreferences.getBoolean(key, true);
                    tabSettingsChanged = true;
                } else if (getString(R.string.show_description_key).equals(key)) {
                    showDescription = sharedPreferences.getBoolean(key, true);
                    tabSettingsChanged = true;
                } else if (getString(R.string.pin_video_while_scrolling_key).equals(key)) {
                    pinVideoWhileScrolling = sharedPreferences.getBoolean(key, false);
                    updatePinnedPlayerLayout();
                } else if (getString(R.string.tv_related_sidebar_width_key).equals(key)) {
                    applyRelatedSidebarWidth();
                } else if (getString(R.string.show_dislike_key).equals(key)) {
                    showDislikes = sharedPreferences.getBoolean(key, true);
                    if (currentInfo != null && binding != null) {
                        updateLikeDislikeViews(currentInfo);
                    }
                }
            };

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    @NonNull
    protected String title = "";
    @State
    @Nullable
    protected String url = null;
    @Nullable
    protected PlayQueue playQueue = null;
    @Nullable
    private PlayQueue pendingLearningQueue;
    private boolean playerDismissed;
    private final PublishSubject<Optional<PlayQueue>> learningQueueChanges =
            PublishSubject.create();

    private final org.schabi.newpipe.player.ui.MinimizedVideoSurface minimizedVideoSurface =
            new org.schabi.newpipe.player.ui.MinimizedVideoSurface();

    @State
    int bottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    int lastStableBottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @Nullable
    private String aiWarningBypassedUrl;
    private boolean nativePipPrepared;
    private final View.OnLayoutChangeListener nativePipLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (nativePipPrepared && bottom - top != oldBottom - oldTop) {
                    setHeightThumbnail();
                }
            };
    private boolean nativePipForcedFullscreen;
    private int nativePipPreviousBottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    protected boolean autoPlayEnabled = true;
    private boolean forceFullscreen = false;

    private Disposable currentWorker;
    @Nullable
    private AlertDialog liveNotStartedDialog;
    @NonNull
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private Disposable positionSubscriber = null;

    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private BottomSheetBehavior.BottomSheetCallback bottomSheetCallback;
    private BroadcastReceiver broadcastReceiver;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private TabAdapter pageAdapter;
    private View activityToolbarLayout;
    private View.OnLayoutChangeListener toolbarLayoutChangeListener;
    private int activityStatusBarInset;
    private int detailNavigationBaseBottomMargin;
    private int appBarBaseStartMargin;
    private int viewPagerBaseStartMargin;
    private int viewPagerBaseBottomMargin;
    private boolean detailLayoutRecreationPending;
    private boolean detailLayoutRecreationRequested;
    private final StartFullscreenRequest startFullscreenRequest = new StartFullscreenRequest();
    private int pendingFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
    private int pendingFullscreenState = FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE;
    @Nullable
    private OneShotPreDrawListener resumeLayoutListener;

    private ContentObserver settingsContentObserver;
    @Nullable
    private PlayerService playerService;
    private Player player;
    private final PlayerHolder playerHolder = PlayerHolder.getInstance();

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    //////////////////////////////////////////////////////////////////////////*/
    @Override
    public void onServiceConnected(@NonNull final PlayerService connectedPlayerService) {
        playerService = connectedPlayerService;
        updatePinnedPlayerLayout();
    }

    @Override
    public void onPlayerConnected(@NonNull final Player connectedPlayer,
                                  final boolean playAfterConnect) {
        if (playerDismissed) {
            return;
        }
        player = connectedPlayer;

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded();

        final Optional<MainPlayerUi> playerUi = player.UIs().get(MainPlayerUi.class);
        if (!player.videoPlayerSelected() && !playAfterConnect) {
            return;
        }

        syncFullscreenWithOrientation(playerUi);

        if (playAfterConnect
                || ((currentInfo != null || currentLocalItem != null)
                && isAutoplayEnabled()
                && playerUi.isEmpty())) {
            autoPlayEnabled = true; // forcefully start playing
            openVideoPlayerAutoFullscreen();
        }
        updateOverlayPlayQueueButtonVisibility();
        updatePinnedPlayerLayout();
    }

    @Override
    public void onPlayerDisconnected() {
        player = null;
        updateMinimizedVideo();
        updatePinnedPlayerLayout();
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            restoreDefaultBrightness();
        }
    }

    @Override
    public void onServiceDisconnected() {
        playerService = null;
    }


    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(final int serviceId,
                                                  @Nullable final String url,
                                                  @NonNull final String name,
                                                  @Nullable final PlayQueue queue) {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, url, name, queue);
        return instance;
    }

    public static VideoDetailFragment getInstanceInCollapsedState() {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.updateBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);
        return instance;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true);
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true);
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true);
        showDislikes = ServiceHelper.isFetchDislikeEnabled(activity);
        pinVideoWhileScrolling = prefs.getBoolean(
                getString(R.string.pin_video_while_scrolling_key), false);
        selectedTabTag = prefs.getString(
                getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG);
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        setupBroadcastReceiver();

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                if (activity != null && !globalScreenOrientationLocked(activity)
                        && PlayerRotationMode.get(activity) != PlayerRotationMode.FIXED) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        };
        activity.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = FragmentVideoDetailBinding.inflate(inflater, container, false);
        applyRelatedSidebarWidth();
        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelResumeLayoutRestore();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        restoreDefaultBrightness();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key),
                        pageAdapter.getItemTitle(binding.viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.d(TAG, "onResume() called");
        }

        activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_RESUMED));
        applyRelatedSidebarWidth();

        updateOverlayPlayQueueButtonVisibility();
        applyTitleDisplayPolicy(
                binding.detailSecondaryControlPanel.getVisibility() != View.GONE);

        setupBrightness();

        if (detailLayoutRecreationRequested && binding != null) {
            binding.getRoot().post(this::reconcileDetailLayoutAfterConfigurationChange);
        }
        scheduleResumeLayoutRestore();

        if (tabSettingsChanged) {
            tabSettingsChanged = false;
            initTabs();
            if (currentInfo != null) {
                updateTabs(currentInfo);
            }
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared()) {
            startLoading(false);
        }
    }

    @Override
    public void onVideoPlaybackResumed() {
        if (DEBUG) {
            Log.d(TAG, "onVideoPlaybackResumed() called");
        }
        if (binding == null || !isResumed()) {
            return;
        }
        // onResume sends a broadcast: the first pre-draw may run while video is still disabled.
        // Correct the fullscreen flag synchronously when the player acknowledges that broadcast,
        // then measure again before drawing the resumed window.
        restorePlayerLayoutAfterResume();
        scheduleResumeLayoutRestore();
    }

    private void scheduleResumeLayoutRestore() {
        cancelResumeLayoutRestore();
        if (binding != null) {
            // A screen-off rotation can resume the retained fragment without another
            // configuration callback. Wait for the current window to be measured before
            // replacing fullscreen heights and insets left by the previous orientation.
            resumeLayoutListener = OneShotPreDrawListener.add(binding.getRoot(), () -> {
                resumeLayoutListener = null;
                restorePlayerLayoutAfterResume();
            });
            binding.getRoot().requestLayout();
        }
    }

    private void applyRelatedSidebarWidth() {
        if (binding != null && binding.relatedItemsLayout != null) {
            TvRelatedSidebar.apply(binding.detailMainContent, binding.relatedItemsLayout);
        }
    }

    private void cancelResumeLayoutRestore() {
        if (resumeLayoutListener != null) {
            resumeLayoutListener.removeListener();
            resumeLayoutListener = null;
        }
    }

    void restorePlayerLayoutAfterResume() {
        if (binding == null || !isAdded() || !isResumed()
                || detailLayoutRecreationPending || detailLayoutRecreationRequested
                || !isPlayerAndPlayerServiceAvailable() || !player.videoPlayerSelected()
                || player.isAudioOnly() || bottomSheetBehavior == null
                || bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED
                || nativePipPrepared || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && activity.isInPictureInPictureMode())
                || getRoot().map(View::getParent).isEmpty()) {
            return;
        }
        final Optional<MainPlayerUi> playerUi = player.UIs().get(MainPlayerUi.class);
        if (playerUi.isEmpty()) {
            return;
        }

        // The resumed configuration is authoritative, including when an orientation request
        // made before locking the phone never completed. Do not toggle fullscreen blindly.
        if (DEBUG) {
            Log.d(TAG, "Restoring resumed player layout: orientation="
                    + getResources().getConfiguration().orientation
                    + ", previousFullscreen=" + playerUi.get().isFullscreen());
        }
        clearPendingFullscreenTransition();
        syncFullscreenWithOrientation(playerUi, getResources().getConfiguration().orientation);
        // setFullscreen() is intentionally idempotent, but Android may have restored the window
        // with stale geometry/system bars even when the player's flag is already correct.
        refreshFullscreenLayout(playerUi.get().isFullscreen());
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding == null) {
            return;
        }
        if (nativePipPrepared) {
            // PiP changes the window configuration, not the user's fullscreen intent. The
            // layout listener applies the measured window size once the resize has completed.
            setHeightThumbnail();
            return;
        }
        final int orientation = newConfig.orientation;
        final boolean keepPhonePlayerLayout =
                FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                        orientation,
                        player != null,
                        player != null && player.videoPlayerSelected(),
                        player != null && player.isAudioOnly(),
                        lastStableBottomSheetState == BottomSheetBehavior.STATE_EXPANDED,
                        DeviceUtils.isTablet(activity)
                                || DeviceUtils.isTv(activity)
                                || DeviceUtils.isDesktopMode(activity));
        if (!keepPhonePlayerLayout
                && FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                        binding.relatedItemsLayout != null,
                        orientation, newConfig.screenWidthDp)) {
            detailLayoutRecreationRequested = true;
            recreateDetailLayoutForConfigurationChange();
            return;
        }
        if (player == null) {
            return;
        }
        // Apply fullscreen only after Android has installed the new configuration.
        // Re-read the current orientation in the posted callback so rapid rotations
        // cannot apply a stale landscape/portrait state.
        binding.getRoot().post(() -> {
            if (binding != null && player != null && isAdded()) {
                syncFullscreenWithOrientation(player.UIs().get(MainPlayerUi.class));
            }
        });
    }

    private void syncFullscreenWithOrientation(
            @NonNull final Optional<MainPlayerUi> playerUi) {
        final int currentOrientation = getResources().getConfiguration().orientation;
        final MainPlayerUi ui = playerUi.orElse(null);
        if (ui == null || !isPhoneVideoFullscreenEligible()) {
            return;
        }

        final int state = FullscreenOrientationPolicy.resolveFullscreenState(
                currentOrientation,
                ui.isFullscreen(),
                ui.getVideoContentOrientation(),
                true,
                pendingFullscreenOrientation,
                pendingFullscreenState);
        applyFullscreenState(ui, state);
        if (pendingFullscreenState != FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE
                && currentOrientation == pendingFullscreenOrientation
                && ui.isFullscreen()
                == (pendingFullscreenState == FullscreenOrientationPolicy.ENTER_FULLSCREEN)) {
            clearPendingFullscreenTransition();
        }
    }

    /**
     * Reconciles from an authoritative configuration, such as foreground resume after screen-off.
     * Explicit pending rotation requests are deliberately ignored because the resumed
     * configuration is the state Android actually restored.
     *
     * @param playerUi the current main-player UI, if attached
     * @param orientation the authoritative Android configuration orientation
     */
    private void syncFullscreenWithOrientation(
            @NonNull final Optional<MainPlayerUi> playerUi,
            final int orientation) {
        final MainPlayerUi ui = playerUi.orElse(null);
        if (ui == null || !isPhoneVideoFullscreenEligible()) {
            return;
        }
        final int state = FullscreenOrientationPolicy.resolveFullscreenState(
                orientation,
                ui.isFullscreen(),
                ui.getVideoContentOrientation(),
                true,
                Configuration.ORIENTATION_UNDEFINED,
                FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE);
        applyFullscreenState(ui, state);
    }

    private boolean isPhoneVideoFullscreenEligible() {
        return player != null
                && !nativePipPrepared
                && (!DeviceUtils.isTablet(activity) || player.UIs().get(MainPlayerUi.class)
                .map(ui -> ui.isAutomaticFullscreenAllowed(activity)).orElse(false))
                && !DeviceUtils.isTv(activity)
                && !DeviceUtils.isDesktopMode(activity)
                && !DeviceUtils.isInMultiWindow(activity)
                && !player.isAudioOnly();
    }

    private static void applyFullscreenState(@NonNull final MainPlayerUi ui, final int state) {
        if (state == FullscreenOrientationPolicy.ENTER_FULLSCREEN) {
            ui.setFullscreen(true);
        } else if (state == FullscreenOrientationPolicy.EXIT_FULLSCREEN) {
            ui.setFullscreen(false);
        }
    }

    private void clearPendingFullscreenTransition() {
        pendingFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
        pendingFullscreenState = FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE;
    }

    private void recreateDetailLayoutForConfigurationChange() {
        detailLayoutRecreationRequested = true;
        if (detailLayoutRecreationPending || binding == null) {
            return;
        }
        detailLayoutRecreationPending = true;
        binding.getRoot().post(() -> {
            if (!isAdded()) {
                detailLayoutRecreationPending = false;
                return;
            }
            final var fragmentManager = getParentFragmentManager();
            if (fragmentManager.isStateSaved()) {
                detailLayoutRecreationPending = false;
                return;
            }
            detailLayoutRecreationRequested = false;
            fragmentManager.beginTransaction()
                    .detach(this)
                    .attach(this)
                    .runOnCommit(() -> {
                        detailLayoutRecreationPending = false;
                        if (binding != null && isAdded()) {
                            binding.getRoot().post(
                                    this::reconcileDetailLayoutAfterConfigurationChange);
                        }
                    })
                    .commit();
        });
    }

    private void reconcileDetailLayoutAfterConfigurationChange() {
        if (binding == null || !isAdded()) {
            return;
        }
        final Configuration configuration = getResources().getConfiguration();
        if (FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                binding.relatedItemsLayout != null,
                configuration.orientation, configuration.screenWidthDp)) {
            detailLayoutRecreationRequested = true;
            recreateDetailLayoutForConfigurationChange();
            return;
        }

        detailLayoutRecreationRequested = false;
        restoreDetailLayoutAfterConfigurationChange();
    }

    private void restoreDetailLayoutAfterConfigurationChange() {
        if (binding == null || !isAdded()) {
            return;
        }

        if (currentInfo != null) {
            prepareAndHandleInfo(currentInfo, false);
        } else if (currentLocalItem != null) {
            prepareAndHandleLocalMedia(currentLocalItem, false);
        }

        final boolean fullscreen = isFullscreen();
        updateDetailContentTopMargin(fullscreen);
        updateDetailContentStartMargins(fullscreen);
        updateDetailNavigationBottomInset();
        updateDetailNavigationVisibility();

        if (binding.relatedItemsLayout != null) {
            if (showRelatedItems && !isRadioQueue()) {
                binding.relatedItemsLayout.setVisibility(
                        fullscreen ? View.GONE : View.VISIBLE);
            } else {
                binding.relatedItemsLayout.setVisibility(View.GONE);
            }
        }

        tryAddVideoPlayerView();
        updatePinnedPlayerLayout();
        if (player != null) {
            syncFullscreenWithOrientation(player.UIs().get(MainPlayerUi.class));
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (!activity.isChangingConfigurations()) {
            activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_STOPPED));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing() && isPlayerAvailable() && player.videoPlayerSelected()) {
            playerHolder.stopService();
        } else {
            playerHolder.setListener(null);
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        activity.unregisterReceiver(broadcastReceiver);
        activity.getContentResolver().unregisterContentObserver(settingsContentObserver);

        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        disposables.clear();
        positionSubscriber = null;
        currentWorker = null;
        updatePinnedPlayerLayout();
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback);

        if (activity.isFinishing()) {
            playQueue = null;
            currentInfo = null;
            stack = new LinkedList<>();
        }
    }

    @Override
    public void onDestroyView() {
        binding.detailLocalLikes.bind(null);
        if (sponsorshipBadge != null) {
            sponsorshipBadge.clear();
            sponsorshipBadge = null;
        }
        minimizedVideoSurface.restore();
        if (tvFullscreenFocusGuard != null) {
            tvFullscreenFocusGuard.update(false, false);
            tvFullscreenFocusGuard = null;
        }
        if (miniPlayerCorners != null) {
            miniPlayerCorners.release();
            miniPlayerCorners = null;
        }
        cancelResumeLayoutRestore();
        if (liveNotStartedDialog != null) {
            liveNotStartedDialog.dismiss();
            liveNotStartedDialog = null;
        }
        if (activityToolbarLayout != null && toolbarLayoutChangeListener != null) {
            activityToolbarLayout.removeOnLayoutChangeListener(toolbarLayoutChangeListener);
        }
        activityToolbarLayout = null;
        toolbarLayoutChangeListener = null;
        activityStatusBarInset = 0;
        binding.getRoot().removeOnLayoutChangeListener(nativePipLayoutListener);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), null);
        detailNavigationBaseBottomMargin = 0;
        appBarBaseStartMargin = 0;
        viewPagerBaseStartMargin = 0;
        viewPagerBaseBottomMargin = 0;
        super.onDestroyView();
        detailNavigation = null;
        binding = null;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            serviceId, url, title, null, false);
                } else {
                    Log.e(TAG, "ReCaptcha failed");
                }
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    private void setOnClickListeners() {
        binding.detailTitleRootLayout.setOnClickListener(v -> {
            if (currentLocalItem == null) {
                toggleTitleAndSecondaryControls();
            }
        });
        binding.detailUploaderRootLayout.setOnClickListener(makeOnClickListener(info -> {
            if (isEmpty(info.getSubChannelUrl())) {
                if (!isEmpty(info.getUploaderUrl())) {
                    openChannel(info.getUploaderUrl(), info.getUploaderName());
                }

                if (DEBUG) {
                    Log.i(TAG, "Can't open sub-channel because we got no channel URL");
                }
            } else {
                openChannel(info.getSubChannelUrl(), info.getSubChannelName());
            }
        }));
        binding.detailThumbnailRootLayout.setOnClickListener(v -> {
            autoPlayEnabled = true; // forcefully start playing
            // FIXME Workaround #7427
            if (isPlayerAvailable()) {
                player.setRecovery();
            }
            openVideoPlayerAutoFullscreen();
        });

        binding.detailControlsBackground.setOnClickListener(v -> openBackgroundPlayer(false));
        binding.detailControlsPopup.setOnClickListener(v -> openPopupPlayer(false));
        binding.detailControlsPlaylistAppend.setOnClickListener(v -> {
            if (getFM() != null && !isLoading.get()
                    && (currentInfo != null || currentLocalItem != null)) {
                final Fragment fragment = getParentFragmentManager().
                        findFragmentById(R.id.fragment_holder);

                // commit previous pending changes to database
                if (fragment instanceof LocalPlaylistFragment) {
                    ((LocalPlaylistFragment) fragment).saveImmediate();
                } else if (fragment instanceof MainFragment) {
                    ((MainFragment) fragment).commitPlaylistTabs();
                }

                final StreamEntity stream = currentLocalItem != null
                        ? new StreamEntity(currentLocalItem) : new StreamEntity(currentInfo);
                disposables.add(PlaylistDialog.createCorrespondingDialog(requireContext(),
                        List.of(stream),
                        dialog -> dialog.show(getParentFragmentManager(), TAG)));
            }
        });
        binding.detailControlsDownload.setOnClickListener(v -> {
            if (org.schabi.newpipe.download.ExternalDownloader.isEnabled(requireContext())
                    && currentInfo != null) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setItems(new String[]{getString(R.string.download),
                                getString(R.string.download_externally)}, (dialog, index) -> {
                            if (index == 1) {
                                org.schabi.newpipe.download.ExternalDownloader.open(
                                        requireContext(), currentInfo.getUrl());
                            } else if (PermissionHelper.checkStoragePermissions(activity,
                                    PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                                openDownloadDialog();
                            }
                        }).show();
            } else if (PermissionHelper.checkStoragePermissions(activity,
                    PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                openDownloadDialog();
            }
        });
        binding.detailControlsShare.setOnClickListener(makeOnClickListener(info ->
                ShareUtils.shareText(requireContext(), info.getName(), info.getUrl(),
                        ExtractorImageCompat.thumbnailImages(info))));
        binding.detailControlsOpenInBrowser.setOnClickListener(makeOnClickListener(info ->
                ShareUtils.openUrlInBrowser(requireContext(), info.getUrl())));
        binding.detailControlsCast.setOnClickListener(makeOnClickListener(info ->
                FCastManager.showDevicePicker(requireContext(), info, player)));
        binding.detailControlsPlayWithKodi.setOnClickListener(makeOnClickListener(info ->
                KoreUtils.playWithKore(requireContext(), Uri.parse(info.getUrl()))));
        if (DEBUG) {
            binding.detailControlsCrashThePlayer.setOnClickListener(v ->
                    VideoDetailPlayerCrasher.onCrashThePlayer(requireContext(), player));
        }

        final View.OnClickListener overlayListener = v -> {
            if (!isRadioQueue()) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        };
        binding.overlayThumbnail.setOnClickListener(overlayListener);
        binding.overlayMetadataLayout.setOnClickListener(overlayListener);
        binding.overlayButtonsLayout.setOnClickListener(overlayListener);
        binding.overlayCloseButton.setOnClickListener(v -> bottomSheetBehavior
                .setState(BottomSheetBehavior.STATE_HIDDEN));
        binding.overlayPlayQueueButton.setOnClickListener(v -> openPlayQueue(requireContext()));
        binding.overlayPlayPauseButton.setOnClickListener(v -> {
            if (playerIsNotStopped()) {
                player.playPause();
                player.UIs().get(VideoPlayerUi.class).ifPresent(ui -> ui.hideControls(0, 0));
                showSystemUi();
            } else {
                autoPlayEnabled = true; // forcefully start playing
                openVideoPlayer(false);
            }

            setOverlayPlayPauseImage(isPlayerAvailable() && player.isPlaying());
        });
    }

    private View.OnClickListener makeOnClickListener(final Consumer<StreamInfo> consumer) {
        return v -> {
            if (!isLoading.get() && currentInfo != null) {
                consumer.accept(currentInfo);
            }
        };
    }

    private String currentTimestampUrl(final StreamInfo info) {
        if (player == null || player.getCurrentItem() == null
                || player.getCurrentItem().getServiceId() != info.getServiceId()
                || !Objects.equals(player.getCurrentItem().getUrl(), info.getUrl())) {
            return info.getUrl();
        }
        return org.schabi.newpipe.util.TimestampedUrl.atPosition(info.getUrl(),
                info.getServiceId(), player.getExoPlayer().getCurrentPosition(), player.isLive());
    }

    private void setOnLongClickListeners() {
        binding.detailControlsShare.setOnLongClickListener(makeOnLongClickListener(info ->
                ShareUtils.shareText(requireContext(), info.getName(), currentTimestampUrl(info),
                        ExtractorImageCompat.thumbnailImages(info))));
        binding.detailControlsOpenInBrowser.setOnLongClickListener(makeOnLongClickListener(info ->
                ShareUtils.openUrlInBrowser(requireContext(), currentTimestampUrl(info))));
        binding.detailTitleRootLayout.setOnLongClickListener(v -> {
            if (isLoading.get() || (currentInfo == null && currentLocalItem == null)) {
                return false;
            }
            if (currentInfo == null) {
                ShareUtils.copyToClipboard(requireContext(),
                        binding.detailVideoTitleView.getText().toString());
            } else {
                final String timestampUrl = currentTimestampUrl(currentInfo);
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setItems(new String[] {getString(R.string.copy_video_title),
                            getString(R.string.copy_timestamped_link)}, (dialog, which) ->
                                ShareUtils.copyToClipboard(requireContext(), which == 0
                                        ? binding.detailVideoTitleView.getText().toString()
                                        : timestampUrl))
                        .show();
            }
            return true;
        });
        binding.detailUploaderRootLayout.setOnLongClickListener(makeOnLongClickListener(info -> {
            if (isEmpty(info.getSubChannelUrl())) {
                Log.w(TAG, "Can't open parent channel because we got no parent channel URL");
            } else {
                openChannel(info.getUploaderUrl(), info.getUploaderName());
            }
        }));

        binding.detailControlsBackground.setOnLongClickListener(
                makeOnMediaLongClick(() -> openBackgroundPlayer(true)));
        binding.detailControlsPopup.setOnLongClickListener(
                makeOnMediaLongClick(() -> openPopupPlayer(true)));
        binding.detailControlsDownload.setOnLongClickListener(makeOnLongClickListener(info ->
                NavigationHelper.openDownloads(activity)));

        final View.OnLongClickListener overlayListener = makeOnLongClickListener(info ->
                openChannel(info.getUploaderUrl(), info.getUploaderName()));
        binding.overlayThumbnail.setOnLongClickListener(overlayListener);
        binding.overlayMetadataLayout.setOnLongClickListener(overlayListener);
    }

    private View.OnLongClickListener makeOnLongClickListener(final Consumer<StreamInfo> consumer) {
        return v -> {
            if (isLoading.get() || currentInfo == null) {
                return false;
            }
            consumer.accept(currentInfo);
            return true;
        };
    }

    private View.OnLongClickListener makeOnMediaLongClick(final Runnable action) {
        return v -> {
            if (isLoading.get() || (currentInfo == null && currentLocalItem == null)) {
                return false;
            }
            action.run();
            return true;
        };
    }

    private void openChannel(final String subChannelUrl, final String subChannelName) {
        try {
            NavigationHelper.openChannelFragment(getFM(), currentInfo.getServiceId(),
                    subChannelUrl, subChannelName);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e);
        }
    }

    private void toggleTitleAndSecondaryControls() {
        final boolean expandSecondaryControls =
                binding.detailSecondaryControlPanel.getVisibility() == View.GONE;
        applyTitleDisplayPolicy(expandSecondaryControls);
        if (expandSecondaryControls) {
            animateRotation(binding.detailToggleSecondaryControlsView,
                    VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 180);
            binding.detailSecondaryControlPanel.setVisibility(View.VISIBLE);
        } else {
            animateRotation(binding.detailToggleSecondaryControlsView,
                    VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 0);
            binding.detailSecondaryControlPanel.setVisibility(View.GONE);
        }
        // ViewPager height has changed, update the detail navigation.
        updateDetailNavigationVisibility();
    }

    private void applyTitleDisplayPolicy(final boolean manuallyExpanded) {
        if (currentLocalItem == null) {
            GridTitleDisplayPolicy.applyToDetail(
                    binding.detailVideoTitleView, manuallyExpanded);
        } else {
            GridTitleDisplayPolicy.applyToLocalDetail(binding.detailVideoTitleView);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override // called from onViewCreated in {@link BaseFragment#onViewCreated}
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        applyTitleDisplayPolicy(false);
        org.schabi.newpipe.views.DetailTextRendering.apply(binding.detailContentRootLayout);

        detailNavigation = rootView.findViewById(R.id.detail_navigation);
        detailNavigationBaseBottomMargin = ((ViewGroup.MarginLayoutParams)
                detailNavigation.getLayoutParams()).bottomMargin;
        final ViewGroup.MarginLayoutParams appBarParams =
                (ViewGroup.MarginLayoutParams) binding.appBarLayout.getLayoutParams();
        appBarBaseStartMargin = appBarParams.getMarginStart();
        final ViewGroup.MarginLayoutParams viewPagerParams =
                (ViewGroup.MarginLayoutParams) binding.viewPager.getLayoutParams();
        viewPagerBaseStartMargin = viewPagerParams.getMarginStart();
        viewPagerBaseBottomMargin = viewPagerParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            updateDetailNavigationBottomInset();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        activityToolbarLayout = requireActivity().findViewById(R.id.toolbar_layout);
        activityStatusBarInset = Math.max(activityToolbarLayout.getPaddingTop(), 0);
        toolbarLayoutChangeListener = (view, left, top, right, bottom,
                                       oldLeft, oldTop, oldRight, oldBottom) -> {
            final int currentTopInset = Math.max(view.getPaddingTop(), 0);
            if (currentTopInset > 0 || activityStatusBarInset == 0) {
                activityStatusBarInset = currentTopInset;
            }
            updateDetailContentTopMargin(isFullscreen());
        };
        activityToolbarLayout.addOnLayoutChangeListener(toolbarLayoutChangeListener);
        binding.getRoot().post(() -> {
            updateDetailContentTopMargin(isFullscreen());
            updateDetailContentStartMargins(isFullscreen());
            updateDetailNavigationBottomInset();
        });
        pageAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(pageAdapter);
        binding.viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                updateDetailNavigationSelection(position);
            }
        });
        detailNavigation.setOnItemSelectedListener(item -> {
            final String tabTag = VideoDetailNavigationMapper.getTabTag(item.getItemId());
            if (tabTag == null) {
                return false;
            }

            final int position = pageAdapter.getItemPositionByTitle(tabTag);
            if (position < 0) {
                return false;
            }
            if (binding.viewPager.getCurrentItem() != position) {
                binding.viewPager.setCurrentItem(position);
            }
            return true;
        });

        binding.detailThumbnailRootLayout.requestFocus();

        binding.detailControlsPlayWithKodi.setVisibility(
                KoreUtils.shouldShowPlayWithKodi(requireContext(), serviceId)
                        ? View.VISIBLE
                        : View.GONE
        );
        binding.detailControlsCast.setVisibility(View.GONE);
        binding.detailControlsCrashThePlayer.setVisibility(
                DEBUG && PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(getString(R.string.show_crash_the_player_key), false)
                        ? View.VISIBLE
                        : View.GONE
        );
        accommodateForTvAndDesktopMode();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initListeners() {
        super.initListeners();

        // Workaround for #5600
        // Forcefully catch click events uncaught by children because otherwise
        // they will be caught by underlying view and "click through" will happen
        binding.getRoot().setOnClickListener(v -> { });
        binding.getRoot().setOnLongClickListener(v -> true);

        setOnClickListeners();
        setOnLongClickListeners();

        final View.OnTouchListener controlsTouchListener = (view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN
                    && PlayButtonHelper.shouldShowHoldToAppendTip(activity)) {

                animate(binding.touchAppendDetail, true, 250, AnimationType.ALPHA, 0, () ->
                        animate(binding.touchAppendDetail, false, 1500, AnimationType.ALPHA, 1000));
            }
            return false;
        };
        binding.detailControlsBackground.setOnTouchListener(controlsTouchListener);
        binding.detailControlsPopup.setOnTouchListener(controlsTouchListener);

        binding.appBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
            // Prevent useless updates to detail navigation visibility if nothing changed.
            if (verticalOffset != lastAppBarVerticalOffset) {
                lastAppBarVerticalOffset = verticalOffset;
                // The view was scrolled.
                updateDetailNavigationVisibility();
            }
        });

        setupBottomPlayer();
        if (!playerHolder.isBound()) {
            setHeightThumbnail();
        } else {
            playerHolder.startService(false, this);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private static LinkedList<StackItem> stack = new LinkedList<>();

    @Override
    public boolean onKeyDown(final int keyCode) {
        return isPlayerAvailable()
                && player.UIs().get(VideoPlayerUi.class)
                .map(playerUi -> playerUi.onKeyDown(keyCode)).orElse(false);
    }

    @Override
    public boolean onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (isFullscreen()) {
            // Keep the orientation request and fullscreen state in sync, as the minimize button
            // does. A direct toggle is undone when the still-landscape surface is reattached.
            player.UIs().get(MainPlayerUi.class)
                    .ifPresent(MainPlayerUi::toggleFullscreenWithOrientation);
            setAutoPlay(false);
            return true;
        }

        // Back leaves the expanded video without changing the current queue item. Let
        // MainActivity collapse the sheet; previous/next remain playback-control actions.
        if (isPlayerAvailable() && player.videoPlayerSelected()) {
            return false;
        }

        // That means that we are on the start of the stack,
        if (stack.size() <= 1) {
            restoreDefaultOrientation();
            return false; // let MainActivity handle the onBack (e.g. to minimize the mini player)
        }

        // Remove top
        stack.pop();
        // Get stack item from the new top
        setupFromHistoryItem(Objects.requireNonNull(stack.peek()));

        return true;
    }

    private void setupFromHistoryItem(final StackItem item) {
        setAutoPlay(false);
        hideMainPlayerOnLoadingNewStream();

        setInitialData(item.getServiceId(), item.getUrl(),
                item.getTitle() == null ? "" : item.getTitle(), item.getPlayQueue());
        if (currentLocalItem != null) {
            prepareAndHandleLocalMedia(currentLocalItem, true);
            openVideoPlayer(false);
        } else {
            startLoading(false);
        }

        // Maybe an item was deleted in background activity
        if (item.getPlayQueue().getItem() == null) {
            return;
        }

        final PlayQueueItem playQueueItem = item.getPlayQueue().getItem();
        // Update title, url, uploader from the last item in the stack (it's current now)
        final boolean isPlayerStopped = !isPlayerAvailable() || player.isStopped();
        if (playQueueItem != null && isPlayerStopped) {
            updateOverlayData(playQueueItem.getTitle(), playQueueItem.getUploader(),
                    ExtractorImageCompat.thumbnailImages(playQueueItem));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (wasCleared()) {
            return;
        }

        if (currentLocalItem != null) {
            prepareAndHandleLocalMedia(currentLocalItem, false);
            return;
        }

        if (currentInfo == null) {
            prepareAndLoadInfo();
        } else {
            prepareAndHandleInfoIfNeededAfterDelay(currentInfo, false, 50);
        }
    }

    public void selectAndLoadVideo(final int newServiceId,
                                   @Nullable final String newUrl,
                                   @NonNull final String newTitle,
                                   @Nullable final PlayQueue newQueue) {
        if (isPlayerAvailable() && newQueue != null && playQueue != null
                && playQueue.getItem() != null && !playQueue.getItem().getUrl().equals(newUrl)) {
            // Preloading can be disabled since playback is surely being replaced.
            player.disablePreloadingOfCurrentTrack();
        }

        hideMainPlayerOnLoadingNewStream();
        setInitialData(newServiceId, newUrl, newTitle, newQueue);
        if (currentLocalItem != null) {
            prepareAndHandleLocalMedia(currentLocalItem, true);
            if (stack.isEmpty() || !stack.peek().getPlayQueue().equalStreams(newQueue)) {
                stack.push(new StackItem(newServiceId, newUrl, newTitle, newQueue));
            }
            if (isAutoplayEnabled() || forceFullscreen) {
                openVideoPlayerAutoFullscreen();
            }
            return;
        }
        startLoading(false, true);
    }

    private void prepareAndHandleInfoIfNeededAfterDelay(final StreamInfo info,
                                                        final boolean scrollToTop,
                                                        final long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity == null || binding == null || currentLocalItem != null
                    || currentInfo != info) {
                return;
            }
            // Data can already be drawn, don't spend time twice
            if (info.getName().equals(binding.detailVideoTitleView.getText().toString())) {
                return;
            }
            prepareAndHandleInfo(info, scrollToTop);
        }, delay);
    }

    private void prepareAndHandleInfo(final StreamInfo info, final boolean scrollToTop) {
        if (DEBUG) {
            Log.d(TAG, "prepareAndHandleInfo() called with: "
                    + "info = [" + info + "], scrollToTop = [" + scrollToTop + "]");
        }

        showLoading();
        initTabs();

        if (scrollToTop) {
            scrollToTop();
        }
        handleResult(info);
        showContent();

    }

    private void prepareAndHandleLocalMedia(@NonNull final PlayQueueItem item,
                                            final boolean scrollToTop) {
        if (item.isLiveTv()) {
            prepareAndHandleLiveTv(item);
            return;
        }
        if (item.isRadio()) {
            prepareAndHandleRadio(item);
            return;
        }
        if (binding == null) {
            return;
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        isLoading.set(false);
        currentInfo = null;
        currentLocalItem = item;
        binding.detailLocalLikes.bind(null);
        serviceId = item.getServiceId();
        url = item.getUrl();
        title = item.getTitle();

        if (scrollToTop) {
            scrollToTop();
        }
        pageAdapter.clearAllItems();
        pageAdapter.addFragment(LocalMediaDescriptionFragment.newInstance(item),
                DESCRIPTION_TAB_TAG);
        pageAdapter.notifyDataSetUpdate();
        binding.viewPager.setVisibility(View.VISIBLE);
        binding.viewPager.setCurrentItem(0, false);
        updateDetailNavigationItems();
        updateDetailNavigationSelection(0);
        updateDetailNavigationVisibility();
        if (binding.relatedItemsLayout != null) {
            binding.relatedItemsLayout.setVisibility(View.GONE);
        }

        binding.detailVideoTitleView.setText(item.getTitle());
        applyTitleDisplayPolicy(false);
        binding.detailToggleSecondaryControlsView.setVisibility(View.GONE);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        final String primary = firstNonEmpty(item.getUploader(), item.getAlbum(),
                item.getFolder(), getString(R.string.local_media_on_device));
        final String secondary;
        if (!isEmpty(item.getAlbum()) && !item.getAlbum().equals(primary)) {
            secondary = item.getAlbum();
        } else if (!isEmpty(item.getFolder()) && !item.getFolder().equals(primary)) {
            secondary = item.getFolder();
        } else {
            secondary = getString(R.string.local_media_on_device);
        }
        binding.detailSubChannelTextView.setText(primary);
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailUploaderTextView.setText(secondary);
        binding.detailUploaderTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelThumbnailView.setVisibility(View.GONE);
        binding.detailUploaderThumbnailView.setVisibility(View.GONE);
        binding.detailUploaderRootLayout.setClickable(false);
        binding.detailsPanel.setVisibility(View.GONE);

        if (item.getDuration() > 0) {
            binding.detailDurationView.setText(Localization.getDurationString(item.getDuration()));
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            binding.detailDurationView.setVisibility(View.VISIBLE);
        } else {
            binding.detailDurationView.setVisibility(View.GONE);
        }
        binding.detailThumbnailPlayButton.setImageResource(
                item.getStreamType() == StreamType.AUDIO_STREAM
                        ? R.drawable.ic_headset_shadow : R.drawable.ic_play_arrow_shadow);
        binding.detailThumbnailPlayButton.setVisibility(View.VISIBLE);
        LocalMediaThumbnailLoader.INSTANCE.load(binding.detailThumbnailImageView, item);

        binding.detailControlsPlaylistAppend.setVisibility(View.VISIBLE);
        binding.detailControlsBackground.setVisibility(View.VISIBLE);
        binding.detailControlsPopup.setVisibility(item.getStreamType() == StreamType.AUDIO_STREAM
                ? View.GONE : View.VISIBLE);
        binding.detailControlsDownload.setVisibility(View.GONE);
        binding.detailControlsShare.setVisibility(View.GONE);
        binding.detailControlsOpenInBrowser.setVisibility(View.GONE);
        binding.detailControlsCast.setVisibility(View.GONE);
        binding.detailControlsPlayWithKodi.setVisibility(View.GONE);

        final StringBuilder metadata = new StringBuilder(
                getString(R.string.local_media_on_device));
        appendLocalMetadata(metadata, item.getFolder());
        appendLocalMetadata(metadata, item.getMimeType());
        if (item.getDuration() > 0) {
            appendLocalMetadata(metadata, Localization.getDurationString(item.getDuration()));
        }
        binding.detailMetaInfoTextView.setText(metadata);
        binding.detailMetaInfoTextView.setVisibility(View.VISIBLE);
        binding.detailMetaInfoSeparator.setVisibility(View.VISIBLE);

        updateLocalOverlay(item);
        hideLoading();
        showContent();
    }

    private void prepareAndHandleLiveTv(@NonNull final PlayQueueItem item) {
        if (binding == null) {
            return;
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        setInitialData(item.getServiceId(), item.getUrl(), item.getTitle(), playQueue);
        currentInfo = null;
        currentLocalItem = item;
        binding.detailLocalLikes.bind(null);
        isLoading.set(false);
        pageAdapter.clearAllItems();
        pageAdapter.notifyDataSetUpdate();
        hideLoading();
        showContent();
        LiveTvDetailPresentation.show(binding, item);
        updateLocalOverlay(item);
    }

    private void prepareAndHandleRadio(@NonNull final PlayQueueItem item) {
        if (binding == null) {
            return;
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        setInitialData(item.getServiceId(), item.getUrl(), item.getTitle(), playQueue);
        currentInfo = null;
        currentLocalItem = item;
        binding.detailLocalLikes.bind(null);
        isLoading.set(false);
        pageAdapter.clearAllItems();
        pageAdapter.notifyDataSetUpdate();
        hideLoading();
        showContent();
        RadioDetailPresentation.show(binding, item);
        updateLocalOverlay(item);
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setDraggable(false);
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            setOverlayExpansionClickable(false);
        }
    }

    @NonNull
    private String firstNonEmpty(@Nullable final String first,
                                 @Nullable final String second,
                                 @Nullable final String third,
                                 @NonNull final String fallback) {
        if (!isEmpty(first)) {
            return first;
        }
        if (!isEmpty(second)) {
            return second;
        }
        if (!isEmpty(third)) {
            return third;
        }
        return fallback;
    }

    private void appendLocalMetadata(@NonNull final StringBuilder metadata,
                                     @Nullable final String value) {
        if (!isEmpty(value) && metadata.indexOf(value) < 0) {
            metadata.append(Localization.DOT_SEPARATOR).append(value);
        }
    }

    protected void prepareAndLoadInfo() {
        scrollToTop();
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, stack.isEmpty());
    }

    private void startLoading(final boolean forceLoad, final boolean addToBackStack) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, addToBackStack);
    }

    private void runWorker(final boolean forceLoad, final boolean addToBackStack) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        currentWorker = org.schabi.newpipe.download.DownloadedCopyRepository.streamInfoOrNetwork(
                        requireContext(), serviceId, url, title,
                        ExtractorHelper.getStreamInfo(serviceId, url, forceLoad))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    isLoading.set(false);
                    hideMainPlayerOnLoadingNewStream();
                    if (shouldHideAgeRestrictedContent(
                            result.getServiceId(),
                            result.getAgeLimit(),
                            prefs.getBoolean(
                                    getString(R.string.show_age_restricted_content), false))) {
                        hideAgeRestrictedContent();
                    } else {
                        handleResult(result);
                        showContent();
                        if (addToBackStack) {
                            if (playQueue == null) {
                                playQueue = TimestampedPlayQueue.create(result, url);
                            }
                            if (stack.isEmpty() || !stack.peek().getPlayQueue()
                                    .equalStreams(playQueue)) {
                                stack.push(new StackItem(serviceId, url, title, playQueue));
                            }
                        }

                        if (isAutoplayEnabled() || forceFullscreen) {
                            openVideoPlayerAutoFullscreen();
                        }
                    }
                }, throwable -> {
                    if (throwable instanceof LiveNotStartException
                            || throwable instanceof VideoNotReleaseException) {
                        showLiveNotStartedDialog();
                    } else {
                        showError(new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                                url == null ? "no url" : url, serviceId, url));
                    }
                });
    }

    private void showLiveNotStartedDialog() {
        handleError();
        if (!isAdded() || activity == null) {
            return;
        }
        if (liveNotStartedDialog != null && liveNotStartedDialog.isShowing()) {
            return;
        }

        liveNotStartedDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.live_stream_not_started_title)
                .setMessage(R.string.live_stream_not_started_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.retry, (dialog, which) -> reloadContent())
                .create();
        liveNotStartedDialog.setOnDismissListener(dialog -> liveNotStartedDialog = null);
        liveNotStartedDialog.show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void initTabs() {
        if (pageAdapter.getCount() != 0) {
            selectedTabTag = pageAdapter.getItemTitle(binding.viewPager.getCurrentItem());
        }
        pageAdapter.clearAllItems();

        if (shouldShowComments()) {
            pageAdapter.addFragment(
                    CommentsFragment.getInstance(serviceId, url, title), COMMENTS_TAB_TAG);
        }

        if (showRelatedItems && binding.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), RELATED_TAB_TAG);
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), DESCRIPTION_TAB_TAG);
        }

        if (LearningMode.areNotesEnabled(requireContext())
                && LearningContentManager.getInstance(requireContext())
                        .isStreamLearning(serviceId, url)) {
            pageAdapter.addFragment(EmptyFragment.newInstance(false), NOTES_TAB_TAG);
        }

        if (pageAdapter.getCount() == 0) {
            pageAdapter.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG);
        }
        pageAdapter.notifyDataSetUpdate();
        updateDetailNavigationItems();

        int position = pageAdapter.getItemPositionByTitle(selectedTabTag);
        if (position < 0) {
            position = Math.min(binding.viewPager.getCurrentItem(), pageAdapter.getCount() - 1);
        }
        binding.viewPager.setCurrentItem(position, false);
        updateDetailNavigationSelection(binding.viewPager.getCurrentItem());
        // The page adapter now contains destinations: show the navigation when useful.
        updateDetailNavigationVisibility();
    }

    private void updateDetailNavigationItems() {
        setDetailNavigationItemVisible(R.id.video_detail_navigation_comments, COMMENTS_TAB_TAG);
        setDetailNavigationItemVisible(R.id.video_detail_navigation_live_chat,
                VideoDetailNavigationMapper.LIVE_CHAT_TAB_TAG);
        setDetailNavigationItemVisible(R.id.video_detail_navigation_related, RELATED_TAB_TAG);
        setDetailNavigationItemVisible(
                R.id.video_detail_navigation_description, DESCRIPTION_TAB_TAG);
        setDetailNavigationItemVisible(R.id.video_detail_navigation_notes, NOTES_TAB_TAG);
    }

    private void setDetailNavigationItemVisible(final int itemId, final String tabTag) {
        final var item = detailNavigation.getMenu().findItem(itemId);
        if (item != null) {
            item.setVisible(pageAdapter.getItemPositionByTitle(tabTag) >= 0);
        }
    }

    private void updateDetailNavigationSelection(final int position) {
        final int itemId = VideoDetailNavigationMapper.getNavigationItemId(
                pageAdapter.getItemTitle(position));
        if (itemId != VideoDetailNavigationMapper.NO_NAVIGATION_ITEM_ID
                && detailNavigation.getSelectedItemId() != itemId) {
            detailNavigation.setSelectedItemId(itemId);
        }
    }

    private void updateTabs(@NonNull final StreamInfo info) {
        if (LiveChatRepository.supports(info)
                && pageAdapter.getItemPositionByTitle(
                        VideoDetailNavigationMapper.LIVE_CHAT_TAB_TAG) < 0) {
            pageAdapter.removeItemByTitle(EMPTY_TAB_TAG);
            pageAdapter.addFragment(
                    LiveChatFragment.newInstance(info.getServiceId(), info.getUrl()),
                    VideoDetailNavigationMapper.LIVE_CHAT_TAB_TAG);
        } else if (!LiveChatRepository.supports(info)) {
            pageAdapter.removeItemByTitle(VideoDetailNavigationMapper.LIVE_CHAT_TAB_TAG);
            if (pageAdapter.getCount() == 0) {
                pageAdapter.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG);
            }
        }
        updateDetailNavigationItems();
        if (showRelatedItems && !isRadioQueue()) {
            if (binding.relatedItemsLayout == null) { // phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedItemsFragment.getInstance(info));
            } else { // tablet + TV
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.relatedItemsLayout,
                                RelatedItemsFragment.getInstance(info, true))
                        .commitAllowingStateLoss();
                binding.relatedItemsLayout.setVisibility(isFullscreen() ? View.GONE : View.VISIBLE);
            }
        }

        if (showDescription) {
            pageAdapter.updateItem(DESCRIPTION_TAB_TAG, new DescriptionFragment(info));
        }

        if (LearningMode.areNotesEnabled(requireContext())
                && LearningContentManager.getInstance(requireContext())
                        .isStreamLearning(info.getServiceId(), info.getUrl())) {
            pageAdapter.updateItem(
                    NOTES_TAB_TAG,
                    LearningNotesFragment.getInstance(info.getServiceId(), info.getUrl()));
        }

        binding.viewPager.setVisibility(View.VISIBLE);
        // Make sure the detail navigation is visible.
        updateDetailNavigationVisibility();
        pageAdapter.notifyDataSetUpdate();
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(COMMENTS);
        } catch (final ExtractionException e) {
            return false;
        }
    }

    public void updateDetailNavigationVisibility() {

        if (binding == null || detailNavigation == null) {
            //If binding is null we do not need to and should not do anything with its object(s)
            return;
        }

        if (isFullscreen() || pageAdapter.getCount() < 2
                || binding.viewPager.getVisibility() != View.VISIBLE) {
            // Hide navigation if there is only one destination or if the pager is hidden.
            detailNavigation.setVisibility(View.GONE);
        } else {
            if (binding.relatedItemsLayout != null) {
                detailNavigation.setTranslationY(0.0f);
                detailNavigation.setVisibility(View.VISIBLE);
                return;
            }
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            detailNavigation.post(() -> {
                final var activity = getActivity();
                if (activity != null) {
                    final Rect pagerHitRect = new Rect();
                    binding.viewPager.getHitRect(pagerHitRect);

                    final int height = DeviceUtils.getWindowHeight(activity.getWindowManager());
                    final int viewPagerVisibleHeight = height - pagerHitRect.top;
                    final int navigationHeight = getResources().getDimensionPixelSize(
                            R.dimen.video_detail_navigation_height);

                    if (viewPagerVisibleHeight > navigationHeight * 2) {
                        // No translation when the visible pager is taller than three bars.
                        detailNavigation.setTranslationY(
                                Math.max(0, navigationHeight * 3 - viewPagerVisibleHeight));
                        detailNavigation.setVisibility(View.VISIBLE);
                    } else {
                        // The pager is not visible enough.
                        detailNavigation.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    public void scrollToTop() {
        binding.appBarLayout.setExpanded(true, true);
        // Notify the detail navigation of scrolling.
        updateDetailNavigationVisibility();
    }

    public void scrollToComment(final CommentsInfoItem comment) {
        final int commentsTabPos = pageAdapter.getItemPositionByTitle(COMMENTS_TAB_TAG);
        final Fragment fragment = pageAdapter.getItem(commentsTabPos);
        if (!(fragment instanceof CommentsFragment)) {
            return;
        }

        // unexpand the app bar only if scrolling to the comment succeeded
        if (((CommentsFragment) fragment).scrollToComment(comment)) {
            binding.appBarLayout.setExpanded(false, false);
            binding.viewPager.setCurrentItem(commentsTabPos, false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void toggleFullscreenIfInFullscreenMode() {
        // If a user watched video inside fullscreen mode and than chose another player
        // return to non-fullscreen mode
        if (isPlayerAvailable()) {
            player.UIs().get(MainPlayerUi.class).ifPresent(playerUi -> {
                if (playerUi.isFullscreen()) {
                    playerUi.toggleFullscreen();
                }
            });
        }
    }

    private void openBackgroundPlayer(final boolean append) {
        if (showAiPlaybackWarningIfNeeded(() -> openBackgroundPlayer(append))) {
            return;
        }

        final boolean useExternalAudioPlayer = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        toggleFullscreenIfInFullscreenMode();

        if (isPlayerAvailable()) {
            // FIXME Workaround #7427
            player.setRecovery();
        }

        if (useExternalAudioPlayer) {
            showExternalAudioPlaybackDialog();
        } else {
            openNormalBackgroundPlayer(append);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (showAiPlaybackWarningIfNeeded(() -> openPopupPlayer(append))) {
            return;
        }

        if (!PermissionHelper.isPopupEnabledElseAsk(activity)) {
            return;
        }

        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this);
        } else {
            // FIXME Workaround #7427
            player.setRecovery();
        }

        final boolean wasFullscreen = isFullscreen();
        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) { //resumePlayback: false
            playerHolder.rememberMainPlayerFullscreenBeforePopup(wasFullscreen);
            toggleFullscreenIfInFullscreenMode();
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.POPUP);
        } else {
            replaceQueueIfUserConfirms(() -> {
                playerHolder.rememberMainPlayerFullscreenBeforePopup(wasFullscreen);
                toggleFullscreenIfInFullscreenMode();
                NavigationHelper.playOnPopupPlayer(activity, queue, true);
            });
        }
    }

    private boolean showAiPlaybackWarningIfNeeded(@NonNull final Runnable onContinue) {
        if (currentInfo == null
                || Objects.equals(aiWarningBypassedUrl, currentInfo.getUrl())
                || !AiSListContentHelper.shouldWarnBeforePlayback(
                        requireContext(),
                        currentInfo.getServiceId(),
                        currentInfo.getUploaderUrl(),
                        currentInfo.getUploaderName())) {
            return false;
        }

        final String bypassUrl = currentInfo.getUrl();
        AiSListContentHelper.showPlaybackWarning(
                requireContext(),
                currentInfo.getUploaderUrl(),
                currentInfo.getUploaderName(),
                () -> {
                    aiWarningBypassedUrl = bypassUrl;
                    onContinue.run();
                });
        return true;
    }

    /**
     * Opens the video player, retaining an explicit fullscreen request until its view is attached.
     * Content dimensions determine the fullscreen orientation once they are available.
     *
     * @param directlyFullscreenIfApplicable whether to open fullscreen when system rotation
     *                                       is locked
     */
    public void openVideoPlayer(final boolean directlyFullscreenIfApplicable) {
        if (showAiPlaybackWarningIfNeeded(
                () -> openVideoPlayer(directlyFullscreenIfApplicable))) {
            return;
        }

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            showExternalVideoPlaybackDialog();
        } else {
            replaceQueueIfUserConfirms(() -> openMainPlayer(directlyFullscreenIfApplicable));
        }
    }

    /**
     * If the option to start directly fullscreen is enabled, or if {@code forceFullscreen} is
     * {@code true} (e.g. when switching from popup player to main player with a different video),
     * calls {@link #openVideoPlayer(boolean)} with {@code directlyFullscreenIfApplicable = true},
     * so that fullscreen starts in the video's orientation when system rotation is locked.
     * Otherwise, if the option to start directly fullscreen
     * is disabled and {@code forceFullscreen} is {@code false}, calls
     * {@link #openVideoPlayer(boolean)} with {@code directlyFullscreenIfApplicable = false},
     * hence preventing it from going directly fullscreen.
     * {@code forceFullscreen} is reset to {@code false} after this call.
     */
    public void openVideoPlayerAutoFullscreen() {
        openVideoPlayer(forceFullscreen
                || PlayerHelper.isStartMainPlayerFullscreenEnabled(requireContext()));
        forceFullscreen = false;
    }

    public void setForceFullscreen(final boolean force) {
        this.forceFullscreen = force;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this);
        }

        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.AUDIO);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper
                    .playOnBackgroundPlayer(activity, queue, true));
        }
    }

    private void openMainPlayer(final boolean directlyFullscreenIfApplicable) {
        final PlayQueue queue = currentInfo == null && currentLocalItem == null
                ? null : setupPlayQueueForIntent(false);
        if (queue != null && directlyFullscreenIfApplicable
                && PlayerHelper.globalScreenOrientationLocked(requireContext())) {
            updateBottomSheetState(BottomSheetBehavior.STATE_EXPANDED);
            startFullscreenRequest.request(queue.getItem());
        }
        if (!isPlayerServiceAvailable()) {
            playerHolder.startService(autoPlayEnabled, this);
            return;
        }
        if (queue == null) {
            return;
        }

        tryAddVideoPlayerView();

        final Context context = requireContext();
        final Intent playerIntent =
                NavigationHelper.getPlayerIntent(context, PlayerService.class, queue,
                                PlayerIntentType.AllOthers)
                        .putExtra(Player.PLAY_WHEN_READY, autoPlayEnabled)
                        .putExtra(Player.RESUME_PLAYBACK, true);
        ContextCompat.startForegroundService(activity, playerIntent);
    }

    /**
     * When the video detail fragment is already showing details for a video and the user opens a
     * new one, the video detail fragment changes all of its old data to the new stream, so if there
     * is a video player currently open it should be hidden. This method does exactly that. If
     * autoplay is enabled, the underlying player is not stopped completely, since it is going to
     * be reused in a few milliseconds and the flickering would be annoying.
     */
    private void hideMainPlayerOnLoadingNewStream() {
        final var root = getRoot();
        if (!isPlayerServiceAvailable() || root.isEmpty() || !player.videoPlayerSelected()) {
            return;
        }

        removeVideoPlayerView();
        if (isAutoplayEnabled()) {
            playerService.stopForImmediateReusing();
            root.ifPresent(view -> view.setVisibility(View.GONE));
        } else {
            playerHolder.stopService();
        }
    }

    private PlayQueue setupPlayQueueForIntent(final boolean append) {
        if (append) {
            if (currentLocalItem != null) {
                return new LocalMediaPlayQueue(List.of(currentLocalItem), 0);
            }
            return new SinglePlayQueue(currentInfo);
        }

        PlayQueue queue = playQueue;
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty()) {
            queue = currentLocalItem == null
                    ? TimestampedPlayQueue.create(currentInfo, url)
                    : new LocalMediaPlayQueue(List.of(currentLocalItem), 0);
        }

        return queue;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoPlay(final boolean autoPlay) {
        this.autoPlayEnabled = autoPlay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getSubChannelName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> { /* successful */ },
                        error -> showSnackBarError(
                                new ErrorInfo(
                                        error,
                                        UserAction.PLAY_STREAM,
                                        "Got an error when modifying history on viewed"
                                )
                        )
                ));
    }

    private boolean isExternalPlayerEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.use_external_video_player_key), false);
    }

    // This method overrides default behaviour when setAutoPlay() is called.
    // Don't auto play if the user selected an external player or disabled it in settings
    private boolean isAutoplayEnabled() {
        return autoPlayEnabled
                && !isExternalPlayerEnabled()
                && (!isPlayerAvailable() || player.videoPlayerSelected())
                && bottomSheetState != BottomSheetBehavior.STATE_HIDDEN
                && PlayerHelper.isAutoplayAllowedByUser(requireContext());
    }

    private void tryAddVideoPlayerView() {
        if (isPlayerAvailable() && getView() != null) {
            // Setup the surface view height, so that it fits the video correctly; this is done also
            // here, and not only in the Handler, to avoid a choppy fullscreen rotation animation.
            setHeightThumbnail();
        }

        // do all the null checks in the posted lambda, too, since the player, the binding and the
        // view could be set or unset before the lambda gets executed on the next main thread cycle
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isPlayerAvailable() || getView() == null) {
                return;
            }

            // setup the surface view height, so that it fits the video correctly
            setHeightThumbnail();

            player.UIs().get(MainPlayerUi.class).ifPresent(playerUi -> {
                // sometimes binding would be null here, even though getView() != null above u.u
                if (binding != null) {
                    final View playerView = playerUi.getBinding().getRoot();
                    if (playerView.getParent() != binding.playerPlaceholder) {
                        playerUi.removeViewFromParent();
                        binding.playerPlaceholder.addView(playerView);
                    }
                    updateMinimizedVideo();
                    playerUi.setupVideoSurfaceIfNeeded();
                    updatePinnedPlayerLayout();
                    // A configuration change can temporarily detach the player UI. Retry the
                    // requested fullscreen transition only after the rebuilt layout and surface
                    // are attached, so the request is not lost and the old surface dimensions
                    // are not cached.
                    startFullscreenRequest.apply(playerUi,
                            () -> onScreenRotationButtonClicked(true));
                    syncFullscreenWithOrientation(Optional.of(playerUi));
                }
            });
        });
    }

    private void removeVideoPlayerView() {
        minimizedVideoSurface.restore();
        makeDefaultHeightForVideoPlaceholder();

        if (player != null) {
            player.UIs().get(VideoPlayerUi.class).ifPresent(VideoPlayerUi::removeViewFromParent);
        }
        updatePinnedPlayerLayout();
    }

    private void makeDefaultHeightForVideoPlaceholder() {
        if (getView() == null) {
            return;
        }

        binding.playerPlaceholder.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT;
        binding.playerPlaceholder.requestLayout();
    }

    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    final DisplayMetrics metrics = getResources().getDisplayMetrics();

                    if (getView() != null) {
                        final int height = (DeviceUtils.isInMultiWindow(activity)
                                ? requireView()
                                : activity.getWindow().getDecorView()).getHeight();
                        setHeightThumbnail(height, metrics);
                        getView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                    }
                    return false;
                }
            };

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * {@link #MAX_PLAYER_HEIGHT})
     */
    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);

        if (isFullscreen()) {
            final int height = (DeviceUtils.isInMultiWindow(activity)
                    ? requireView()
                    : activity.getWindow().getDecorView()).getHeight();
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics);
            } else {
                requireView().getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            }
        } else {
            final int height = (int) (isPortrait
                    ? metrics.widthPixels / (16.0f / 9.0f)
                    : metrics.heightPixels / 2.0f);
            setHeightThumbnail(height, metrics);
        }
    }

    private void setHeightThumbnail(final int requestedHeight, final DisplayMetrics metrics) {
        // A portrait video's fullscreen state need not change when entering PiP. Never retain
        // the pre-PiP thumbnail height: it centers the surface outside the small visible window.
        final int newHeight = nativePipPrepared && binding.getRoot().getHeight() > 0
                ? binding.getRoot().getHeight() : requestedHeight;
        binding.detailThumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, newHeight));
        binding.detailThumbnailImageView.setMinimumHeight(newHeight);
        updatePinnedPlayerLayout(newHeight);
        if (isPlayerAvailable()) {
            final int maxHeight = (int) (metrics.heightPixels * MAX_PLAYER_HEIGHT);
            player.UIs().get(VideoPlayerUi.class).ifPresent(ui ->
                    ui.getBinding().surfaceView.setHeights(newHeight,
                            ui.isFullscreen() ? newHeight : maxHeight));
        }
    }

    static boolean isPhoneDetailLayout(final boolean hasRelatedItemsLayout) {
        return !hasRelatedItemsLayout;
    }

    static int getContentTopMargin(final boolean phoneDetailLayout,
                                   final int thumbnailHeight) {
        return phoneDetailLayout && thumbnailHeight > 0 ? thumbnailHeight : 0;
    }

    static int resolveThumbnailHeight(final int requestedHeight,
                                      final int layoutHeight,
                                      final int measuredHeight) {
        return requestedHeight > 0
                ? requestedHeight
                : layoutHeight > 0
                        ? layoutHeight
                        : Math.max(measuredHeight, 0);
    }

    static boolean shouldUsePinnedPlayerLayout(final boolean preferenceEnabled,
                                               final boolean videoPlayerSelected,
                                               final boolean playerAttached,
                                               final boolean fullscreen,
                                               final boolean phoneDetailLayout,
                                               final boolean tvLayout,
                                               final boolean tabletLayout) {
        return preferenceEnabled
                && videoPlayerSelected
                && playerAttached
                && !fullscreen
                && phoneDetailLayout
                && !tvLayout
                && !tabletLayout;
    }

    static boolean shouldHideAgeRestrictedContent(final int streamServiceId,
                                                  final int ageLimit,
                                                  final boolean showAgeRestrictedContent) {
        return streamServiceId != ServiceList.BitChute.getServiceId()
                && ageLimit != NO_AGE_LIMIT
                && !showAgeRestrictedContent;
    }

    static boolean shouldHidePreviousStreamContent(final boolean streamInfoCached) {
        return !streamInfoCached;
    }

    static boolean shouldShowQueueItemLoadingPreview(final boolean hasQueueItem,
                                                     final boolean localMedia) {
        return hasQueueItem && !localMedia;
    }

    static int getDetailContentTopMargin(final boolean fullscreen,
                                         final int statusBarInset) {
        return fullscreen ? 0 : Math.max(statusBarInset, 0);
    }

    static int getDetailContentStartMargin(final boolean fullscreen,
                                           final int baseStartMargin) {
        return fullscreen ? 0 : Math.max(baseStartMargin, 0);
    }

    static int getPlayerPlaceholderBackgroundColor(final boolean fullscreen) {
        return fullscreen ? Color.BLACK : Color.TRANSPARENT;
    }

    static int getDetailNavigationBottomInset(final boolean fullscreen,
                                              final boolean wideDetailLayout,
                                              final int navigationBarInset,
                                              final int displayCutoutInset) {
        if (fullscreen || wideDetailLayout) {
            return 0;
        }
        return Math.max(Math.max(navigationBarInset, displayCutoutInset), 0);
    }

    private void updateDetailNavigationBottomInset() {
        if (binding == null || detailNavigation == null) {
            return;
        }

        Insets navigationBarInsets = Insets.NONE;
        Insets displayCutoutInsets = Insets.NONE;
        final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(binding.getRoot());
        if (rootInsets != null) {
            navigationBarInsets = rootInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            displayCutoutInsets = rootInsets.getInsets(
                    WindowInsetsCompat.Type.displayCutout());
        }
        final int bottomInset = getDetailNavigationBottomInset(
                isFullscreen(), binding.relatedItemsLayout != null,
                navigationBarInsets.bottom, displayCutoutInsets.bottom);

        final ViewGroup.MarginLayoutParams navigationParams =
                (ViewGroup.MarginLayoutParams) detailNavigation.getLayoutParams();
        final int desiredNavigationBottomMargin = detailNavigationBaseBottomMargin + bottomInset;
        if (navigationParams.bottomMargin != desiredNavigationBottomMargin) {
            navigationParams.bottomMargin = desiredNavigationBottomMargin;
            detailNavigation.setLayoutParams(navigationParams);
        }

        final ViewGroup.MarginLayoutParams pagerParams =
                (ViewGroup.MarginLayoutParams) binding.viewPager.getLayoutParams();
        final int desiredPagerBottomMargin = viewPagerBaseBottomMargin + bottomInset;
        if (pagerParams.bottomMargin != desiredPagerBottomMargin) {
            pagerParams.bottomMargin = desiredPagerBottomMargin;
            binding.viewPager.setLayoutParams(pagerParams);
        }
    }

    private void updateDetailContentTopMargin(final boolean fullscreen) {
        if (binding == null || activityToolbarLayout == null) {
            return;
        }
        final int desiredTopMargin = getDetailContentTopMargin(
                fullscreen, activityStatusBarInset);
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) binding.detailMainContent.getLayoutParams();
        if (params.topMargin != desiredTopMargin) {
            params.topMargin = desiredTopMargin;
            binding.detailMainContent.setLayoutParams(params);
        }
    }

    private void updateDetailContentStartMargins(final boolean fullscreen) {
        if (binding == null) {
            return;
        }
        setStartMargin(binding.appBarLayout,
                getDetailContentStartMargin(fullscreen, appBarBaseStartMargin));
        setStartMargin(binding.viewPager,
                getDetailContentStartMargin(fullscreen, viewPagerBaseStartMargin));
    }

    private static void setStartMargin(@NonNull final View view, final int startMargin) {
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.getMarginStart() != startMargin) {
            params.setMarginStart(startMargin);
            view.setLayoutParams(params);
        }
    }

    private void updatePinnedPlayerLayout() {
        updatePinnedPlayerLayout(0);
    }

    private void updatePinnedPlayerLayout(final int requestedThumbnailHeight) {
        if (binding == null || getView() == null) {
            return;
        }

        final boolean playerAttached = getRoot()
                .map(root -> root.getParent() == binding.playerPlaceholder)
                .orElse(false);
        final boolean phoneDetailLayout = isPhoneDetailLayout(binding.relatedItemsLayout != null);
        final boolean usePinnedMode = shouldUsePinnedPlayerLayout(
                pinVideoWhileScrolling,
                isPlayerAvailable() && player.videoPlayerSelected(), playerAttached,
                isFullscreen(), phoneDetailLayout,
                DeviceUtils.isTv(requireContext()), DeviceUtils.isTablet(requireContext()));
        final int layoutHeight = binding.detailThumbnailImageView.getLayoutParams().height;
        final int thumbnailHeight = resolveThumbnailHeight(requestedThumbnailHeight, layoutHeight,
                binding.detailThumbnailImageView.getHeight());
        final int playerHeight = usePinnedMode ? thumbnailHeight : 0;
        final int collapseMode = usePinnedMode
                ? PINNED_PLAYER_COLLAPSE_MODE : LEGACY_PLAYER_COLLAPSE_MODE;
        final int scrollFlags;
        if (isFullscreen() && DeviceUtils.isTv(requireContext())) {
            // D-pad focus must not scroll the fullscreen video out of the viewport.
            scrollFlags = 0;
        } else {
            scrollFlags = usePinnedMode
                    ? PINNED_DETAIL_SCROLL_FLAGS : LEGACY_DETAIL_SCROLL_FLAGS;
        }

        final AppBarLayout.LayoutParams appBarParams =
                (AppBarLayout.LayoutParams) binding.detailCollapsingToolbarLayout.getLayoutParams();
        boolean changed = false;
        if (appBarParams.getScrollFlags() != scrollFlags) {
            appBarParams.setScrollFlags(scrollFlags);
            binding.detailCollapsingToolbarLayout.setLayoutParams(appBarParams);
            changed = true;
        }
        final int desiredContentTopMargin = getContentTopMargin(phoneDetailLayout, thumbnailHeight);
        final ViewGroup.MarginLayoutParams contentParams =
                (ViewGroup.MarginLayoutParams) binding.detailContentRootLayout.getLayoutParams();
        if (contentParams.topMargin != desiredContentTopMargin) {
            contentParams.topMargin = desiredContentTopMargin;
            binding.detailContentRootLayout.setLayoutParams(contentParams);
            changed = true;
        }

        if (binding.detailCollapsingToolbarLayout.getMinimumHeight() != playerHeight) {
            binding.detailCollapsingToolbarLayout.setMinimumHeight(playerHeight);
            changed = true;
        }

        final CollapsingToolbarLayout.LayoutParams thumbnailParams =
                (CollapsingToolbarLayout.LayoutParams)
                        binding.detailThumbnailRootLayout.getLayoutParams();
        if (thumbnailParams.getCollapseMode() != collapseMode) {
            thumbnailParams.setCollapseMode(collapseMode);
            binding.detailThumbnailRootLayout.setLayoutParams(thumbnailParams);
            changed = true;
        }

        if (phoneDetailLayout) {
            final ViewGroup collapsingToolbarLayout = binding.detailCollapsingToolbarLayout;
            final View frontView = usePinnedMode
                    ? binding.detailThumbnailRootLayout : binding.detailContentRootLayout;
            if (collapsingToolbarLayout.indexOfChild(frontView)
                    != collapsingToolbarLayout.getChildCount() - 1) {
                frontView.bringToFront();
            }
        }
        if (changed) {
            binding.appBarLayout.requestLayout();
        }
    }

    private void showContent() {
        binding.detailContentRootHiding.setVisibility(View.VISIBLE);
    }

    /**
     * Observe the current queue and its pages without retaining destroyed child views.
     * @return the current queue followed by queue changes and newly loaded pages
     */
    public Observable<Optional<PlayQueue>> learningQueueUpdates() {
        return Observable.defer(() -> learningQueueChanges
                .startWithItem(Optional.ofNullable(playQueue))
                .switchMap(optional -> {
                    if (optional.isEmpty()) {
                        return Observable.just(optional);
                    }
                    final PlayQueue queue = optional.get();
                    if (queue.getLearningPlaylistContext() == null) {
                        return Observable.just(optional);
                    }
                    if (queue.getBroadcastReceiver() == null) {
                        queue.init();
                    }
                    return queue.getBroadcastReceiver().toObservable()
                            .map(event -> optional);
                }));
    }

    public boolean hasLearningPlaylist(final int streamServiceId, final String streamUrl) {
        final var navigation = LearningPlaylistNavigation.from(
                playQueue, ProfileManager.getActiveProfileId(requireContext()),
                streamServiceId, streamUrl);
        return navigation != null && LearningMode.isPlaylistNavigationEnabled(requireContext())
                && LearningContentManager.getInstance(requireContext())
                        .isSourceMarked(navigation.getCourse().getSourceId());
    }

    public void openNextLearningLesson(final PlayQueue queue) {
        final var navigation = LearningPlaylistNavigation.from(
                queue, ProfileManager.getActiveProfileId(requireContext()), serviceId, url);
        if (queue != playQueue || navigation == null || navigation.getNext() == null
                || !LearningMode.isPlaylistNavigationEnabled(requireContext())
                || !LearningContentManager.getInstance(requireContext())
                        .isSourceMarked(navigation.getCourse().getSourceId())) {
            return;
        }
        final PlayQueueItem next = navigation.getNext();
        queue.setIndex(queue.getIndex() + 1);
        if (next.isLocalMedia()) {
            NavigationHelper.playOnMainPlayer(requireContext(), queue, false);
        } else {
            NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                    next.getServiceId(), next.getUrl(), next.getTitle(), queue, false);
        }
    }

    boolean isStaleLearningQueue(final PlayQueue queue) {
        if (playerDismissed) {
            return true;
        }
        if (pendingLearningQueue == null) {
            return false;
        }
        return !queue.equalStreamsAndIndex(pendingLearningQueue);
    }

    protected void setInitialData(final int newServiceId,
                                  @Nullable final String newUrl,
                                  @NonNull final String newTitle,
                                  @Nullable final PlayQueue newPlayQueue) {
        this.serviceId = newServiceId;
        this.url = newUrl;
        this.title = newTitle;
        this.playQueue = newPlayQueue;
        pendingLearningQueue = newPlayQueue != null
                && newPlayQueue.getLearningPlaylistContext() != null ? newPlayQueue : null;
        if (newUrl != null) {
            playerDismissed = false;
        }
        learningQueueChanges.onNext(Optional.ofNullable(newPlayQueue));
        final PlayQueueItem queueItem = newPlayQueue == null ? null : newPlayQueue.getItem();
        currentLocalItem = queueItem != null && queueItem.isDirectMedia()
                ? queueItem : null;
    }

    private void setErrorImage(final int imageResource) {
        if (binding == null || activity == null) {
            return;
        }

        binding.detailThumbnailImageView.setImageDrawable(
                AppCompatResources.getDrawable(requireContext(), imageResource));
        animate(binding.detailThumbnailImageView, false, 0, AnimationType.ALPHA,
                0, () -> animate(binding.detailThumbnailImageView, true, 500));
    }

    @Override
    public void handleError() {
        super.handleError();
        setErrorImage(R.drawable.not_available_monkey);


        if (binding.relatedItemsLayout != null) { // hide related streams for tablets
            binding.relatedItemsLayout.setVisibility(View.INVISIBLE);
        }

        // hide comments / related streams / description tabs
        binding.viewPager.setVisibility(View.GONE);
        detailNavigation.setVisibility(View.GONE);
    }

    private void hideAgeRestrictedContent() {
        showTextError(getString(R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)));
    }

    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                switch (intent.getAction()) {
                    case ACTION_SHOW_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;
                    case ACTION_HIDE_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                        break;
                    case ACTION_PLAYER_STARTED:
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!playerHolder.isBound()) {
                            playerHolder.startService(
                                    false, VideoDetailFragment.this);
                        }
                        break;
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER);
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER);
        intentFilter.addAction(ACTION_PLAYER_STARTED);
        ContextCompat.registerReceiver(activity, broadcastReceiver, intentFilter,
                ContextCompat.RECEIVER_EXPORTED);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    //////////////////////////////////////////////////////////////////////////*/

    private void restoreDefaultOrientation() {
        if (isPlayerAvailable() && player.videoPlayerSelected()) {
            toggleFullscreenIfInFullscreenMode();
        }

        // This will show the system UI and exit fullscreen without changing playback state.
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        if (activity != null && !DeviceUtils.isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        binding.detailLocalLikes.bind(null);

        if (sponsorshipBadge != null) {
            sponsorshipBadge.clear();
        }

        super.showLoading();

        // If data is already cached, the transition from visible to hidden and back is unnecessary.
        final boolean streamInfoCached =
                ExtractorHelper.isCached(serviceId, url, InfoCache.Type.STREAM);
        if (shouldHidePreviousStreamContent(streamInfoCached)) {
            binding.detailContentRootHiding.setVisibility(View.INVISIBLE);
            binding.viewPager.setVisibility(View.GONE);
            detailNavigation.setVisibility(View.GONE);
        }

        animate(binding.detailThumbnailPlayButton, false, 50);
        animate(binding.detailDurationView, false, 100);
        binding.detailPositionView.setVisibility(View.GONE);
        binding.positionView.setVisibility(View.GONE);

        binding.detailVideoTitleView.setText(title);

        applyTitleDisplayPolicy(false);
        animate(binding.detailVideoTitleView, true, 0);

        binding.detailToggleSecondaryControlsView.setVisibility(View.GONE);
        binding.detailTitleRootLayout.setClickable(false);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        if (binding.relatedItemsLayout != null) {
            if (showRelatedItems && !isRadioQueue()) {
                binding.relatedItemsLayout.setVisibility(
                        isFullscreen() ? View.GONE : View.INVISIBLE);
            } else {
                binding.relatedItemsLayout.setVisibility(View.GONE);
            }
        }

        CoilUtils.dispose(binding.detailThumbnailImageView);
        CoilUtils.dispose(binding.detailSubChannelThumbnailView);
        CoilUtils.dispose(binding.overlayThumbnail);
        CoilUtils.dispose(binding.detailUploaderThumbnailView);
        binding.detailThumbnailImageView.setImageBitmap(null);
        binding.detailSubChannelThumbnailView.setImageBitmap(null);
        showQueueItemLoadingPreview();
    }

    private void showQueueItemLoadingPreview() {
        final PlayQueueItem queueItem = playQueue == null ? null : playQueue.getItem();
        if (!shouldShowQueueItemLoadingPreview(
                queueItem != null, queueItem != null && queueItem.isLocalMedia())) {
            return;
        }

        CoilHelper.INSTANCE.loadDetailsThumbnail(
                binding.detailThumbnailImageView,
                ExtractorImageCompat.thumbnailImages(queueItem));
        if (queueItem.getDuration() > 0) {
            binding.detailDurationView.setText(
                    Localization.getDurationString(queueItem.getDuration()));
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else if (queueItem.getStreamType() == StreamType.LIVE_STREAM
                || queueItem.getStreamType() == StreamType.AUDIO_LIVE_STREAM) {
            binding.detailDurationView.setText(R.string.duration_live);
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animate(binding.detailDurationView, true, 100);
        }
    }

    @Override
    public void handleResult(@NonNull final StreamInfo info) {
        super.handleResult(info);

        currentInfo = info;
        binding.detailLocalLikes.bind(info);
        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName(), playQueue);

        applyTitleDisplayPolicy(false);
        binding.detailUploaderRootLayout.setClickable(true);
        binding.detailsPanel.setVisibility(View.VISIBLE);
        binding.detailControlsShare.setVisibility(View.VISIBLE);
        binding.detailControlsOpenInBrowser.setVisibility(View.VISIBLE);
        binding.detailControlsCast.setVisibility(
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && FCastManager.canCast(requireContext(), info))
                        || FCastManager.canSendToTv(requireContext(), info)
                        || FCastManager.canSendToHomeAudio(requireContext())
                        ? View.VISIBLE : View.GONE);
        binding.detailControlsPlayWithKodi.setVisibility(
                KoreUtils.shouldShowPlayWithKodi(requireContext(), info.getServiceId())
                        ? View.VISIBLE : View.GONE);

        updateTabs(info);

        animate(binding.detailThumbnailPlayButton, true, 200);
        binding.detailVideoTitleView.setText(title);

        if (sponsorshipBadge == null) {
            sponsorshipBadge = new org.schabi.newpipe.sponsorblock.FullVideoLabelBadge(
                    binding.detailThumbnailRootLayout);
        }
        sponsorshipBadge.bind(info.getServiceId(), info.getUrl());

        binding.detailSubChannelThumbnailView.setVisibility(View.GONE);

        if (!isEmpty(info.getSubChannelName())) {
            displayBothUploaderAndSubChannel(info);
        } else {
            displayUploaderAsSubChannel(info);
        }

        if (info.getViewCount() >= 0) {
            if (info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization.listeningCount(activity,
                        info.getViewCount()));
            } else if (info.getStreamType().equals(StreamType.LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization
                        .localizeWatchingCount(activity, info.getViewCount()));
            } else {
                binding.detailViewCountView.setText(Localization
                        .localizeViewCount(activity, info.getViewCount()));
            }
            binding.detailViewCountView.setVisibility(View.VISIBLE);
        } else {
            binding.detailViewCountView.setVisibility(View.GONE);
        }

        updateLikeDislikeViews(info);

        if (info.getDuration() > 0) {
            binding.detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            binding.detailDurationView.setText(R.string.duration_live);
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else {
            binding.detailDurationView.setVisibility(View.GONE);
        }

        binding.detailTitleRootLayout.setClickable(true);
        binding.detailToggleSecondaryControlsView.setRotation(0);
        binding.detailToggleSecondaryControlsView.setVisibility(View.VISIBLE);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        checkUpdateProgressInfo(info);
        LocalMediaThumbnailLoader.INSTANCE.clear(binding.detailThumbnailImageView);
        CoilHelper.INSTANCE.loadDetailsThumbnail(binding.detailThumbnailImageView,
                ExtractorImageCompat.thumbnailImages(info));
        disposables.add(DeArrowService.getBranding(requireContext(), info)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(branding -> {
                    if (currentInfo != info
                            || !DeArrowService.applyBranding(requireContext(), info, branding)) {
                        return;
                    }
                    title = info.getName();
                    binding.detailVideoTitleView.setText(title);
                    LocalMediaThumbnailLoader.INSTANCE.clear(binding.detailThumbnailImageView);
                    CoilHelper.INSTANCE.loadDetailsThumbnail(binding.detailThumbnailImageView,
                            ExtractorImageCompat.thumbnailImages(info));
                    if (!isPlayerAvailable() || player.isStopped()) {
                        updateOverlayData(info.getName(), info.getUploaderName(),
                                ExtractorImageCompat.thumbnailImages(info));
                    }
                }, throwable -> Log.w(TAG, "Unable to load DeArrow branding", throwable)));
        showMetaInfoInTextView(org.schabi.newpipe.extractor.services.youtube.extractors
                        .YoutubeContentDisclosure.otherMetadata(info.getMetaInfo()),
                binding.detailMetaInfoTextView,
                binding.detailMetaInfoSeparator, disposables);

        if (!isPlayerAvailable() || player.isStopped()) {
            updateOverlayData(info.getName(), info.getUploaderName(),
                    ExtractorImageCompat.thumbnailImages(info));
        }

        if (!info.getErrors().isEmpty()) {
            // Bandcamp fan pages are not yet supported and thus a ContentNotAvailableException is
            // thrown. This is not an error and thus should not be shown to the user.
            for (final Throwable throwable : info.getErrors()) {
                if (throwable instanceof ContentNotSupportedException
                        && "Fan pages are not supported".equals(throwable.getMessage())) {
                    info.getErrors().remove(throwable);
                }
            }

            if (!info.getErrors().isEmpty()) {
                showSnackBarError(new ErrorInfo(info.getErrors(), UserAction.REQUESTED_STREAM,
                        "Some info not extracted: " + info.getUrl(), info));
            }
        }

        binding.detailControlsDownload.setVisibility(
                StreamTypeUtil.isLiveStream(info.getStreamType()) ? View.GONE : View.VISIBLE);
        binding.detailControlsBackground.setVisibility(
                info.getAudioStreams().isEmpty() && info.getVideoStreams().isEmpty()
                        ? View.GONE : View.VISIBLE);

        // YouTube live video can be supplied only through HLS/DASH manifests, without
        // individual video streams in StreamInfo. Keep its video controls and play icon.
        final boolean hasLiveVideoManifest = info.getStreamType() == StreamType.LIVE_STREAM
                && (!info.getHlsUrl().isEmpty() || !info.getDashMpdUrl().isEmpty());
        final boolean noVideoStreams = info.getVideoStreams().isEmpty()
                && info.getVideoOnlyStreams().isEmpty() && !hasLiveVideoManifest;
        binding.detailControlsPopup.setVisibility(noVideoStreams ? View.GONE : View.VISIBLE);
        binding.detailThumbnailPlayButton.setImageResource(
                noVideoStreams ? R.drawable.ic_headset_shadow : R.drawable.ic_play_arrow_shadow);
    }

    private void updateLikeDislikeViews(@NonNull final StreamInfo info) {
        binding.detailThumbsDisabledView.setVisibility(View.GONE);

        final boolean likesAvailable = info.getLikeCount() >= 0;
        if (likesAvailable) {
            binding.detailThumbsUpCountView.setText(Localization.shortCount(activity,
                    info.getLikeCount()));
        }
        binding.detailThumbsUpCountView.setVisibility(likesAvailable ? View.VISIBLE : View.GONE);
        binding.detailThumbsUpImgView.setVisibility(likesAvailable ? View.VISIBLE : View.GONE);

        final boolean dislikesAvailable = showDislikes && info.getDislikeCount() >= 0;
        if (dislikesAvailable) {
            binding.detailThumbsDownCountView.setText(Localization
                    .shortCount(activity, info.getDislikeCount()));
        }
        binding.detailThumbsDownCountView.setVisibility(
                dislikesAvailable ? View.VISIBLE : View.GONE);
        binding.detailThumbsDownImgView.setVisibility(dislikesAvailable ? View.VISIBLE : View.GONE);
    }

    private void displayUploaderAsSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getUploaderName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);

        if (info.getUploaderSubscriberCount() > -1) {
            binding.detailUploaderTextView.setText(
                    Localization.shortSubscriberCount(activity, info.getUploaderSubscriberCount()));
            binding.detailUploaderTextView.setVisibility(View.VISIBLE);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
        }

        CoilHelper.INSTANCE.loadAvatar(binding.detailSubChannelThumbnailView,
                ExtractorImageCompat.uploaderAvatarImages(info));
        binding.detailSubChannelThumbnailView.setVisibility(View.VISIBLE);
        binding.detailUploaderThumbnailView.setVisibility(View.GONE);
    }

    private void displayBothUploaderAndSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getSubChannelName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);

        final StringBuilder subText = new StringBuilder();
        if (!isEmpty(info.getUploaderName())) {
            subText.append(
                    String.format(getString(R.string.video_detail_by), info.getUploaderName()));
        }
        if (info.getUploaderSubscriberCount() > -1) {
            if (subText.length() > 0) {
                subText.append(Localization.DOT_SEPARATOR);
            }
            subText.append(
                    Localization.shortSubscriberCount(activity, info.getUploaderSubscriberCount()));
        }

        if (subText.length() > 0) {
            binding.detailUploaderTextView.setText(subText);
            binding.detailUploaderTextView.setVisibility(View.VISIBLE);
            binding.detailUploaderTextView.setSelected(true);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
        }

        CoilHelper.INSTANCE.loadAvatar(binding.detailSubChannelThumbnailView,
                info.getSubChannelAvatars());
        binding.detailSubChannelThumbnailView.setVisibility(View.VISIBLE);
        CoilHelper.INSTANCE.loadAvatar(binding.detailUploaderThumbnailView,
                ExtractorImageCompat.uploaderAvatarImages(info));
        binding.detailUploaderThumbnailView.setVisibility(View.VISIBLE);
    }

    public void openDownloadDialog() {
        if (currentInfo == null) {
            return;
        }

        try {
            final DownloadDialog downloadDialog = new DownloadDialog(activity, currentInfo);
            downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
        } catch (final Exception e) {
            ErrorUtil.showSnackbar(activity, new ErrorInfo(e, UserAction.DOWNLOAD_OPEN_DIALOG,
                    "Showing download dialog", currentInfo));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    private void checkUpdateProgressInfo(@NonNull final StreamInfo info) {
        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        if (!getResumePlaybackEnabled(activity)) {
            binding.positionView.setVisibility(View.GONE);
            binding.detailPositionView.setVisibility(View.GONE);
            return;
        }
        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        positionSubscriber = recordManager.loadStreamState(info)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    updatePlaybackProgress(
                            state.getProgressMillis(), info.getDuration() * 1000);
                }, e -> {
                    // impossible since the onErrorComplete()
                }, () -> {
                    binding.positionView.setVisibility(View.GONE);
                    binding.detailPositionView.setVisibility(View.GONE);
                });
    }

    private void updatePlaybackProgress(final long progress, final long duration) {
        if (!getResumePlaybackEnabled(activity)) {
            return;
        }
        final int progressSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(progress);
        final int durationSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
        // If the old and the new progress values have a big difference then use animation.
        // Otherwise don't because it affects CPU
        final int progressDifference = Math.abs(binding.positionView.getProgress()
                - progressSeconds);
        binding.positionView.setMax(durationSeconds);
        if (progressDifference > 2) {
            binding.positionView.setProgressAnimated(progressSeconds);
        } else {
            binding.positionView.setProgress(progressSeconds);
        }
        final String position = Localization.getDurationString(progressSeconds);
        if (position != binding.detailPositionView.getText()) {
            binding.detailPositionView.setText(position);
        }
        if (binding.positionView.getVisibility() != View.VISIBLE) {
            animate(binding.positionView, true, 100);
            animate(binding.detailPositionView, true, 100);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onViewCreated() {
        tryAddVideoPlayerView();
    }

    @Override
    public void onQueueUpdate(final PlayQueue queue) {
        if (isStaleLearningQueue(queue)) {
            return;
        }
        pendingLearningQueue = null;
        playQueue = queue;
        learningQueueChanges.onNext(Optional.of(queue));
        final PlayQueueItem queueItem = queue.getItem();
        final boolean radio = isRadioQueue();
        if (binding != null && bottomSheetBehavior != null) {
            bottomSheetBehavior.setDraggable(!radio);
            if (radio && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            setOverlayExpansionClickable(
                    bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
        }
        if (queueItem != null && queueItem.isDirectMedia()
                && binding != null
                && (currentLocalItem == null || !currentLocalItem.isSameItem(queueItem))) {
            setInitialData(queueItem.getServiceId(), queueItem.getUrl(),
                    queueItem.getTitle(), queue);
            prepareAndHandleLocalMedia(queueItem, true);
        }
        if (DEBUG) {
            Log.d(TAG, "onQueueUpdate() called with: serviceId = ["
                    + serviceId + "], url = [" + url + "], name = ["
                    + title + "], playQueue = [" + playQueue + "]");
        }

        // Register broadcast receiver to listen to playQueue changes
        // and hide the overlayPlayQueueButton when the playQueue is empty / destroyed.
        if (playQueue != null && playQueue.getBroadcastReceiver() != null) {
            playQueue.getBroadcastReceiver().subscribe(
                    event -> updateOverlayPlayQueueButtonVisibility()
            );
        }

        if (radio) {
            updateOverlayPlayQueueButtonVisibility();
            return;
        }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        @Nullable final StackItem stackPeek = stack.peek();
        if (stackPeek != null && !stackPeek.getPlayQueue().equalStreams(queue)) {
            @Nullable final PlayQueueItem playQueueItem = queue.getItem();
            if (playQueueItem != null) {
                stack.push(new StackItem(playQueueItem.getServiceId(), playQueueItem.getUrl(),
                        playQueueItem.getTitle(), queue));
                return;
            } // else continue below
        }

        @Nullable final StackItem stackWithQueue = findQueueInStack(queue);
        if (stackWithQueue != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and
            // no longer able to track progress. That's why we update our cached disposed
            // queue with the new one that is active and have the same history.
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.setPlayQueue(queue);
        }
    }

    @Override
    public void onPlaybackUpdate(final int state,
                                 final int repeatMode,
                                 final boolean shuffled,
                                 final PlaybackParameters parameters) {
        setOverlayPlayPauseImage(player != null && player.isPlaying());
        updateMinimizedVideo();

        switch (state) {
            case Player.STATE_PLAYING:
                if (binding.positionView.getAlpha() != 1.0f
                        && player.getPlayQueue() != null
                        && player.getPlayQueue().getItem() != null
                        && player.getPlayQueue().getItem().getUrl().equals(url)) {
                    animate(binding.positionView, true, 100);
                    animate(binding.detailPositionView, true, 100);
                }
                break;
        }
    }

    @Override
    public void onProgressUpdate(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
        // A queued callback can outlive the service connection or the fragment view.
        final Player currentPlayer = player;
        if (currentPlayer == null || binding == null || playerDismissed || playQueue == null
                || !currentPlayer.isPlaying()) {
            return;
        }

        final PlayQueue currentQueue = currentPlayer.getPlayQueue();
        final PlayQueueItem currentItem = currentQueue == null ? null : currentQueue.getItem();
        if (currentItem != null && url != null && url.equals(currentItem.getUrl())) {
            updatePlaybackProgress(currentProgress, duration);
        }
    }

    @Override
    public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
        if (isStaleLearningQueue(queue)) {
            return;
        }
        final StackItem item = findQueueInStack(queue);
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.setTitle(info.getName());
            item.setUrl(info.getUrl());
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (!queue.equalStreams(playQueue)) {
            return;
        }

        updateOverlayData(info.getName(), info.getUploaderName(),
                ExtractorImageCompat.thumbnailImages(info));
        if (currentInfo != null && info.getUrl().equals(currentInfo.getUrl())) {
            return;
        }

        currentInfo = info;
        binding.detailLocalLikes.bind(info);
        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), queue);
        setAutoPlay(false);
        // Delay execution just because it freezes the main thread, and while playing
        // next/previous video you see visual glitches
        // (when non-vertical video goes after vertical video)
        prepareAndHandleInfoIfNeededAfterDelay(info, true, 200);
    }

    @Override
    public void onMetadataUpdate(final MediaItemTag tag, final PlayQueue queue) {
        if (isStaleLearningQueue(queue)) {
            return;
        }
        final PlayQueueItem item = queue.getItem();
        if (item != null && item.isRadio()) {
            playQueue = queue;
            prepareAndHandleRadio(item);
            if (binding != null) {
                binding.overlayTitleTextView.setText(item.getTitle());
                binding.overlayChannelTextView.setText(isEmpty(item.getUploader())
                        ? getString(R.string.live_radio) : item.getUploader());
                LocalMediaThumbnailLoader.INSTANCE.clear(binding.overlayThumbnail);
                CoilHelper.INSTANCE.loadRadioLogo(binding.overlayThumbnail, tag.getThumbnailUrl());
            }
            return;
        }
        if (item == null || (!item.isLocalMedia() && !item.isLiveTv())
                || (playQueue != null && !queue.equalStreams(playQueue))) {
            return;
        }
        setInitialData(item.getServiceId(), item.getUrl(), item.getTitle(), queue);
        setAutoPlay(false);
        prepareAndHandleLocalMedia(item, true);
    }

    @Override
    public void onPlayerError(final PlaybackException error, final boolean isCatchableException) {
        if (binding != null && org.schabi.newpipe.error.InvidiousDirectRetry
                .isAvailable(serviceId, url)) {
            com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
                    R.string.invidious_media_error,
                    com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.invidious_direct_retry, view ->
                            org.schabi.newpipe.error.InvidiousDirectRetry.confirm(
                                    requireActivity(), serviceId, url, title))
                    .show();
        }
        if (!isCatchableException) {
            // Properly exit from fullscreen
            toggleFullscreenIfInFullscreenMode();
            hideMainPlayerOnLoadingNewStream();
        }
    }

    @Override
    public void onServiceStopped() {
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            setOverlayPlayPauseImage(false);
            if (currentInfo != null) {
                updateOverlayData(currentInfo.getName(),
                        currentInfo.getUploaderName(),
                        ExtractorImageCompat.thumbnailImages(currentInfo));
            } else if (currentLocalItem != null) {
                updateLocalOverlay(currentLocalItem);
            }
            updateOverlayPlayQueueButtonVisibility();
        }
    }

    @Override
    public void onFullscreenStateChanged(final boolean fullscreen) {
        if (miniPlayerCorners != null) {
            miniPlayerCorners.setExpansionFraction(
                    fullscreen || bottomSheetState != BottomSheetBehavior.STATE_COLLAPSED
                            ? 1f : 0f);
        }
        setupBrightness();
        if (!isPlayerAndPlayerServiceAvailable()
                || player.UIs().get(MainPlayerUi.class).isEmpty()
                || getRoot().map(View::getParent).isEmpty()) {
            return;
        }

        if (fullscreen && !DeviceUtils.isTv(activity)) {
            binding.overlayPlayPauseButton.requestFocus();
        }
        final Configuration configuration = getResources().getConfiguration();
        if (!fullscreen && FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                binding.relatedItemsLayout != null,
                configuration.orientation,
                configuration.screenWidthDp)) {
            showSystemUi();
            detailLayoutRecreationRequested = true;
            recreateDetailLayoutForConfigurationChange();
            return;
        }
        refreshFullscreenLayout(fullscreen);
        if (fullscreen && DeviceUtils.isTv(activity)) {
            player.UIs().get(MainPlayerUi.class).ifPresent(ui -> {
                ui.showControls(0);
                ui.getBinding().playPauseButton.requestFocus();
            });
        }
        scrollToTop();
    }

    @Override
    public void onPlaybackPresentationModeChanged(@NonNull final PlaybackPresentationMode mode) {
        // Switching between video/listen/background modes changes whether the mini player
        // should host the retained surface or fall back to the thumbnail.
        if (binding != null) {
            updateMinimizedVideo();
        }
    }

    void refreshFullscreenLayout(final boolean fullscreen) {
        if (tvFullscreenFocusGuard == null) {
            tvFullscreenFocusGuard = new TvFullscreenFocusGuard(binding);
        }
        tvFullscreenFocusGuard.update(fullscreen, DeviceUtils.isTv(requireContext()));
        if (fullscreen && DeviceUtils.isTv(requireContext())) {
            binding.appBarLayout.setExpanded(true, false);
        }
        if (fullscreen) {
            hideSystemUiIfNeeded();
        } else {
            showSystemUi();
        }
        binding.playerPlaceholder.setBackgroundColor(
                getPlayerPlaceholderBackgroundColor(fullscreen));
        updateDetailContentTopMargin(fullscreen);
        updateDetailContentStartMargins(fullscreen);
        updateDetailNavigationBottomInset();
        binding.getRoot().post(this::updateDetailNavigationBottomInset);
        if (fullscreen) {
            detailNavigation.setVisibility(View.GONE);
        } else {
            updateDetailNavigationVisibility();
        }

        if (binding.relatedItemsLayout != null) {
            if (showRelatedItems && !isRadioQueue()) {
                binding.relatedItemsLayout.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            } else {
                binding.relatedItemsLayout.setVisibility(View.GONE);
            }
        }
        updatePinnedPlayerLayout();

        tryAddVideoPlayerView();
    }

    @Override
    public void onScreenRotationButtonClicked(final boolean fullscreen) {
        requestFullscreenOrientation(fullscreen);
    }

    @Override
    public void onManualFullscreenButtonClicked(final boolean fullscreen) {
        requestFullscreenOrientation(fullscreen);
    }

    private void requestFullscreenOrientation(final boolean fullscreen) {
        final Optional<MainPlayerUi> playerUi = player == null
                ? Optional.empty() : player.UIs().get(MainPlayerUi.class);
        final MainPlayerUi ui = playerUi.orElse(null);
        final int currentOrientation = getResources().getConfiguration().orientation;
        final boolean landscape = currentOrientation == Configuration.ORIENTATION_LANDSCAPE;

        if (ui != null && DeviceUtils.isTablet(activity)) {
            ui.onManualFullscreenChange(currentOrientation);
        }

        // TV never rotates. Tablets keep their current orientation in the same cases as before,
        // but apply the requested fullscreen state explicitly instead of toggling blindly.
        if (DeviceUtils.isTv(activity) || DeviceUtils.isDesktopMode(activity)
                || DeviceUtils.isInMultiWindow(activity) || DeviceUtils.isTablet(activity)
                && (!globalScreenOrientationLocked(activity) || landscape)) {
            clearPendingFullscreenTransition();
            if (ui != null) {
                ui.setFullscreen(fullscreen);
            }
            return;
        }

        final FullscreenOrientationPolicy.VideoContentOrientation contentOrientation =
                ui == null
                        ? FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN
                        : ui.getVideoContentOrientation();
        final int targetOrientation = FullscreenOrientationPolicy.targetConfigurationOrientation(
                fullscreen, contentOrientation);
        pendingFullscreenOrientation = targetOrientation;
        pendingFullscreenState = fullscreen
                ? FullscreenOrientationPolicy.ENTER_FULLSCREEN
                : FullscreenOrientationPolicy.EXIT_FULLSCREEN;

        // If Android is already in the requested orientation it will not emit another
        // configuration callback. Portrait fullscreen must still lock the activity so rotating
        // the device cannot override the video's content orientation.
        if (FullscreenOrientationPolicy.isTargetOrientation(
                currentOrientation, targetOrientation)) {
            if (FullscreenOrientationPolicy.shouldLockPortraitFullscreen(
                    fullscreen, contentOrientation)) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            if (ui != null) {
                applyFullscreenState(ui, pendingFullscreenState);
                clearPendingFullscreenTransition();
            }
            return;
        }

        final int requestedOrientation =
                targetOrientation == Configuration.ORIENTATION_LANDSCAPE
                        ? FullscreenOrientationPolicy.requestedLandscapeOrientation(
                                PlayerRotationMode.get(activity))
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        activity.setRequestedOrientation(requestedOrientation);
    }

    @Override
    public void onMoreOptionsLongClicked() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        final ValueAnimator valueAnimator = ValueAnimator
                .ofInt(0, -binding.playerPlaceholder.getHeight());
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            behavior.setTopAndBottomOffset((int) animation.getAnimatedValue());
            binding.appBarLayout.requestLayout();
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(500);
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "showSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        EdgeToEdgeHelper.showSystemBars(activity);
        restoreSystemBarAppearance();
    }

    private void restoreSystemBarAppearance() {
        EdgeToEdgeHelper.setLightSystemBars(
                activity, ThemeHelper.isLightThemeSelected(activity));
    }

    private void clearLightSystemBarAppearance() {
        EdgeToEdgeHelper.setLightSystemBars(activity, false);
    }

    private void hideSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "hideSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // In multiWindow mode status bar is not transparent for devices with cutout
        // if it is hidden. Keeping it visible is better in this case.
        final boolean isInMultiWindow = DeviceUtils.isInMultiWindow(activity);
        EdgeToEdgeHelper.hideSystemBars(activity, !isInMultiWindow);
        clearLightSystemBarAppearance();
    }

    // Listener implementation
    @Override
    public void hideSystemUiIfNeeded() {
        if (isFullscreen()
                && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            hideSystemUi();
        }
    }

    private boolean isFullscreen() {
        return isPlayerAvailable() && player.UIs().get(VideoPlayerUi.class)
                .map(VideoPlayerUi::isFullscreen).orElse(false);
    }

    public boolean isNativePipEligible() {
        if (!isPlayerAvailable() || binding == null || !player.videoPlayerSelected()
                || player.isAudioOnly()) {
            return false;
        }
        final int state = player.getCurrentState();
        return bottomSheetBehavior != null
                && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN
                && (state == Player.STATE_PLAYING
                || state == Player.STATE_BUFFERING
                || state == Player.STATE_PAUSED
                || state == Player.STATE_PAUSED_SEEK);
    }

    public boolean isNativePipPlaying() {
        return isPlayerAvailable() && (player.isPlaying() || player.isLoading());
    }

    public float getNativePipAspectRatio() {
        return player == null ? 0.0f : player.UIs().get(MainPlayerUi.class)
                .map(MainPlayerUi::getVideoAspectRatio)
                .orElse(0.0f);
    }

    @NonNull
    public Rect getNativePipSourceRect() {
        final Rect sourceRect = new Rect();
        if (player != null) {
            player.UIs().get(MainPlayerUi.class)
                    .ifPresent(ui -> ui.getRenderedVideoSurface().getGlobalVisibleRect(sourceRect));
        }
        return sourceRect;
    }

    public void prepareNativePipEntry() {
        if (nativePipPrepared || !isNativePipEligible()) {
            return;
        }
        final MainPlayerUi ui = player.UIs().get(MainPlayerUi.class).orElse(null);
        if (ui == null) {
            return;
        }
        nativePipPrepared = true;
        ui.getBinding().playbackInformationPanel.setVisibility(View.GONE);
        minimizedVideoSurface.restore();
        binding.getRoot().addOnLayoutChangeListener(nativePipLayoutListener);
        FullscreenCommentsSidebar.hide(activity);
        nativePipPreviousBottomSheetState = bottomSheetBehavior.getState();
        if (nativePipPreviousBottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        nativePipForcedFullscreen = !ui.isFullscreen();
        if (nativePipForcedFullscreen) {
            ui.toggleFullscreen();
        }
        ui.closeItemsList();
        ui.hideControls(0, 0);
        setHeightThumbnail();
        // Eligibility already requires video playback. Re-enabling it here can reload the
        // media source (always for local files), clearing the decoded frame during PiP entry.
    }

    public void onNativePipModeChanged(final boolean inPictureInPictureMode) {
        if (inPictureInPictureMode) {
            prepareNativePipEntry();
            if (player != null) {
                player.UIs().get(MainPlayerUi.class)
                        .ifPresent(ui -> {
                            ui.hideControls(0, 0);
                            ui.restoreVideoSurfaceAfterLayoutTransition();
                        });
            }
            return;
        }
        if (binding != null) {
            binding.getRoot().removeOnLayoutChangeListener(nativePipLayoutListener);
        }
        nativePipPrepared = false;
        if (player == null) {
            nativePipForcedFullscreen = false;
            return;
        }
        player.UIs().get(MainPlayerUi.class).ifPresent(ui -> {
            if (nativePipForcedFullscreen && ui.isFullscreen()) {
                ui.toggleFullscreen();
            }
            if (bottomSheetBehavior != null
                    && nativePipPreviousBottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
        nativePipForcedFullscreen = false;
        if (binding != null) {
            refreshFullscreenLayout(isFullscreen());
        }
        scheduleResumeLayoutRestore();
    }

    private boolean playerIsNotStopped() {
        return isPlayerAvailable() && !player.isStopped();
    }

    private void restoreDefaultBrightness() {
        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (lp.screenBrightness == -1) {
            return;
        }

        // Restore the old  brightness when fragment.onPause() called or
        // when a player is in portrait
        lp.screenBrightness = -1;
        activity.getWindow().setAttributes(lp);
    }

    private void setupBrightness() {
        if (activity == null) {
            return;
        }

        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (!isFullscreen() || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            // Apply system brightness when the player is not in fullscreen
            restoreDefaultBrightness();
        } else {
            // Do not restore if user has disabled brightness gesture
            if (!PlayerHelper.getActionForRightGestureSide(activity)
                    .equals(getString(R.string.brightness_control_key))
                    && !PlayerHelper.getActionForLeftGestureSide(activity)
                    .equals(getString(R.string.brightness_control_key))) {
                return;
            }
            // Restore already saved brightness level
            final float brightnessLevel = PlayerHelper.getScreenBrightness(activity);
            if (brightnessLevel == lp.screenBrightness) {
                return;
            }
            lp.screenBrightness = brightnessLevel;
            activity.getWindow().setAttributes(lp);
        }
    }

    /**
     * Make changes to the UI to accommodate for better usability on bigger screens such as TVs
     * or in Android's desktop mode (DeX etc).
     */
    private void accommodateForTvAndDesktopMode() {
        if (DeviceUtils.isTv(getContext())) {
            // remove ripple effects from detail controls
            final int transparent = ContextCompat.getColor(requireContext(),
                    R.color.transparent_background_color);
            binding.detailControlsPlaylistAppend.setBackgroundColor(transparent);
            binding.detailControlsBackground.setBackgroundColor(transparent);
            binding.detailControlsPopup.setBackgroundColor(transparent);
            binding.detailControlsDownload.setBackgroundColor(transparent);
            binding.detailControlsShare.setBackgroundColor(transparent);
            binding.detailControlsOpenInBrowser.setBackgroundColor(transparent);
            binding.detailControlsCast.setBackgroundColor(transparent);
            binding.detailControlsPlayWithKodi.setBackgroundColor(transparent);
        }
        if (DeviceUtils.isTv(getContext()) || DeviceUtils.isDesktopMode(getContext())) {
            // The thumbnail container also hosts playback. Its focused/hovered foreground
            // must not tint the video; individual controls retain their focus indicators.
            binding.detailThumbnailRootLayout.setForeground(null);
        }
    }

    private void checkLandscape() {
        if ((!player.isPlaying() && player.getPlayQueue() != playQueue)
                || player.getPlayQueue() == null) {
            setAutoPlay(true);
        }

        player.UIs().get(MainPlayerUi.class).ifPresent(MainPlayerUi::checkLandscape);
        // Let's give a user time to look at video information page if video is not playing
        if (globalScreenOrientationLocked(activity) && !player.isPlaying()) {
            player.play();
        }
    }

    /*
     * Means that the player fragment was swiped away via BottomSheetLayout
     * and is empty but ready for any new actions. See cleanUp()
     * */
    private boolean wasCleared() {
        return url == null;
    }

    @Nullable
    private StackItem findQueueInStack(final PlayQueue queue) {
        StackItem item = null;
        final Iterator<StackItem> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            final StackItem next = iterator.next();
            if (next.getPlayQueue().equalStreams(queue)) {
                item = next;
                break;
            }
        }
        return item;
    }

    private void replaceQueueIfUserConfirms(final Runnable onAllow) {
        @Nullable final PlayQueue activeQueue = isPlayerAvailable() ? player.getPlayQueue() : null;

        // Player will have STATE_IDLE when a user pressed back button
        if (isClearingQueueConfirmationRequired(activity)
                && playerIsNotStopped()
                && activeQueue != null
                && !activeQueue.equalStreams(playQueue)) {
            showClearingQueueConfirmation(onAllow);
        } else {
            onAllow.run();
        }
    }

    private void showClearingQueueConfirmation(final Runnable onAllow) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.clear_queue_confirmation_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    onAllow.run();
                    dialog.dismiss();
                })
                .show();
    }

    private void showExternalVideoPlaybackDialog() {
        if (currentInfo == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.select_quality_external_players);
        builder.setNeutralButton(R.string.open_in_browser, (dialog, i) ->
                ShareUtils.openUrlInBrowser(requireActivity(), url));

        final List<VideoStream> videoStreamsForExternalPlayers =
                ListHelper.getSortedStreamVideosList(
                        activity,
                        getUrlAndNonTorrentStreams(currentInfo.getVideoStreams()),
                        getUrlAndNonTorrentStreams(currentInfo.getVideoOnlyStreams()),
                        false,
                        false
                );

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players);
            builder.setPositiveButton(R.string.ok, null);

        } else {
            final int selectedVideoStreamIndexForExternalPlayers =
                    ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers);
            final CharSequence[] resolutions = videoStreamsForExternalPlayers.stream()
                    .map(VideoStream::getResolution).toArray(CharSequence[]::new);

            builder.setSingleChoiceItems(resolutions, selectedVideoStreamIndexForExternalPlayers,
                    null);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (dialog, i) -> {
                final int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                // We don't have to manage the index validity because if there is no stream
                // available for external players, this code will be not executed and if there is
                // no stream which matches the default resolution, 0 is returned by
                // ListHelper.getDefaultResolutionIndex.
                // The index cannot be outside the bounds of the list as its always between 0 and
                // the list size - 1, .
                startOnExternalPlayer(activity, currentInfo,
                        videoStreamsForExternalPlayers.get(index));
            });
        }
        builder.show();
    }

    private void showExternalAudioPlaybackDialog() {
        if (currentInfo == null) {
            return;
        }

        final List<AudioStream> audioStreams = getUrlAndNonTorrentStreams(
                currentInfo.getAudioStreams());
        final List<AudioStream> audioTracks =
                ListHelper.getFilteredAudioStreams(activity, audioStreams);

        if (audioTracks.isEmpty()) {
            Toast.makeText(activity, R.string.no_audio_streams_available_for_external_players,
                    Toast.LENGTH_SHORT).show();
        } else if (audioTracks.size() == 1) {
            startOnExternalPlayer(activity, currentInfo, audioTracks.get(0));
        } else {
            final int selectedAudioStream =
                    ListHelper.getDefaultAudioFormat(activity, audioTracks);
            final CharSequence[] trackNames = audioTracks.stream()
                    .map(audioStream -> Localization.audioTrackName(activity, audioStream))
                    .toArray(CharSequence[]::new);

            new AlertDialog.Builder(activity)
                    .setTitle(R.string.select_audio_track_external_players)
                    .setNeutralButton(R.string.open_in_browser, (dialog, i) ->
                            ShareUtils.openUrlInBrowser(requireActivity(), url))
                    .setSingleChoiceItems(trackNames, selectedAudioStream, null)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, i) -> {
                        final int index = ((AlertDialog) dialog).getListView()
                                .getCheckedItemPosition();
                        startOnExternalPlayer(activity, currentInfo, audioTracks.get(index));
                    })
                    .show();
        }
    }

    /*
     * Remove unneeded information while waiting for a next task
     * */
    private void cleanUp() {
        playerDismissed = true;
        // New beginning
        stack.clear();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        setInitialData(0, null, "", null);
        currentInfo = null;
        playerHolder.stopService();
        updateOverlayData(null, null, List.of());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Bottom mini player
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * That's for Android TV support. Move focus from main fragment to the player or back
     * based on what is currently selected
     *
     * @param toMain if true than the main fragment will be focused or the player otherwise
     */
    private void moveFocusToMainFragment(final boolean toMain) {
        setupBrightness();
        final ViewGroup mainFragment = requireActivity().findViewById(R.id.fragment_holder);
        // Hamburger button steels a focus even under bottomSheet
        final Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        final int afterDescendants = ViewGroup.FOCUS_AFTER_DESCENDANTS;
        final int blockDescendants = ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        if (toMain) {
            mainFragment.setDescendantFocusability(afterDescendants);
            toolbar.setDescendantFocusability(afterDescendants);
            if (DeviceUtils.isTv(activity)) {
                updateTvMiniPlayerFocus();
            } else {
                ((ViewGroup) requireView()).setDescendantFocusability(blockDescendants);
            }
            // Only focus the mainFragment if the mainFragment (e.g. search-results)
            // or the toolbar (e.g. Textfield for search) don't have focus.
            // This was done to fix problems with the keyboard input, see also #7490
            if (!mainFragment.hasFocus() && !toolbar.hasFocus()) {
                mainFragment.requestFocus();
            }
        } else {
            mainFragment.setDescendantFocusability(blockDescendants);
            toolbar.setDescendantFocusability(blockDescendants);
            ((ViewGroup) requireView()).setDescendantFocusability(afterDescendants);
            if (DeviceUtils.isTv(activity)) {
                updateTvMiniPlayerFocus();
            }
            // Only focus the player if it not already has focus
            if (!binding.getRoot().hasFocus()) {
                binding.detailThumbnailRootLayout.requestFocus();
            }
        }
    }

    /**
     * When the mini player exists the view underneath it is not touchable.
     * Bottom padding should be equal to the mini player's height in this case
     *
     * @param showMore whether main fragment should be expanded or not
     */
    private void manageSpaceAtTheBottom(final boolean showMore) {
        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        final ViewGroup holder = requireActivity().findViewById(R.id.fragment_holder);
        final int newBottomPadding;
        if (showMore) {
            newBottomPadding = 0;
        } else {
            newBottomPadding = peekHeight;
        }
        if (holder.getPaddingBottom() == newBottomPadding) {
            return;
        }
        holder.setPadding(holder.getPaddingLeft(),
                holder.getPaddingTop(),
                holder.getPaddingRight(),
                newBottomPadding);
    }

    private void setupBottomPlayer() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();

        final FrameLayout bottomSheetLayout = activity.findViewById(R.id.fragment_player_holder);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetBehavior.setState(lastStableBottomSheetState);
        updateBottomSheetState(lastStableBottomSheetState);
        if (miniPlayerCorners != null) {
            miniPlayerCorners.release();
        }
        miniPlayerCorners = new MiniPlayerCorners(bottomSheetLayout,
                PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean(getString(R.string.rounded_mini_player_key), true));
        miniPlayerCorners.setExpansionFraction(
                isFullscreen() || lastStableBottomSheetState != BottomSheetBehavior.STATE_COLLAPSED
                        ? 1f : 0f);

        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false);
            bottomSheetBehavior.setPeekHeight(peekHeight);
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.overlayLayout.setAlpha(MAX_OVERLAY_ALPHA);
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                binding.overlayLayout.setAlpha(0);
                setOverlayElementsClickable(false);
            }
        }

        bottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                updateBottomSheetState(newState);
                updateMinimizedVideo();

                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(true);

                        bottomSheetBehavior.setPeekHeight(0);
                        cleanUp();
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        moveFocusToMainFragment(false);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);
                        // Disable click because overlay buttons located on top of buttons
                        // from the player
                        setOverlayElementsClickable(false);
                        hideSystemUiIfNeeded();
                        // Conditions when the player should be expanded to fullscreen
                        if (getResources().getConfiguration().orientation
                                == Configuration.ORIENTATION_LANDSCAPE
                                && isPlayerAvailable()
                                && player.isPlaying()
                                && !isFullscreen()
                                && !DeviceUtils.isTablet(activity)) {
                            player.UIs().get(MainPlayerUi.class)
                                    .ifPresent(MainPlayerUi::toggleFullscreen);
                        }
                        updatePinnedPlayerLayout();
                        setOverlayLook(binding.appBarLayout, behavior, 1);
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        clearPendingFullscreenTransition();
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);

                        // Re-enable clicks
                        setOverlayElementsClickable(true);
                        if (isPlayerAvailable()) {
                            player.UIs().get(MainPlayerUi.class)
                                    .ifPresent(MainPlayerUi::closeItemsList);
                        }
                        updatePinnedPlayerLayout();
                        setOverlayLook(binding.appBarLayout, behavior, 0);
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                    case BottomSheetBehavior.STATE_SETTLING:
                        if (isFullscreen()) {
                            showSystemUi();
                        }
                        if (isPlayerAvailable()) {
                            player.UIs().get(MainPlayerUi.class).ifPresent(ui -> {
                                if (ui.isControlsVisible()) {
                                    ui.hideControls(0, 0);
                                }
                            });
                        }
                        break;
                    case BottomSheetBehavior.STATE_HALF_EXPANDED:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                setOverlayLook(binding.appBarLayout, behavior, slideOffset);
            }
        };

        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
        if (DeviceUtils.isTv(activity)) {
            updateTvMiniPlayerFocus();
        }
        updateMinimizedVideo();

        // User opened a new page and the player will hide itself
        activity.getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    private void updateMinimizedVideo() {
        if (binding == null) {
            return;
        }
        final boolean minimized = bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED
                && !nativePipPrepared && !isFullscreen()
                && player != null && player.videoPlayerSelected()
                && player.getPlaybackPresentationMode().rendersVideo() && !player.isAudioOnly();
        final Optional<MainPlayerUi> ui = player == null ? Optional.empty()
                : player.UIs().get(MainPlayerUi.class);
        if (minimized && ui.isPresent()) {
            final MainPlayerUi mainPlayerUi = ui.get();
            final boolean moved = minimizedVideoSurface.show(
                    mainPlayerUi.getBinding().videoSurfaceContainer, binding.overlayVideo);
            if (minimizedVideoSurface.isHosting()) {
                binding.overlayVideo.setVisibility(View.VISIBLE);
                binding.overlayThumbnail.setAlpha(0f);
                binding.overlayLayout.setAlpha(1f);
                if (moved) {
                    // Detaching and reattaching a SurfaceView can destroy its surface without a
                    // matching surfaceCreated callback, leaving the decoder output black.
                    // Rebind explicitly once the move has reached the UI queue.
                    mainPlayerUi.restoreVideoSurfaceAfterLayoutTransition();
                }
            } else {
                // The player view has not been attached yet, so there is no surface to show.
                // Fall back to the thumbnail instead of leaving an empty visible host.
                minimizedVideoSurface.restore();
                binding.overlayVideo.setVisibility(View.GONE);
                binding.overlayThumbnail.setAlpha(1f);
            }
        } else {
            final boolean restored = minimizedVideoSurface.restore();
            binding.overlayVideo.setVisibility(View.GONE);
            binding.overlayThumbnail.setAlpha(1f);
            if (restored) {
                // Rebind after returning the surface to the full player as well, otherwise the
                // expanded player can stay black until the next surface callback.
                ui.ifPresent(MainPlayerUi::restoreVideoSurfaceAfterLayoutTransition);
            }
        }
    }

    private void updateOverlayPlayQueueButtonVisibility() {
        final boolean isPlayQueueEmpty =
                player == null // no player => no play queue :)
                        || player.getPlayQueue() == null
                        || player.getPlayQueue().isEmpty();
        if (binding != null) {
            // binding is null when rotating the device...
            binding.overlayPlayQueueButton.setVisibility(
                    isPlayQueueEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateOverlayData(@Nullable final String overlayTitle,
                                   @Nullable final String uploader,
                                   @NonNull final List<Image> thumbnails) {
        binding.overlayTitleTextView.setText(isEmpty(overlayTitle) ? "" : overlayTitle);
        binding.overlayChannelTextView.setText(isEmpty(uploader) ? "" : uploader);
        LocalMediaThumbnailLoader.INSTANCE.clear(binding.overlayThumbnail);
        CoilHelper.INSTANCE.loadDetailsThumbnail(binding.overlayThumbnail, thumbnails);
    }

    private void updateLocalOverlay(@NonNull final PlayQueueItem item) {
        binding.overlayTitleTextView.setText(item.getTitle());
        binding.overlayChannelTextView.setText(firstNonEmpty(item.getUploader(), item.getAlbum(),
                item.getFolder(), getString(R.string.local_media_on_device)));
        if (item.isLiveTv() || item.isRadio()) {
            LocalMediaThumbnailLoader.INSTANCE.clear(binding.overlayThumbnail);
            CoilHelper.INSTANCE.loadThumbnail(binding.overlayThumbnail,
                    item.getLocalThumbnailUrl());
        } else {
            LocalMediaThumbnailLoader.INSTANCE.load(binding.overlayThumbnail, item);
        }
    }

    private void setOverlayPlayPauseImage(final boolean playerIsPlaying) {
        final int drawable = playerIsPlaying
                ? R.drawable.ic_pause
                : R.drawable.ic_play_arrow;
        binding.overlayPlayPauseButton.setImageResource(drawable);
    }

    private void setOverlayLook(final AppBarLayout appBar,
                                final AppBarLayout.Behavior behavior,
                                final float slideOffset) {
        if (miniPlayerCorners != null) {
            miniPlayerCorners.setExpansionFraction(isFullscreen() ? 1f : slideOffset);
        }
        // SlideOffset < 0 when mini player is about to close via swipe.
        // Stop animation in this case
        if (behavior == null || slideOffset < 0) {
            return;
        }
        binding.overlayLayout.setAlpha(slideOffset == 0 ? 1f
                : Math.min(MAX_OVERLAY_ALPHA, 1 - slideOffset));
        // These numbers are not special. They just do a cool transition
        behavior.setTopAndBottomOffset(
                (int) (-binding.detailThumbnailImageView.getHeight() * 2 * (1 - slideOffset) / 3));
        appBar.requestLayout();
    }

    private void updateTvMiniPlayerFocus() {
        org.schabi.newpipe.player.ui.TvMiniPlayerFocus.apply(binding.getRoot(),
                binding.detailMainContent, binding.overlayLayout, bottomSheetState);
    }

    private boolean isRadioQueue() {
        final PlayQueueItem item = playQueue == null ? null : playQueue.getItem();
        return item != null && item.isRadio();
    }

    private void setOverlayExpansionClickable(final boolean enable) {
        final boolean canExpand = enable && !isRadioQueue();
        binding.overlayThumbnail.setClickable(canExpand);
        binding.overlayThumbnail.setLongClickable(canExpand);
        binding.overlayMetadataLayout.setClickable(canExpand);
        binding.overlayMetadataLayout.setLongClickable(canExpand);
        binding.overlayButtonsLayout.setClickable(canExpand);
    }

    private void setOverlayElementsClickable(final boolean enable) {
        setOverlayExpansionClickable(enable);
        binding.overlayPlayQueueButton.setClickable(enable);
        binding.overlayPlayPauseButton.setClickable(enable);
        binding.overlayCloseButton.setClickable(enable);
    }

    // helpers to check the state of player and playerService
    boolean isPlayerAvailable() {
        return player != null;
    }

    boolean isPlayerServiceAvailable() {
        return playerService != null;
    }

    boolean isPlayerAndPlayerServiceAvailable() {
        return player != null && playerService != null;
    }

    public Optional<View> getRoot() {
        return Optional.ofNullable(player)
                .flatMap(player1 -> player1.UIs().get(VideoPlayerUi.class))
                .map(playerUi -> playerUi.getBinding().getRoot());
    }

    private void updateBottomSheetState(final int newState) {
        bottomSheetState = newState;
        if (newState != BottomSheetBehavior.STATE_DRAGGING
                && newState != BottomSheetBehavior.STATE_SETTLING) {
            lastStableBottomSheetState = newState;
        }
    }
}
