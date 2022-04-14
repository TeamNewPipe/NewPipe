package org.schabi.newpipe.player;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NO_PERMISSION;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_TIMEOUT;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_UNSPECIFIED;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SKIP;
import static com.google.android.exoplayer2.Player.DiscontinuityReason;
import static com.google.android.exoplayer2.Player.Listener;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.Player.RepeatMode;
import static org.schabi.newpipe.QueueItemMenuUtil.openPopupMenu;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.MainPlayer.ACTION_CLOSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.MainPlayer.ACTION_RECREATE_NOTIFICATION;
import static org.schabi.newpipe.player.MainPlayer.ACTION_REPEAT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_SHUFFLE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.player.helper.PlayerHelper.buildCloseOverlayLayoutParams;
import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimizeOnExitAction;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimumVideoHeight;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.helper.PlayerHelper.isPlaybackResumeEnabled;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextRepeatMode;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextResizeModeAndSaveToPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlaybackParametersFromPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlayerTypeFromIntent;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePopupLayoutParamsFromPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrieveSeekDurationFromPreferences;
import static org.schabi.newpipe.player.helper.PlayerHelper.savePlaybackParametersToPrefs;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnticipateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.collection.ArraySet;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.databinding.PlayerPopupCloseOverlayBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainPlayer.PlayerType;
import org.schabi.newpipe.player.event.DisplayPortion;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.MediaSessionManager;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.listeners.view.PlaybackSpeedClickListener;
import org.schabi.newpipe.player.listeners.view.QualityClickListener;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playback.PlayerMediaSession;
import org.schabi.newpipe.player.playback.SurfaceHolderCallback;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.ExpandableSurfaceView;
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;

public final class Player implements
        PlaybackListener,
        Listener,
        SeekBar.OnSeekBarChangeListener,
        View.OnClickListener,
        PopupMenu.OnMenuItemClickListener,
        PopupMenu.OnDismissListener,
        View.OnLongClickListener {
    public static final boolean DEBUG = MainActivity.DEBUG;
    public static final String TAG = Player.class.getSimpleName();

    /*//////////////////////////////////////////////////////////////////////////
    // States
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_PREFLIGHT = -1;
    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String REPEAT_MODE = "repeat_mode";
    public static final String PLAYBACK_QUALITY = "playback_quality";
    public static final String PLAY_QUEUE_KEY = "play_queue_key";
    public static final String ENQUEUE = "enqueue";
    public static final String ENQUEUE_NEXT = "enqueue_next";
    public static final String RESUME_PLAYBACK = "resume_playback";
    public static final String PLAY_WHEN_READY = "play_when_ready";
    public static final String PLAYER_TYPE = "player_type";
    public static final String IS_MUTED = "is_muted";

    /*//////////////////////////////////////////////////////////////////////////
    // Time constants
    //////////////////////////////////////////////////////////////////////////*/

    public static final int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    public static final int PROGRESS_LOOP_INTERVAL_MILLIS = 1000; // 1 second
    public static final int DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final int DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds
    public static final int SEEK_OVERLAY_DURATION = 450; // 450 millis

    /*//////////////////////////////////////////////////////////////////////////
    // Other constants
    //////////////////////////////////////////////////////////////////////////*/

    private static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private static final int RENDERER_UNAVAILABLE = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    // play queue might be null e.g. while player is starting
    @Nullable private PlayQueue playQueue;
    private PlayQueueAdapter playQueueAdapter;
    private StreamSegmentAdapter segmentAdapter;

    @Nullable private MediaSourceManager playQueueManager;

    @Nullable private PlayQueueItem currentItem;
    @Nullable private MediaItemTag currentMetadata;
    @Nullable private Bitmap currentThumbnail;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    private ExoPlayer simpleExoPlayer;
    private AudioReactor audioReactor;
    private MediaSessionManager mediaSessionManager;
    @Nullable private SurfaceHolderCallback surfaceHolderCallback;

    @NonNull private final DefaultTrackSelector trackSelector;
    @NonNull private final LoadController loadController;
    @NonNull private final RenderersFactory renderFactory;

    @NonNull private final VideoPlaybackResolver videoResolver;
    @NonNull private final AudioPlaybackResolver audioResolver;

    private final MainPlayer service; //TODO try to remove and replace everything with context

    /*//////////////////////////////////////////////////////////////////////////
    // Player states
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerType playerType = PlayerType.VIDEO;
    private int currentState = STATE_PREFLIGHT;

    // audio only mode does not mean that player type is background, but that the player was
    // minimized to background but will resume automatically to the original player type
    private boolean isAudioOnly = false;
    private boolean isPrepared = false;
    private boolean wasPlaying = false;
    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    private boolean fragmentIsVisible = false;

    private List<VideoStream> availableStreams;
    private int selectedStreamIndex;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerBinding binding;

    private final Handler controlsVisibilityHandler = new Handler();

    // fullscreen player
    private boolean isQueueVisible = false;
    private boolean areSegmentsVisible = false;
    private ItemTouchHelper itemTouchHelper;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/

    private static final int POPUP_MENU_ID_QUALITY = 69;
    private static final int POPUP_MENU_ID_PLAYBACK_SPEED = 79;
    private static final int POPUP_MENU_ID_CAPTION = 89;

    private boolean isSomePopupMenuVisible = false;
    private PopupMenu qualityPopupMenu;
    private PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerPopupCloseOverlayBinding closeOverlayBinding;

    private boolean isPopupClosing = false;

    private float screenWidth;
    private float screenHeight;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player window manager
    //////////////////////////////////////////////////////////////////////////*/

    public static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    public static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    @Nullable private WindowManager.LayoutParams popupLayoutParams; // null if player is not popup
    @Nullable private final WindowManager windowManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/

    private static final float MAX_GESTURE_LENGTH = 0.75f;

    private int maxGestureLength; // scaled
    private GestureDetectorCompat gestureDetector;
    private PlayerGestureListener playerGestureListener;

    /*//////////////////////////////////////////////////////////////////////////
    // Listeners and disposables
    //////////////////////////////////////////////////////////////////////////*/

    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;
    private ContentObserver settingsContentObserver;

    @NonNull private final SerialDisposable progressUpdateDisposable = new SerialDisposable();
    @NonNull private final CompositeDisposable databaseUpdateDisposable = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull private final Context context;
    @NonNull private final SharedPreferences prefs;
    @NonNull private final HistoryRecordManager recordManager;

    @NonNull private final SeekbarPreviewThumbnailHolder seekbarPreviewThumbnailHolder =
            new SeekbarPreviewThumbnailHolder();


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor

    public Player(@NonNull final MainPlayer service) {
        this.service = service;
        context = service;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        recordManager = new HistoryRecordManager(context);

        setupBroadcastReceiver();

        trackSelector = new DefaultTrackSelector(context, PlayerHelper.getQualitySelector());
        final PlayerDataSource dataSource = new PlayerDataSource(context, DownloaderImpl.USER_AGENT,
                new DefaultBandwidthMeter.Builder(context).build());
        loadController = new LoadController();
        renderFactory = new DefaultRenderersFactory(context);

        videoResolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
        audioResolver = new AudioPlaybackResolver(context, dataSource);

        windowManager = ContextCompat.getSystemService(context, WindowManager.class);
    }

    private VideoPlaybackResolver.QualityResolver getQualityResolver() {
        return new VideoPlaybackResolver.QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
                return videoPlayerSelected()
                        ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                  final String playbackQuality) {
                return videoPlayerSelected()
                        ? getResolutionIndex(context, sortedVideos, playbackQuality)
                        : getPopupResolutionIndex(context, sortedVideos, playbackQuality);
            }
        };
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Setup and initialization
    //////////////////////////////////////////////////////////////////////////*/
    //region Setup and initialization

    public void setupFromView(@NonNull final PlayerBinding playerBinding) {
        initViews(playerBinding);
        if (exoPlayerIsNull()) {
            initPlayer(true);
        }
        initListeners();

        setupPlayerSeekOverlay();
    }

    private void initViews(@NonNull final PlayerBinding playerBinding) {
        binding = playerBinding;
        setupSubtitleView();

        binding.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));

        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));
        binding.playbackSeekBar.getProgressDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY));

        final ContextThemeWrapper themeWrapper = new ContextThemeWrapper(getContext(),
                R.style.DarkPopupMenu);

        qualityPopupMenu = new PopupMenu(themeWrapper, binding.qualityTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, binding.playbackSpeed);
        captionPopupMenu = new PopupMenu(themeWrapper, binding.captionTextView);

        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

        binding.titleTextView.setSelected(true);
        binding.channelTextView.setSelected(true);

        // Prevent hiding of bottom sheet via swipe inside queue
        binding.itemsList.setNestedScrollingEnabled(false);
    }

    private void initPlayer(final boolean playOnReady) {
        if (DEBUG) {
            Log.d(TAG, "initPlayer() called with: playOnReady = [" + playOnReady + "]");
        }

        simpleExoPlayer = new ExoPlayer.Builder(context, renderFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadController)
                .build();
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(playOnReady);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
        simpleExoPlayer.setHandleAudioBecomingNoisy(true);

        audioReactor = new AudioReactor(context, simpleExoPlayer);
        mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                new PlayerMediaSession(this));

        registerBroadcastReceiver();

        // Setup video view
        setupVideoSurface();

        // enable media tunneling
        if (DEBUG && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.disable_media_tunneling_key), false)) {
            Log.d(TAG, "[" + Util.DEVICE_DEBUG_INFO + "] "
                    + "media tunneling disabled in debug preferences");
        } else if (DeviceUtils.shouldSupportMediaTunneling()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true));
        } else if (DEBUG) {
            Log.d(TAG, "[" + Util.DEVICE_DEBUG_INFO + "] does not support media tunneling");
        }
    }

    private void initListeners() {
        binding.qualityTextView.setOnClickListener(
                new QualityClickListener(this, qualityPopupMenu));
        binding.playbackSpeed.setOnClickListener(
                new PlaybackSpeedClickListener(this, playbackSpeedPopupMenu));

        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.captionTextView.setOnClickListener(this);
        binding.resizeTextView.setOnClickListener(this);
        binding.playbackLiveSync.setOnClickListener(this);

        playerGestureListener = new PlayerGestureListener(this, service);
        gestureDetector = new GestureDetectorCompat(context, playerGestureListener);
        binding.getRoot().setOnTouchListener(playerGestureListener);

        binding.queueButton.setOnClickListener(v -> onQueueClicked());
        binding.segmentsButton.setOnClickListener(v -> onSegmentsClicked());
        binding.repeatButton.setOnClickListener(v -> onRepeatClicked());
        binding.shuffleButton.setOnClickListener(v -> onShuffleClicked());
        binding.addToPlaylistButton.setOnClickListener(v -> {
            if (getParentActivity() != null) {
                onAddToPlaylistClicked(getParentActivity().getSupportFragmentManager());
            }
        });

        binding.playPauseButton.setOnClickListener(this);
        binding.playPreviousButton.setOnClickListener(this);
        binding.playNextButton.setOnClickListener(this);

        binding.moreOptionsButton.setOnClickListener(this);
        binding.moreOptionsButton.setOnLongClickListener(this);
        binding.share.setOnClickListener(this);
        binding.share.setOnLongClickListener(this);
        binding.fullScreenButton.setOnClickListener(this);
        binding.screenRotationButton.setOnClickListener(this);
        binding.playWithKodi.setOnClickListener(this);
        binding.openInBrowser.setOnClickListener(this);
        binding.playerCloseButton.setOnClickListener(this);
        binding.switchMute.setOnClickListener(this);

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                setupScreenRotationButton();
            }
        };
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
        binding.getRoot().addOnLayoutChangeListener(this::onLayoutChange);

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, (view, windowInsets) -> {
            final Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (!cutout.equals(Insets.NONE)) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            }
            return windowInsets;
        });

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays and fast_seek_overlay too. Without it they will be off-centered.
        binding.playbackControlRoot.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    binding.playerOverlays.setPadding(
                            v.getPaddingLeft(),
                            v.getPaddingTop(),
                            v.getPaddingRight(),
                            v.getPaddingBottom());

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
                });
    }

    /**
     * Initializes the Fast-For/Backward overlay.
     */
    private void setupPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(() -> retrieveSeekDurationFromPreferences(this) / 1000)
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
                        if (exoPlayerIsNull()) {
                            // Abort seeking
                            playerGestureListener.endMultiDoubleTap();
                            return FastSeekDirection.NONE;
                        }
                        if (portion == DisplayPortion.LEFT) {
                            // Check if it's possible to rewind
                            // Small puffer to eliminate infinite rewind seeking
                            if (simpleExoPlayer.getCurrentPosition() < 500L) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.BACKWARD;
                        } else if (portion == DisplayPortion.RIGHT) {
                            // Check if it's possible to fast-forward
                            if (currentState == STATE_COMPLETED
                                    || simpleExoPlayer.getCurrentPosition()
                                    >= simpleExoPlayer.getDuration()) {
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
                            fastForward();
                        } else {
                            fastRewind();
                        }
                    }
                });
        playerGestureListener.doubleTapControls(binding.fastSeekOverlay);
    }

    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback initialization via intent
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback initialization via intent

    @SuppressWarnings("MethodLength")
    public void handleIntent(@NonNull final Intent intent) {
        // fail fast if no play queue was provided
        final String queueCache = intent.getStringExtra(PLAY_QUEUE_KEY);
        if (queueCache == null) {
            return;
        }
        final PlayQueue newQueue = SerializedCache.getInstance().take(queueCache, PlayQueue.class);
        if (newQueue == null) {
            return;
        }

        final PlayerType oldPlayerType = playerType;
        playerType = retrievePlayerTypeFromIntent(intent);
        // We need to setup audioOnly before super(), see "sourceOf"
        isAudioOnly = audioPlayerSelected();

        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
        }

        // Resolve enqueue intents
        if (intent.getBooleanExtra(ENQUEUE, false) && playQueue != null) {
            playQueue.append(newQueue.getStreams());
            return;

        // Resolve enqueue next intents
        } else if (intent.getBooleanExtra(ENQUEUE_NEXT, false) && playQueue != null) {
            final int currentIndex = playQueue.getIndex();
            playQueue.append(newQueue.getStreams());
            playQueue.move(playQueue.size() - 1, currentIndex + 1);
            return;
        }

        // needed for tablets, check the function for a better explanation
        directlyOpenFullscreenIfNeeded();

        final PlaybackParameters savedParameters = retrievePlaybackParametersFromPrefs(this);
        final float playbackSpeed = savedParameters.speed;
        final float playbackPitch = savedParameters.pitch;
        final boolean playbackSkipSilence = getPrefs().getBoolean(getContext().getString(
                R.string.playback_skip_silence_key), getPlaybackSkipSilence());

        final boolean samePlayQueue = playQueue != null && playQueue.equals(newQueue);
        final int repeatMode = intent.getIntExtra(REPEAT_MODE, getRepeatMode());
        final boolean playWhenReady = intent.getBooleanExtra(PLAY_WHEN_READY, true);
        final boolean isMuted = intent.getBooleanExtra(IS_MUTED, isMuted());

        /*
         * TODO As seen in #7427 this does not work:
         * There are 3 situations when playback shouldn't be started from scratch (zero timestamp):
         * 1. User pressed on a timestamp link and the same video should be rewound to the timestamp
         * 2. User changed a player from, for example. main to popup, or from audio to main, etc
         * 3. User chose to resume a video based on a saved timestamp from history of played videos
         * In those cases time will be saved because re-init of the play queue is a not an instant
         *  task and requires network calls
         * */
        // seek to timestamp if stream is already playing
        if (!exoPlayerIsNull()
                && newQueue.size() == 1 && newQueue.getItem() != null
                && playQueue != null && playQueue.size() == 1 && playQueue.getItem() != null
                && newQueue.getItem().getUrl().equals(playQueue.getItem().getUrl())
                && newQueue.getItem().getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if (simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
                simpleExoPlayer.prepare();
            }
            simpleExoPlayer.seekTo(playQueue.getIndex(), newQueue.getItem().getRecoveryPosition());
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (!exoPlayerIsNull()
                && samePlayQueue
                && playQueue != null
                && !playQueue.isDisposed()) {
            // Do not re-init the same PlayQueue. Save time
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if (simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
                simpleExoPlayer.prepare();
            }
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (intent.getBooleanExtra(RESUME_PLAYBACK, false)
                && isPlaybackResumeEnabled(this)
                && !samePlayQueue
                && !newQueue.isEmpty()
                && newQueue.getItem() != null
                && newQueue.getItem().getRecoveryPosition() == PlayQueueItem.RECOVERY_UNSET) {
            databaseUpdateDisposable.add(recordManager.loadStreamState(newQueue.getItem())
                    .observeOn(AndroidSchedulers.mainThread())
                    // Do not place initPlayback() in doFinally() because
                    // it restarts playback after destroy()
                    //.doFinally()
                    .subscribe(
                            state -> {
                                if (!state.isFinished(newQueue.getItem().getDuration())) {
                                    // resume playback only if the stream was not played to the end
                                    newQueue.setRecovery(newQueue.getIndex(),
                                            state.getProgressMillis());
                                }
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            },
                            error -> {
                                if (DEBUG) {
                                    Log.w(TAG, "Failed to start playback", error);
                                }
                                // In case any error we can start playback without history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            },
                            () -> {
                                // Completed but not found in history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            }
                    ));
        } else {
            // Good to go...
            // In a case of equal PlayQueues we can re-init old one but only when it is disposed
            initPlayback(samePlayQueue ? playQueue : newQueue, repeatMode, playbackSpeed,
                    playbackPitch, playbackSkipSilence, playWhenReady, isMuted);
        }

        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            setRecovery();
            reloadPlayQueueManager();
        }

        setupElementsVisibility();
        setupElementsSize();

        if (audioPlayerSelected()) {
            service.removeViewFromParent();
        } else if (popupPlayerSelected()) {
            binding.getRoot().setVisibility(View.VISIBLE);
            initPopup();
            initPopupCloseOverlay();
            binding.playPauseButton.requestFocus();
        } else {
            binding.getRoot().setVisibility(View.VISIBLE);
            initVideoPlayer();
            closeItemsList();
            // Android TV: without it focus will frame the whole player
            binding.playPauseButton.requestFocus();

            // Note: This is for automatically playing (when "Resume playback" is off), see #6179
            if (getPlayWhenReady()) {
                play();
            } else {
                pause();
            }
        }
        NavigationHelper.sendPlayerStartedEvent(context);
    }

    /**
     * Open fullscreen on tablets where the option to have the main player start automatically in
     * fullscreen mode is on. Rotating the device to landscape is already done in {@link
     * VideoDetailFragment#openVideoPlayer(boolean)} when the thumbnail is clicked, and that's
     * enough for phones, but not for tablets since the mini player can be also shown in landscape.
     */
    private void directlyOpenFullscreenIfNeeded() {
        if (fragmentListener != null
                && PlayerHelper.isStartMainPlayerFullscreenEnabled(service)
                && DeviceUtils.isTablet(service)
                && videoPlayerSelected()
                && PlayerHelper.globalScreenOrientationLocked(service)) {
            fragmentListener.onScreenRotationButtonClicked();
        }
    }

    private void initPlayback(@NonNull final PlayQueue queue,
                              @RepeatMode final int repeatMode,
                              final float playbackSpeed,
                              final float playbackPitch,
                              final boolean playbackSkipSilence,
                              final boolean playOnReady,
                              final boolean isMuted) {
        destroyPlayer();
        initPlayer(playOnReady);
        setRepeatMode(repeatMode);
        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(binding.shuffleButton, simpleExoPlayer.getShuffleModeEnabled());
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence);

        playQueue = queue;
        playQueue.init();
        reloadPlayQueueManager();

        if (playQueueAdapter != null) {
            playQueueAdapter.dispose();
        }
        playQueueAdapter = new PlayQueueAdapter(context, playQueue);
        segmentAdapter = new StreamSegmentAdapter(getStreamSegmentListener());

        simpleExoPlayer.setVolume(isMuted ? 0 : 1);
        notifyQueueUpdateToListeners();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Destroy and recovery
    //////////////////////////////////////////////////////////////////////////*/
    //region Destroy and recovery

    private void destroyPlayer() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayer() called");
        }

        cleanupVideoSurface();

        if (!exoPlayerIsNull()) {
            simpleExoPlayer.removeListener(this);
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
        if (playQueue != null) {
            playQueue.dispose();
        }
        if (audioReactor != null) {
            audioReactor.dispose();
        }
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }
        if (mediaSessionManager != null) {
            mediaSessionManager.dispose();
        }

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        destroyPlayer();
        unregisterBroadcastReceiver();

        databaseUpdateDisposable.clear();
        progressUpdateDisposable.set(null);
        PicassoHelper.cancelTag(PicassoHelper.PLAYER_THUMBNAIL_TAG); // cancel thumbnail loading

        if (binding != null) {
            binding.endScreen.setImageBitmap(null);
        }

        context.getContentResolver().unregisterContentObserver(settingsContentObserver);
    }

    public void setRecovery() {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int queuePos = playQueue.getIndex();
        final long windowPos = simpleExoPlayer.getCurrentPosition();
        final long duration = simpleExoPlayer.getDuration();

        // No checks due to https://github.com/TeamNewPipe/NewPipe/pull/7195#issuecomment-962624380
        setRecovery(queuePos, Math.max(0, Math.min(windowPos, duration)));
    }

    private void setRecovery(final int queuePos, final long windowPos) {
        if (playQueue.size() <= queuePos) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        }
        playQueue.setRecovery(queuePos, windowPos);
    }

    private void reloadPlayQueueManager() {
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }

        if (playQueue != null) {
            playQueueManager = new MediaSourceManager(this, playQueue);
        }
    }

    @Override // own playback listener
    public void onPlaybackShutdown() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackShutdown() called");
        }
        // destroys the service, which in turn will destroy the player
        service.stopService();
    }

    public void smoothStopPlayer() {
        // Pausing would make transition from one stream to a new stream not smooth, so only stop
        simpleExoPlayer.stop();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player type specific setup
    //////////////////////////////////////////////////////////////////////////*/
    //region Player type specific setup

    private void initVideoPlayer() {
        // restore last resize mode
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(this));
        binding.getRoot().setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) {
            Log.d(TAG, "initPopup() called");
        }

        // Popup is already added to windowManager
        if (popupHasParent()) {
            return;
        }

        updateScreenSize();

        popupLayoutParams = retrievePopupLayoutParamsFromPrefs(this);
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);

        checkPopupPositionBounds();

        binding.loadingPanel.setMinimumWidth(popupLayoutParams.width);
        binding.loadingPanel.setMinimumHeight(popupLayoutParams.height);

        service.removeViewFromParent();
        Objects.requireNonNull(windowManager).addView(binding.getRoot(), popupLayoutParams);

        // Popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) {
            Log.d(TAG, "initPopupCloseOverlay() called");
        }

        // closeOverlayView is already added to windowManager
        if (closeOverlayBinding != null) {
            return;
        }

        closeOverlayBinding = PlayerPopupCloseOverlayBinding.inflate(LayoutInflater.from(context));

        final WindowManager.LayoutParams closeOverlayLayoutParams = buildCloseOverlayLayoutParams();
        closeOverlayBinding.closeButton.setVisibility(View.GONE);
        Objects.requireNonNull(windowManager).addView(
                closeOverlayBinding.getRoot(), closeOverlayLayoutParams);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Elements visibility and size: popup and main players have different look
    //////////////////////////////////////////////////////////////////////////*/
    //region Elements visibility and size: popup and main players have different look

    /**
     * This method ensures that popup and main players have different look.
     * We use one layout for both players and need to decide what to show and what to hide.
     * Additional measuring should be done inside {@link #setupElementsSize}.
     */
    private void setupElementsVisibility() {
        if (popupPlayerSelected()) {
            binding.fullScreenButton.setVisibility(View.VISIBLE);
            binding.screenRotationButton.setVisibility(View.GONE);
            binding.resizeTextView.setVisibility(View.GONE);
            binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.GONE);
            binding.queueButton.setVisibility(View.GONE);
            binding.segmentsButton.setVisibility(View.GONE);
            binding.moreOptionsButton.setVisibility(View.GONE);
            binding.topControls.setOrientation(LinearLayout.HORIZONTAL);
            binding.primaryControls.getLayoutParams().width
                    = LinearLayout.LayoutParams.WRAP_CONTENT;
            binding.secondaryControls.setAlpha(1.0f);
            binding.secondaryControls.setVisibility(View.VISIBLE);
            binding.secondaryControls.setTranslationY(0);
            binding.share.setVisibility(View.GONE);
            binding.playWithKodi.setVisibility(View.GONE);
            binding.openInBrowser.setVisibility(View.GONE);
            binding.switchMute.setVisibility(View.GONE);
            binding.playerCloseButton.setVisibility(View.GONE);
            binding.topControls.bringToFront();
            binding.topControls.setClickable(false);
            binding.topControls.setFocusable(false);
            binding.bottomControls.bringToFront();
            closeItemsList();
        } else if (videoPlayerSelected()) {
            binding.fullScreenButton.setVisibility(View.GONE);
            setupScreenRotationButton();
            binding.resizeTextView.setVisibility(View.VISIBLE);
            binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
            binding.moreOptionsButton.setVisibility(View.VISIBLE);
            binding.topControls.setOrientation(LinearLayout.VERTICAL);
            binding.primaryControls.getLayoutParams().width
                    = LinearLayout.LayoutParams.MATCH_PARENT;
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
        }
        showHideKodiButton();

        if (isFullscreen) {
            binding.titleTextView.setVisibility(View.VISIBLE);
            binding.channelTextView.setVisibility(View.VISIBLE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
        }
        setMuteButton(binding.switchMute, isMuted());

        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    /**
     * Changes padding, size of elements based on player selected right now.
     * Popup player has small padding in comparison with the main player
     */
    private void setupElementsSize() {
        final Resources res = context.getResources();
        final int buttonsMinWidth;
        final int playerTopPad;
        final int controlsPad;
        final int buttonsPad;

        if (popupPlayerSelected()) {
            buttonsMinWidth = 0;
            playerTopPad = 0;
            controlsPad = res.getDimensionPixelSize(R.dimen.player_popup_controls_padding);
            buttonsPad = res.getDimensionPixelSize(R.dimen.player_popup_buttons_padding);
        } else if (videoPlayerSelected()) {
            buttonsMinWidth = res.getDimensionPixelSize(R.dimen.player_main_buttons_min_width);
            playerTopPad = res.getDimensionPixelSize(R.dimen.player_main_top_padding);
            controlsPad = res.getDimensionPixelSize(R.dimen.player_main_controls_padding);
            buttonsPad = res.getDimensionPixelSize(R.dimen.player_main_buttons_padding);
        } else {
            return;
        }

        binding.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0);
        binding.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0);
        binding.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setMinimumWidth(buttonsMinWidth);
        binding.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
    }

    private void showHideKodiButton() {
        // show kodi button if it supports the current service and it is enabled in settings
        binding.playWithKodi.setVisibility(videoPlayerSelected()
                && playQueue != null && playQueue.getItem() != null
                && KoreUtils.shouldShowPlayWithKodi(context, playQueue.getItem().getServiceId())
                ? View.VISIBLE : View.GONE);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    private void setupBroadcastReceiver() {
        if (DEBUG) {
            Log.d(TAG, "setupBroadcastReceiver() called");
        }

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context ctx, final Intent intent) {
                onBroadcastReceived(intent);
            }
        };
        intentFilter = new IntentFilter();

        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_PLAY_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_NEXT);
        intentFilter.addAction(ACTION_FAST_REWIND);
        intentFilter.addAction(ACTION_FAST_FORWARD);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(ACTION_SHUFFLE);
        intentFilter.addAction(ACTION_RECREATE_NOTIFICATION);

        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
    }

    private void onBroadcastReceived(final Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
        }

        switch (intent.getAction()) {
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                pause();
                break;
            case ACTION_CLOSE:
                service.stopService();
                break;
            case ACTION_PLAY_PAUSE:
                playPause();
                if (!fragmentIsVisible) {
                    // Ensure that we have audio-only stream playing when a user
                    // started to play from notification's play button from outside of the app
                    onFragmentStopped();
                }
                break;
            case ACTION_PLAY_PREVIOUS:
                playPrevious();
                break;
            case ACTION_PLAY_NEXT:
                playNext();
                break;
            case ACTION_FAST_REWIND:
                fastRewind();
                break;
            case ACTION_FAST_FORWARD:
                fastForward();
                break;
            case ACTION_REPEAT:
                onRepeatClicked();
                break;
            case ACTION_SHUFFLE:
                onShuffleClicked();
                break;
            case ACTION_RECREATE_NOTIFICATION:
                NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, true);
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED:
                fragmentIsVisible = true;
                useVideoSource(true);
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED:
                fragmentIsVisible = false;
                onFragmentStopped();
                break;
            case Intent.ACTION_CONFIGURATION_CHANGED:
                assureCorrectAppLanguage(service);
                if (DEBUG) {
                    Log.d(TAG, "onConfigurationChanged() called");
                }
                if (popupPlayerSelected()) {
                    updateScreenSize();
                    changePopupSize(popupLayoutParams.width);
                    checkPopupPositionBounds();
                }
                // Close it because when changing orientation from portrait
                // (in fullscreen mode) the size of queue layout can be larger than the screen size
                closeItemsList();
                // When the orientation changed, the screen height might be smaller.
                // If the end screen thumbnail is not re-scaled,
                // it can be larger than the current screen height
                // and thus enlarging the whole player.
                // This causes the seekbar to be ouf the visible area.
                updateEndScreenThumbnail();
                break;
            case Intent.ACTION_SCREEN_ON:
                // Interrupt playback only when screen turns on
                // and user is watching video in popup player.
                // Same actions for video player will be handled in ACTION_VIDEO_FRAGMENT_RESUMED
                if (popupPlayerSelected() && (isPlaying() || isLoading())) {
                    useVideoSource(true);
                }
                break;
            case Intent.ACTION_SCREEN_OFF:
                // Interrupt playback only when screen turns off with popup player working
                if (popupPlayerSelected() && (isPlaying() || isLoading())) {
                    useVideoSource(false);
                }
                break;
            case Intent.ACTION_HEADSET_PLUG: //FIXME
                /*notificationManager.cancel(NOTIFICATION_ID);
                mediaSessionManager.dispose();
                mediaSessionManager.enable(getBaseContext(), basePlayerImpl.simpleExoPlayer);*/
                break;
        }
    }

    private void registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver();
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (final IllegalArgumentException unregisteredException) {
            Log.w(TAG, "Broadcast receiver already unregistered: "
                    + unregisteredException.getMessage());
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail loading
    //////////////////////////////////////////////////////////////////////////*/
    //region Thumbnail loading

    private void initThumbnail(final String url) {
        if (DEBUG) {
            Log.d(TAG, "Thumbnail - initThumbnail() called with url = ["
                    + (url == null ? "null" : url) + "]");
        }
        if (isNullOrEmpty(url)) {
            return;
        }

        // scale down the notification thumbnail for performance
        PicassoHelper.loadScaledDownThumbnail(context, url).into(new Target() {
            @Override
            public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onLoadingComplete() called with: url = [" + url
                            + "], " + "loadedImage = [" + bitmap + " -> " + bitmap.getWidth() + "x"
                            + bitmap.getHeight() + "], from = [" + from + "]");
                }

                currentThumbnail = bitmap;
                NotificationUtil.getInstance()
                        .createNotificationIfNeededAndUpdate(Player.this, false);
                // there is a new thumbnail, so changed the end screen thumbnail, too.
                updateEndScreenThumbnail();
            }

            @Override
            public void onBitmapFailed(final Exception e, final Drawable errorDrawable) {
                Log.e(TAG, "Thumbnail - onBitmapFailed() called with: url = [" + url + "]", e);
                currentThumbnail = null;
                NotificationUtil.getInstance()
                        .createNotificationIfNeededAndUpdate(Player.this, false);
            }

            @Override
            public void onPrepareLoad(final Drawable placeHolderDrawable) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onLoadingStarted() called with: url = [" + url + "]");
                }
            }
        });
    }

    /**
     * Scale the player audio / end screen thumbnail down if necessary.
     * <p>
     * This is necessary when the thumbnail's height is larger than the device's height
     * and thus is enlarging the player's height
     * causing the bottom playback controls to be out of the visible screen.
     * </p>
     */
    public void updateEndScreenThumbnail() {
        if (currentThumbnail == null) {
            return;
        }

        final float endScreenHeight = calculateMaxEndScreenThumbnailHeight();

        final Bitmap endScreenBitmap = Bitmap.createScaledBitmap(
                currentThumbnail,
                (int) (currentThumbnail.getWidth()
                        / (currentThumbnail.getHeight() / endScreenHeight)),
                (int) endScreenHeight,
                true);

        if (DEBUG) {
            Log.d(TAG, "Thumbnail - updateEndScreenThumbnail() called with: "
                    + "currentThumbnail = [" + currentThumbnail + "], "
                    + currentThumbnail.getWidth() + "x" + currentThumbnail.getHeight()
                    + ", scaled end screen height = " + endScreenHeight
                    + ", scaled end screen width = " + endScreenBitmap.getWidth());
        }

        binding.endScreen.setImageBitmap(endScreenBitmap);
    }

    /**
     * Calculate the maximum allowed height for the {@link R.id.endScreen}
     * to prevent it from enlarging the player.
     * <p>
     * The calculating follows these rules:
     * <ul>
     * <li>
     *     Show at least stream title and content creator on TVs and tablets
     *     when in landscape (always the case for TVs) and not in fullscreen mode.
     *     This requires to have at least <code>85dp</code> free space for {@link R.id.detail_root}
     *     and additional space for the stream title text size
     *     ({@link R.id.detail_title_root_layout}).
     *     The text size is <code>15sp</code> on tablets and <code>16sp</code> on TVs,
     *     see {@link R.id.titleTextView}.
     * </li>
     * <li>
     *     Otherwise, the max thumbnail height is the screen height.
     * </li>
     * </ul>
     *
     * @return the maximum height for the end screen thumbnail
     */
    private float calculateMaxEndScreenThumbnailHeight() {
        // ensure that screenHeight is initialized and thus not 0
        updateScreenSize();

        if (DeviceUtils.isTv(context) && !isFullscreen) {
            final int videoInfoHeight =
                    DeviceUtils.dpToPx(85, context) + DeviceUtils.spToPx(16, context);
            return Math.min(currentThumbnail.getHeight(), screenHeight - videoInfoHeight);
        } else if (DeviceUtils.isTablet(context) && service.isLandscape() && !isFullscreen) {
            final int videoInfoHeight =
                    DeviceUtils.dpToPx(85, context) + DeviceUtils.spToPx(15, context);
            return Math.min(currentThumbnail.getHeight(), screenHeight - videoInfoHeight);
        } else { // fullscreen player: max height is the device height
            return Math.min(currentThumbnail.getHeight(), screenHeight);
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Popup player utils
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup player utils

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary
     * that goes from (0, 0) to (screenWidth, screenHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed
     * and {@code true} is returned to represent this change.
     * </p>
     */
    public void checkPopupPositionBounds() {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: "
                    + "screenWidth = [" + screenWidth + "], "
                    + "screenHeight = [" + screenHeight + "]");
        }
        if (popupLayoutParams == null) {
            return;
        }

        if (popupLayoutParams.x < 0) {
            popupLayoutParams.x = 0;
        } else if (popupLayoutParams.x > screenWidth - popupLayoutParams.width) {
            popupLayoutParams.x = (int) (screenWidth - popupLayoutParams.width);
        }

        if (popupLayoutParams.y < 0) {
            popupLayoutParams.y = 0;
        } else if (popupLayoutParams.y > screenHeight - popupLayoutParams.height) {
            popupLayoutParams.y = (int) (screenHeight - popupLayoutParams.height);
        }
    }

    public void updateScreenSize() {
        if (windowManager != null) {
            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);

            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            if (DEBUG) {
                Log.d(TAG, "updateScreenSize() called: screenWidth = ["
                        + screenWidth + "], screenHeight = [" + screenHeight + "]");
            }
        }
    }

    /**
     * Changes the size of the popup based on the width.
     * @param width the new width, height is calculated with
     *              {@link PlayerHelper#getMinimumVideoHeight(float)}
     */
    public void changePopupSize(final int width) {
        if (DEBUG) {
            Log.d(TAG, "changePopupSize() called with: width = [" + width + "]");
        }

        if (anyPopupViewIsNull()) {
            return;
        }

        final float minimumWidth = context.getResources().getDimension(R.dimen.popup_minimum_width);
        final int actualWidth = (int) (width > screenWidth ? screenWidth
                : (width < minimumWidth ? minimumWidth : width));
        final int actualHeight = (int) getMinimumVideoHeight(width);
        if (DEBUG) {
            Log.d(TAG, "updatePopupSize() updated values:"
                    + "  width = [" + actualWidth + "], height = [" + actualHeight + "]");
        }

        popupLayoutParams.width = actualWidth;
        popupLayoutParams.height = actualHeight;
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);
        Objects.requireNonNull(windowManager)
                .updateViewLayout(binding.getRoot(), popupLayoutParams);
    }

    private void changePopupWindowFlags(final int flags) {
        if (DEBUG) {
            Log.d(TAG, "changePopupWindowFlags() called with: flags = [" + flags + "]");
        }

        if (!anyPopupViewIsNull()) {
            popupLayoutParams.flags = flags;
            Objects.requireNonNull(windowManager)
                    .updateViewLayout(binding.getRoot(), popupLayoutParams);
        }
    }

    public void closePopup() {
        if (DEBUG) {
            Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        }
        if (isPopupClosing) {
            return;
        }
        isPopupClosing = true;

        saveStreamProgressState();
        Objects.requireNonNull(windowManager).removeView(binding.getRoot());

        animatePopupOverlayAndFinishService();
    }

    public void removePopupFromView() {
        if (windowManager != null) {
            // wrap in try-catch since it could sometimes generate errors randomly
            try {
                if (popupHasParent()) {
                    windowManager.removeView(binding.getRoot());
                }
            } catch (final IllegalArgumentException e) {
                Log.w(TAG, "Failed to remove popup from window manager", e);
            }

            try {
                final boolean closeOverlayHasParent = closeOverlayBinding != null
                        && closeOverlayBinding.getRoot().getParent() != null;
                if (closeOverlayHasParent) {
                    windowManager.removeView(closeOverlayBinding.getRoot());
                }
            } catch (final IllegalArgumentException e) {
                Log.w(TAG, "Failed to remove popup overlay from window manager", e);
            }
        }
    }

    private void animatePopupOverlayAndFinishService() {
        final int targetTranslationY =
                (int) (closeOverlayBinding.closeButton.getRootView().getHeight()
                        - closeOverlayBinding.closeButton.getY());

        closeOverlayBinding.closeButton.animate().setListener(null).cancel();
        closeOverlayBinding.closeButton.animate()
                .setInterpolator(new AnticipateInterpolator())
                .translationY(targetTranslationY)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(final Animator animation) {
                        end();
                    }

                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        end();
                    }

                    private void end() {
                        Objects.requireNonNull(windowManager)
                                .removeView(closeOverlayBinding.getRoot());
                        closeOverlayBinding = null;
                        service.stopService();
                    }
                }).start();
    }

    private boolean popupHasParent() {
        return binding != null
                && binding.getRoot().getLayoutParams() instanceof WindowManager.LayoutParams
                && binding.getRoot().getParent() != null;
    }

    private boolean anyPopupViewIsNull() {
        // TODO understand why checking getParentActivity() != null
        return popupLayoutParams == null || windowManager == null
                || getParentActivity() != null || binding.getRoot().getParent() == null;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback parameters
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback parameters

    public float getPlaybackSpeed() {
        return getPlaybackParameters().speed;
    }

    private void setPlaybackSpeed(final float speed) {
        setPlaybackParameters(speed, getPlaybackPitch(), getPlaybackSkipSilence());
    }

    public float getPlaybackPitch() {
        return getPlaybackParameters().pitch;
    }

    public boolean getPlaybackSkipSilence() {
        return !exoPlayerIsNull() && simpleExoPlayer.getSkipSilenceEnabled();
    }

    public PlaybackParameters getPlaybackParameters() {
        if (exoPlayerIsNull()) {
            return PlaybackParameters.DEFAULT;
        }
        return simpleExoPlayer.getPlaybackParameters();
    }

    /**
     * Sets the playback parameters of the player, and also saves them to shared preferences.
     * Speed and pitch are rounded up to 2 decimal places before being used or saved.
     *
     * @param speed       the playback speed, will be rounded to up to 2 decimal places
     * @param pitch       the playback pitch, will be rounded to up to 2 decimal places
     * @param skipSilence skip silence during playback
     */
    public void setPlaybackParameters(final float speed, final float pitch,
                                      final boolean skipSilence) {
        final float roundedSpeed = Math.round(speed * 100.0f) / 100.0f;
        final float roundedPitch = Math.round(pitch * 100.0f) / 100.0f;

        savePlaybackParametersToPrefs(this, roundedSpeed, roundedPitch, skipSilence);
        simpleExoPlayer.setPlaybackParameters(
                new PlaybackParameters(roundedSpeed, roundedPitch));
        simpleExoPlayer.setSkipSilenceEnabled(skipSilence);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    private void onUpdateProgress(final int currentProgress,
                                  final int duration,
                                  final int bufferPercent) {
        if (!isPrepared) {
            return;
        }

        if (duration != binding.playbackSeekBar.getMax()) {
            setVideoDurationToControls(duration);
        }
        if (currentState != STATE_PAUSED) {
            updatePlayBackElementsCurrentDuration(currentProgress);
        }
        if (simpleExoPlayer.isLoading() || bufferPercent > 90) {
            binding.playbackSeekBar.setSecondaryProgress(
                    (int) (binding.playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!isLiveEdge());

        notifyProgressUpdateToListeners(currentProgress, duration, bufferPercent);

        if (areSegmentsVisible) {
            segmentAdapter.selectSegmentAt(getNearestStreamSegmentPosition(currentProgress));
        }

        if (isQueueVisible) {
            updateQueueTime(currentProgress);
        }
    }

    private void startProgressLoop() {
        progressUpdateDisposable.set(getProgressUpdateDisposable());
    }

    private void stopProgressLoop() {
        progressUpdateDisposable.set(null);
    }

    private boolean isProgressLoopRunning() {
        return progressUpdateDisposable.get() != null;
    }

    private void triggerProgressUpdate() {
        if (exoPlayerIsNull()) {
            return;
        }
        // Use duration of currentItem for non-live streams,
        // because HLS streams are fragmented
        // and thus the whole duration is not available to the player
        // TODO: revert #6307 when introducing proper HLS support
        final int duration;
        if (currentItem != null
                && !StreamTypeUtil.isLiveStream(currentItem.getStreamType())
        ) {
            // convert seconds to milliseconds
            duration = (int) (currentItem.getDuration() * 1000);
        } else {
            duration = (int) simpleExoPlayer.getDuration();
        }
        onUpdateProgress(
                Math.max((int) simpleExoPlayer.getCurrentPosition(), 0),
                duration,
                simpleExoPlayer.getBufferedPercentage()
        );
    }

    private Disposable getProgressUpdateDisposable() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
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

        // Seekbar Preview Thumbnail
        SeekbarPreviewThumbnailHelper
                .tryResizeAndSetSeekbarPreviewThumbnail(
                        getContext(),
                        seekbarPreviewThumbnailHolder.getBitmapAt(progress),
                        binding.currentSeekbarPreviewThumbnail,
                        binding.subtitleView::getWidth);

        adjustSeekbarPreviewContainer();
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
            final int checkedContainerLeft =
                    Math.max(
                            Math.min(
                                    uncheckedContainerLeft,
                                    // Max left
                                    binding.playbackWindowRoot.getWidth()
                                            - binding.seekbarPreviewContainer.getWidth()
                            ),
                            0 // Min left
                    );

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
        if (currentState != STATE_PAUSED_SEEK) {
            changeState(STATE_PAUSED_SEEK);
        }

        saveWasPlaying();
        if (isPlaying()) {
            simpleExoPlayer.pause();
        }

        showControls(0);
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

        seekTo(seekBar.getProgress());
        if (wasPlaying || simpleExoPlayer.getDuration() == seekBar.getProgress()) {
            simpleExoPlayer.play();
        }

        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, false, 200, AnimationType.SCALE_AND_ALPHA);

        if (currentState == STATE_PAUSED_SEEK) {
            changeState(STATE_BUFFERING);
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }
        if (wasPlaying) {
            showControlsThenHide();
        }
    }

    public void saveWasPlaying() {
        this.wasPlaying = getPlayWhenReady();
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
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }
        showOrHideButtons();
        showSystemUIPartially();

        final int hideTime = binding.playbackControlRoot.isInTouchMode()
                ? DEFAULT_CONTROLS_HIDE_TIME
                : DPAD_CONTROLS_HIDE_TIME;

        showHideShadow(true, DEFAULT_CONTROLS_DURATION);
        animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.ALPHA, 0, () -> hideControls(DEFAULT_CONTROLS_DURATION, hideTime));
    }

    public void showControls(final long duration) {
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        showOrHideButtons();
        showSystemUIPartially();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration);
        animate(binding.playbackControlRoot, true, duration);
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]");
        }

        showOrHideButtons();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
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

    private void showOrHideButtons() {
        if (playQueue == null) {
            return;
        }

        final boolean showPrev = playQueue.getIndex() != 0;
        final boolean showNext = playQueue.getIndex() + 1 != playQueue.getStreams().size();
        final boolean showQueue = playQueue.getStreams().size() > 1 && !popupPlayerSelected();
        /* only when stream has segments and is not playing in popup player */
        final boolean showSegment = !popupPlayerSelected()
                && !getCurrentStreamInfo()
                .map(StreamInfo::getStreamSegments)
                .map(List::isEmpty)
                .orElse(/*no stream info=*/true);

        binding.playPreviousButton.setVisibility(showPrev ? View.VISIBLE : View.INVISIBLE);
        binding.playPreviousButton.setAlpha(showPrev ? 1.0f : 0.0f);
        binding.playNextButton.setVisibility(showNext ? View.VISIBLE : View.INVISIBLE);
        binding.playNextButton.setAlpha(showNext ? 1.0f : 0.0f);
        binding.queueButton.setVisibility(showQueue ? View.VISIBLE : View.GONE);
        binding.queueButton.setAlpha(showQueue ? 1.0f : 0.0f);
        binding.segmentsButton.setVisibility(showSegment ? View.VISIBLE : View.GONE);
        binding.segmentsButton.setAlpha(showSegment ? 1.0f : 0.0f);
    }

    private void showSystemUIPartially() {
        final AppCompatActivity activity = getParentActivity();
        if (isFullscreen && activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
                activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
            final int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            activity.getWindow().getDecorView().setSystemUiVisibility(visibility);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void hideSystemUIIfNeeded() {
        if (fragmentListener != null) {
            fragmentListener.hideSystemUiIfNeeded();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states
    @Override
    public void onPlayWhenReadyChanged(final boolean playWhenReady, final int reason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlayWhenReadyChanged() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "reason = [" + reason + "]");
        }
        final int playbackState = exoPlayerIsNull()
                ? com.google.android.exoplayer2.Player.STATE_IDLE
                : simpleExoPlayer.getPlaybackState();
        updatePlaybackState(playWhenReady, playbackState);
    }

    @Override
    public void onPlaybackStateChanged(final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlaybackStateChanged() called with: "
                    + "playbackState = [" + playbackState + "]");
        }
        updatePlaybackState(getPlayWhenReady(), playbackState);
    }

    private void updatePlaybackState(final boolean playWhenReady, final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "playbackState = [" + playbackState + "]");
        }

        if (currentState == STATE_PAUSED_SEEK) {
            if (DEBUG) {
                Log.d(TAG, "updatePlaybackState() is currently blocked");
            }
            return;
        }

        switch (playbackState) {
            case com.google.android.exoplayer2.Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case com.google.android.exoplayer2.Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case com.google.android.exoplayer2.Player.STATE_READY: //3
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case com.google.android.exoplayer2.Player.STATE_ENDED: // 4
                changeState(STATE_COMPLETED);
                saveStreamProgressStateCompleted();
                isPrepared = false;
                break;
        }
    }

    @Override // exoplayer listener
    public void onIsLoadingChanged(final boolean isLoading) {
        if (!isLoading && currentState == STATE_PAUSED && isProgressLoopRunning()) {
            stopProgressLoop();
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    @Override // own playback listener
    public void onPlaybackBlock() {
        if (exoPlayerIsNull()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackBlock() called");
        }

        currentItem = null;
        currentMetadata = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override // own playback listener
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackUnblock() called");
        }

        if (exoPlayerIsNull()) {
            return;
        }
        if (currentState == STATE_BLOCKED) {
            changeState(STATE_BUFFERING);
        }
        simpleExoPlayer.setMediaSource(mediaSource, false);
        simpleExoPlayer.prepare();
    }

    public void changeState(final int state) {
        if (DEBUG) {
            Log.d(TAG, "changeState() called with: state = [" + state + "]");
        }
        currentState = state;
        switch (state) {
            case STATE_BLOCKED:
                onBlocked();
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
        notifyPlaybackUpdateToListeners();
    }

    private void onPrepared(final boolean playWhenReady) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        }

        setVideoDurationToControls((int) simpleExoPlayer.getDuration());

        binding.playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));

        if (playWhenReady) {
            audioReactor.requestAudioFocus();
        }
    }

    private void onBlocked() {
        if (DEBUG) {
            Log.d(TAG, "onBlocked() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0);

        binding.playbackSeekBar.setEnabled(false);
        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animate(binding.loadingPanel, true, 0);
        animate(binding.surfaceForeground, true, 100);

        binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow);
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(false);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onPlaying() {
        if (DEBUG) {
            Log.d(TAG, "onPlaying() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        updateStreamRelatedViews();

        binding.playbackSeekBar.setEnabled(true);
        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause);
                    animatePlayButtons(true, 200);
                    if (!isQueueVisible) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        changePopupWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);
        checkLandscape();
        binding.getRoot().setKeepScreenOn(true);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called");
        }
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        binding.loadingPanel.setVisibility(View.VISIBLE);

        binding.getRoot().setKeepScreenOn(true);

        if (NotificationUtil.getInstance().shouldUpdateBufferingSlot()) {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }
    }

    private void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }

        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        // Don't let UI elements popup during double tap seeking. This state is entered sometimes
        // during seeking/loading. This if-else check ensures that the controls aren't popping up.
        if (!playerGestureListener.isDoubleTapping()) {
            showControls(400);
            binding.loadingPanel.setVisibility(View.GONE);

            animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                    () -> {
                        binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow);
                        animatePlayButtons(true, 200);
                        if (!isQueueVisible) {
                            binding.playPauseButton.requestFocus();
                        }
                    });
        }
        changePopupWindowFlags(IDLE_WINDOW_FLAGS);

        // Remove running notification when user does not want minimization to background or popup
        if (PlayerHelper.getMinimizeOnExitAction(context) == MINIMIZE_ON_EXIT_MODE_NONE
                && videoPlayerSelected()) {
            NotificationUtil.getInstance().cancelNotificationAndStopForeground(service);
        } else {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    private void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }

        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(true);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onCompleted() {
        if (DEBUG) {
            Log.d(TAG, "onCompleted() called" + (playQueue == null ? ". playQueue is null" : ""));
        }
        if (playQueue == null) {
            return;
        }

        animate(binding.playPauseButton, false, 0, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    binding.playPauseButton.setImageResource(R.drawable.ic_replay);
                    animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                });

        binding.getRoot().setKeepScreenOn(false);
        changePopupWindowFlags(IDLE_WINDOW_FLAGS);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        if (isFullscreen) {
            toggleFullscreen();
        }

        if (playQueue.getIndex() < playQueue.size() - 1) {
            playQueue.offsetIndex(+1);
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        // When a (short) video ends the elements have to display the correct values - see #6180
        updatePlayBackElementsCurrentDuration(binding.playbackSeekBar.getMax());

        showControls(500);
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        binding.loadingPanel.setVisibility(View.GONE);
        animate(binding.surfaceForeground, true, 100);
    }

    private void animatePlayButtons(final boolean show, final int duration) {
        animate(binding.playPauseButton, show, duration, AnimationType.SCALE_AND_ALPHA);

        boolean showQueueButtons = show;
        if (playQueue == null) {
            showQueueButtons = false;
        }

        if (!showQueueButtons || playQueue.getIndex() > 0) {
            animate(
                    binding.playPreviousButton,
                    showQueueButtons,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
        if (!showQueueButtons || playQueue.getIndex() + 1 < playQueue.getStreams().size()) {
            animate(
                    binding.playNextButton,
                    showQueueButtons,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    //////////////////////////////////////////////////////////////////////////*/
    //region Repeat and shuffle

    public void onRepeatClicked() {
        if (DEBUG) {
            Log.d(TAG, "onRepeatClicked() called");
        }
        setRepeatMode(nextRepeatMode(getRepeatMode()));
    }

    public void onShuffleClicked() {
        if (DEBUG) {
            Log.d(TAG, "onShuffleClicked() called");
        }

        if (exoPlayerIsNull()) {
            return;
        }
        simpleExoPlayer.setShuffleModeEnabled(!simpleExoPlayer.getShuffleModeEnabled());
    }

    @RepeatMode
    public int getRepeatMode() {
        return exoPlayerIsNull() ? REPEAT_MODE_OFF : simpleExoPlayer.getRepeatMode();
    }

    private void setRepeatMode(@RepeatMode final int repeatMode) {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.setRepeatMode(repeatMode);
        }
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: "
                    + "repeatMode = [" + repeatMode + "]");
        }
        setRepeatModeButton(binding.repeatButton, repeatMode);
        onShuffleOrRepeatModeChanged();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: "
                    + "mode = [" + shuffleModeEnabled + "]");
        }

        if (playQueue != null) {
            if (shuffleModeEnabled) {
                playQueue.shuffle();
            } else {
                playQueue.unshuffle();
            }
        }

        setShuffleButton(binding.shuffleButton, shuffleModeEnabled);
        onShuffleOrRepeatModeChanged();
    }

    private void onShuffleOrRepeatModeChanged() {
        notifyPlaybackUpdateToListeners();
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void setRepeatModeButton(final AppCompatImageButton imageButton,
                                     @RepeatMode final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void setShuffleButton(@NonNull final ImageButton button, final boolean shuffled) {
        button.setImageAlpha(shuffled ? 255 : 77);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playlist append
    //////////////////////////////////////////////////////////////////////////*/
    //region Playlist append

    public void onAddToPlaylistClicked(@NonNull final FragmentManager fragmentManager) {
        if (DEBUG) {
            Log.d(TAG, "onAddToPlaylistClicked() called");
        }

        if (getPlayQueue() != null) {
            PlaylistDialog.createCorrespondingDialog(
                    getContext(),
                    getPlayQueue()
                            .getStreams()
                            .stream()
                            .map(StreamEntity::new)
                            .collect(Collectors.toList()),
                    dialog -> dialog.show(fragmentManager, TAG)
            );
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Mute / Unmute
    //////////////////////////////////////////////////////////////////////////*/
    //region Mute / Unmute

    public void onMuteUnmuteButtonClicked() {
        if (DEBUG) {
            Log.d(TAG, "onMuteUnmuteButtonClicked() called");
        }
        simpleExoPlayer.setVolume(isMuted() ? 1 : 0);
        notifyPlaybackUpdateToListeners();
        setMuteButton(binding.switchMute, isMuted());
    }

    boolean isMuted() {
        return !exoPlayerIsNull() && simpleExoPlayer.getVolume() == 0;
    }

    private void setMuteButton(@NonNull final ImageButton button, final boolean isMuted) {
        button.setImageDrawable(AppCompatResources.getDrawable(context, isMuted
                ? R.drawable.ic_volume_off : R.drawable.ic_volume_up));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer listeners (that didn't fit in other categories)
    //////////////////////////////////////////////////////////////////////////*/
    //region ExoPlayer listeners (that didn't fit in other categories)

    /**
     * <p>Listens for event or state changes on ExoPlayer. When any event happens, we check for
     * changes in the currently-playing metadata and update the encapsulating
     * {@link Player}. Downstream listeners are also informed.</p>
     *
     * <p>When the renewed metadata contains any error, it is reported as a notification.
     * This is done because not all source resolution errors are {@link PlaybackException}, which
     * are also captured by {@link ExoPlayer} and stops the playback.</p>
     *
     * @param player The {@link com.google.android.exoplayer2.Player} whose state changed.
     * @param events The {@link com.google.android.exoplayer2.Player.Events} that has triggered
     *               the player state changes.
     **/
    @Override
    public void onEvents(@NonNull final com.google.android.exoplayer2.Player player,
                         @NonNull final com.google.android.exoplayer2.Player.Events events) {
        Listener.super.onEvents(player, events);
        MediaItemTag.from(player.getCurrentMediaItem()).ifPresent(tag -> {
            if (tag == currentMetadata) {
                return;
            }
            currentMetadata = tag;
            if (!tag.getErrors().isEmpty()) {
                final ErrorInfo errorInfo = new ErrorInfo(
                        tag.getErrors().get(0),
                        UserAction.PLAY_STREAM,
                        "Loading failed for [" + tag.getTitle() + "]: " + tag.getStreamUrl(),
                        tag.getServiceId());
                ErrorUtil.createNotification(context, errorInfo);
            }
            tag.getMaybeStreamInfo().ifPresent(info -> {
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onEvents() update stream info: " + info.getName());
                }
                updateMetadataWith(info);
            });
        });
    }

    @Override
    public void onTracksInfoChanged(@NonNull final TracksInfo tracksInfo) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onTracksChanged(), "
                    + "track group size = " + tracksInfo.getTrackGroupInfos().size());
        }
        onTextTracksChanged(tracksInfo);
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - playbackParameters(), speed = [" + playbackParameters.speed
                    + "], pitch = [" + playbackParameters.pitch + "]");
        }
        binding.playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onPositionDiscontinuity(@NonNull final PositionInfo oldPosition,
                                        @NonNull final PositionInfo newPosition,
                                        @DiscontinuityReason final int discontinuityReason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with "
                    + "oldPositionIndex = [" + oldPosition.mediaItemIndex + "], "
                    + "oldPositionMs = [" + oldPosition.positionMs + "], "
                    + "newPositionIndex = [" + newPosition.mediaItemIndex + "], "
                    + "newPositionMs = [" + newPosition.positionMs + "], "
                    + "discontinuityReason = [" + discontinuityReason + "]");
        }
        if (playQueue == null) {
            return;
        }

        // Refresh the playback if there is a transition to the next video
        final int newIndex = newPosition.mediaItemIndex;
        switch (discontinuityReason) {
            case DISCONTINUITY_REASON_AUTO_TRANSITION:
            case DISCONTINUITY_REASON_REMOVE:
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (getRepeatMode() == REPEAT_MODE_ONE && newIndex == playQueue.getIndex()) {
                    registerStreamViewed();
                    break;
                }
            case DISCONTINUITY_REASON_SEEK:
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onSeekProcessed() called");
                }
                if (isPrepared) {
                    saveStreamProgressState();
                }
            case DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
            case DISCONTINUITY_REASON_INTERNAL:
                // Player index may be invalid when playback is blocked
                if (getCurrentState() != STATE_BLOCKED && newIndex != playQueue.getIndex()) {
                    saveStreamProgressStateCompleted(); // current stream has ended
                    playQueue.setIndex(newIndex);
                }
                break;
            case DISCONTINUITY_REASON_SKIP:
                break; // only makes Android Studio linter happy, as there are no ads
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }

    @Override
    public void onCues(@NonNull final List<Cue> cues) {
        binding.subtitleView.onCues(cues);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Errors
    //////////////////////////////////////////////////////////////////////////*/
    //region Errors
    /**
     * Process exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
     * <p>There are multiple types of errors:</p>
     * <ul>
     * <li>{@link PlaybackException#ERROR_CODE_BEHIND_LIVE_WINDOW BEHIND_LIVE_WINDOW}:
     * If the playback on livestreams are lagged too far behind the current playable
     * window. Then we seek to the latest timestamp and restart the playback.
     * This error is <b>catchable</b>.
     * </li>
     * <li>From {@link PlaybackException#ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE BAD_IO} to
     * {@link PlaybackException#ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED UNSUPPORTED_FORMATS}:
     * If the stream source is validated by the extractor but not recognized by the player,
     * then we can try to recover playback by signalling an error on the {@link PlayQueue}.</li>
     * <li>For {@link PlaybackException#ERROR_CODE_TIMEOUT PLAYER_TIMEOUT},
     * {@link PlaybackException#ERROR_CODE_IO_UNSPECIFIED MEDIA_SOURCE_RESOLVER_TIMEOUT} and
     * {@link PlaybackException#ERROR_CODE_IO_NETWORK_CONNECTION_FAILED NO_NETWORK}:
     * We can keep set the recovery record and keep to player at the current state until
     * it is ready to play by restarting the {@link MediaSourceManager}.</li>
     * <li>On any ExoPlayer specific issue internal to its device interaction, such as
     * {@link PlaybackException#ERROR_CODE_DECODER_INIT_FAILED DECODER_ERROR}:
     * We terminate the playback.</li>
     * <li>For any other unspecified issue internal: We set a recovery and try to restart
     * the playback.</li>
     * For any error above that is <b>not</b> explicitly <b>catchable</b>, the player will
     * create a notification so users are aware.
     * </ul>
     * @see com.google.android.exoplayer2.Player.Listener#onPlayerError(PlaybackException)
     * */
    // Any error code not explicitly covered here are either unrelated to NewPipe use case
    // (e.g. DRM) or not recoverable (e.g. Decoder error). In both cases, the player should
    // shutdown.
    @SuppressLint("SwitchIntDef")
    @Override
    public void onPlayerError(@NonNull final PlaybackException error) {
        Log.e(TAG, "ExoPlayer - onPlayerError() called with:", error);

        saveStreamProgressState();
        boolean isCatchableException = false;

        switch (error.errorCode) {
            case ERROR_CODE_BEHIND_LIVE_WINDOW:
                isCatchableException = true;
                simpleExoPlayer.seekToDefaultPosition();
                simpleExoPlayer.prepare();
                // Inform the user that we are reloading the stream by
                // switching to the buffering state
                onBuffering();
                break;
            case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
            case ERROR_CODE_IO_BAD_HTTP_STATUS:
            case ERROR_CODE_IO_FILE_NOT_FOUND:
            case ERROR_CODE_IO_NO_PERMISSION:
            case ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED:
            case ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
            case ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                // Source errors, signal on playQueue and move on:
                if (!exoPlayerIsNull() && playQueue != null) {
                    playQueue.error();
                }
                break;
            case ERROR_CODE_TIMEOUT:
            case ERROR_CODE_IO_UNSPECIFIED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
            case ERROR_CODE_UNSPECIFIED:
                // Reload playback on unexpected errors:
                setRecovery();
                reloadPlayQueueManager();
                break;
            default:
                // API, remote and renderer errors belong here:
                onPlaybackShutdown();
                break;
        }

        if (!isCatchableException) {
            createErrorNotification(error);
        }

        if (fragmentListener != null) {
            fragmentListener.onPlayerError(error, isCatchableException);
        }
    }

    private void createErrorNotification(@NonNull final PlaybackException error) {
        final ErrorInfo errorInfo;
        if (currentMetadata == null) {
            errorInfo = new ErrorInfo(error, UserAction.PLAY_STREAM,
                    "Player error[type=" + error.getErrorCodeName()
                            + "] occurred, currentMetadata is null");
        } else {
            errorInfo = new ErrorInfo(error, UserAction.PLAY_STREAM,
                    "Player error[type=" + error.getErrorCodeName()
                            + "] occurred while playing " + currentMetadata.getStreamUrl(),
                    currentMetadata.getServiceId());
        }
        ErrorUtil.createNotification(context, errorInfo);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback position and seek
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback position and seek

    /**
     * Sets the current duration into the corresponding elements.
     * @param currentProgress
     */
    private void updatePlayBackElementsCurrentDuration(final int currentProgress) {
        // Don't set seekbar progress while user is seeking
        if (currentState != STATE_PAUSED_SEEK) {
            binding.playbackSeekBar.setProgress(currentProgress);
        }
        binding.playbackCurrentTime.setText(getTimeString(currentProgress));
    }

    @Override // own playback listener (this is a getter)
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (exoPlayerIsNull() || isLive() || !isPlaying()) {
            return false;
        }

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        final long currentDurationMillis = simpleExoPlayer.getDuration();
        return currentDurationMillis - currentPositionMillis < timeToEndMillis;
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge.
     *
     * @return whether the livestream is playing at or beyond the edge
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLiveEdge() {
        if (exoPlayerIsNull() || !isLive()) {
            return false;
        }

        final Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
        final int currentWindowIndex = simpleExoPlayer.getCurrentMediaItemIndex();
        if (currentTimeline.isEmpty() || currentWindowIndex < 0
                || currentWindowIndex >= currentTimeline.getWindowCount()) {
            return false;
        }

        final Timeline.Window timelineWindow = new Timeline.Window();
        currentTimeline.getWindow(currentWindowIndex, timelineWindow);
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer.getCurrentPosition();
    }

    @Override // own playback listener
    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item, final boolean wasBlocked) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackSynchronize(was blocked: " + wasBlocked
                    + ") called with item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }

        final boolean hasPlayQueueItemChanged = currentItem != item;

        final int currentPlayQueueIndex = playQueue.indexOf(item);
        final int currentPlaylistIndex = simpleExoPlayer.getCurrentMediaItemIndex();
        final int currentPlaylistSize = simpleExoPlayer.getCurrentTimeline().getWindowCount();

        // If nothing to synchronize
        if (!hasPlayQueueItemChanged) {
            return;
        }
        currentItem = item;

        // Check if on wrong window
        if (currentPlayQueueIndex != playQueue.getIndex()) {
            Log.e(TAG, "Playback - Play Queue may be desynchronized: item "
                    + "index=[" + currentPlayQueueIndex + "], "
                    + "queue index=[" + playQueue.getIndex() + "]");

            // Check if bad seek position
        } else if ((currentPlaylistSize > 0 && currentPlayQueueIndex >= currentPlaylistSize)
                || currentPlayQueueIndex < 0) {
            Log.e(TAG, "Playback - Trying to seek to invalid "
                    + "index=[" + currentPlayQueueIndex + "] with "
                    + "playlist length=[" + currentPlaylistSize + "]");

        } else if (wasBlocked || currentPlaylistIndex != currentPlayQueueIndex || !isPlaying()) {
            if (DEBUG) {
                Log.d(TAG, "Playback - Rewinding to correct "
                        + "index=[" + currentPlayQueueIndex + "], "
                        + "from=[" + currentPlaylistIndex + "], "
                        + "size=[" + currentPlaylistSize + "].");
            }

            if (item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
                simpleExoPlayer.seekTo(currentPlayQueueIndex, item.getRecoveryPosition());
                playQueue.unsetRecovery(currentPlayQueueIndex);
            } else {
                simpleExoPlayer.seekToDefaultPosition(currentPlayQueueIndex);
            }
        }
    }

    public void seekTo(final long positionMillis) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]");
        }
        if (!exoPlayerIsNull()) {
            // prevent invalid positions when fast-forwarding/-rewinding
            long normalizedPositionMillis = positionMillis;
            if (normalizedPositionMillis < 0) {
                normalizedPositionMillis = 0;
            } else if (normalizedPositionMillis > simpleExoPlayer.getDuration()) {
                normalizedPositionMillis = simpleExoPlayer.getDuration();
            }

            simpleExoPlayer.seekTo(normalizedPositionMillis);
        }
    }

    private void seekBy(final long offsetMillis) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]");
        }
        seekTo(simpleExoPlayer.getCurrentPosition() + offsetMillis);
    }

    public void seekToDefault() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.seekToDefaultPosition();
        }
    }

    /**
     * Sets the video duration time into all control components (e.g. seekbar).
     * @param duration
     */
    private void setVideoDurationToControls(final int duration) {
        binding.playbackEndTime.setText(getTimeString(duration));

        binding.playbackSeekBar.setMax(duration);
        // This is important for Android TVs otherwise it would apply the default from
        // setMax/Min methods which is (max - min) / 20
        binding.playbackSeekBar.setKeyProgressIncrement(
                PlayerHelper.retrieveSeekDurationFromPreferences(this));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player actions (play, pause, previous, fast-forward, ...)
    //////////////////////////////////////////////////////////////////////////*/
    //region Player actions (play, pause, previous, fast-forward, ...)

    public void play() {
        if (DEBUG) {
            Log.d(TAG, "play() called");
        }
        if (audioReactor == null || playQueue == null || exoPlayerIsNull()) {
            return;
        }

        audioReactor.requestAudioFocus();

        if (currentState == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        simpleExoPlayer.play();
        saveStreamProgressState();
    }

    public void pause() {
        if (DEBUG) {
            Log.d(TAG, "pause() called");
        }
        if (audioReactor == null || exoPlayerIsNull()) {
            return;
        }

        audioReactor.abandonAudioFocus();
        simpleExoPlayer.pause();
        saveStreamProgressState();
    }

    public void playPause() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPause() called");
        }

        if (getPlayWhenReady()
                // When state is completed (replay button is shown) then (re)play and do not pause
                && currentState != STATE_COMPLETED) {
            pause();
        } else {
            play();
        }
    }

    public void playPrevious() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPrevious() called");
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track. Also restart the track if the current track
         * is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS
                || playQueue.getIndex() == 0) {
            seekToDefault();
            playQueue.offsetIndex(0);
        } else {
            saveStreamProgressState();
            playQueue.offsetIndex(-1);
        }
        triggerProgressUpdate();
    }

    public void playNext() {
        if (DEBUG) {
            Log.d(TAG, "onPlayNext() called");
        }
        if (playQueue == null) {
            return;
        }

        saveStreamProgressState();
        playQueue.offsetIndex(+1);
        triggerProgressUpdate();
    }

    public void fastForward() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
    }

    public void fastRewind() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(-retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // StreamInfo history: views and progress
    //////////////////////////////////////////////////////////////////////////*/
    //region StreamInfo history: views and progress

    private void registerStreamViewed() {
        getCurrentStreamInfo().ifPresent(info -> databaseUpdateDisposable
                .add(recordManager.onViewed(info).onErrorComplete().subscribe()));
    }

    private void saveStreamProgressState(final long progressMillis) {
        if (!getCurrentStreamInfo().isPresent()
                || !prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "saveStreamProgressState() called with: progressMillis=" + progressMillis
                    + ", currentMetadata=[" + getCurrentStreamInfo().get().getName() + "]");
        }

        databaseUpdateDisposable
                .add(recordManager.saveStreamState(getCurrentStreamInfo().get(), progressMillis)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(e -> {
                    if (DEBUG) {
                        e.printStackTrace();
                    }
                })
                .onErrorComplete()
                .subscribe());
    }

    public void saveStreamProgressState() {
        if (exoPlayerIsNull() || currentMetadata == null || playQueue == null
                || playQueue.getIndex() != simpleExoPlayer.getCurrentMediaItemIndex()) {
            // Make sure play queue and current window index are equal, to prevent saving state for
            // the wrong stream on discontinuity (e.g. when the stream just changed but the
            // playQueue index and currentMetadata still haven't updated)
            return;
        }
        // Save current position. It will help to restore this position once a user
        // wants to play prev or next stream from the queue
        playQueue.setRecovery(playQueue.getIndex(), simpleExoPlayer.getContentPosition());
        saveStreamProgressState(simpleExoPlayer.getCurrentPosition());
    }

    public void saveStreamProgressStateCompleted() {
        // current stream has ended, so the progress is its duration (+1 to overcome rounding)
        getCurrentStreamInfo().ifPresent(info ->
                saveStreamProgressState((info.getDuration() + 1) * 1000));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Metadata
    //////////////////////////////////////////////////////////////////////////*/
    //region Metadata

    private void onMetadataChanged(@NonNull final StreamInfo info) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.getName());
        }

        initThumbnail(info.getThumbnailUrl());
        registerStreamViewed();
        updateStreamRelatedViews();
        showHideKodiButton();

        binding.titleTextView.setText(info.getName());
        binding.channelTextView.setText(info.getUploaderName());

        this.seekbarPreviewThumbnailHolder.resetFrom(this.getContext(), info.getPreviewFrames());

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);

        final boolean showThumbnail = prefs.getBoolean(
                context.getString(R.string.show_thumbnail_key), true);
        mediaSessionManager.setMetadata(
                getVideoTitle(),
                getUploaderName(),
                showThumbnail ? Optional.ofNullable(getThumbnail()) : Optional.empty(),
                StreamTypeUtil.isLiveStream(info.getStreamType()) ? -1 : info.getDuration()
        );

        notifyMetadataUpdateToListeners();

        if (areSegmentsVisible) {
            if (segmentAdapter.setItems(info)) {
                final int adapterPosition = getNearestStreamSegmentPosition(
                        simpleExoPlayer.getCurrentPosition());
                segmentAdapter.selectSegmentAt(adapterPosition);
                binding.itemsList.scrollToPosition(adapterPosition);
            } else {
                closeItemsList();
            }
        }
    }

    private void updateMetadataWith(@NonNull final StreamInfo streamInfo) {
        if (exoPlayerIsNull()) {
            return;
        }

        maybeAutoQueueNextStream(streamInfo);
        onMetadataChanged(streamInfo);
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, true);
    }

    @NonNull
    private String getVideoUrl() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getStreamUrl();
    }

    @NonNull
    private String getVideoUrlAtCurrentTime() {
        final int timeSeconds = binding.playbackSeekBar.getProgress() / 1000;
        String videoUrl = getVideoUrl();
        if (!isLive() && timeSeconds >= 0 && currentMetadata != null
                && currentMetadata.getServiceId() == YouTube.getServiceId()) {
            // Timestamp doesn't make sense in a live stream so drop it
            videoUrl += ("&t=" + timeSeconds);
        }
        return videoUrl;
    }

    @NonNull
    public String getVideoTitle() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getTitle();
    }

    @NonNull
    public String getUploaderName() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getUploaderName();
    }

    @Nullable
    public Bitmap getThumbnail() {
        if (currentThumbnail == null) {
            currentThumbnail = BitmapFactory.decodeResource(
                    context.getResources(), R.drawable.dummy_thumbnail);
        }
        return currentThumbnail;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    //////////////////////////////////////////////////////////////////////////*/
    //region Play queue, segments and streams

    private void maybeAutoQueueNextStream(@NonNull final StreamInfo info) {
        if (playQueue == null || playQueue.getIndex() != playQueue.size() - 1
                || getRepeatMode() != REPEAT_MODE_OFF
                || !PlayerHelper.isAutoQueueEnabled(context)) {
            return;
        }
        // auto queue when starting playback on the last item when not repeating
        final PlayQueue autoQueue = PlayerHelper.autoQueueOf(info,
                playQueue.getStreams());
        if (autoQueue != null) {
            playQueue.append(autoQueue.getStreams());
        }
    }

    public void selectQueueItem(final PlayQueueItem item) {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int index = playQueue.indexOf(item);
        if (index == -1) {
            return;
        }

        if (playQueue.getIndex() == index && simpleExoPlayer.getCurrentMediaItemIndex() == index) {
            seekToDefault();
        } else {
            saveStreamProgressState();
        }
        playQueue.setIndex(index);
    }

    @Override
    public void onPlayQueueEdited() {
        notifyPlaybackUpdateToListeners();
        showOrHideButtons();
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
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

        binding.itemsList.scrollToPosition(playQueue.getIndex());

        updateQueueTime((int) simpleExoPlayer.getCurrentPosition());
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

        final int adapterPosition = getNearestStreamSegmentPosition(simpleExoPlayer
                .getCurrentPosition());
        segmentAdapter.selectSegmentAt(adapterPosition);
        binding.itemsList.scrollToPosition(adapterPosition);
    }

    private void buildSegments() {
        binding.itemsList.setAdapter(segmentAdapter);
        binding.itemsList.setClickable(true);
        binding.itemsList.setLongClickable(false);

        binding.itemsList.clearOnScrollListeners();
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }

        getCurrentStreamInfo().ifPresent(segmentAdapter::setItems);

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
                    AnimationType.SLIDE_AND_ALPHA, 0, () -> {
                        // Even when queueLayout is GONE it receives touch events
                        // and ruins normal behavior of the app. This line fixes it
                        binding.itemsListPanel.setTranslationY(
                                -binding.itemsListPanel.getHeight() * 5);
                    });

            // clear focus, otherwise a white rectangle remains on top of the player
            binding.itemsListClose.clearFocus();
            binding.playPauseButton.requestFocus();
        }
    }

    private OnScrollBelowItemsListener getQueueScrollListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(final RecyclerView recyclerView) {
                if (playQueue != null && !playQueue.isComplete()) {
                    playQueue.fetch();
                } else if (binding != null) {
                    binding.itemsList.clearOnScrollListeners();
                }
            }
        };
    }

    private StreamSegmentAdapter.StreamSegmentListener getStreamSegmentListener() {
        return (item, seconds) -> {
            segmentAdapter.selectSegment(item);
            seekTo(seconds * 1000L);
            triggerProgressUpdate();
        };
    }

    private int getNearestStreamSegmentPosition(final long playbackPosition) {
        int nearestPosition = 0;
        final List<StreamSegment> segments = getCurrentStreamInfo()
                .map(StreamInfo::getStreamSegments)
                .orElse(Collections.emptyList());

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
                if (playQueue != null) {
                    playQueue.move(sourceIndex, targetIndex);
                }
            }

            @Override
            public void onSwiped(final int index) {
                if (index != -1) {
                    playQueue.remove(index);
                }
            }
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(final PlayQueueItem item, final View view) {
                selectQueueItem(item);
            }

            @Override
            public void held(final PlayQueueItem item, final View view) {
                if (playQueue.indexOf(item) != -1) {
                    openPopupMenu(playQueue, item, view, true,
                            getParentActivity().getSupportFragmentManager(), context);
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

    @Override // own playback listener
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        if (audioPlayerSelected()) {
            return audioResolver.resolve(info);
        }

        if (isAudioOnly && videoResolver.getStreamSourceType().orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
                == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY) {
            // If the current info has only video streams with audio and if the stream is played as
            // audio, we need to use the audio resolver, otherwise the video stream will be played
            // in background.
            return audioResolver.resolve(info);
        }

        // Even if the stream is played in background, we need to use the video resolver if the
        // info played is separated video-only and audio-only streams; otherwise, if the audio
        // resolver was called when the app was in background, the app will only stream audio when
        // the user come back to the app and will never fetch the video stream.
        // Note that the video is not fetched when the app is in background because the video
        // renderer is fully disabled (see useVideoSource method), except for HLS streams
        // (see https://github.com/google/ExoPlayer/issues/9282).
        return videoResolver.resolve(info);
    }

    public void disablePreloadingOfCurrentTrack() {
        loadController.disablePreloadingOfCurrentTrack();
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        return (selectedStreamIndex >= 0 && availableStreams != null
                && availableStreams.size() > selectedStreamIndex)
                ? availableStreams.get(selectedStreamIndex) : null;
    }

    private void updateStreamRelatedViews() {
        if (!getCurrentStreamInfo().isPresent()) {
            return;
        }
        final StreamInfo info = getCurrentStreamInfo().get();

        binding.qualityTextView.setVisibility(View.GONE);
        binding.playbackSpeed.setVisibility(View.GONE);

        binding.playbackEndTime.setVisibility(View.GONE);
        binding.playbackLiveSync.setVisibility(View.GONE);

        switch (info.getStreamType()) {
            case AUDIO_STREAM:
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
                break;

            case VIDEO_STREAM:
                if (currentMetadata == null
                        || !currentMetadata.getMaybeQuality().isPresent()
                        || (info.getVideoStreams().isEmpty()
                        && info.getVideoOnlyStreams().isEmpty())) {
                    break;
                }

                availableStreams = currentMetadata.getMaybeQuality().get().getSortedVideoStreams();
                selectedStreamIndex =
                        currentMetadata.getMaybeQuality().get().getSelectedVideoStreamIndex();
                buildQualityMenu();

                binding.qualityTextView.setVisibility(View.VISIBLE);
                binding.surfaceView.setVisibility(View.VISIBLE);
            default:
                binding.endScreen.setVisibility(View.GONE);
                binding.playbackEndTime.setVisibility(View.VISIBLE);
                break;
        }

        buildPlaybackSpeedMenu();
        binding.playbackSpeed.setVisibility(View.VISIBLE);
    }

    private void updateQueueTime(final int currentTime) {
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

        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.resolution);
        }
        if (getSelectedVideoStream() != null) {
            binding.qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
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
        binding.playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private void buildCaptionMenu(@NonNull final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_CAPTION);
        captionPopupMenu.setOnDismissListener(this);

        // Add option for turning off caption
        final MenuItem captionOffItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            final int textRendererIndex = getCaptionRendererIndex();
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true));
            }
            prefs.edit().remove(context.getString(R.string.caption_user_set_key)).apply();
            return true;
        });

        // Add all available captions
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            final MenuItem captionItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                    i + 1, Menu.NONE, captionLanguage);
            captionItem.setOnMenuItemClickListener(menuItem -> {
                final int textRendererIndex = getCaptionRendererIndex();
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    // DefaultTrackSelector will select for text tracks in the following order.
                    // When multiple tracks share the same rank, a random track will be chosen.
                    // 1. ANY track exactly matching preferred language name
                    // 2. ANY track exactly matching preferred language stem
                    // 3. ROLE_FLAG_CAPTION track matching preferred language stem
                    // 4. ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND track matching preferred language stem
                    // This means if a caption track of preferred language is not available,
                    // then an auto-generated track of that language will be chosen automatically.
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setPreferredTextLanguages(captionLanguage,
                                    PlayerHelper.captionLanguageStemOf(captionLanguage))
                            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                            .setRendererDisabled(textRendererIndex, false));
                    prefs.edit().putString(context.getString(R.string.caption_user_set_key),
                            captionLanguage).apply();
                }
                return true;
            });
        }

        // apply caption language from previous user preference
        final List<String> selectedPreferredLanguages =
                trackSelector.getParameters().preferredTextLanguages;
        final String userPreferredLanguage =
                prefs.getString(context.getString(R.string.caption_user_set_key), null);
        final int textRendererIndex = getCaptionRendererIndex();

        if (userPreferredLanguage != null
                && !selectedPreferredLanguages.contains(userPreferredLanguage)
                && textRendererIndex != RENDERER_UNAVAILABLE) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredTextLanguages(userPreferredLanguage,
                            PlayerHelper.captionLanguageStemOf(userPreferredLanguage))
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false));
        }
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
            final int menuItemIndex = menuItem.getItemId();
            if (selectedStreamIndex == menuItemIndex || availableStreams == null
                    || availableStreams.size() <= menuItemIndex) {
                return true;
            }

            saveStreamProgressState(); //TODO added, check if good
            final String newResolution = availableStreams.get(menuItemIndex).resolution;
            setRecovery();
            setPlaybackQuality(newResolution);
            reloadPlayQueueManager();

            binding.qualityTextView.setText(menuItem.getTitle());
            return true;
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_PLAYBACK_SPEED) {
            final int speedIndex = menuItem.getItemId();
            final float speed = PLAYBACK_SPEEDS[speedIndex];

            setPlaybackSpeed(speed);
            binding.playbackSpeed.setText(formatSpeed(speed));
        }

        return false;
    }

    /**
     * Called when some popup menu is dismissed.
     */
    @Override
    public void onDismiss(@Nullable final PopupMenu menu) {
        if (DEBUG) {
            Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]");
        }
        isSomePopupMenuVisible = false; //TODO check if this works
        if (getSelectedVideoStream() != null) {
            binding.qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        if (isPlaying()) {
            hideControls(DEFAULT_CONTROLS_DURATION, 0);
            hideSystemUIIfNeeded();
        }
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }
        captionPopupMenu.show();
        isSomePopupMenuVisible = true;
    }

    private void setPlaybackQuality(@Nullable final String quality) {
        videoResolver.setPlaybackQuality(quality);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    private void setupSubtitleView() {
        final float captionScale = PlayerHelper.getCaptionScale(context);
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        if (popupPlayerSelected()) {
            final float captionRatio = (captionScale - 1.0f) / 5.0f + 1.0f;
            binding.subtitleView.setFractionalTextSize(
                    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio);
        } else {
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            final float captionRatioInverse = 20f + 4f * (1.0f - captionScale);
            binding.subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_PX, minimumLength / captionRatioInverse);
        }
        binding.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(captionStyle);
    }

    private void onTextTracksChanged(@NonNull final TracksInfo currentTrackInfo) {
        if (binding == null) {
            return;
        }

        if (trackSelector.getCurrentMappedTrackInfo() == null
                || !currentTrackInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_TEXT)) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        // Extract all loaded languages
        final List<TracksInfo.TrackGroupInfo> textTracks = currentTrackInfo
                .getTrackGroupInfos()
                .stream()
                .filter(trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.getTrackType())
                .collect(Collectors.toList());
        final List<String> availableLanguages = textTracks.stream()
                .map(TracksInfo.TrackGroupInfo::getTrackGroup)
                .filter(textTrack -> textTrack.length > 0)
                .map(textTrack -> textTrack.getFormat(0).language)
                .collect(Collectors.toList());

        // Find selected text track
        final Optional<Format> selectedTracks = textTracks.stream()
                .filter(TracksInfo.TrackGroupInfo::isSelected)
                .filter(info -> info.getTrackGroup().length >= 1)
                .map(info -> info.getTrackGroup().getFormat(0))
                .findFirst();

        // Build UI
        buildCaptionMenu(availableLanguages);
        if (trackSelector.getParameters().getRendererDisabled(getCaptionRendererIndex())
                || !selectedTracks.isPresent()) {
            binding.captionTextView.setText(R.string.caption_none);
        } else {
            binding.captionTextView.setText(selectedTracks.get().language);
        }
        binding.captionTextView.setVisibility(
                availableLanguages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private int getCaptionRendererIndex() {
        if (exoPlayerIsNull()) {
            return RENDERER_UNAVAILABLE;
        }

        for (int t = 0; t < simpleExoPlayer.getRendererCount(); t++) {
            if (simpleExoPlayer.getRendererType(t) == C.TRACK_TYPE_TEXT) {
                return t;
            }
        }

        return RENDERER_UNAVAILABLE;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Click listeners

    @Override
    public void onClick(final View v) {
        if (DEBUG) {
            Log.d(TAG, "onClick() called with: v = [" + v + "]");
        }
        if (v.getId() == binding.resizeTextView.getId()) {
            onResizeClicked();
        } else if (v.getId() == binding.captionTextView.getId()) {
            onCaptionClicked();
        } else if (v.getId() == binding.playbackLiveSync.getId()) {
            seekToDefault();
        } else if (v.getId() == binding.playPauseButton.getId()) {
            playPause();
        } else if (v.getId() == binding.playPreviousButton.getId()) {
            playPrevious();
        } else if (v.getId() == binding.playNextButton.getId()) {
            playNext();
        } else if (v.getId() == binding.moreOptionsButton.getId()) {
            onMoreOptionsClicked();
        } else if (v.getId() == binding.share.getId()) {
            ShareUtils.shareText(context, getVideoTitle(), getVideoUrlAtCurrentTime(),
                            currentItem.getThumbnailUrl());
        } else if (v.getId() == binding.playWithKodi.getId()) {
            onPlayWithKodiClicked();
        } else if (v.getId() == binding.openInBrowser.getId()) {
            onOpenInBrowserClicked();
        } else if (v.getId() == binding.fullScreenButton.getId()) {
            setRecovery();
            NavigationHelper.playOnMainPlayer(context, playQueue, true);
            return;
        } else if (v.getId() == binding.screenRotationButton.getId()) {
            // Only if it's not a vertical video or vertical video but in landscape with locked
            // orientation a screen orientation can be changed automatically
            if (!isVerticalVideo
                    || (service.isLandscape() && globalScreenOrientationLocked(context))) {
                fragmentListener.onScreenRotationButtonClicked();
            } else {
                toggleFullscreen();
            }
        } else if (v.getId() == binding.switchMute.getId()) {
            onMuteUnmuteButtonClicked();
        } else if (v.getId() == binding.playerCloseButton.getId()) {
            context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER));
        }

        manageControlsAfterOnClick(v);
    }

    /**
     * Manages the controls after a click occurred on the player UI.
     * @param v – The view that was clicked
     */
    public void manageControlsAfterOnClick(@NonNull final View v) {
        if (currentState == STATE_COMPLETED) {
            return;
        }

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, DEFAULT_CONTROLS_DURATION);
        animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.ALPHA, 0, () -> {
                    if (currentState == STATE_PLAYING && !isSomePopupMenuVisible) {
                        if (v.getId() == binding.playPauseButton.getId()
                                // Hide controls in fullscreen immediately
                                || (v.getId() == binding.screenRotationButton.getId()
                                && isFullscreen)) {
                            hideControls(0, 0);
                        } else {
                            hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                        }
                    }
                });
    }

    @Override
    public boolean onLongClick(final View v) {
        if (v.getId() == binding.moreOptionsButton.getId() && isFullscreen) {
            fragmentListener.onMoreOptionsLongClicked();
            hideControls(0, 0);
            hideSystemUIIfNeeded();
        } else if (v.getId() == binding.share.getId()) {
            ShareUtils.copyToClipboard(context, getVideoUrlAtCurrentTime());
        }
        return true;
    }

    public boolean onKeyDown(final int keyCode) {
        switch (keyCode) {
            default:
                break;
            case KeyEvent.KEYCODE_SPACE:
                if (isFullscreen) {
                    playPause();
                    if (isPlaying()) {
                        hideControls(0, 0);
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (DeviceUtils.isTv(context) && isControlsVisible()) {
                    hideControls(0, 0);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if ((binding.getRoot().hasFocus() && !binding.playbackControlRoot.hasFocus())
                        || isQueueVisible) {
                    // do not interfere with focus in playlist and play queue etc.
                    return false;
                }

                if (currentState == Player.STATE_BLOCKED) {
                    return true;
                }

                if (isControlsVisible()) {
                    hideControls(DEFAULT_CONTROLS_DURATION, DPAD_CONTROLS_HIDE_TIME);
                } else {
                    binding.playPauseButton.requestFocus();
                    showControlsThenHide();
                    showSystemUIPartially();
                    return true;
                }
                break;
        }

        return false;
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
        if (currentMetadata != null) {
            pause();
            try {
                NavigationHelper.playWithKore(context, Uri.parse(getVideoUrl()));
            } catch (final Exception e) {
                if (DEBUG) {
                    Log.i(TAG, "Failed to start kore", e);
                }
                KoreUtils.showInstallKoreDialog(getParentActivity());
            }
        }
    }

    private void onOpenInBrowserClicked() {
        getCurrentStreamInfo()
                .map(Info::getOriginalUrl)
                .ifPresent(originalUrl -> ShareUtils.openUrlInBrowser(
                        Objects.requireNonNull(getParentActivity()), originalUrl));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Video size, resize, orientation, fullscreen
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size, resize, orientation, fullscreen

    private void setupScreenRotationButton() {
        binding.screenRotationButton.setVisibility(videoPlayerSelected()
                && (globalScreenOrientationLocked(context) || isVerticalVideo
                        || DeviceUtils.isTablet(context))
                ? View.VISIBLE : View.GONE);
        binding.screenRotationButton.setImageDrawable(AppCompatResources.getDrawable(context,
                isFullscreen ? R.drawable.ic_fullscreen_exit
                : R.drawable.ic_fullscreen));
    }

    private void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        binding.surfaceView.setResizeMode(resizeMode);
        binding.resizeTextView.setText(PlayerHelper.resizeTypeOf(context, resizeMode));
    }

    void onResizeClicked() {
        if (binding != null) {
            setResizeMode(nextResizeModeAndSaveToPrefs(this, binding.surfaceView.getResizeMode()));
        }
    }

    @Override // exoplayer listener
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: "
                    + "width / height = [" + videoSize.width + " / " + videoSize.height
                    + " = " + (((float) videoSize.width) / videoSize.height) + "], "
                    + "unappliedRotationDegrees = [" + videoSize.unappliedRotationDegrees + "], "
                    + "pixelWidthHeightRatio = [" + videoSize.pixelWidthHeightRatio + "]");
        }

        binding.surfaceView.setAspectRatio(((float) videoSize.width) / videoSize.height);
        isVerticalVideo = videoSize.width < videoSize.height;

        if (globalScreenOrientationLocked(context)
                && isFullscreen
                && service.isLandscape() == isVerticalVideo
                && !DeviceUtils.isTv(context)
                && !DeviceUtils.isTablet(context)
                && fragmentListener != null) {
            // set correct orientation
            fragmentListener.onScreenRotationButtonClicked();
        }

        setupScreenRotationButton();
    }

    public void toggleFullscreen() {
        if (DEBUG) {
            Log.d(TAG, "toggleFullscreen() called");
        }
        if (popupPlayerSelected() || exoPlayerIsNull() || fragmentListener == null) {
            return;
        }

        isFullscreen = !isFullscreen;
        if (!isFullscreen) {
            // Apply window insets because Android will not do it when orientation changes
            // from landscape to portrait (open vertical video to reproduce)
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        } else {
            // Android needs tens milliseconds to send new insets but a user is able to see
            // how controls changes it's position from `0` to `nav bar height` padding.
            // So just hide the controls to hide this visual inconsistency
            hideControls(0, 0);
        }
        fragmentListener.onFullscreenStateChanged(isFullscreen);

        if (isFullscreen) {
            binding.titleTextView.setVisibility(View.VISIBLE);
            binding.channelTextView.setVisibility(View.VISIBLE);
            binding.playerCloseButton.setVisibility(View.GONE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
            binding.playerCloseButton.setVisibility(
                    videoPlayerSelected() ? View.VISIBLE : View.GONE);
        }
        setupScreenRotationButton();
    }

    public void checkLandscape() {
        final AppCompatActivity parent = getParentActivity();
        final boolean videoInLandscapeButNotInFullscreen =
                service.isLandscape() && !isFullscreen && videoPlayerSelected() && !isAudioOnly;

        final boolean notPaused = currentState != STATE_COMPLETED && currentState != STATE_PAUSED;
        if (parent != null
                && videoInLandscapeButNotInFullscreen
                && notPaused
                && !DeviceUtils.isTablet(context)) {
            toggleFullscreen();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/
    //region Gestures

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void onLayoutChange(final View view, final int l, final int t, final int r, final int b,
                                final int ol, final int ot, final int or, final int ob) {
        if (l != ol || t != ot || r != or || b != ob) {
            // Use smaller value to be consistent between screen orientations
            // (and to make usage easier)
            final int width = r - l;
            final int height = b - t;
            final int min = Math.min(width, height);
            maxGestureLength = (int) (min * MAX_GESTURE_LENGTH);

            if (DEBUG) {
                Log.d(TAG, "maxGestureLength = " + maxGestureLength);
            }

            binding.volumeProgressBar.setMax(maxGestureLength);
            binding.brightnessProgressBar.setMax(maxGestureLength);

            setInitialGestureValues();
            binding.itemsListPanel.getLayoutParams().height
                    = height - binding.itemsListPanel.getTop();
        }
    }

    private void setInitialGestureValues() {
        if (audioReactor != null) {
            final float currentVolumeNormalized =
                    (float) audioReactor.getVolume() / audioReactor.getMaxVolume();
            binding.volumeProgressBar.setProgress(
                    (int) (binding.volumeProgressBar.getMax() * currentVolumeNormalized));
        }
    }

    private int distanceFromCloseButton(@NonNull final MotionEvent popupMotionEvent) {
        final int closeOverlayButtonX = closeOverlayBinding.closeButton.getLeft()
                + closeOverlayBinding.closeButton.getWidth() / 2;
        final int closeOverlayButtonY = closeOverlayBinding.closeButton.getTop()
                + closeOverlayBinding.closeButton.getHeight() / 2;

        final float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
        final float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

        return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2)
                + Math.pow(closeOverlayButtonY - fingerY, 2));
    }

    private float getClosingRadius() {
        final int buttonRadius = closeOverlayBinding.closeButton.getWidth() / 2;
        // 20% wider than the button itself
        return buttonRadius * 1.2f;
    }

    public boolean isInsideClosingRadius(@NonNull final MotionEvent popupMotionEvent) {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Activity / fragment binding
    //////////////////////////////////////////////////////////////////////////*/
    //region Activity / fragment binding

    public void setFragmentListener(final PlayerServiceEventListener listener) {
        fragmentListener = listener;
        fragmentIsVisible = true;
        // Apply window insets because Android will not do it when orientation changes
        // from landscape to portrait
        if (!isFullscreen) {
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        }
        binding.itemsListPanel.setPadding(0, 0, 0, 0);
        notifyQueueUpdateToListeners();
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        triggerProgressUpdate();
    }

    public void removeFragmentListener(final PlayerServiceEventListener listener) {
        if (fragmentListener == listener) {
            fragmentListener = null;
        }
    }

    void setActivityListener(final PlayerEventListener listener) {
        activityListener = listener;
        // TODO why not queue update?
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        triggerProgressUpdate();
    }

    void removeActivityListener(final PlayerEventListener listener) {
        if (activityListener == listener) {
            activityListener = null;
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

    /**
     * This will be called when a user goes to another app/activity, turns off a screen.
     * We don't want to interrupt playback and don't want to see notification so
     * next lines of code will enable audio-only playback only if needed
     */
    private void onFragmentStopped() {
        if (videoPlayerSelected() && (isPlaying() || isLoading())) {
            switch (getMinimizeOnExitAction(context)) {
                case MINIMIZE_ON_EXIT_MODE_BACKGROUND:
                    useVideoSource(false);
                    break;
                case MINIMIZE_ON_EXIT_MODE_POPUP:
                    setRecovery();
                    NavigationHelper.playOnPopupPlayer(getParentActivity(), playQueue, true);
                    break;
                case MINIMIZE_ON_EXIT_MODE_NONE: default:
                    pause();
                    break;
            }
        }
    }

    private void notifyQueueUpdateToListeners() {
        if (fragmentListener != null && playQueue != null) {
            fragmentListener.onQueueUpdate(playQueue);
        }
        if (activityListener != null && playQueue != null) {
            activityListener.onQueueUpdate(playQueue);
        }
    }

    private void notifyMetadataUpdateToListeners() {
        getCurrentStreamInfo().ifPresent(info -> {
            if (fragmentListener != null) {
                fragmentListener.onMetadataUpdate(info, playQueue);
            }
            if (activityListener != null) {
                activityListener.onMetadataUpdate(info, playQueue);
            }
        });
    }

    private void notifyPlaybackUpdateToListeners() {
        if (fragmentListener != null && !exoPlayerIsNull() && playQueue != null) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && !exoPlayerIsNull() && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void notifyProgressUpdateToListeners(final int currentProgress,
                                                 final int duration,
                                                 final int bufferPercent) {
        if (fragmentListener != null) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    @Nullable
    public AppCompatActivity getParentActivity() {
        // ! instanceof ViewGroup means that view was added via windowManager for Popup
        if (binding == null || !(binding.getRoot().getParent() instanceof ViewGroup)) {
            return null;
        }

        return (AppCompatActivity) ((ViewGroup) binding.getRoot().getParent()).getContext();
    }

    private void useVideoSource(final boolean videoEnabled) {
        if (playQueue == null || isAudioOnly == !videoEnabled || audioPlayerSelected()) {
            return;
        }

        isAudioOnly = !videoEnabled;
        // When a user returns from background, controls could be hidden but SystemUI will be shown
        // 100%. Hide it.
        if (!isAudioOnly && !isControlsVisible()) {
            hideSystemUIIfNeeded();
        }

        // The current metadata may be null sometimes (for e.g. when using an unstable connection
        // in livestreams) so we will be not able to execute the block below.
        // Reload the play queue manager in this case, which is the behavior when we don't know the
        // index of the video renderer or playQueueManagerReloadingNeeded returns true.
        final Optional<StreamInfo> optCurrentStreamInfo = getCurrentStreamInfo();
        if (!optCurrentStreamInfo.isPresent()) {
            reloadPlayQueueManager();
            setRecovery();
            return;
        }

        final StreamInfo info = optCurrentStreamInfo.get();

        // In the case we don't know the source type, fallback to the one with video with audio or
        // audio-only source.
        final SourceType sourceType = videoResolver.getStreamSourceType().orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY);

        if (playQueueManagerReloadingNeeded(sourceType, info, getVideoRendererIndex())) {
            reloadPlayQueueManager();
        } else {
            final StreamType streamType = info.getStreamType();
            if (streamType == StreamType.AUDIO_STREAM
                    || streamType == StreamType.AUDIO_LIVE_STREAM) {
                // Nothing to do more than setting the recovery position
                setRecovery();
                return;
            }

            final DefaultTrackSelector.ParametersBuilder parametersBuilder =
                    trackSelector.buildUponParameters();

            if (videoEnabled) {
                // Enable again the video track and the subtitles, if there is one selected
                parametersBuilder.setDisabledTrackTypes(Collections.emptySet());
            } else {
                // Disable the video track and the ability to select subtitles
                // Use an ArraySet because we can't use Set.of() on all supported APIs by the app
                final ArraySet<Integer> disabledTracks = new ArraySet<>();
                disabledTracks.add(C.TRACK_TYPE_TEXT);
                disabledTracks.add(C.TRACK_TYPE_VIDEO);
                parametersBuilder.setDisabledTrackTypes(disabledTracks);
            }

            trackSelector.setParameters(parametersBuilder);
        }

        setRecovery();
    }

    /**
     * Return whether the play queue manager needs to be reloaded when switching player type.
     *
     * <p>
     * The play queue manager needs to be reloaded if the video renderer index is not known and if
     * the content is not an audio content, but also if none of the following cases is met:
     *
     * <ul>
     *     <li>the content is an {@link StreamType#AUDIO_STREAM audio stream} or an
     *     {@link StreamType#AUDIO_LIVE_STREAM audio live stream};</li>
     *     <li>the content is a {@link StreamType#LIVE_STREAM live stream} and the source type is a
     *     {@link SourceType#LIVE_STREAM live source};</li>
     *     <li>the content's source is {@link SourceType#VIDEO_WITH_SEPARATED_AUDIO a video stream
     *     with a separated audio source} or has no audio-only streams available <b>and</b> is a
     *     {@link StreamType#LIVE_STREAM live stream} or a
     *     {@link StreamType#LIVE_STREAM live stream}.
     *     </li>
     * </ul>
     * </p>
     *
     * @param sourceType         the {@link SourceType} of the stream
     * @param streamInfo         the {@link StreamInfo} of the stream
     * @param videoRendererIndex the video renderer index of the video source, if that's a video
     *                           source (or {@link #RENDERER_UNAVAILABLE})
     * @return whether the play queue manager needs to be reloaded
     */
    private boolean playQueueManagerReloadingNeeded(final SourceType sourceType,
                                                    @NonNull final StreamInfo streamInfo,
                                                    final int videoRendererIndex) {
        final StreamType streamType = streamInfo.getStreamType();

        if (videoRendererIndex == RENDERER_UNAVAILABLE && streamType != StreamType.AUDIO_STREAM
                && streamType != StreamType.AUDIO_LIVE_STREAM) {
            return true;
        }

        // The content is an audio stream, an audio live stream, or a live stream with a live
        // source: it's not needed to reload the play queue manager because the stream source will
        // be the same
        if ((streamType == StreamType.AUDIO_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM)
                || (streamType == StreamType.LIVE_STREAM
                        && sourceType == SourceType.LIVE_STREAM)) {
            return false;
        }

        // The content's source is a video with separated audio or a video with audio -> the video
        // and its fetch may be disabled
        // The content's source is a video with embedded audio and the content has no separated
        // audio stream available: it's probably not needed to reload the play queue manager
        // because the stream source will be probably the same as the current played
        if (sourceType == SourceType.VIDEO_WITH_SEPARATED_AUDIO
                || (sourceType == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
                    && isNullOrEmpty(streamInfo.getAudioStreams()))) {
            // It's not needed to reload the play queue manager only if the content's stream type
            // is a video stream or a live stream
            return streamType != StreamType.VIDEO_STREAM && streamType != StreamType.LIVE_STREAM;
        }

        // Other cases: the play queue manager reload is needed
        return true;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    private Optional<StreamInfo> getCurrentStreamInfo() {
        return Optional.ofNullable(currentMetadata).flatMap(MediaItemTag::getMaybeStreamInfo);
    }

    public int getCurrentState() {
        return currentState;
    }

    public boolean exoPlayerIsNull() {
        return simpleExoPlayer == null;
    }

    public boolean isStopped() {
        return exoPlayerIsNull() || simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_IDLE;
    }

    public boolean isPlaying() {
        return !exoPlayerIsNull() && simpleExoPlayer.isPlaying();
    }

    public boolean getPlayWhenReady() {
        return !exoPlayerIsNull() && simpleExoPlayer.getPlayWhenReady();
    }

    private boolean isLoading() {
        return !exoPlayerIsNull() && simpleExoPlayer.isLoading();
    }

    private boolean isLive() {
        try {
            return !exoPlayerIsNull() && simpleExoPlayer.isCurrentMediaItemDynamic();
        } catch (final IndexOutOfBoundsException e) {
            // Why would this even happen =(... but lets log it anyway, better safe than sorry
            if (DEBUG) {
                Log.d(TAG, "player.isCurrentWindowDynamic() failed: ", e);
            }
            return false;
        }
    }


    @NonNull
    public Context getContext() {
        return context;
    }

    @NonNull
    public SharedPreferences getPrefs() {
        return prefs;
    }

    public MediaSessionManager getMediaSessionManager() {
        return mediaSessionManager;
    }


    public PlayerType getPlayerType() {
        return playerType;
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


    @Nullable
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public AudioReactor getAudioReactor() {
        return audioReactor;
    }

    public GestureDetectorCompat getGestureDetector() {
        return gestureDetector;
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    public boolean isVerticalVideo() {
        return isVerticalVideo;
    }

    public boolean isPopupClosing() {
        return isPopupClosing;
    }


    public boolean isSomePopupMenuVisible() {
        return isSomePopupMenuVisible;
    }

    public void setSomePopupMenuVisible(final boolean somePopupMenuVisible) {
        isSomePopupMenuVisible = somePopupMenuVisible;
    }

    public ImageButton getPlayPauseButton() {
        return binding.playPauseButton;
    }

    public View getClosingOverlayView() {
        return binding.closingOverlay;
    }

    public ProgressBar getVolumeProgressBar() {
        return binding.volumeProgressBar;
    }

    public ProgressBar getBrightnessProgressBar() {
        return binding.brightnessProgressBar;
    }

    public int getMaxGestureLength() {
        return maxGestureLength;
    }

    public ImageView getVolumeImageView() {
        return binding.volumeImageView;
    }

    public RelativeLayout getVolumeRelativeLayout() {
        return binding.volumeRelativeLayout;
    }

    public ImageView getBrightnessImageView() {
        return binding.brightnessImageView;
    }

    public RelativeLayout getBrightnessRelativeLayout() {
        return binding.brightnessRelativeLayout;
    }

    public FloatingActionButton getCloseOverlayButton() {
        return closeOverlayBinding.closeButton;
    }

    public View getLoadingPanel() {
        return binding.loadingPanel;
    }

    public TextView getCurrentDisplaySeek() {
        return binding.currentDisplaySeek;
    }

    public PlayerFastSeekOverlay getFastSeekOverlay() {
        return binding.fastSeekOverlay;
    }

    @Nullable
    public WindowManager.LayoutParams getPopupLayoutParams() {
        return popupLayoutParams;
    }

    @Nullable
    public WindowManager getWindowManager() {
        return windowManager;
    }

    public float getScreenWidth() {
        return screenWidth;
    }

    public float getScreenHeight() {
        return screenHeight;
    }

    public View getRootView() {
        return binding.getRoot();
    }

    public ExpandableSurfaceView getSurfaceView() {
        return binding.surfaceView;
    }

    public PlayQueueAdapter getPlayQueueAdapter() {
        return playQueueAdapter;
    }

    public PlayerBinding getBinding() {
        return binding;
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // SurfaceHolderCallback helpers
    //////////////////////////////////////////////////////////////////////////*/
    //region SurfaceHolderCallback helpers

    private void setupVideoSurface() {
        // make sure there is nothing left over from previous calls
        cleanupVideoSurface();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // >=API23
            surfaceHolderCallback = new SurfaceHolderCallback(context, simpleExoPlayer);
            binding.surfaceView.getHolder().addCallback(surfaceHolderCallback);
            final Surface surface = binding.surfaceView.getHolder().getSurface();
            // ensure player is using an unreleased surface, which the surfaceView might not be
            // when starting playback on background or during player switching
            if (surface.isValid()) {
                // initially set the surface manually otherwise
                // onRenderedFirstFrame() will not be called
                simpleExoPlayer.setVideoSurface(surface);
            }
        } else {
            simpleExoPlayer.setVideoSurfaceView(binding.surfaceView);
        }
    }

    private void cleanupVideoSurface() {
        // Only for API >= 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && surfaceHolderCallback != null) {
            if (binding != null) {
                binding.surfaceView.getHolder().removeCallback(surfaceHolderCallback);
            }
            surfaceHolderCallback.release();
            surfaceHolderCallback = null;
        }
    }
    //endregion

    /**
     * Get the video renderer index of the current playing stream.
     *
     * This method returns the video renderer index of the current
     * {@link MappingTrackSelector.MappedTrackInfo} or {@link #RENDERER_UNAVAILABLE} if the current
     * {@link MappingTrackSelector.MappedTrackInfo} is null or if there is no video renderer index.
     *
     * @return the video renderer index or {@link #RENDERER_UNAVAILABLE} if it cannot be get
     */
    private int getVideoRendererIndex() {
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector
                .getCurrentMappedTrackInfo();

        if (mappedTrackInfo == null) {
            return RENDERER_UNAVAILABLE;
        }

        // Check every renderer
        return IntStream.range(0, mappedTrackInfo.getRendererCount())
                // Check the renderer is a video renderer and has at least one track
                .filter(i -> !mappedTrackInfo.getTrackGroups(i).isEmpty()
                        && simpleExoPlayer.getRendererType(i) == C.TRACK_TYPE_VIDEO)
                // Return the first index found (there is at most one renderer per renderer type)
                .findFirst()
                // No video renderer index with at least one track found: return unavailable index
                .orElse(RENDERER_UNAVAILABLE);
    }
}
