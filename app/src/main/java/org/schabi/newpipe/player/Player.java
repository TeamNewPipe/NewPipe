package org.schabi.newpipe.player;

import static com.google.android.exoplayer2.PlaybackException.*;
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
import static org.schabi.newpipe.player.PlayerService.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.*;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.*;
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
import android.text.InputType;
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
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CueGroup;
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
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrAttestationException;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.bulletComments.MovieBulletCommentsPlayer;
import org.schabi.newpipe.player.event.DisplayPortion;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.CustomRenderersFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.MediaSessionManager;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.listeners.view.PlaybackSpeedClickListener;
import org.schabi.newpipe.player.listeners.view.QualityClickListener;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
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
import org.schabi.newpipe.player.resolver.QualityResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder;
import org.schabi.newpipe.sleep.SleepTimerService;
import org.schabi.newpipe.util.*;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.ExpandableSurfaceView;
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay;
import android.widget.TextView;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
    public static final String VIDEO_SEGMENTS = "video_segments";

    /*//////////////////////////////////////////////////////////////////////////
    // Time constants
    //////////////////////////////////////////////////////////////////////////*/

    public static final int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    public static final int PROGRESS_LOOP_INTERVAL_MILLIS = 1000; // 1 second
    public static final int DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final int DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds
    public static final int SEEK_OVERLAY_DURATION = 450; // 450 millis
    private static final int UNSKIP_WINDOW_MILLIS = 5000; // 5 seconds

    /*//////////////////////////////////////////////////////////////////////////
    // Other constants
    //////////////////////////////////////////////////////////////////////////*/

    private static final float[] PLAYBACK_SPEEDS = {0.1f, 0.3f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f, 5.0f, 10.0f};

    private static final int RENDERER_UNAVAILABLE = -1;
    // Cooldown between automatic recoveries from a surface-released decoder-init failure, so a
    // genuinely broken surface can't loop recover->fail forever.
    private static final long SURFACE_ERROR_RECOVERY_COOLDOWN_MS = 10_000;
    private long lastSurfaceErrorRecoveryMs;
    private static final int MAX_RETRY_COUNT = 2;

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

    public ExoPlayer simpleExoPlayer;
    private AudioReactor audioReactor;
    @Nullable private MediaSessionManager mediaSessionManager;
    private PlayerMediaSession playerMediaSession;
    @Nullable private SurfaceHolderCallback surfaceHolderCallback;

    @NonNull private final DefaultTrackSelector trackSelector;
    @NonNull private final LoadController loadController;
    @NonNull private final DefaultRenderersFactory renderFactory;

    @NonNull private final VideoPlaybackResolver videoResolver;
    @NonNull private final AudioPlaybackResolver audioResolver;

    public final PlayerServiceInterface service; //TODO try to remove and replace everything with context

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
    private boolean wasAtLiveEdge = false;
    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    private boolean fragmentIsVisible = false;
    private long startupTraceId;

    private boolean isFullscreenGestureEnabled = true;

    private List<VideoStream> availableStreams;
    private int selectedStreamIndex;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerBinding binding;

    private final Handler controlsVisibilityHandler = new Handler();
    private final Handler sabrBackoffHandler = new Handler();
    private final Runnable sabrBackoffUpdate = new Runnable() {
        @Override
        public void run() {
            updateSabrBackoffCountdown();
            if (currentState == STATE_BLOCKED
                    || (!exoPlayerIsNull() && simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_BUFFERING)) {
                sabrBackoffHandler.postDelayed(this, 250L);
            }
        }
    };

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
    private static final int POPUP_MENU_ID_AUDIO_TRACK = 99;
    private static final int POPUP_MENU_ID_DISPLAY_MODE = 109;
    private static final int POPUP_MENU_ID_ASPECT_RATIO = 119;

    private boolean isSomePopupMenuVisible = false;
    private PopupMenu qualityPopupMenu;
    private PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;
    private PopupMenu audioTrackPopupMenu;
    private PopupMenu displayModePopupMenu;

    // Aspect ratio forced by the user, 0 means "auto" (use the video's own aspect ratio)
    private float forcedAspectRatio;
    private float videoNaturalAspectRatio;

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
    public PlayerServiceEventListener fragmentListener;
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

    private Future<?> enqueueTimer;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor

    /*//////////////////////////////////////////////////////////////////////////
    // SponsorBlock
    //////////////////////////////////////////////////////////////////////////*/
    private SponsorBlockMode sponsorBlockMode = SponsorBlockMode.DISABLED;
    private int lastSkipTarget = -1;
    private SponsorBlockSegment lastSegment;
    private boolean autoSkipGracePeriod = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    /*//////////////////////////////////////////////////////////////////////////
    // Gesture
    //////////////////////////////////////////////////////////////////////////*/
    private boolean longPressSpeedingEnabled = false;
    public float longPressSpeedingFactor = 1.0f;

    private PlayerDataSource dataSource;



    public Player(@NonNull final PlayerServiceInterface service) {
        this.service = service;
        context = service.getInstance();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean isSponsorBlockEnabled = prefs.getBoolean(
                context.getString(R.string.sponsor_block_enable_key), true);

        setSponsorBlockMode(isSponsorBlockEnabled
                ? SponsorBlockMode.ENABLED
                : SponsorBlockMode.DISABLED);

        preferenceChangeListener =
                (sharedPreferences, key) -> {
                    if (context.getString(R.string.sponsor_block_enable_key).equals(key)) {
                        setSponsorBlockMode(sharedPreferences.getBoolean(key, true)
                                ? SponsorBlockMode.ENABLED
                                : SponsorBlockMode.DISABLED);
                    }
                };

        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        recordManager = new HistoryRecordManager(context);

        setupBroadcastReceiver();

        trackSelector = createTrackSelector();
        dataSource = new PlayerDataSource(context, DownloaderImpl.USER_AGENT,
                new DefaultBandwidthMeter.Builder(context).build());
        loadController = new LoadController();

        renderFactory = prefs.getBoolean(
                context.getString(
                        R.string.always_use_exoplayer_set_output_surface_workaround_key), false)
                ? new CustomRenderersFactory(context) : new DefaultRenderersFactory(context);

        if (prefs.getBoolean(context.getString(
                R.string.disable_exoplayer_media_codec_async_queueing_key), false)) {
            renderFactory.forceDisableMediaCodecAsynchronousQueueing();
        }
        renderFactory.setEnableDecoderFallback(true);

        videoResolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
        audioResolver = new AudioPlaybackResolver(context, dataSource);

        windowManager = ContextCompat.getSystemService(context, WindowManager.class);
        longPressSpeedingFactor = Float.parseFloat(prefs.getString(context.getString(R.string.speeding_playback_key), "3"));

        isFullscreenGestureEnabled = PlayerHelper.isFullscreenGestureEnabled(context);
    }

    private QualityResolver getQualityResolver() {
        return new QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
                return videoPlayerSelected()
                        ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                  final String selectedResolution,
                                                  @Nullable final String selectedCodec) {
                return ListHelper.getResolutionAndCodecIndex(
                        selectedResolution, selectedCodec, sortedVideos);
            }

            @Override
            public int getCurrentAudioQualityIndex(List<AudioStream> audioStreams) {
                return ListHelper.getDefaultAudioFormat(context, audioStreams);
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

        updateDisplayModeButtonText();

        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));
        binding.playbackSeekBar.getProgressDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY));

        final ContextThemeWrapper themeWrapper = new ContextThemeWrapper(getContext(),
                R.style.DarkPopupMenu);

        qualityPopupMenu = new PopupMenu(themeWrapper, binding.qualityTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, binding.playbackSpeed);
        captionPopupMenu = new PopupMenu(themeWrapper, binding.captionTextView);
        audioTrackPopupMenu = new PopupMenu(themeWrapper, binding.audioTrackTextView);
        displayModePopupMenu = new PopupMenu(themeWrapper, binding.resizeTextView);

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
        playerMediaSession = new PlayerMediaSession(this, simpleExoPlayer);
        mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                playerMediaSession, service.getMediaSession(),
                service.getMediaBrowserPlaybackPreparer());

        registerBroadcastReceiver();

        // Setup video view
        setupVideoSurface();

        // enable media tunneling
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.disable_media_tunneling_key), false)) {
            Log.d(TAG, "[" + Util.DEVICE_DEBUG_INFO + "] "
                    + "media tunneling disabled by user preference");
        } else if (DeviceUtils.shouldSupportMediaTunneling()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true));
        } else {
            Log.d(TAG, "[" + Util.DEVICE_DEBUG_INFO + "] does not support media tunneling");
        }
    }

    private DefaultTrackSelector createTrackSelector() {
        return new DefaultTrackSelector(context, PlayerHelper.getQualitySelector());
    }

    private void initListeners() {
        binding.qualityTextView.setOnClickListener(
                new QualityClickListener(this, qualityPopupMenu));
        binding.playbackSpeed.setOnClickListener(
                new PlaybackSpeedClickListener(this, playbackSpeedPopupMenu));

        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.captionTextView.setOnClickListener(this);
        binding.audioTrackTextView.setOnClickListener(this);
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
        binding.switchCommentsVisibility.setOnClickListener(this);
        binding.playWithKodi.setOnClickListener(this);
        binding.openInBrowser.setOnClickListener(this);
        binding.playerCloseButton.setOnClickListener(this);
        binding.switchMute.setOnClickListener(this);
        binding.sleepTimer.setOnClickListener(this);
        binding.sleepTimer.setOnLongClickListener(this);
        binding.skipButton.setOnClickListener(this);
        binding.unskipButton.setOnClickListener(this);

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
                    if(v.getPaddingLeft() != 0 || v.getPaddingTop() != 0
                            || v.getPaddingRight() != 0 || v.getPaddingBottom() != 0){
                        binding.playButtons.setPadding(
                                -v.getPaddingLeft(), -v.getPaddingTop(),-v.getPaddingRight(),-v.getPaddingBottom());
                        binding.loadingPanelWrapper.setPadding(
                                -v.getPaddingLeft(), -v.getPaddingTop(),-v.getPaddingRight(),-v.getPaddingBottom());
                    } else {
                        binding.playButtons.setPadding(0, 0, 0, 0);
                        binding.loadingPanelWrapper.setPadding(0, 0, 0, 0);
                    }

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
        final long intentStartupTraceId = PlaybackStartupTrace.fromIntent(intent);
        if (intentStartupTraceId > 0) {
            startupTraceId = intentStartupTraceId;
            PlaybackStartupTrace.mark(startupTraceId, "service_intent_received");
        }
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

//        if (intent.hasExtra(PLAYBACK_QUALITY)) {
//            setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
//        }

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

        final DefaultTrackSelector.Parameters.Builder parametersBuilder =
                trackSelector.buildUponParameters();
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, audioPlayerSelected());
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioPlayerSelected());
        final String preferredAudioLanguage = prefs.getString(
                context.getString(R.string.preferred_audio_language_key), "original");
        if ("original".equals(preferredAudioLanguage)) {
            parametersBuilder.setPreferredAudioLanguages();
        } else {
            parametersBuilder.setPreferredAudioLanguages(preferredAudioLanguage);
        }
        trackSelector.setParameters(parametersBuilder);

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
            if (shouldSeek()) {
                simpleExoPlayer.seekTo(playQueue.getIndex(), newQueue.getItem().getRecoveryPosition());
            }
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
                && PlayerHelper.isStartMainPlayerFullscreenEnabled(service.getInstance())
                && DeviceUtils.isTablet(service.getInstance())
                && videoPlayerSelected()
                && PlayerHelper.globalScreenOrientationLocked(service.getInstance())) {
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
        PlaybackStartupTrace.mark(startupTraceId, "player_init_started");
        destroyPlayer();
        initPlayer(playOnReady);

        playQueue = queue;
        playQueue.init();
        reloadPlayQueueManager();
        PlaybackStartupTrace.mark(startupTraceId, "media_source_manager_ready");

        if (playQueueAdapter != null) {
            playQueueAdapter.dispose();
        }
        playQueueAdapter = new PlayQueueAdapter(context, playQueue);
        segmentAdapter = new StreamSegmentAdapter(getStreamSegmentListener());

        simpleExoPlayer.setVolume(isMuted ? 0 : 1);
        if (playQueue != null) {
            simpleExoPlayer.setShuffleModeEnabled(playQueue.isShuffled());
            playerMediaSession = new PlayerMediaSession(this, simpleExoPlayer);
            mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                    playerMediaSession, service.getMediaSession(),
                    service.getMediaBrowserPlaybackPreparer());
        }

        setRepeatMode(repeatMode);
        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(binding.shuffleButton, simpleExoPlayer.getShuffleModeEnabled());
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence);

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

        stopSabrBackoffCountdown();
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
            mediaSessionManager = null;
        }

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
        if(bcPlayer != null){
            bcPlayer.disconnect();
            clearBCPlayer();
        }
        if(enqueueTimer != null){
            enqueueTimer.cancel(true);
        }
        dataSource.disconnectWebSocketClients();
    }

    public void destroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        
        // Close popup menus before destroying to prevent crash
        closeAllPopupMenus();
        
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

        final long newPos =  Math.max(0, Math.min(windowPos, duration));
        if(newPos > 0) {
            setRecovery(queuePos, newPos);
        }
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
            playQueueManager = new MediaSourceManager(context, this, playQueue);
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
            binding.switchCommentsVisibility.setVisibility(View.GONE);
            binding.playWithKodi.setVisibility(View.GONE);
            binding.openInBrowser.setVisibility(View.GONE);
            binding.sleepTimer.setVisibility(View.GONE);
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
            binding.switchCommentsVisibility.setVisibility(View.VISIBLE);
            binding.openInBrowser.setVisibility(View.VISIBLE);
            binding.sleepTimer.setVisibility(View.VISIBLE);
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
            binding.sleepTimer.setVisibility(View.VISIBLE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
            binding.sleepTimer.setVisibility(View.GONE);
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

        intentFilter.addAction(VideoDetailFragment.ACTION_SEEK_TO);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);

        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_ERROR);
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
            case VideoDetailFragment.ACTION_SEEK_TO:
                seekTo(intent.getIntExtra("Timestamp", 0) * 1000L);
                if(wasPlaying){
                    simpleExoPlayer.play();
                }
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
                assureCorrectAppLanguage(service.getInstance());
                if (DEBUG) {
                    Log.d(TAG, "onConfigurationChanged() called");
                }
                if (popupPlayerSelected()) {
                    updateScreenSize();
                    changePopupSize(popupLayoutParams.width);
                    checkPopupPositionBounds();
                }
                // Close popup menus to prevent crash when view is not attached after rotation
                closeAllPopupMenus();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter);
        }
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
        PicassoHelper.loadScaledDownThumbnail(context, url, true).into(new Target() {
            @Override
            public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onLoadingComplete() called with: url = [" + url
                            + "], " + "loadedImage = [" + bitmap + " -> " + bitmap.getWidth() + "x"
                            + bitmap.getHeight() + "], from = [" + from + "]");
                }

                // Picasso owns the bitmap passed to Targets and may reuse it for other requests.
                // Keep a player-owned copy because the thumbnail is also retained by the media
                // session and notification after this callback returns.
                if (bitmap.isRecycled()) {
                    Log.w(TAG, "Ignoring a recycled player thumbnail");
                    currentThumbnail = null;
                } else {
                    currentThumbnail = bitmap.copy(
                            bitmap.getConfig() == null
                                    ? Bitmap.Config.ARGB_8888 : bitmap.getConfig(), false);
                }
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
            // Close popup menus before removing from view to prevent crash
            closeAllPopupMenus();
            
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

    public void setPlaybackSpeed(final float speed) {
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

        final boolean isCurrentlyAtLiveEdge = isLiveEdge();
        if (isCurrentlyAtLiveEdge && !wasAtLiveEdge && getPlaybackSpeed() != 1.0f) {
            setPlaybackSpeed(1.0f);
        }
        wasAtLiveEdge = isCurrentlyAtLiveEdge;

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

    public void triggerProgressUpdate() {
        triggerProgressUpdate(false, false, false, false);
    }

    public void triggerProgressUpdate(final boolean isRewind) {
        triggerProgressUpdate(isRewind, false, false, false);
    }

    private void triggerProgressUpdate(final boolean isRewind,
                                       final boolean isGracedRewind,
                                       final boolean bypassSecondaryMode,
                                       final boolean isUnSkip) {
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
        final int currentProgress = Math.max((int) simpleExoPlayer.getCurrentPosition(), 0);

        if (prefs.getBoolean(context.getString(R.string.force_end_on_overtime_key), false)
                && currentItem != null
                && currentItem.getStreamType() == StreamType.VIDEO_STREAM
                && currentState != STATE_COMPLETED
                && duration > 0
                && currentProgress > duration + 3000) {
            changeState(STATE_COMPLETED);
            saveStreamProgressStateCompleted();
            isPrepared = false;
            return;
        }

        onUpdateProgress(
                currentProgress,
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage());
        triggerCheckForSponsorBlockSegments(currentProgress, isRewind,
                isGracedRewind, bypassSecondaryMode, isUnSkip);
    }

    private void triggerCheckForSponsorBlockSegments(final int currentProgress,
                                                     final boolean isRewind,
                                                     final boolean isGracedRewind,
                                                     final boolean bypassSecondaryMode,
                                                     final boolean isUnSkip) {
        if (sponsorBlockMode != SponsorBlockMode.ENABLED || !isPrepared) {
            return;
        }

        getSkippableSponsorBlockSegment(currentProgress).ifPresent(sponsorBlockSegment -> {

            final boolean showManualButtons = prefs.getBoolean(
                    context.getString(R.string.sponsor_block_show_manual_skip_key), false);
            // per-sponsorBlockSegment category skip setting
            final SponsorBlockSecondaryMode secondaryMode = getSecondaryMode(sponsorBlockSegment);

            // show/hide manual skip buttons
            if (showManualButtons && secondaryMode != SponsorBlockSecondaryMode.HIGHLIGHT) {
                if (currentProgress < sponsorBlockSegment.endTime
                        && currentProgress > sponsorBlockSegment.startTime) {
                    showAutoSkip();
                } else {
                    hideAutoSkip();
                }

                if (currentProgress > sponsorBlockSegment.startTime
                        && currentProgress < sponsorBlockSegment.endTime + UNSKIP_WINDOW_MILLIS) {
                    showAutoUnskip();
                } else {
                    hideAutoUnskip();
                }
            }

            if (DEBUG) {
                Log.d("SPONSOR_BLOCK", "Un-skip grace: isGracedRewind = "
                        + isGracedRewind + ", autoSkipGracePeriod = " + autoSkipGracePeriod);
            }

            // temporarily pause auto skipping
            // bypass grace when this is an un-skip request
            if (!isGracedRewind) {
                if (autoSkipGracePeriod) {
                    return;
                }
            } else {

                autoSkipGracePeriod = true;
            }

            // prevent skip looping in unship window
            if (lastSegment == sponsorBlockSegment && !bypassSecondaryMode) {
                return;
            }

            // Do not skip if highlight mode. Do not skip if manual mode + no explicit bypass
            if (secondaryMode == SponsorBlockSecondaryMode.DISABLED
                    || secondaryMode == SponsorBlockSecondaryMode.HIGHLIGHT
                    || (secondaryMode == SponsorBlockSecondaryMode.MANUAL
                    && !bypassSecondaryMode)) {
                return;
            }

            int skipTarget = isRewind
                    ? (int) Math.ceil((sponsorBlockSegment.startTime)) - 1
                    : (int) Math.ceil((sponsorBlockSegment.endTime));

            if (skipTarget < 0) {
                skipTarget = 0;
            }

            // temporarily force EXACT seek parameters to prevent infinite skip looping
            final SeekParameters seekParams = simpleExoPlayer.getSeekParameters();
            simpleExoPlayer.setSeekParameters(SeekParameters.EXACT);

            seekTo(skipTarget);

            simpleExoPlayer.setSeekParameters(seekParams);
            if (!isRewind || isGracedRewind) {
                // DO NOT TRACK for non-graced rewinds to work, BUT always track for graced
                lastSegment = sponsorBlockSegment;
            }

            if (isUnSkip) {
                return;
            }

            final boolean canShowNotifications = prefs.getBoolean(
                    context.getString(R.string.sponsor_block_notifications_key), false);

            if (canShowNotifications) {
                final String toastText =
                        SponsorBlockHelper.convertCategoryToSkipMessage(
                                context, sponsorBlockSegment.category);

                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
            }

            if (DEBUG) {
                Log.d("SPONSOR_BLOCK", "Skipped segment: currentProgress = ["
                        + currentProgress + "], skipped to = [" + skipTarget + "]");
            }
        });
    }

    public void showAutoUnskip() {
        binding.unskipButton.setVisibility(View.VISIBLE);
    }

    public void hideAutoUnskip() {
        binding.unskipButton.setVisibility(View.GONE);
    }

    public void showAutoSkip() {
        binding.skipButton.setVisibility(View.VISIBLE);
    }
    public void hideAutoSkip() {
        binding.skipButton.setVisibility(View.GONE);
    }
    public void onUnskipClicked() {
        if (DEBUG) {
            Log.d(TAG, "onUnskipClicked() called");
        }
        toggleUnskip();
    }

    public void onSkipClicked() {
        if (DEBUG) {
            Log.d(TAG, "onSkipClicked() called");
        }
        toggleSkip();
    }
    public void toggleUnskip() {
        triggerProgressUpdate(true, true, true, true);
    }

    public void toggleSkip() {
        autoSkipGracePeriod = false;
        triggerProgressUpdate(false, true, true, false);
    }

    private Disposable getProgressUpdateDisposable() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                        AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(false),
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
        if (!seekbarPreviewThumbnailHolder.getBitmapAt(progress).isEmpty()) {
            SeekbarPreviewThumbnailHelper
                    .tryResizeAndSetSeekbarPreviewThumbnail(
                            getContext(),
                            seekbarPreviewThumbnailHolder.getBitmapAt(progress).get(),
                            binding.currentSeekbarPreviewThumbnail,
                            binding.subtitleView::getWidth);
        }


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
        final boolean showQueue = true;
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
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
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
                PlaybackStartupTrace.mark(startupTraceId, "player_ready");
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                if (Build.VERSION.SDK_INT >= 37) {
                    NotificationUtil.getInstance()
                            .createNotificationAndStartForeground(this, service.getInstance());
                }
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
        if (!isLoading) {
            if(currentState == STATE_PAUSED && isProgressLoopRunning()){
                stopProgressLoop();
            }
        } else {
            if(!isProgressLoopRunning()){
                startProgressLoop();
            }
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
        PlaybackStartupTrace.mark(startupTraceId, "media_source_attached");
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
                initBCPlayer();
                startBCPlayer();
                break;
            case STATE_BUFFERING:
                onBuffering();
                break;
            case STATE_PAUSED:
                if(enqueueTimer != null){
                    enqueueTimer.cancel(true);
                }
                onPaused();
                pauseBCPlayer();
                break;
            case STATE_PAUSED_SEEK:
                if(enqueueTimer != null){
                    enqueueTimer.cancel(true);
                }
                onPausedSeek();
                pauseBCPlayer();
                break;
            case STATE_COMPLETED:
                onCompleted();
                completeBCPlayer();
                break;
        }
        notifyPlaybackUpdateToListeners();
    }

    private MovieBulletCommentsPlayer bcPlayer = null;

    private Duration getCurrentPositionDuration() {
        if (currentItem == null) {
            return null;
        }
        return Duration.ofMillis(simpleExoPlayer.getCurrentPosition());
    }

    private Duration getDurationInDuration() {
        if (currentItem == null) {
            return null;
        }
        return Duration.ofMillis(simpleExoPlayer.getDuration());
    }

    /*////////////////////////////////////////////////
     * BulletCommentsPlayer
     *////////////////////////////////////////////////
    //region BulletCommentsPlayer
    private void initBCPlayer() {
        try {
            if (currentMetadata != null && NewPipe.getService(currentMetadata.getServiceId())
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(StreamingService.ServiceInfo.MediaCapability.BULLET_COMMENTS)
                    && !audioPlayerSelected()) {
                if(bcPlayer!= null){
                    if(utils.DetimestampedEqual(bcPlayer.getUrl(), currentMetadata.getStreamUrl())){
                        return ;
                    }
                    bcPlayer.disconnect();
                }
                clearBCPlayer();
                bcPlayer = new MovieBulletCommentsPlayer(binding.bulletCommentsView);
                bcPlayer.setInitialData(currentMetadata.getServiceId(),
                        currentMetadata.getStreamUrl());
                bcPlayer.init();
                Log.d(TAG, "BulletCommentsView initialized.");
            } else {
                Log.i(TAG, "Current service does not have MediaCapability of BULLET_COMMENTS"
                        + ", skipping BulletCommentsView initialization.");
            }
        } catch (final ExtractionException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        isBCPlayerVisible = prefs.getBoolean("isBCPlayerVisible", false);
        Log.i(TAG, "BulletCommentPlayer initial visibility: " + isBCPlayerVisible);
        if (bcPlayer == null) {
            // If set to INVISIBLE, the space remains.
            binding.switchCommentsVisibility.setVisibility(View.GONE);
        } else {
            binding.switchCommentsVisibility.setVisibility(View.VISIBLE);
            binding.switchCommentsVisibility.setImageDrawable(isBCPlayerVisible?AppCompatResources.getDrawable(context,
                    R.drawable.ic_bullet_comment_enabled):AppCompatResources.getDrawable(context,
                    R.drawable.ic_bullet_comment_disabled));
        }
    }

    private Disposable bcPlayerDrawCommentsObservable = null;

    public void startBCPlayer() {
        if (bcPlayer == null | bcPlayerDrawCommentsObservable != null | !isBCPlayerVisible) {
            return;
        }
        bcPlayer.start(getCurrentPositionDuration());
        bcPlayerDrawCommentsObservable = Observable.interval(
                        bcPlayer.INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .observeOn(AndroidSchedulers.mainThread())
                .map(s -> {
                    Duration ret = getCurrentPositionDuration();
                    if(currentItem!= null && currentItem.getStartAt() != -1 && currentItem.getStreamType() == StreamType.LIVE_STREAM){
                        ret = Duration.ofMillis(new Date().getTime() - currentItem.getStartAt());
                    }
                    if(ret == null){
                        return Duration.ofMillis(-1);
                    }
                    return ret;
                })
                .subscribe(s ->  {
                            if(isPlaying() && !audioPlayerSelected()){
                                bcPlayer.drawComments(s.plus(bcPlayer.INTERVAL));
                            }
                        },
                        e -> Log.e(TAG, Log.getStackTraceString(e))
                );
        Log.d(TAG, "BulletCommentsView started.");
    }

    private void completeBCPlayer() {
        if (bcPlayer == null | !isBCPlayerVisible | currentMetadata == null) {
            return;
        }
        bcPlayer.complete(Objects.requireNonNull(getDurationInDuration()));
        clearBCPlayer();
        Log.d(TAG, "BulletCommentsView completed.");
    }

    public void pauseBCPlayer() {
        if (bcPlayer == null) {
            return;
        }
        if (bcPlayerDrawCommentsObservable != null) {
            bcPlayerDrawCommentsObservable.dispose();
            bcPlayerDrawCommentsObservable = null;
            Log.d(TAG, "BulletCommentsView observable disposed.");
        }
        bcPlayer.pause();
        Log.d(TAG, "BulletCommentsView paused.");
    }

    private void clearBCPlayer() {
        if (bcPlayer == null) {
            return;
        }
        if (bcPlayerDrawCommentsObservable != null) {
            bcPlayerDrawCommentsObservable.dispose();
            bcPlayerDrawCommentsObservable = null;
            Log.d(TAG, "BulletCommentsView observable disposed.");
        }
        bcPlayer.clear();
        Log.d(TAG, "BulletCommentsView cleared.");
    }

    private boolean isBCPlayerVisible = false;

    private void onSwitchBCPlayerVisibilityClicked() {
        isBCPlayerVisible = !isBCPlayerVisible;
        prefs.edit().putBoolean("isBCPlayerVisible", isBCPlayerVisible).apply();
        binding.switchCommentsVisibility.setImageDrawable(isBCPlayerVisible?AppCompatResources.getDrawable(context,
                R.drawable.ic_bullet_comment_enabled):AppCompatResources.getDrawable(context,
                R.drawable.ic_bullet_comment_disabled));
        Log.i(TAG, "BulletCommentPlayer visibility changed to " + isBCPlayerVisible);
        if (isBCPlayerVisible) {
            startBCPlayer();
        } else {
            clearBCPlayer();
        }
    }

    //endregion BulletCommentsPlayer

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
        startSabrBackoffCountdown();
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
        stopSabrBackoffCountdown();
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        updateStreamRelatedViews();

        if(getCurrentStreamInfo().isPresent()){
            StreamInfo streamInfo = getCurrentStreamInfo().get();
            if(streamInfo.isRoundPlayStream() && (
                    enqueueTimer == null || enqueueTimer.isDone() || enqueueTimer.isCancelled())){
                enqueueTimer = executor.schedule(() -> maybeAutoQueueNextStream(streamInfo, true),
                        Math.max(simpleExoPlayer.getDuration()
                                - simpleExoPlayer.getCurrentPosition() - 1000, 0), MILLISECONDS);
            }
        }

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
        startSabrBackoffCountdown();

        binding.getRoot().setKeepScreenOn(true);
        if (NotificationUtil.getInstance().shouldUpdateBufferingSlot()) {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }
    }

    private void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }
        stopSabrBackoffCountdown();
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
            NotificationUtil.getInstance().cancelNotificationAndStopForeground(service.getInstance());
        } else {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    private void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }

        stopSabrBackoffCountdown();
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(true);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onCompleted() {
        if (DEBUG) {
            Log.d(TAG, "onCompleted() called" + (playQueue == null ? ". playQueue is null" : ""));
        }
        stopSabrBackoffCountdown();
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

    private void startSabrBackoffCountdown() {
        SabrBackoffCoordinator.getInstance().setPlayerBuffering(context, true);
        sabrBackoffHandler.removeCallbacks(sabrBackoffUpdate);
        sabrBackoffUpdate.run();
    }

    private void stopSabrBackoffCountdown() {
        SabrBackoffCoordinator.getInstance().setPlayerBuffering(context, false);
        sabrBackoffHandler.removeCallbacks(sabrBackoffUpdate);
        if (binding != null) {
            binding.sabrBackoffCountdown.setVisibility(View.GONE);
        }
    }

    private void updateSabrBackoffCountdown() {
        if (binding == null) {
            return;
        }
        final long remainingMs = SabrBackoffCoordinator.getInstance().getRemainingMs();
        if (!fragmentIsVisible || remainingMs <= 0L) {
            binding.sabrBackoffCountdown.setVisibility(View.GONE);
            return;
        }
        final int seconds = SabrBackoffCoordinator.remainingSeconds(remainingMs);
        binding.sabrBackoffCountdown.setText(context.getString(
                R.string.sabr_backoff_notification_content, seconds));
        binding.sabrBackoffCountdown.setVisibility(View.VISIBLE);
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

    public void setRepeatMode(@RepeatMode final int repeatMode) {
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
        if (playerMediaSession != null) {
            playerMediaSession.refresh();
        }
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

    public void onScreenRotationButtonClicked() {
        if (!isVerticalVideo
                || (service.isLandscape() && globalScreenOrientationLocked(context))) {
            fragmentListener.onScreenRotationButtonClicked();
        } else {
            toggleFullscreen();
        }
    }




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
                return; // we still have the same metadata, no need to do anything
            }
            final StreamInfo previousInfo = Optional.ofNullable(currentMetadata)
                    .flatMap(MediaItemTag::getMaybeStreamInfo).orElse(null);
            currentMetadata = tag;

            if (!currentMetadata.getErrors().isEmpty()) {
                // new errors might have been added even if previousInfo == tag.getMaybeStreamInfo()
                final ErrorInfo errorInfo = new ErrorInfo(
                        currentMetadata.getErrors(),
                        UserAction.PLAY_STREAM,
                        "Loading failed for [" + currentMetadata.getTitle()
                                + "]: " + currentMetadata.getStreamUrl(),
                        currentMetadata.getServiceId());
                ErrorUtil.createNotification(context, errorInfo);
            }

            currentMetadata.getMaybeStreamInfo().ifPresent(info -> {
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onEvents() update stream info: " + info.getName());
                }
                if (previousInfo == null || !previousInfo.getUrl().equals(info.getUrl())) {
                    // only update with the new stream info if it has actually changed
                    updateMetadataWith(info);
                }
            });
        });
    }

    @Override
    public void onTracksChanged(@NonNull final Tracks tracks) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onTracksChanged(), "
                    + "track group size = " + tracks.getGroups().size());
        }
        if(enqueueTimer != null){
            enqueueTimer.cancel(true);
        }
        onTextTracksChanged(tracks);
        onAudioTracksChanged();
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
        PlaybackStartupTrace.finish(startupTraceId);
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }

    @Override
    public void onCues(@NonNull final CueGroup cueGroup) {
        binding.subtitleView.setCues(cueGroup.cues);
    }

    public void onPrepare() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.prepare();
        }
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

        if (containsSabrAttestationException(error)) {
            // Attestation retries are handled inside the media bridge. An attestation exception
            // reaching the player has exhausted those recovery paths and must remain terminal
            // instead of rebuilding the source with a fresh retry budget.
            onPlaybackShutdown();
        } else {

            switch (error.errorCode) {
            case ERROR_CODE_BEHIND_LIVE_WINDOW:
                isCatchableException = true;
                simpleExoPlayer.seekToDefaultPosition();
                simpleExoPlayer.prepare();
                // Inform the user that we are reloading the stream by
                // switching to the buffering state
                onBuffering();
                break;
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
                    onBufferingFailed();
                }
                break;
            case ERROR_CODE_IO_UNSPECIFIED:
                if (error.getCause().getMessage() != null
                        && error.getCause().getMessage().contains("Response code: 403")) {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity())
                                .setTitle(R.string.network_error)
                                .setMessage(R.string.ip_blocked_summary)
                                .setPositiveButton(R.string.ok, (dialog, which) -> {
                                    // Handle "Yes" click
                                });
                        builder.show();
                    } catch (Exception e) {
                        e.printStackTrace(); // when there is no context, e.g. background playing
                    }
                    onPlaybackShutdown();
                    break;
                }
            case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
            case ERROR_CODE_IO_BAD_HTTP_STATUS:
            case ERROR_CODE_TIMEOUT:
            case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
            case ERROR_CODE_UNSPECIFIED:
                setRecovery();
                reloadPlayQueueManager();
                break;
case ERROR_CODE_DECODER_INIT_FAILED: {
                final boolean surfaceReleased = isSurfaceReleasedError(error);
                if (surfaceReleased && System.currentTimeMillis() - lastSurfaceErrorRecoveryMs
                        > SURFACE_ERROR_RECOVERY_COOLDOWN_MS) {
                    // The decoder died because the video surface was released under it (screen off /
                    // surface lifecycle race), NOT because the device lacks a decoder. Recover like a
                    // stream error instead of killing playback with the misleading "no hardware
                    // decoder, use VLC" dialog. Cooldown-bounded so a genuinely broken surface still
                    // falls through to shutdown below.
                    lastSurfaceErrorRecoveryMs = System.currentTimeMillis();
                    setRecovery();
                    reloadPlayQueueManager();
                    break;
                }
                // Only show the dialog when a hosting activity exists AND the failure is really
                // about decoding capability. getParentActivity() is null in the background/popup
                // player, and AlertDialog.Builder(null) NPEs -> the app crashed on a decoder-init
                // failure while backgrounded. The error notification below still surfaces it.
                final AppCompatActivity parentActivity = getParentActivity();
                if (parentActivity != null && !surfaceReleased) {
                    new AlertDialog.Builder(parentActivity)
                            .setTitle(R.string.decoder_init_failure)
                            .setMessage(R.string.unable_to_decode_summary)
                            .setPositiveButton(R.string.ok, (dialog, which) -> { })
                            .show();
                }
                onPlaybackShutdown();
                break;
            }
            default:
                // API, remote and renderer errors belong here:
                onPlaybackShutdown();
                break;
            }
        }

        if (!isCatchableException) {
            showMediaCodecWorkaroundHint(error);
            createErrorNotification(error);
        }

        if (fragmentListener != null) {
            fragmentListener.onPlayerError(error, isCatchableException);
        }
    }

    private static boolean containsSabrAttestationException(@NonNull final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SabrAttestationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void showMediaCodecWorkaroundHint(@NonNull final PlaybackException error) {
        if (error.errorCode != ERROR_CODE_DECODING_FAILED
                && error.errorCode != ERROR_CODE_FAILED_RUNTIME_CHECK) {
            return;
        }
        try {
            final String stackTrace = Log.getStackTraceString(error);
            final boolean hasSetOutputSurface = stackTrace.contains("setOutputSurface");
            final boolean hasAsyncCodecAdapter =
                    stackTrace.contains("AsynchronousMediaCodecAdapter")
                            || stackTrace.contains("AsynchronousMediaCodecBufferEnqueuer");
            final int message;
            if (hasSetOutputSurface && !prefs.getBoolean(context.getString(
                    R.string.always_use_exoplayer_set_output_surface_workaround_key), false)) {
                message = R.string.media_codec_surface_workaround_hint;
            } else if (hasAsyncCodecAdapter && !prefs.getBoolean(context.getString(
                    R.string.disable_exoplayer_media_codec_async_queueing_key), false)) {
                message = R.string.media_codec_async_workaround_hint;
            } else {
                return;
            }

            new AlertDialog.Builder(getParentActivity())
                    .setTitle(R.string.media_codec_workaround_hint_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * True when a decoder-init failure was caused by the video surface being released under the
     * codec (screen off / surface lifecycle race) rather than by a missing/unsupported decoder.
     */
    private static boolean isSurfaceReleasedError(@NonNull final PlaybackException error) {
        Throwable cause = error.getCause();
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            final String message = cause.getMessage();
            if (cause instanceof IllegalArgumentException && message != null
                    && message.toLowerCase(Locale.US).contains("surface")) {
                return true;
            }
        }
        return false;
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
        if (currentState != STATE_PAUSED_SEEK) {
            binding.playbackSeekBar.setProgress(currentProgress);
        }
        // YouTube livestreams use DASH and getCurrentPosition() works correctly
        // Other services (HLS) need startAt hack to show correct time
        if (currentItem != null
                && StreamTypeUtil.isLiveStream(currentItem.getStreamType())
                && currentMetadata != null
                && currentMetadata.getServiceId() != YouTube.getServiceId()
                && currentItem.getStartAt() != -1) {
            binding.playbackCurrentTime.setText(getTimeString((int) (new Date().getTime() - currentItem.getStartAt())));
        } else {
            binding.playbackCurrentTime.setText(getTimeString(currentProgress));
        }
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

            if (item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET && shouldSeek()) {
                simpleExoPlayer.seekTo(currentPlayQueueIndex, item.getRecoveryPosition());
                playQueue.unsetRecovery(currentPlayQueueIndex);
            } else {
                simpleExoPlayer.seekToDefaultPosition(currentPlayQueueIndex);
            }
        }
    }

    public boolean shouldSeek() {
        return !prefs.getBoolean(context.getString(R.string.always_start_from_beginning_key), false);
    }

    private boolean isCurrentStreamSabr() {
        return getCurrentStreamInfo().map(info -> {
            for (final VideoStream s : info.getVideoOnlyStreams()) {
                if (s.getDeliveryMethod() == DeliveryMethod.SABR) {
                    return true;
                }
            }
            for (final VideoStream s : info.getVideoStreams()) {
                if (s.getDeliveryMethod() == DeliveryMethod.SABR) {
                    return true;
                }
            }
            for (final AudioStream s : info.getAudioStreams()) {
                if (s.getDeliveryMethod() == DeliveryMethod.SABR) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
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
     *
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

        if (currentState == STATE_COMPLETED && playQueue != null && playQueue.getItem() != null &&
                playQueue.getItem().getRecoveryPosition() / 1000 >= playQueue.getItem().getDuration() - 5) {
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
        triggerProgressUpdate(true);
    }

    public void fastRewind() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(-retrieveSeekDurationFromPreferences(this));
        if (prefs.getBoolean(
                context.getString(R.string.sponsor_block_graced_rewind_key), false)) {
            triggerProgressUpdate(true, true, false, false);
            return;
        }

        destroyUnskipVars(); // destroy, else rewind into segment won't skip
        triggerProgressUpdate(true);
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

        // a forced aspect ratio is a per-video correction, don't carry it over to the next one;
        // it temporarily forced the resize mode to Fit, so restore the persisted resize mode
        if (forcedAspectRatio > 0) {
            forcedAspectRatio = 0.0f;
            setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(this));
        }

        initThumbnail(info.getThumbnailUrl());
        registerStreamViewed();
        updateStreamRelatedViews();
        showHideKodiButton();
        initBCPlayer(); // TODO: bullet comments may be reset unexpectedly for round play streams
        startBCPlayer();

        binding.titleTextView.setText(info.getName());
        binding.channelTextView.setText(info.getUploaderName());

        this.seekbarPreviewThumbnailHolder.resetFrom(this.getContext(), info.getPreviewFrames());

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);

        mediaSessionManager.setPlayer(this);

        notifyMetadataUpdateToListeners();

        onAudioTracksChanged();

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

        onMarkSeekbarRequested(info);
    }

    private void updateMetadataWith(@NonNull final StreamInfo streamInfo) {
        if (exoPlayerIsNull()) {
            return;
        }

        maybeAutoQueueNextStream(streamInfo, false);
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

    private void maybeAutoQueueNextStream(@NonNull final StreamInfo info, boolean forceEnqueue) {
        if (playQueue == null) {
            return;
        }
        List<StreamInfoItem> partitions = info.getPartitions();
        if(partitions.size() > 1
                && playQueue.getStreams().stream()
                .map(result -> result.getUrl().split("p="))
                .filter(parts -> parts.length == 2)
                .map(parts -> new String[]{parts[0], parts[1]})
                .reduce((a, b) -> Integer.parseInt(a[1]) + 1 == Integer.parseInt(b[1]) && a[0].equals(b[0]) ? b : new String[]{"", "-1"})
                .filter(result -> !Arrays.equals(result, new String[]{"", "-1"}))
                .isPresent()
                && playQueue.getIndex() == playQueue.size() - 1
        ){
            int p = Integer.parseInt(info.getUrl().split(Pattern.quote("?p="))[1].split("&")[0]);
            if(partitions.size() > p){
                playQueue.append(getAutoQueuedSinglePlayQueue(partitions.get(p)).getStreams());
            }
        }
        if (!forceEnqueue && (playQueue.getIndex() != playQueue.size() - 1
                || getRepeatMode() != REPEAT_MODE_OFF
                || !PlayerHelper.isAutoQueueEnabled(context))) {
            return;
        }

        boolean dontAutoQueueLongVideos = prefs.getBoolean(
            context.getString(R.string.dont_auto_queue_long_key), true
        );

        // auto queue when starting playback on the last item when not repeating
        final PlayQueue autoQueue = PlayerHelper.autoQueueOf(info,
                playQueue.getStreams(), dontAutoQueueLongVideos);
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
        PlaybackStartupTrace.mark(startupTraceId, "resolver_started");
        final long initialPositionMs = shouldSeek()
                && item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET
                ? item.getRecoveryPosition() : 0;
        final MediaSource resolved;
        if (audioPlayerSelected()) {
            resolved = Optional.ofNullable(audioResolver.resolve(info))
                    .orElse(videoResolver.resolve(info, initialPositionMs));
            PlaybackStartupTrace.mark(startupTraceId, "resolver_finished");
            return resolved;
        }

        if (isAudioOnly && videoResolver.getStreamSourceType().orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
                == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY) {
            // If the current info has only video streams with audio and if the stream is played as
            // audio, we need to use the audio resolver, otherwise the video stream will be played
            // in background.
            resolved = Optional.ofNullable(audioResolver.resolve(info))
                    .orElse(videoResolver.resolve(info, initialPositionMs));
            PlaybackStartupTrace.mark(startupTraceId, "resolver_finished");
            return resolved;
        }

        // Even if the stream is played in background, we need to use the video resolver if the
        // info played is separated video-only and audio-only streams; otherwise, if the audio
        // resolver was called when the app was in background, the app will only stream audio when
        // the user come back to the app and will never fetch the video stream.
        // Note that the video is not fetched when the app is in background because the video
        // renderer is fully disabled (see useVideoSource method), except for HLS streams
        // (see https://github.com/google/ExoPlayer/issues/9282).
        resolved = videoResolver.resolve(info, initialPositionMs);
        PlaybackStartupTrace.mark(startupTraceId, "resolver_finished");
        return resolved;
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
            case POST_LIVE_STREAM:
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
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, videoStream.getCodec().toUpperCase().split("\\.")[0] + " " + videoStream.resolution);
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
        final int textRendererIndex = getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        // If user prefers to show no caption, then disable the renderer.
        // Otherwise, DefaultTrackSelector may automatically find an available caption
        // and display that.
        final String userPreferredLanguage =
                prefs.getString(context.getString(R.string.caption_user_set_key), null);
        if (userPreferredLanguage == null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setRendererDisabled(textRendererIndex, true));
            return;
        }

        // Only set preferred language if it does not match the user preference,
        // otherwise there might be an infinite cycle at onTextTracksChanged.
        final List<String> selectedPreferredLanguages =
                trackSelector.getParameters().preferredTextLanguages;
        if (!selectedPreferredLanguages.contains(userPreferredLanguage)) {
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
            setRecovery();
            setSelectedStream(availableStreams.get(menuItemIndex));
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

    private void setSelectedStream(@NonNull final VideoStream stream) {
        videoResolver.setSelectedStream(stream);
    }

    private void closeAllPopupMenus() {
        if (qualityPopupMenu != null) {
            qualityPopupMenu.dismiss();
        }
        if (playbackSpeedPopupMenu != null) {
            playbackSpeedPopupMenu.dismiss();
        }
        if (captionPopupMenu != null) {
            captionPopupMenu.dismiss();
        }
        if (displayModePopupMenu != null) {
            displayModePopupMenu.dismiss();
        }
        isSomePopupMenuVisible = false;
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
            binding.subtitleView.setFractionalTextSize(
                    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale);
        }
        binding.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(captionStyle);
    }

    private void onTextTracksChanged(@NonNull final Tracks currentTrack) {
        if (binding == null) {
            return;
        }

        final boolean trackTypeTextSupported = !currentTrack.containsType(C.TRACK_TYPE_TEXT)
                || currentTrack.isTypeSupported(C.TRACK_TYPE_TEXT, false);
        if (trackSelector.getCurrentMappedTrackInfo() == null || !trackTypeTextSupported) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        // Extract all loaded languages
        final List<Tracks.Group> textTracks = currentTrack
                .getGroups()
                .stream()
                .filter(trackGroupInfo -> C.TRACK_TYPE_TEXT == trackGroupInfo.getType())
                .collect(Collectors.toList());
        final List<String> availableLanguages = textTracks.stream()
                .map(Tracks.Group::getMediaTrackGroup)
                .filter(textTrack -> textTrack.length > 0)
                .map(textTrack -> textTrack.getFormat(0).language)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Find selected text track
        final Optional<Format> selectedTracks = textTracks.stream()
                .filter(Tracks.Group::isSelected)
                .filter(info -> info.getMediaTrackGroup().length >= 1)
                .map(info -> info.getMediaTrackGroup().getFormat(0))
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

    private void onAudioTracksChanged() {
        if (binding == null) {
            return;
        }

        final Optional<StreamInfo> optStreamInfo = getCurrentStreamInfo();
        if (!optStreamInfo.isPresent()) {
            binding.audioTrackTextView.setVisibility(View.GONE);
            return;
        }

        final StreamInfo streamInfo = optStreamInfo.get();
        final List<AudioStream> audioStreams = ListHelper.getFilteredAudioStreams(
                context, streamInfo.getAudioStreams());

        if (audioStreams.size() <= 1) {
            binding.audioTrackTextView.setVisibility(View.GONE);
            return;
        }

        buildAudioTrackMenu(audioStreams);

        final String currentAudioTrack = videoResolver.getAudioTrack();
        final int selectedIndex;
        if (currentAudioTrack != null) {
            int idx = -1;
            for (int i = 0; i < audioStreams.size(); i++) {
                if (currentAudioTrack.equals(audioStreams.get(i).getAudioTrackId())) {
                    idx = i;
                    break;
                }
            }
            selectedIndex = idx >= 0 ? idx : 0;
        } else {
            selectedIndex = ListHelper.getDefaultAudioFormat(context, audioStreams);
        }

        if (selectedIndex >= 0 && selectedIndex < audioStreams.size()) {
            final AudioStream selected = audioStreams.get(selectedIndex);
            binding.audioTrackTextView.setText(
                    selected.getAudioTrackName() != null
                            ? selected.getAudioTrackName()
                            : (selected.getAudioLocale() != null ? selected.getAudioLocale() : "Unknown"));
        }

        binding.audioTrackTextView.setVisibility(View.VISIBLE);
    }

    private void buildAudioTrackMenu(@NonNull final List<AudioStream> audioStreams) {
        if (audioTrackPopupMenu == null) {
            return;
        }
        audioTrackPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_AUDIO_TRACK);
        audioTrackPopupMenu.setOnDismissListener(this);

        for (int i = 0; i < audioStreams.size(); i++) {
            final AudioStream audioStream = audioStreams.get(i);
            final String trackName = audioStream.getAudioTrackName() != null
                    ? audioStream.getAudioTrackName()
                    : (audioStream.getAudioLocale() != null ? audioStream.getAudioLocale() : "Unknown");
            final MenuItem audioTrackItem = audioTrackPopupMenu.getMenu().add(
                    POPUP_MENU_ID_AUDIO_TRACK, i, Menu.NONE, trackName);
            final String trackId = audioStream.getAudioTrackId();
            audioTrackItem.setOnMenuItemClickListener(menuItem -> {
                setAudioTrack(trackId);
                return true;
            });
        }
    }

    private void setAudioTrack(@Nullable final String audioTrackId) {
        saveStreamProgressState();
        setRecovery();
        videoResolver.setAudioTrack(audioTrackId);
        audioResolver.setAudioTrack(audioTrackId);
        if (isCurrentStreamSabr() && !exoPlayerIsNull()) {
            final DefaultTrackSelector.Parameters.Builder parameters =
                    trackSelector.buildUponParameters();
            if (audioTrackId == null || audioTrackId.isEmpty()) {
                parameters.setPreferredAudioLanguages();
            } else {
                parameters.setPreferredAudioLanguages(
                        audioTrackId.split("[._-]", 2)[0]);
            }
            trackSelector.setParameters(parameters);
            return;
        }
        reloadPlayQueueManager();
    }

    private void onAudioTrackClicked() {
        if (DEBUG) {
            Log.d(TAG, "onAudioTrackClicked() called");
        }
        audioTrackPopupMenu.show();
        isSomePopupMenuVisible = true;
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
            onDisplayModeClicked();
        } else if (v.getId() == binding.captionTextView.getId()) {
            onCaptionClicked();
        } else if (v.getId() == binding.audioTrackTextView.getId()) {
            onAudioTrackClicked();
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
        } else if (v.getId() == binding.switchCommentsVisibility.getId()) {
            onSwitchBCPlayerVisibilityClicked();
        } else if (v.getId() == binding.playWithKodi.getId()) {
            onPlayWithKodiClicked();
        } else if (v.getId() == binding.openInBrowser.getId()) {
            onOpenInBrowserClicked();
        } else if (v.getId() == binding.sleepTimer.getId()) {
            onSleepTimerClicked();
        } else if (v.getId() == binding.fullScreenButton.getId()) {
            setRecovery();
            if (popupPlayerSelected()) {
                // Clean up popup properly before switching to main player
                service.stopService();
            }
            NavigationHelper.playOnMainPlayer(context, playQueue, true);
            return;
        } else if (v.getId() == binding.screenRotationButton.getId()) {
            // Only if it's not a vertical video or vertical video but in landscape with locked
            // orientation a screen orientation can be changed automatically
            try {
                Thread.sleep(50); // don't know why we need this from 4.5.0, but without this it act strange when exiting
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            onScreenRotationButtonClicked();
        } else if (v.getId() == binding.switchMute.getId()) {
            onMuteUnmuteButtonClicked();
        } else if (v.getId() == binding.playerCloseButton.getId()) {
            context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER));
            service.stopService();
        } else if (v.getId() == binding.skipButton.getId()) {
            onSkipClicked();
        } else if (v.getId() == binding.unskipButton.getId()) {
            onUnskipClicked();
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
        } else if (v.getId() == binding.sleepTimer.getId()) {
            onSleepTimerLongClicked();
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

    private void onSleepTimerClicked() {
        AppCompatActivity activity = getParentActivity();
        assert activity != null;
        Intent serviceIntent = new Intent(activity, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_START_TIMER);
        // get time from shared preferences
        int time = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(activity).getString(
                activity.getString(R.string.sleep_timer_length_key), String.valueOf(15)
        ));
        serviceIntent.putExtra("timeInMillis", time * 60000); // 60 seconds
        activity.startService(serviceIntent);
        binding.sleepTimer.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_timer));
    }

    private void onSleepTimerLongClicked() {
        AppCompatActivity activity = getParentActivity();
        assert activity != null;
        Intent serviceIntent = new Intent(activity, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_STOP_TIMER);
        activity.startService(serviceIntent);
        binding.sleepTimer.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_timer_off));
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
        updateDisplayModeButtonText();
    }

    /**
     * Updates the display-mode button label: the forced aspect ratio takes precedence over the
     * resize mode, since selecting an aspect ratio is what the user sees applied.
     */
    private void updateDisplayModeButtonText() {
        binding.resizeTextView.setText(forcedAspectRatio > 0
                ? PlayerHelper.aspectRatioNameOf(forcedAspectRatio)
                : PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));
    }

    void onDisplayModeClicked() {
        if (DEBUG) {
            Log.d(TAG, "onDisplayModeClicked() called");
        }
        if (displayModePopupMenu == null) {
            return;
        }
        // rebuild on every open so the checkmark reflects the current resize mode / forced ratio
        buildDisplayModeMenu();
        displayModePopupMenu.show();
        isSomePopupMenuVisible = true;
    }

    /**
     * Builds the single display-mode menu that combines the resize modes (Fit / Fill / Zoom) with
     * the forced aspect ratios (1:1 / 4:3 / ... / Custom). Picking a resize mode clears any forced
     * aspect ratio; picking an aspect ratio applies it with the resize mode set to Fit.
     */
    private void buildDisplayModeMenu() {
        if (displayModePopupMenu == null) {
            return;
        }
        final Menu menu = displayModePopupMenu.getMenu();
        menu.removeGroup(POPUP_MENU_ID_DISPLAY_MODE);
        menu.removeGroup(POPUP_MENU_ID_ASPECT_RATIO);
        // draw a divider between the resize-mode group and the aspect-ratio group
        MenuCompat.setGroupDividerEnabled(menu, true);
        displayModePopupMenu.setOnDismissListener(this);

        // a forced aspect ratio takes precedence: when active, no resize mode is the "current" one
        final boolean ratioActive = forcedAspectRatio > 0;
        final int currentResizeMode = binding.surfaceView.getResizeMode();
        MenuItem activeItem = null;

        int order = 0;
        for (final int resizeMode : new int[]{
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM}) {
            final MenuItem resizeItem = menu.add(POPUP_MENU_ID_DISPLAY_MODE, order, order,
                    PlayerHelper.resizeTypeOf(context, resizeMode));
            resizeItem.setOnMenuItemClickListener(menuItem -> {
                onResizeModeSelected(resizeMode);
                return true;
            });
            if (!ratioActive && resizeMode == currentResizeMode) {
                activeItem = resizeItem;
            }
            order++;
        }

        for (int i = 0; i < PlayerHelper.ASPECT_RATIO_VALUES.length; i++) {
            final float ratio = PlayerHelper.ASPECT_RATIO_VALUES[i];
            final MenuItem ratioItem = menu.add(POPUP_MENU_ID_ASPECT_RATIO, order, order,
                    PlayerHelper.ASPECT_RATIO_LABELS[i]);
            ratioItem.setOnMenuItemClickListener(menuItem -> {
                setForcedAspectRatio(ratio);
                return true;
            });
            if (ratioActive && Math.abs(forcedAspectRatio - ratio) < 0.001f) {
                activeItem = ratioItem;
            }
            order++;
        }

        final MenuItem customItem = menu.add(POPUP_MENU_ID_ASPECT_RATIO, order, order,
                R.string.aspect_ratio_custom);
        customItem.setOnMenuItemClickListener(menuItem -> {
            openCustomAspectRatioDialog();
            return true;
        });
        // a forced ratio that matches none of the presets is a custom value
        if (ratioActive && activeItem == null) {
            activeItem = customItem;
        }

        menu.setGroupCheckable(POPUP_MENU_ID_DISPLAY_MODE, true, true);
        menu.setGroupCheckable(POPUP_MENU_ID_ASPECT_RATIO, true, true);
        if (activeItem != null) {
            activeItem.setChecked(true);
        }
    }

    private void onResizeModeSelected(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        // a resize mode supersedes any forced aspect ratio, which would otherwise have no effect
        forcedAspectRatio = 0.0f;
        if (videoNaturalAspectRatio > 0) {
            binding.surfaceView.setAspectRatio(videoNaturalAspectRatio);
        }
        setResizeMode(resizeMode);
        PlayerHelper.saveResizeMode(this, resizeMode);
    }

    private void setForcedAspectRatio(final float aspectRatio) {
        forcedAspectRatio = aspectRatio;
        // a forced aspect ratio is only meaningful with Fit; this resize mode change is per-video
        // and is intentionally not persisted, so the saved resize mode is restored on the next video
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        final float effectiveRatio = aspectRatio > 0 ? aspectRatio : videoNaturalAspectRatio;
        if (effectiveRatio > 0) {
            binding.surfaceView.setAspectRatio(effectiveRatio);
        }
    }

    private void openCustomAspectRatioDialog() {
        final AppCompatActivity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final EditText input = new EditText(activity);
        input.setHint(R.string.aspect_ratio_custom_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (forcedAspectRatio > 0) {
            input.setText(PlayerHelper.aspectRatioNameOf(forcedAspectRatio));
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.aspect_ratio_custom_title)
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final float ratio = PlayerHelper.parseAspectRatio(input.getText().toString());
                    if (ratio > 0) {
                        setForcedAspectRatio(ratio);
                    } else {
                        Toast.makeText(context, R.string.aspect_ratio_invalid, Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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

        videoNaturalAspectRatio = ((float) videoSize.width) / videoSize.height;
        binding.surfaceView.setAspectRatio(forcedAspectRatio > 0
                ? forcedAspectRatio : videoNaturalAspectRatio);
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
            binding.sleepTimer.setVisibility(View.VISIBLE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
            binding.playerCloseButton.setVisibility(
                    videoPlayerSelected() ? View.VISIBLE : View.GONE);
            binding.sleepTimer.setVisibility(View.GONE);
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
                    NavigationHelper.playOnPopupPlayer(context, playQueue, true);
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

        // A SABR source already exposes both audio and video, so background / foreground video
        // toggles only need to update Media3 track selection instead of rebuilding the source.
        if (!isCurrentStreamSabr()
                && playQueueManagerReloadingNeeded(sourceType, info, getVideoRendererIndex())) {
            reloadPlayQueueManager();
        } else {
            final StreamType streamType = info.getStreamType();
            if (streamType == StreamType.AUDIO_STREAM
                    || streamType == StreamType.AUDIO_LIVE_STREAM) {
                // Nothing to do more than setting the recovery position
                setRecovery();
                return;
            }

            final DefaultTrackSelector.Parameters.Builder parametersBuilder =
                    trackSelector.buildUponParameters();

            // Enable/disable the video track and the ability to select subtitles
            parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !videoEnabled);
            parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoEnabled);

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

    public Optional<StreamInfo> getCurrentStreamInfo() {
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

    @Nullable
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

    public boolean isFullscreenGestureEnabled() {
        return isFullscreenGestureEnabled;
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

    public TextView getSwipeSeekDisplay() {
        return binding.swipeSeekDisplay;
    }

    public TextView getSwipeSpeedDisplay() {
        return binding.swipeSpeedDisplay;
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

    public long getCurrentPosition() {
        return exoPlayerIsNull() ? 0 : simpleExoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return exoPlayerIsNull() ? 0 : simpleExoPlayer.getDuration();
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
            surfaceHolderCallback = new SurfaceHolderCallback(simpleExoPlayer);
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
            surfaceHolderCallback = null;
        }
    }
    //endregion

    /*//////////////////////////////////////////////////////////////////////////
    // SponsorBlock
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public SponsorBlockMode getSponsorBlockMode() {
        return sponsorBlockMode;
    }

    public void setSponsorBlockMode(final SponsorBlockMode mode) {
        sponsorBlockMode = mode;
        // Also set pref
        prefs.edit().putString(context.getString(R.string.pref_sponsorblock_mode_key), mode.name()).apply();
    }

    public Optional<SponsorBlockSegment> getSkippableSponsorBlockSegment(final int progress) {
        return getCurrentStreamInfo().map(info -> {
            final SponsorBlockSegment[] sponsorBlockSegments = info.getSponsorBlockSegments();
            if (sponsorBlockSegments == null) {
                return null;
            }

            for (final SponsorBlockSegment sponsorBlockSegment : sponsorBlockSegments) {
                if (sponsorBlockSegment.action != SponsorBlockAction.SKIP) {
                    continue;
                }

                if (progress < sponsorBlockSegment.startTime) {
                    continue;
                }

                if (progress > sponsorBlockSegment.endTime) {
                    continue;
                }

                return sponsorBlockSegment;
            }

            // fallback on old SponsorBlockSegment (for un-skip)
            if (lastSegment != null
                    && progress > lastSegment.endTime + UNSKIP_WINDOW_MILLIS) {
                // un-skip window is over
                hideUnskipButtons();
                destroyUnskipVars();
            } else if (lastSegment != null
                    && progress < lastSegment.endTime + UNSKIP_WINDOW_MILLIS
                    && progress >= lastSegment.startTime) {
                // use old sponsorBlockSegment if exists AND currentProgress in bounds
                return lastSegment;
            }

            hideUnskipButtons();
            return null;
        });
    }

    private void hideUnskipButtons() {
        if (DEBUG) {
            Log.d("SPONSOR_BLOCK", "Hiding manual skip buttons (UNSKIP)");
        }
        hideAutoSkip();
        hideAutoUnskip();
    }

    private void destroyUnskipVars() {
        lastSegment = null;
        autoSkipGracePeriod = false;

        if (DEBUG) {
            Log.d("SPONSOR_BLOCK", "Destroyed last segment variables (UNSKIP)");
        }
    }

    private SponsorBlockSecondaryMode getSecondaryMode(final SponsorBlockSegment segment) {
        if (segment == null) {
            return SponsorBlockSecondaryMode.DISABLED;
        }

        // get pref
        final String defaultValue = context.getString(
                R.string.sponsor_block_skip_mode_automatic_value);
        final String key;
        switch (segment.category) {
            case SPONSOR:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_sponsor_mode_key),
                        defaultValue);
                break;
            case INTRO:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_intro_mode_key),
                        defaultValue);
                break;
            case OUTRO:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_outro_mode_key),
                        defaultValue);
                break;
            case INTERACTION:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_interaction_mode_key),
                        defaultValue);
                break;
            case HIGHLIGHT:
                key = context.getString(R.string.sponsor_block_skip_mode_highlight_value);
                break;
            case SELF_PROMO:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_self_promo_mode_key),
                        defaultValue);
                break;
            case NON_MUSIC:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_non_music_mode_key),
                        defaultValue);
                break;
            case PREVIEW:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_preview_mode_key),
                        defaultValue);
                break;
            case FILLER:
                key = prefs.getString(
                        context.getString(R.string.sponsor_block_category_filler_mode_key),
                        defaultValue);
                break;
            default:
                key = "";
                break;
        }

        // map pref to enum
        final SponsorBlockSecondaryMode pref;
        if (key.equals(context.getString(R.string.sponsor_block_skip_mode_automatic_value))) {
            pref = SponsorBlockSecondaryMode.ENABLED;
        } else if (key.equals(context.getString(R.string.sponsor_block_skip_mode_manual_value))) {
            pref = SponsorBlockSecondaryMode.MANUAL;
        } else if (key.equals(context.getString(
                R.string.sponsor_block_skip_mode_highlight_value))) {
            pref = SponsorBlockSecondaryMode.HIGHLIGHT;
        } else {
            pref = SponsorBlockSecondaryMode.DISABLED;
        }
        if (DEBUG) {
            Log.d("SPONSOR_BLOCK", "Sponsor segment secondary mode: category = ["
                    + segment.category + "], preference = [" + pref + "]");
        }

        return pref;
    }

    public void onMarkSeekbarRequested(@NonNull final StreamInfo streamInfo) {
        SponsorBlockHelper.markSegments(context, binding.playbackSeekBar, streamInfo);
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
    public void setLongPressSpeedingEnabled(boolean enabled) {
        longPressSpeedingEnabled = enabled;
    }

    public boolean getLongPressSpeedingEnabled() {
        return longPressSpeedingEnabled;
    }

    public void onBufferingFailed() {
        pause();
        pauseBCPlayer();
        currentState = STATE_PAUSED;
        notifyPlaybackUpdateToListeners();
        dataSource.disconnectWebSocketClients();
    }
}
