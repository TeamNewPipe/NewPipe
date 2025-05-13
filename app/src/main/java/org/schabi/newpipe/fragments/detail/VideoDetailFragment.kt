package org.schabi.newpipe.fragments.detail

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import coil3.util.CoilUtils.dispose
import com.evernote.android.state.State
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.EmptyFragment
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment.Companion.getInstance
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment.Companion.getInstance
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateRotation
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.event.OnKeyDownListener
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder.Companion.getInstance
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.playqueue.events.PlayQueueEvent
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.CoilHelper.loadAvatar
import org.schabi.newpipe.util.image.CoilHelper.loadDetailsThumbnail
import java.util.LinkedList
import java.util.List
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VideoDetailFragment :

    BaseStateFragment<StreamInfo?>(),
    BackPressable,
    PlayerServiceExtendedEventListener,
    OnKeyDownListener {
    // tabs
    private var showComments = false
    private var showRelatedItems = false
    private var showDescription = false
    private var selectedTabTag: String? = null

    @AttrRes
    val tabIcons: MutableList<Int?> = ArrayList<Int?>()

    @StringRes
    val tabContentDescriptions: MutableList<Int?> = ArrayList<Int?>()
    private var tabSettingsChanged = false
    private var lastAppBarVerticalOffset = Int.Companion.MAX_VALUE // prevents useless updates

    private val preferenceChangeListener =
        OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences?, key: String? ->
            if (getString(R.string.show_comments_key) == key) {
                showComments = sharedPreferences!!.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_next_video_key) == key) {
                showRelatedItems = sharedPreferences!!.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_description_key) == key) {
                showDescription = sharedPreferences!!.getBoolean(key, true)
                tabSettingsChanged = true
            }
        }

    @JvmField
    @State
    var serviceId: Int = NO_SERVICE_ID

    @JvmField
    @State
    var title: String = ""

    @JvmField
    @State
    var url: String? = null
    private var playQueue: PlayQueue? = null

    @JvmField
    @State
    var bottomSheetState: Int = BottomSheetBehavior.STATE_EXPANDED

    @JvmField
    @State
    var lastStableBottomSheetState: Int = BottomSheetBehavior.STATE_EXPANDED

    @JvmField
    @State
    var autoPlayEnabled: Boolean = true

    private var currentInfo: StreamInfo? = null
    private var currentWorker: Disposable? = null
    private val disposables = CompositeDisposable()
    private var positionSubscriber: Disposable? = null

    private var bottomSheetBehavior: BottomSheetBehavior<FrameLayout?>? = null
    private var bottomSheetCallback: BottomSheetCallback? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var binding: FragmentVideoDetailBinding? = null

    private var pageAdapter: TabAdapter? = null

    private var settingsContentObserver: ContentObserver? = null
    private var playerService: PlayerService? = null
    private var player: Player? = null
    private val playerHolder = getInstance()

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    ////////////////////////////////////////////////////////////////////////// */
    override fun onServiceConnected(connectedPlayerService: PlayerService) {
        playerService = connectedPlayerService
    }

    override fun onPlayerConnected(
        connectedPlayer: Player,
        playAfterConnect: Boolean
    ) {
        player = connectedPlayer

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded()

        val playerUi: Optional<MainPlayerUi> =
            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
        if (!player!!.videoPlayerSelected() && !playAfterConnect) {
            return
        }

        if (DeviceUtils.isLandscape(requireContext())) {
            // If the video is playing but orientation changed
            // let's make the video in fullscreen again
            checkLandscape()
        } else if (playerUi.map<Boolean?>(Function { ui: MainPlayerUi? -> ui!!.isFullscreen() && !ui.isVerticalVideo() })
            .orElse(false) && // Tablet UI has orientation-independent fullscreen
            !DeviceUtils.isTablet(activity)
        ) {
            // Device is in portrait orientation after rotation but UI is in fullscreen.
            // Return back to non-fullscreen state
            playerUi.ifPresent(Consumer { obj: MainPlayerUi? -> obj!!.toggleFullscreen() })
        }

        if (playAfterConnect ||
            (
                currentInfo != null && this.isAutoplayEnabled &&
                    playerUi.isEmpty()
                )
        ) {
            autoPlayEnabled = true // forcefully start playing
            openVideoPlayerAutoFullscreen()
        }
        updateOverlayPlayQueueButtonVisibility()
    }

    override fun onPlayerDisconnected() {
        player = null
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            restoreDefaultBrightness()
        }
    }

    override fun onServiceDisconnected() {
        playerService = null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true)
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true)
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true)
        selectedTabTag = prefs.getString(
            getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG
        )
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        setupBroadcastReceiver()

        settingsContentObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                if (activity != null && !PlayerHelper.globalScreenOrientationLocked(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                }
            }
        }
        activity.getContentResolver().registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
            settingsContentObserver!!
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoDetailBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onPause() {
        super.onPause()
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        restoreDefaultBrightness()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putString(
                getString(R.string.stream_info_selected_tab_key),
                pageAdapter!!.getItemTitle(binding!!.viewPager.getCurrentItem())
            )
            .apply()
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) {
            Log.d(TAG, "onResume() called")
        }

        activity.sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_RESUMED))

        updateOverlayPlayQueueButtonVisibility()

        setupBrightness()

        if (tabSettingsChanged) {
            tabSettingsChanged = false
            initTabs()
            if (currentInfo != null) {
                updateTabs(currentInfo!!)
            }
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared()) {
            startLoading(false)
        }
    }

    override fun onStop() {
        super.onStop()

        if (!activity.isChangingConfigurations()) {
            activity.sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_STOPPED))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing() && this.isPlayerAvailable && player!!.videoPlayerSelected()) {
            playerHolder.stopService()
        } else {
            playerHolder.setListener(null)
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        activity.unregisterReceiver(broadcastReceiver)
        activity.getContentResolver().unregisterContentObserver(settingsContentObserver!!)

        if (positionSubscriber != null) {
            positionSubscriber!!.dispose()
        }
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        disposables.clear()
        positionSubscriber = null
        currentWorker = null
        bottomSheetBehavior!!.removeBottomSheetCallback(bottomSheetCallback!!)

        if (activity.isFinishing()) {
            playQueue = null
            currentInfo = null
            stack = LinkedList<StackItem?>()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                NavigationHelper.openVideoDetailFragment(
                    requireContext(), getFM(),
                    serviceId, url, title, null, false
                )
            } else {
                Log.e(TAG, "ReCaptcha failed")
            }
        } else {
            Log.e(TAG, "Request code from activity not supported [" + requestCode + "]")
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    ////////////////////////////////////////////////////////////////////////// */
    private fun setOnClickListeners() {
        binding!!.detailTitleRootLayout.setOnClickListener(View.OnClickListener { v: View? -> toggleTitleAndSecondaryControls() })
        binding!!.detailUploaderRootLayout.setOnClickListener(
            makeOnClickListener(
                Consumer { info: StreamInfo? ->
                    if (TextUtils.isEmpty(
                            info!!.getSubChannelUrl()
                        )
                    ) {
                        if (!TextUtils.isEmpty(info.getUploaderUrl())) {
                            openChannel(info.getUploaderUrl(), info.getUploaderName())
                        }

                        if (DEBUG) {
                            Log.i(TAG, "Can't open sub-channel because we got no channel URL")
                        }
                    } else {
                        openChannel(info.getSubChannelUrl(), info.getSubChannelName())
                    }
                }
            )
        )
        binding!!.detailThumbnailRootLayout.setOnClickListener(
            View.OnClickListener { v: View? ->
                autoPlayEnabled = true // forcefully start playing
                // FIXME Workaround #7427
                if (this.isPlayerAvailable) {
                    player!!.setRecovery()
                }
                openVideoPlayerAutoFullscreen()
            }
        )

        binding!!.detailControlsBackground.setOnClickListener(
            View.OnClickListener { v: View? ->
                openBackgroundPlayer(
                    false
                )
            }
        )
        binding!!.detailControlsPopup.setOnClickListener(
            View.OnClickListener { v: View? ->
                openPopupPlayer(
                    false
                )
            }
        )
        binding!!.detailControlsPlaylistAppend.setOnClickListener(
            makeOnClickListener(
                Consumer { info: StreamInfo? ->
                    if (getFM() != null && currentInfo != null) {
                        val fragment = getParentFragmentManager().findFragmentById(R.id.fragment_holder)

                        // commit previous pending changes to database
                        if (fragment is LocalPlaylistFragment) {
                            fragment.saveImmediate()
                        } else if (fragment is MainFragment) {
                            fragment.commitPlaylistTabs()
                        }

                        disposables.add(
                            PlaylistDialog.createCorrespondingDialog(
                                requireContext(),
                                List.of<StreamEntity?>(StreamEntity(info!!)),
                                Consumer { dialog: PlaylistDialog? ->
                                    dialog!!.show(
                                        getParentFragmentManager(),
                                        TAG
                                    )
                                }
                            )
                        )
                    }
                }
            )
        )
        binding!!.detailControlsDownload.setOnClickListener(
            View.OnClickListener { v: View? ->
                if (PermissionHelper.checkStoragePermissions(
                        activity,
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE
                    )
                ) {
                    openDownloadDialog()
                }
            }
        )
        binding!!.detailControlsShare.setOnClickListener(
            makeOnClickListener(
                Consumer { info: StreamInfo? ->
                    ShareUtils.shareText(
                        requireContext(), info!!.getName(), info.getUrl(),
                        info.getThumbnails()
                    )
                }
            )
        )
        binding!!.detailControlsOpenInBrowser.setOnClickListener(
            makeOnClickListener(
                Consumer { info: StreamInfo? ->
                    ShareUtils.openUrlInBrowser(
                        requireContext(),
                        info!!.getUrl()
                    )
                }
            )
        )
        binding!!.detailControlsPlayWithKodi.setOnClickListener(
            makeOnClickListener(
                Consumer { info: StreamInfo? ->
                    KoreUtils.playWithKore(
                        requireContext(),
                        Uri.parse(
                            info!!.getUrl()
                        )
                    )
                }
            )
        )
        if (DEBUG) {
            binding!!.detailControlsCrashThePlayer.setOnClickListener(
                View.OnClickListener { v: View? ->
                    VideoDetailPlayerCrasher.onCrashThePlayer(
                        requireContext(),
                        player
                    )
                }
            )
        }

        val overlayListener = View.OnClickListener { v: View? ->
            bottomSheetBehavior!!
                .setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        binding!!.overlayThumbnail.setOnClickListener(overlayListener)
        binding!!.overlayMetadataLayout.setOnClickListener(overlayListener)
        binding!!.overlayButtonsLayout.setOnClickListener(overlayListener)
        binding!!.overlayCloseButton.setOnClickListener(
            View.OnClickListener { v: View? ->
                bottomSheetBehavior!!
                    .setState(BottomSheetBehavior.STATE_HIDDEN)
            }
        )
        binding!!.overlayPlayQueueButton.setOnClickListener(
            View.OnClickListener { v: View? ->
                NavigationHelper.openPlayQueue(
                    requireContext()
                )
            }
        )
        binding!!.overlayPlayPauseButton.setOnClickListener(
            View.OnClickListener { v: View? ->
                if (playerIsNotStopped()) {
                    player!!.playPause()
                    player!!.UIs().getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
                        .ifPresent(Consumer { ui: VideoPlayerUi? -> ui!!.hideControls(0, 0) })
                    showSystemUi()
                } else {
                    autoPlayEnabled = true // forcefully start playing
                    openVideoPlayer(false)
                }
                setOverlayPlayPauseImage(this.isPlayerAvailable && player!!.isPlaying())
            }
        )
    }

    private fun makeOnClickListener(consumer: Consumer<StreamInfo?>): View.OnClickListener {
        return View.OnClickListener { v: View? ->
            if (!isLoading.get() && currentInfo != null) {
                consumer.accept(currentInfo)
            }
        }
    }

    private fun setOnLongClickListeners() {
        binding!!.detailTitleRootLayout.setOnLongClickListener(
            makeOnLongClickListener(
                Consumer { info: StreamInfo? ->
                    ShareUtils.copyToClipboard(
                        requireContext(),
                        binding!!.detailVideoTitleView.getText().toString()
                    )
                }
            )
        )
        binding!!.detailUploaderRootLayout.setOnLongClickListener(
            makeOnLongClickListener(
                Consumer { info: StreamInfo? ->
                    if (TextUtils.isEmpty(
                            info!!.getSubChannelUrl()
                        )
                    ) {
                        Log.w(TAG, "Can't open parent channel because we got no parent channel URL")
                    } else {
                        openChannel(info.getUploaderUrl(), info.getUploaderName())
                    }
                }
            )
        )

        binding!!.detailControlsBackground.setOnLongClickListener(
            makeOnLongClickListener(
                Consumer { info: StreamInfo? ->
                    openBackgroundPlayer(
                        true
                    )
                }
            )
        )
        binding!!.detailControlsPopup.setOnLongClickListener(
            makeOnLongClickListener(
                Consumer { info: StreamInfo? ->
                    openPopupPlayer(
                        true
                    )
                }
            )
        )
        binding!!.detailControlsDownload.setOnLongClickListener(
            makeOnLongClickListener(
                Consumer { info: StreamInfo? ->
                    NavigationHelper.openDownloads(
                        activity
                    )
                }
            )
        )

        val overlayListener = makeOnLongClickListener(
            Consumer { info: StreamInfo? ->
                openChannel(
                    info!!.getUploaderUrl(), info.getUploaderName()
                )
            }
        )
        binding!!.overlayThumbnail.setOnLongClickListener(overlayListener)
        binding!!.overlayMetadataLayout.setOnLongClickListener(overlayListener)
    }

    private fun makeOnLongClickListener(consumer: Consumer<StreamInfo?>): OnLongClickListener {
        return OnLongClickListener { v: View? ->
            if (isLoading.get() || currentInfo == null) {
                return@OnLongClickListener false
            }
            consumer.accept(currentInfo)
            true
        }
    }

    private fun openChannel(subChannelUrl: String?, subChannelName: String) {
        try {
            NavigationHelper.openChannelFragment(
                getFM(), currentInfo!!.getServiceId(),
                subChannelUrl, subChannelName
            )
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Opening channel fragment", e)
        }
    }

    private fun toggleTitleAndSecondaryControls() {
        if (binding!!.detailSecondaryControlPanel.getVisibility() == View.GONE) {
            binding!!.detailVideoTitleView.setMaxLines(10)
            binding!!.detailToggleSecondaryControlsView
                .animateRotation(VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 180)
            binding!!.detailSecondaryControlPanel.setVisibility(View.VISIBLE)
        } else {
            binding!!.detailVideoTitleView.setMaxLines(1)
            binding!!.detailToggleSecondaryControlsView
                .animateRotation(VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 0)
            binding!!.detailSecondaryControlPanel.setVisibility(View.GONE)
        }
        // view pager height has changed, update the tab layout
        updateTabLayoutVisibility()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    // called from onViewCreated in {@link BaseFragment#onViewCreated}
    override fun initViews(rootView: View?, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        pageAdapter = TabAdapter(getChildFragmentManager())
        binding!!.viewPager.setAdapter(pageAdapter)
        binding!!.tabLayout.setupWithViewPager(binding!!.viewPager)

        binding!!.detailThumbnailRootLayout.requestFocus()

        binding!!.detailControlsPlayWithKodi.setVisibility(
            if (KoreUtils.shouldShowPlayWithKodi(requireContext(), serviceId))
                View.VISIBLE
            else
                View.GONE
        )
        binding!!.detailControlsCrashThePlayer.setVisibility(
            if (DEBUG && PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.show_crash_the_player_key), false)
            )
                View.VISIBLE
            else
                View.GONE
        )
        accommodateForTvAndDesktopMode()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initListeners() {
        super.initListeners()

        setOnClickListeners()
        setOnLongClickListeners()

        val controlsTouchListener = OnTouchListener { view: View?, motionEvent: MotionEvent? ->
            if (motionEvent!!.getAction() == MotionEvent.ACTION_DOWN &&
                PlayButtonHelper.shouldShowHoldToAppendTip(activity)
            ) {
                binding!!.touchAppendDetail.animate(
                    true,
                    250,
                    AnimationType.ALPHA,
                    0,
                    Runnable {
                        binding!!.touchAppendDetail.animate(
                            false,
                            1500,
                            AnimationType.ALPHA,
                            1000
                        )
                    }
                )
            }
            false
        }
        binding!!.detailControlsBackground.setOnTouchListener(controlsTouchListener)
        binding!!.detailControlsPopup.setOnTouchListener(controlsTouchListener)

        binding!!.appBarLayout.addOnOffsetChangedListener(
            OnOffsetChangedListener { layout: AppBarLayout?, verticalOffset: Int ->
                // prevent useless updates to tab layout visibility if nothing changed
                if (verticalOffset != lastAppBarVerticalOffset) {
                    lastAppBarVerticalOffset = verticalOffset
                    // the view was scrolled
                    updateTabLayoutVisibility()
                }
            }
        )

        setupBottomPlayer()
        if (!playerHolder.isBound) {
            setHeightThumbnail()
        } else {
            playerHolder.startService(false, this)
        }
    }

    override fun onKeyDown(keyCode: Int): Boolean {
        return this.isPlayerAvailable &&
            player!!.UIs().getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
                .map<Boolean?>(Function { playerUi: VideoPlayerUi? -> playerUi!!.onKeyDown(keyCode) })
                .orElse(false)
    }

    override fun onBackPressed(): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called")
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (this.isFullscreen) {
            if (!DeviceUtils.isTablet(activity)) {
                player!!.pause()
            }
            restoreDefaultOrientation()
            setAutoPlay(false)
            return true
        }

        // If we have something in history of played items we replay it here
        if (this.isPlayerAvailable &&
            player!!.getPlayQueue() != null && player!!.videoPlayerSelected() &&
            player!!.getPlayQueue()!!.previous()
        ) {
            return true // no code here, as previous() was used in the if
        }

        // That means that we are on the start of the stack,
        if (stack.size <= 1) {
            restoreDefaultOrientation()
            return false // let MainActivity handle the onBack (e.g. to minimize the mini player)
        }

        // Remove top
        stack.pop()
        // Get stack item from the new top
        setupFromHistoryItem(Objects.requireNonNull<StackItem?>(stack.peek()))

        return true
    }

    private fun setupFromHistoryItem(item: StackItem) {
        setAutoPlay(false)
        hideMainPlayerOnLoadingNewStream()

        setInitialData(
            item.getServiceId(), item.getUrl(),
            if (item.getTitle() == null) "" else item.getTitle(), item.getPlayQueue()
        )
        startLoading(false)

        // Maybe an item was deleted in background activity
        if (item.getPlayQueue().getItem() == null) {
            return
        }

        val playQueueItem = item.getPlayQueue().getItem()
        // Update title, url, uploader from the last item in the stack (it's current now)
        val isPlayerStopped = !this.isPlayerAvailable || player!!.isStopped()
        if (playQueueItem != null && isPlayerStopped) {
            updateOverlayData(
                playQueueItem.getTitle(),
                playQueueItem.getUploader(), playQueueItem.getThumbnails()
            )
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    ////////////////////////////////////////////////////////////////////////// */
    override fun doInitialLoadLogic() {
        if (wasCleared()) {
            return
        }

        if (currentInfo == null) {
            prepareAndLoadInfo()
        } else {
            prepareAndHandleInfoIfNeededAfterDelay(currentInfo!!, false, 50)
        }
    }

    fun selectAndLoadVideo(
        newServiceId: Int,
        newUrl: String?,
        newTitle: String,
        newQueue: PlayQueue?
    ) {
        if (this.isPlayerAvailable && newQueue != null && playQueue != null && playQueue!!.getItem() != null && (
            playQueue!!.getItem()!!
                .getUrl() != newUrl
            )
        ) {
            // Preloading can be disabled since playback is surely being replaced.
            player!!.disablePreloadingOfCurrentTrack()
        }

        setInitialData(newServiceId, newUrl, newTitle, newQueue)
        startLoading(false, true)
    }

    private fun prepareAndHandleInfoIfNeededAfterDelay(
        info: StreamInfo,
        scrollToTop: Boolean,
        delay: Long
    ) {
        Handler(Looper.getMainLooper()).postDelayed(
            Runnable {
                if (activity == null) {
                    return@Runnable
                }
                // Data can already be drawn, don't spend time twice
                if (info.getName() == binding!!.detailVideoTitleView.getText().toString()) {
                    return@Runnable
                }
                prepareAndHandleInfo(info, scrollToTop)
            },
            delay
        )
    }

    private fun prepareAndHandleInfo(info: StreamInfo, scrollToTop: Boolean) {
        if (DEBUG) {
            Log.d(
                TAG,
                (
                    "prepareAndHandleInfo() called with: " +
                        "info = [" + info + "], scrollToTop = [" + scrollToTop + "]"
                    )
            )
        }

        showLoading()
        initTabs()

        if (scrollToTop) {
            scrollToTop()
        }
        handleResult(info)
        showContent()
    }

    private fun prepareAndLoadInfo() {
        scrollToTop()
        startLoading(false)
    }

    public override fun startLoading(forceLoad: Boolean) {
        startLoading(forceLoad, null)
    }

    private fun startLoading(forceLoad: Boolean, addToBackStack: Boolean?) {
        super.startLoading(forceLoad)

        initTabs()
        currentInfo = null
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }

        runWorker(forceLoad, if (addToBackStack != null) addToBackStack else stack.isEmpty())
    }

    private fun runWorker(forceLoad: Boolean, addToBackStack: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                io.reactivex.rxjava3.functions.Consumer { result: StreamInfo? ->
                    isLoading.set(false)
                    hideMainPlayerOnLoadingNewStream()
                    if (result!!.getAgeLimit() != StreamExtractor.NO_AGE_LIMIT && !prefs.getBoolean(
                            getString(R.string.show_age_restricted_content), false
                        )
                    ) {
                        hideAgeRestrictedContent()
                    } else {
                        handleResult(result)
                        showContent()
                        if (addToBackStack) {
                            if (playQueue == null) {
                                playQueue = SinglePlayQueue(result)
                            }
                            if (stack.isEmpty() || stack.peek()!!.getPlayQueue() != playQueue) {
                                stack.push(StackItem(serviceId, url, title, playQueue))
                            }
                        }

                        if (this.isAutoplayEnabled) {
                            openVideoPlayerAutoFullscreen()
                        }
                    }
                },
                io.reactivex.rxjava3.functions.Consumer { throwable: Throwable? ->
                    showError(
                        ErrorInfo(
                            throwable!!, UserAction.REQUESTED_STREAM,
                            (if (url == null) "no url" else url)!!, serviceId
                        )
                    )
                }
            )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    ////////////////////////////////////////////////////////////////////////// */
    private fun initTabs() {
        if (pageAdapter!!.getCount() != 0) {
            selectedTabTag = pageAdapter!!.getItemTitle(binding!!.viewPager.getCurrentItem())
        }
        pageAdapter!!.clearAllItems()
        tabIcons.clear()
        tabContentDescriptions.clear()

        if (shouldShowComments()) {
            pageAdapter!!.addFragment(getInstance(serviceId, url), COMMENTS_TAB_TAG)
            tabIcons.add(R.drawable.ic_comment)
            tabContentDescriptions.add(R.string.comments_tab_description)
        }

        if (showRelatedItems && binding!!.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter!!.addFragment(EmptyFragment.newInstance(false), RELATED_TAB_TAG)
            tabIcons.add(R.drawable.ic_art_track)
            tabContentDescriptions.add(R.string.related_items_tab_description)
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter!!.addFragment(EmptyFragment.newInstance(false), DESCRIPTION_TAB_TAG)
            tabIcons.add(R.drawable.ic_description)
            tabContentDescriptions.add(R.string.description_tab_description)
        }

        if (pageAdapter!!.getCount() == 0) {
            pageAdapter!!.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG)
        }
        pageAdapter!!.notifyDataSetUpdate()

        if (pageAdapter!!.getCount() >= 2) {
            val position = pageAdapter!!.getItemPositionByTitle(selectedTabTag)
            if (position != -1) {
                binding!!.viewPager.setCurrentItem(position)
            }
            updateTabIconsAndContentDescriptions()
        }
        // the page adapter now contains tabs: show the tab layout
        updateTabLayoutVisibility()
    }

    /**
     * To be called whenever [.pageAdapter] is modified, since that triggers a refresh in
     * [FragmentVideoDetailBinding.tabLayout] resetting all tab's icons and content
     * descriptions. This reads icons from [.tabIcons] and content descriptions from
     * [.tabContentDescriptions], which are all set in [.initTabs].
     */
    private fun updateTabIconsAndContentDescriptions() {
        for (i in tabIcons.indices) {
            val tab = binding!!.tabLayout.getTabAt(i)
            if (tab != null) {
                tab.setIcon(tabIcons.get(i)!!)
                tab.setContentDescription(tabContentDescriptions.get(i)!!)
            }
        }
    }

    private fun updateTabs(info: StreamInfo) {
        if (showRelatedItems) {
            if (binding!!.relatedItemsLayout == null) { // phone
                pageAdapter!!.updateItem(RELATED_TAB_TAG, getInstance(info))
            } else { // tablet + TV
                getChildFragmentManager().beginTransaction()
                    .replace(R.id.relatedItemsLayout, getInstance(info))
                    .commitAllowingStateLoss()
                binding!!.relatedItemsLayout!!.setVisibility(if (this.isFullscreen) View.GONE else View.VISIBLE)
            }
        }

        if (showDescription) {
            pageAdapter!!.updateItem(DESCRIPTION_TAB_TAG, DescriptionFragment(info))
        }

        binding!!.viewPager.setVisibility(View.VISIBLE)
        // make sure the tab layout is visible
        updateTabLayoutVisibility()
        pageAdapter!!.notifyDataSetUpdate()
        updateTabIconsAndContentDescriptions()
    }

    private fun shouldShowComments(): Boolean {
        try {
            return showComments && NewPipe.getService(serviceId)
                .getServiceInfo()
                .getMediaCapabilities()
                .contains(MediaCapability.COMMENTS)
        } catch (e: ExtractionException) {
            return false
        }
    }

    fun updateTabLayoutVisibility() {
        if (binding == null) {
            // If binding is null we do not need to and should not do anything with its object(s)
            return
        }

        if (pageAdapter!!.getCount() < 2 || binding!!.viewPager.getVisibility() != View.VISIBLE) {
            // hide tab layout if there is only one tab or if the view pager is also hidden
            binding!!.tabLayout.setVisibility(View.GONE)
        } else {
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            binding!!.tabLayout.post(
                Runnable {
                    val activity = getActivity()
                    if (activity != null) {
                        val pagerHitRect = Rect()
                        binding!!.viewPager.getHitRect(pagerHitRect)

                        val height = DeviceUtils.getWindowHeight(activity.getWindowManager())
                        val viewPagerVisibleHeight = height - pagerHitRect.top
                        // see TabLayout.DEFAULT_HEIGHT, which is equal to 48dp
                        val tabLayoutHeight = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 48f, getResources().getDisplayMetrics()
                        )

                        if (viewPagerVisibleHeight > tabLayoutHeight * 2) {
                            // no translation at all when viewPagerVisibleHeight > tabLayout.height * 3
                            binding!!.tabLayout.setTranslationY(
                                max(
                                    0.0,
                                    (tabLayoutHeight * 3 - viewPagerVisibleHeight).toDouble()
                                ).toFloat()
                            )
                            binding!!.tabLayout.setVisibility(View.VISIBLE)
                        } else {
                            // view pager is not visible enough
                            binding!!.tabLayout.setVisibility(View.GONE)
                        }
                    }
                }
            )
        }
    }

    fun scrollToTop() {
        binding!!.appBarLayout.setExpanded(true, true)
        // notify tab layout of scrolling
        updateTabLayoutVisibility()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun toggleFullscreenIfInFullscreenMode() {
        // If a user watched video inside fullscreen mode and than chose another player
        // return to non-fullscreen mode
        if (this.isPlayerAvailable) {
            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
                .ifPresent(
                    Consumer { playerUi: MainPlayerUi? ->
                        if (playerUi!!.isFullscreen()) {
                            playerUi.toggleFullscreen()
                        }
                    }
                )
        }
    }

    private fun openBackgroundPlayer(append: Boolean) {
        val useExternalAudioPlayer = PreferenceManager
            .getDefaultSharedPreferences(activity)
            .getBoolean(activity.getString(R.string.use_external_audio_player_key), false)

        toggleFullscreenIfInFullscreenMode()

        if (this.isPlayerAvailable) {
            // FIXME Workaround #7427
            player!!.setRecovery()
        }

        if (useExternalAudioPlayer) {
            showExternalAudioPlaybackDialog()
        } else {
            openNormalBackgroundPlayer(append)
        }
    }

    private fun openPopupPlayer(append: Boolean) {
        if (!PermissionHelper.isPopupEnabledElseAsk(activity)) {
            return
        }

        // See UI changes while remote playQueue changes
        if (!this.isPlayerAvailable) {
            playerHolder.startService(false, this)
        } else {
            // FIXME Workaround #7427
            player!!.setRecovery()
        }

        toggleFullscreenIfInFullscreenMode()

        val queue = setupPlayQueueForIntent(append)
        if (append) { // resumePlayback: false
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.POPUP)
        } else {
            replaceQueueIfUserConfirms(
                Runnable {
                    NavigationHelper
                        .playOnPopupPlayer(activity, queue, true)
                }
            )
        }
    }

    /**
     * Opens the video player, in fullscreen if needed. In order to open fullscreen, the activity
     * is toggled to landscape orientation (which will then cause fullscreen mode).
     *
     * @param directlyFullscreenIfApplicable whether to open fullscreen if we are not already
     * in landscape and screen orientation is locked
     */
    fun openVideoPlayer(directlyFullscreenIfApplicable: Boolean) {
        if (directlyFullscreenIfApplicable &&
            !DeviceUtils.isLandscape(requireContext()) && PlayerHelper.globalScreenOrientationLocked(
                    requireContext()
                )
        ) {
            // Make sure the bottom sheet turns out expanded. When this code kicks in the bottom
            // sheet could not have fully expanded yet, and thus be in the STATE_SETTLING state.
            // When the activity is rotated, and its state is saved and then restored, the bottom
            // sheet would forget what it was doing, since even if STATE_SETTLING is restored, it
            // doesn't tell which state it was settling to, and thus the bottom sheet settles to
            // STATE_COLLAPSED. This can be solved by manually setting the state that will be
            // restored (i.e. bottomSheetState) to STATE_EXPANDED.
            updateBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)
            // toggle landscape in order to open directly in fullscreen
            onScreenRotationButtonClicked()
        }

        if (PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(this.getString(R.string.use_external_video_player_key), false)
        ) {
            showExternalVideoPlaybackDialog()
        } else {
            replaceQueueIfUserConfirms(Runnable { this.openMainPlayer() })
        }
    }

    /**
     * If the option to start directly fullscreen is enabled, calls
     * [.openVideoPlayer] with `directlyFullscreenIfApplicable = true`, so that
     * if the user is not already in landscape and he has screen orientation locked the activity
     * rotates and fullscreen starts. Otherwise, if the option to start directly fullscreen is
     * disabled, calls [.openVideoPlayer] with `directlyFullscreenIfApplicable
     * = false`, hence preventing it from going directly fullscreen.
     */
    fun openVideoPlayerAutoFullscreen() {
        openVideoPlayer(PlayerHelper.isStartMainPlayerFullscreenEnabled(requireContext()))
    }

    private fun openNormalBackgroundPlayer(append: Boolean) {
        // See UI changes while remote playQueue changes
        if (!this.isPlayerAvailable) {
            playerHolder.startService(false, this)
        }

        val queue = setupPlayQueueForIntent(append)
        if (append) {
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.AUDIO)
        } else {
            replaceQueueIfUserConfirms(
                Runnable {
                    NavigationHelper
                        .playOnBackgroundPlayer(activity, queue, true)
                }
            )
        }
    }

    private fun openMainPlayer() {
        if (noPlayerServiceAvailable()) {
            playerHolder.startService(autoPlayEnabled, this)
            return
        }
        if (currentInfo == null) {
            return
        }

        val queue = setupPlayQueueForIntent(false)
        tryAddVideoPlayerView()

        val playerIntent = NavigationHelper.getPlayerIntent<PlayerService?>(
            requireContext(),
            PlayerService::class.java, queue, true, autoPlayEnabled
        )
        ContextCompat.startForegroundService(activity, playerIntent)
    }

    /**
     * When the video detail fragment is already showing details for a video and the user opens a
     * new one, the video detail fragment changes all of its old data to the new stream, so if there
     * is a video player currently open it should be hidden. This method does exactly that. If
     * autoplay is enabled, the underlying player is not stopped completely, since it is going to
     * be reused in a few milliseconds and the flickering would be annoying.
     */
    private fun hideMainPlayerOnLoadingNewStream() {
        val root = this.root
        if (noPlayerServiceAvailable() || root.isEmpty() || !player!!.videoPlayerSelected()) {
            return
        }

        removeVideoPlayerView()
        if (this.isAutoplayEnabled) {
            playerService!!.stopForImmediateReusing()
            root.ifPresent(Consumer { view: View -> view.setVisibility(View.GONE) })
        } else {
            playerHolder.stopService()
        }
    }

    private fun setupPlayQueueForIntent(append: Boolean): PlayQueue {
        if (append) {
            return SinglePlayQueue(currentInfo)
        }

        var queue = playQueue
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty()) {
            queue = SinglePlayQueue(currentInfo)
        }

        return queue
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    fun setAutoPlay(autoPlay: Boolean) {
        this.autoPlayEnabled = autoPlay
    }

    private fun startOnExternalPlayer(
        context: Context,
        info: StreamInfo,
        selectedStream: Stream
    ) {
        NavigationHelper.playOnExternalPlayer(
            context, currentInfo!!.getName(),
            currentInfo!!.getSubChannelName(), selectedStream
        )

        val recordManager = HistoryRecordManager(requireContext())
        disposables.add(
            recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                    io.reactivex.rxjava3.functions.Consumer { ignored: Long? -> },
                    io.reactivex.rxjava3.functions.Consumer { error: Throwable? ->
                        Log.e(
                            TAG,
                            "Register view failure: ",
                            error
                        )
                    }
                )
        )
    }

    private val isExternalPlayerEnabled: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.use_external_video_player_key), false)

    private val isAutoplayEnabled: Boolean
        // This method overrides default behaviour when setAutoPlay() is called.
        get() = autoPlayEnabled &&
            !this.isExternalPlayerEnabled && (!this.isPlayerAvailable || player!!.videoPlayerSelected()) &&
            bottomSheetState != BottomSheetBehavior.STATE_HIDDEN && PlayerHelper.isAutoplayAllowedByUser(
            requireContext()
        )

    private fun tryAddVideoPlayerView() {
        if (this.isPlayerAvailable && getView() != null) {
            // Setup the surface view height, so that it fits the video correctly; this is done also
            // here, and not only in the Handler, to avoid a choppy fullscreen rotation animation.
            setHeightThumbnail()
        }

        // do all the null checks in the posted lambda, too, since the player, the binding and the
        // view could be set or unset before the lambda gets executed on the next main thread cycle
        Handler(Looper.getMainLooper()).post(
            Runnable {
                if (!this.isPlayerAvailable || getView() == null) {
                    return@Runnable
                }
                // setup the surface view height, so that it fits the video correctly
                setHeightThumbnail()
                player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
                    .ifPresent(
                        Consumer { playerUi: MainPlayerUi? ->
                            // sometimes binding would be null here, even though getView() != null above u.u
                            if (binding != null) {
                                // prevent from re-adding a view multiple times
                                playerUi!!.removeViewFromParent()
                                binding!!.playerPlaceholder.addView(playerUi.getBinding().getRoot())
                                playerUi.setupVideoSurfaceIfNeeded()
                            }
                        }
                    )
            }
        )
    }

    private fun removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder()

        if (player != null) {
            player!!.UIs().getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
                .ifPresent(Consumer { obj: VideoPlayerUi? -> obj!!.removeViewFromParent() })
        }
    }

    private fun makeDefaultHeightForVideoPlaceholder() {
        if (getView() == null) {
            return
        }

        binding!!.playerPlaceholder.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT
        binding!!.playerPlaceholder.requestLayout()
    }

    private val preDrawListener: ViewTreeObserver.OnPreDrawListener =
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val metrics = getResources().getDisplayMetrics()

                if (getView() != null) {
                    val height = (
                        if (DeviceUtils.isInMultiWindow(activity))
                            requireView()
                        else
                            activity.getWindow().getDecorView()
                        ).getHeight()
                    setHeightThumbnail(height, metrics)
                    requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener)
                }
                return false
            }
        }

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * [.MAX_PLAYER_HEIGHT])
     */
    private fun setHeightThumbnail() {
        val metrics = getResources().getDisplayMetrics()
        val isPortrait = metrics.heightPixels > metrics.widthPixels
        requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener)

        if (this.isFullscreen) {
            val height = (
                if (DeviceUtils.isInMultiWindow(activity))
                    requireView()
                else
                    activity.getWindow().getDecorView()
                ).getHeight()
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics)
            } else {
                requireView().getViewTreeObserver().addOnPreDrawListener(preDrawListener)
            }
        } else {
            val height = (
                if (isPortrait)
                    metrics.widthPixels / (16.0f / 9.0f)
                else
                    metrics.heightPixels / 2.0f
                ).toInt()
            setHeightThumbnail(height, metrics)
        }
    }

    private fun setHeightThumbnail(newHeight: Int, metrics: DisplayMetrics) {
        binding!!.detailThumbnailImageView.setLayoutParams(
            FrameLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, newHeight
            )
        )
        binding!!.detailThumbnailImageView.setMinimumHeight(newHeight)
        if (this.isPlayerAvailable) {
            val maxHeight = (metrics.heightPixels * MAX_PLAYER_HEIGHT).toInt()
            player!!.UIs().getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
                .ifPresent(
                    Consumer { ui: VideoPlayerUi? ->
                        ui!!.getBinding().surfaceView.setHeights(
                            newHeight,
                            if (ui.isFullscreen()) newHeight else maxHeight
                        )
                    }
                )
        }
    }

    private fun showContent() {
        binding!!.detailContentRootHiding.setVisibility(View.VISIBLE)
    }

    private fun setInitialData(
        newServiceId: Int,
        newUrl: String?,
        newTitle: String,
        newPlayQueue: PlayQueue?
    ) {
        this.serviceId = newServiceId
        this.url = newUrl
        this.title = newTitle
        this.playQueue = newPlayQueue
    }

    private fun setErrorImage() {
        if (binding == null || activity == null) {
            return
        }

        binding!!.detailThumbnailImageView.setImageDrawable(
            AppCompatResources.getDrawable(requireContext(), R.drawable.not_available_monkey)
        )
        binding!!.detailThumbnailImageView.animate(
            false, 0, AnimationType.ALPHA,
            0, Runnable { binding!!.detailThumbnailImageView.animate(true, 500) }
        )
    }

    override fun handleError() {
        super.handleError()
        setErrorImage()

        if (binding!!.relatedItemsLayout != null) { // hide related streams for tablets
            binding!!.relatedItemsLayout!!.setVisibility(View.INVISIBLE)
        }

        // hide comments / related streams / description tabs
        binding!!.viewPager.setVisibility(View.GONE)
        binding!!.tabLayout.setVisibility(View.GONE)
    }

    private fun hideAgeRestrictedContent() {
        showTextError(
            getString(
                R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)
            )
        )
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when (intent.getAction()) {
                    ACTION_SHOW_MAIN_PLAYER -> bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                    ACTION_HIDE_MAIN_PLAYER -> bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
                    ACTION_PLAYER_STARTED -> {
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!playerHolder.isBound) {
                            playerHolder.startService(
                                false, this@VideoDetailFragment
                            )
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER)
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER)
        intentFilter.addAction(ACTION_PLAYER_STARTED)
        activity.registerReceiver(broadcastReceiver, intentFilter)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    ////////////////////////////////////////////////////////////////////////// */
    private fun restoreDefaultOrientation() {
        if (this.isPlayerAvailable && player!!.videoPlayerSelected()) {
            toggleFullscreenIfInFullscreenMode()
        }

        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        if (activity != null && !DeviceUtils.isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    override fun showLoading() {
        super.showLoading()

        // if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if (!ExtractorHelper.isCached(serviceId, url!!, InfoCache.Type.STREAM)) {
            binding!!.detailContentRootHiding.setVisibility(View.INVISIBLE)
        }

        binding!!.detailThumbnailPlayButton.animate(false, 50)
        binding!!.detailDurationView.animate(false, 100)
        binding!!.detailPositionView.setVisibility(View.GONE)
        binding!!.positionView.setVisibility(View.GONE)

        binding!!.detailVideoTitleView.setText(title)
        binding!!.detailVideoTitleView.setMaxLines(1)
        binding!!.detailVideoTitleView.animate(true, 0)

        binding!!.detailToggleSecondaryControlsView.setVisibility(View.GONE)
        binding!!.detailTitleRootLayout.setClickable(false)
        binding!!.detailSecondaryControlPanel.setVisibility(View.GONE)

        if (binding!!.relatedItemsLayout != null) {
            if (showRelatedItems) {
                binding!!.relatedItemsLayout!!.setVisibility(
                    if (this.isFullscreen) View.GONE else View.INVISIBLE
                )
            } else {
                binding!!.relatedItemsLayout!!.setVisibility(View.GONE)
            }
        }

        dispose(binding!!.detailThumbnailImageView)
        dispose(binding!!.detailSubChannelThumbnailView)
        dispose(binding!!.overlayThumbnail)
        dispose(binding!!.detailUploaderThumbnailView)

        binding!!.detailThumbnailImageView.setImageBitmap(null)
        binding!!.detailSubChannelThumbnailView.setImageBitmap(null)
    }

    override fun handleResult(info: StreamInfo?) {
        super.handleResult(info)

        currentInfo = info
        setInitialData(info!!.getServiceId(), info.getOriginalUrl(), info.getName(), playQueue)

        updateTabs(info)

        binding!!.detailThumbnailPlayButton.animate(true, 200)
        binding!!.detailVideoTitleView.setText(title)

        binding!!.detailSubChannelThumbnailView.setVisibility(View.GONE)

        if (!TextUtils.isEmpty(info.getSubChannelName())) {
            displayBothUploaderAndSubChannel(info)
        } else {
            displayUploaderAsSubChannel(info)
        }

        if (info.getViewCount() >= 0) {
            if (info.getStreamType() == StreamType.AUDIO_LIVE_STREAM) {
                binding!!.detailViewCountView.setText(
                    Localization.listeningCount(
                        activity,
                        info.getViewCount()
                    )
                )
            } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
                binding!!.detailViewCountView.setText(
                    Localization
                        .localizeWatchingCount(activity, info.getViewCount())
                )
            } else {
                binding!!.detailViewCountView.setText(
                    Localization
                        .localizeViewCount(activity, info.getViewCount())
                )
            }
            binding!!.detailViewCountView.setVisibility(View.VISIBLE)
        } else {
            binding!!.detailViewCountView.setVisibility(View.GONE)
        }

        if (info.getDislikeCount() == -1L && info.getLikeCount() == -1L) {
            binding!!.detailThumbsDownImgView.setVisibility(View.VISIBLE)
            binding!!.detailThumbsUpImgView.setVisibility(View.VISIBLE)
            binding!!.detailThumbsUpCountView.setVisibility(View.GONE)
            binding!!.detailThumbsDownCountView.setVisibility(View.GONE)

            binding!!.detailThumbsDisabledView.setVisibility(View.VISIBLE)
        } else {
            if (info.getDislikeCount() >= 0) {
                binding!!.detailThumbsDownCountView.setText(
                    Localization
                        .shortCount(activity, info.getDislikeCount())
                )
                binding!!.detailThumbsDownCountView.setVisibility(View.VISIBLE)
                binding!!.detailThumbsDownImgView.setVisibility(View.VISIBLE)
            } else {
                binding!!.detailThumbsDownCountView.setVisibility(View.GONE)
                binding!!.detailThumbsDownImgView.setVisibility(View.GONE)
            }

            if (info.getLikeCount() >= 0) {
                binding!!.detailThumbsUpCountView.setText(
                    Localization.shortCount(
                        activity,
                        info.getLikeCount()
                    )
                )
                binding!!.detailThumbsUpCountView.setVisibility(View.VISIBLE)
                binding!!.detailThumbsUpImgView.setVisibility(View.VISIBLE)
            } else {
                binding!!.detailThumbsUpCountView.setVisibility(View.GONE)
                binding!!.detailThumbsUpImgView.setVisibility(View.GONE)
            }
            binding!!.detailThumbsDisabledView.setVisibility(View.GONE)
        }

        if (info.getDuration() > 0) {
            binding!!.detailDurationView.setText(Localization.getDurationString(info.getDuration()))
            binding!!.detailDurationView.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.duration_background_color)
            )
            binding!!.detailDurationView.animate(true, 100)
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            binding!!.detailDurationView.setText(R.string.duration_live)
            binding!!.detailDurationView.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.live_duration_background_color)
            )
            binding!!.detailDurationView.animate(true, 100)
        } else {
            binding!!.detailDurationView.setVisibility(View.GONE)
        }

        binding!!.detailTitleRootLayout.setClickable(true)
        binding!!.detailToggleSecondaryControlsView.setRotation(0f)
        binding!!.detailToggleSecondaryControlsView.setVisibility(View.VISIBLE)
        binding!!.detailSecondaryControlPanel.setVisibility(View.GONE)

        checkUpdateProgressInfo(info)
        loadDetailsThumbnail(
            binding!!.detailThumbnailImageView,
            info.getThumbnails()
        )
        ExtractorHelper.showMetaInfoInTextView(
            info.getMetaInfo(), binding!!.detailMetaInfoTextView,
            binding!!.detailMetaInfoSeparator, disposables
        )

        if (!this.isPlayerAvailable || player!!.isStopped()) {
            updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnails())
        }

        if (!info.getErrors().isEmpty()) {
            // Bandcamp fan pages are not yet supported and thus a ContentNotAvailableException is
            // thrown. This is not an error and thus should not be shown to the user.
            for (throwable in info.getErrors()) {
                if (throwable is ContentNotSupportedException &&
                    "Fan pages are not supported" == throwable.message
                ) {
                    info.getErrors().remove(throwable)
                }
            }

            if (!info.getErrors().isEmpty()) {
                showSnackBarError(
                    ErrorInfo(
                        info.getErrors(),
                        UserAction.REQUESTED_STREAM, info.getUrl(), info
                    )
                )
            }
        }

        binding!!.detailControlsDownload.setVisibility(
            if (StreamTypeUtil.isLiveStream(info.getStreamType())) View.GONE else View.VISIBLE
        )
        binding!!.detailControlsBackground.setVisibility(
            if (info.getAudioStreams().isEmpty() && info.getVideoStreams().isEmpty())
                View.GONE
            else
                View.VISIBLE
        )

        val noVideoStreams =
            info.getVideoStreams().isEmpty() && info.getVideoOnlyStreams().isEmpty()
        binding!!.detailControlsPopup.setVisibility(if (noVideoStreams) View.GONE else View.VISIBLE)
        binding!!.detailThumbnailPlayButton.setImageResource(
            if (noVideoStreams) R.drawable.ic_headset_shadow else R.drawable.ic_play_arrow_shadow
        )
    }

    private fun displayUploaderAsSubChannel(info: StreamInfo) {
        binding!!.detailSubChannelTextView.setText(info.getUploaderName())
        binding!!.detailSubChannelTextView.setVisibility(View.VISIBLE)
        binding!!.detailSubChannelTextView.setSelected(true)

        if (info.getUploaderSubscriberCount() > -1) {
            binding!!.detailUploaderTextView.setText(
                Localization.shortSubscriberCount(activity, info.getUploaderSubscriberCount())
            )
            binding!!.detailUploaderTextView.setVisibility(View.VISIBLE)
        } else {
            binding!!.detailUploaderTextView.setVisibility(View.GONE)
        }

        loadAvatar(
            binding!!.detailSubChannelThumbnailView,
            info.getUploaderAvatars()
        )
        binding!!.detailSubChannelThumbnailView.setVisibility(View.VISIBLE)
        binding!!.detailUploaderThumbnailView.setVisibility(View.GONE)
    }

    private fun displayBothUploaderAndSubChannel(info: StreamInfo) {
        binding!!.detailSubChannelTextView.setText(info.getSubChannelName())
        binding!!.detailSubChannelTextView.setVisibility(View.VISIBLE)
        binding!!.detailSubChannelTextView.setSelected(true)

        val subText = StringBuilder()
        if (!TextUtils.isEmpty(info.getUploaderName())) {
            subText.append(
                String.format(getString(R.string.video_detail_by), info.getUploaderName())
            )
        }
        if (info.getUploaderSubscriberCount() > -1) {
            if (subText.length > 0) {
                subText.append(Localization.DOT_SEPARATOR)
            }
            subText.append(
                Localization.shortSubscriberCount(activity, info.getUploaderSubscriberCount())
            )
        }

        if (subText.length > 0) {
            binding!!.detailUploaderTextView.setText(subText)
            binding!!.detailUploaderTextView.setVisibility(View.VISIBLE)
            binding!!.detailUploaderTextView.setSelected(true)
        } else {
            binding!!.detailUploaderTextView.setVisibility(View.GONE)
        }

        loadAvatar(
            binding!!.detailSubChannelThumbnailView,
            info.getSubChannelAvatars()
        )
        binding!!.detailSubChannelThumbnailView.setVisibility(View.VISIBLE)
        loadAvatar(
            binding!!.detailUploaderThumbnailView,
            info.getUploaderAvatars()
        )
        binding!!.detailUploaderThumbnailView.setVisibility(View.VISIBLE)
    }

    fun openDownloadDialog() {
        if (currentInfo == null) {
            return
        }

        try {
            val downloadDialog = DownloadDialog(activity, currentInfo!!)
            downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog")
        } catch (e: Exception) {
            showSnackbar(
                activity,
                ErrorInfo(
                    e, UserAction.DOWNLOAD_OPEN_DIALOG,
                    "Showing download dialog", currentInfo
                )
            )
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    ////////////////////////////////////////////////////////////////////////// */
    private fun checkUpdateProgressInfo(info: StreamInfo) {
        if (positionSubscriber != null) {
            positionSubscriber!!.dispose()
        }
        if (!DependentPreferenceHelper.getResumePlaybackEnabled(activity)) {
            binding!!.positionView.setVisibility(View.GONE)
            binding!!.detailPositionView.setVisibility(View.GONE)
            return
        }
        val recordManager = HistoryRecordManager(requireContext())
        positionSubscriber = recordManager.loadStreamState(info)
            .subscribeOn(Schedulers.io())
            .onErrorComplete()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                io.reactivex.rxjava3.functions.Consumer { state: StreamStateEntity? ->
                    updatePlaybackProgress(
                        state!!.getProgressMillis(), info.getDuration() * 1000
                    )
                },
                io.reactivex.rxjava3.functions.Consumer { e: Throwable? -> },
                Action {
                    binding!!.positionView.setVisibility(View.GONE)
                    binding!!.detailPositionView.setVisibility(View.GONE)
                }
            )
    }

    private fun updatePlaybackProgress(progress: Long, duration: Long) {
        if (!DependentPreferenceHelper.getResumePlaybackEnabled(activity)) {
            return
        }
        val progressSeconds = TimeUnit.MILLISECONDS.toSeconds(progress).toInt()
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(duration).toInt()
        // If the old and the new progress values have a big difference then use animation.
        // Otherwise don't because it affects CPU
        val progressDifference = abs(
            (
                binding!!.positionView.getProgress() -
                    progressSeconds
                ).toDouble()
        ).toInt()
        binding!!.positionView.setMax(durationSeconds)
        if (progressDifference > 2) {
            binding!!.positionView.setProgressAnimated(progressSeconds)
        } else {
            binding!!.positionView.setProgress(progressSeconds)
        }
        val position = Localization.getDurationString(progressSeconds.toLong())
        if (position !== binding!!.detailPositionView.getText()) {
            binding!!.detailPositionView.setText(position)
        }
        if (binding!!.positionView.getVisibility() != View.VISIBLE) {
            binding!!.positionView.animate(true, 100)
            binding!!.detailPositionView.animate(true, 100)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    ////////////////////////////////////////////////////////////////////////// */
    override fun onViewCreated() {
        tryAddVideoPlayerView()
    }

    override fun onQueueUpdate(queue: PlayQueue) {
        playQueue = queue
        if (DEBUG) {
            Log.d(
                TAG,
                (
                    "onQueueUpdate() called with: serviceId = [" +
                        serviceId + "], url = [" + url + "], name = [" +
                        title + "], playQueue = [" + playQueue + "]"
                    )
            )
        }

        // Register broadcast receiver to listen to playQueue changes
        // and hide the overlayPlayQueueButton when the playQueue is empty / destroyed.
        if (playQueue != null && playQueue!!.getBroadcastReceiver() != null) {
            playQueue!!.getBroadcastReceiver()!!.subscribe(
                io.reactivex.rxjava3.functions.Consumer { event: PlayQueueEvent? -> updateOverlayPlayQueueButtonVisibility() }
            )
        }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        val stackPeek: StackItem? = stack.peek()
        if (stackPeek != null && stackPeek.getPlayQueue() != queue) {
            val playQueueItem = queue.getItem()
            if (playQueueItem != null) {
                stack.push(
                    StackItem(
                        playQueueItem.getServiceId(), playQueueItem.getUrl(),
                        playQueueItem.getTitle(), queue
                    )
                )
                return
            } // else continue below
        }

        val stackWithQueue = findQueueInStack(queue)
        if (stackWithQueue != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and
            // no longer able to track progress. That's why we update our cached disposed
            // queue with the new one that is active and have the same history.
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.setPlayQueue(queue)
        }
    }

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters?
    ) {
        setOverlayPlayPauseImage(player != null && player!!.isPlaying())

        if (state == Player.STATE_PLAYING) {
            if (binding!!.positionView.getAlpha() != 1.0f && player!!.getPlayQueue() != null && player!!.getPlayQueue()!!
                .getItem() != null && player!!.getPlayQueue()!!.getItem()!!.getUrl() == url
            ) {
                binding!!.positionView.animate(true, 100)
                binding!!.detailPositionView.animate(true, 100)
            }
        }
    }

    override fun onProgressUpdate(
        currentProgress: Int,
        duration: Int,
        bufferPercent: Int
    ) {
        // Progress updates every second even if media is paused. It's useless until playing
        if (!player!!.isPlaying() || playQueue == null) {
            return
        }

        if (player!!.getPlayQueue()!!.getItem()!!.getUrl() == url) {
            updatePlaybackProgress(currentProgress.toLong(), duration.toLong())
        }
    }

    override fun onMetadataUpdate(info: StreamInfo, queue: PlayQueue) {
        val item = findQueueInStack(queue)
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.setTitle(info.getName())
            item.setUrl(info.getUrl())
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (queue != playQueue) {
            return
        }

        updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnails())
        if (currentInfo != null && info.getUrl() == currentInfo!!.getUrl()) {
            return
        }

        currentInfo = info
        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), queue)
        setAutoPlay(false)
        // Delay execution just because it freezes the main thread, and while playing
        // next/previous video you see visual glitches
        // (when non-vertical video goes after vertical video)
        prepareAndHandleInfoIfNeededAfterDelay(info, true, 200)
    }

    override fun onPlayerError(error: PlaybackException?, isCatchableException: Boolean) {
        if (!isCatchableException) {
            // Properly exit from fullscreen
            toggleFullscreenIfInFullscreenMode()
            hideMainPlayerOnLoadingNewStream()
        }
    }

    override fun onServiceStopped() {
        // the binding could be null at this point, if the app is finishing
        if (binding != null) {
            setOverlayPlayPauseImage(false)
            if (currentInfo != null) {
                updateOverlayData(
                    currentInfo!!.getName(),
                    currentInfo!!.getUploaderName(),
                    currentInfo!!.getThumbnails()
                )
            }
            updateOverlayPlayQueueButtonVisibility()
        }
    }

    override fun onFullscreenStateChanged(fullscreen: Boolean) {
        setupBrightness()
        if (!this.isPlayerAndPlayerServiceAvailable || player!!.UIs()
            .getOpt<MainPlayerUi>(MainPlayerUi::class.java).isEmpty() ||
            this.root.map<ViewParent?>(Function { obj: View? -> obj!!.getParent() }).isEmpty()
        ) {
            return
        }

        if (fullscreen) {
            hideSystemUiIfNeeded()
            binding!!.overlayPlayPauseButton.requestFocus()
        } else {
            showSystemUi()
        }

        if (binding!!.relatedItemsLayout != null) {
            binding!!.relatedItemsLayout!!.setVisibility(if (fullscreen) View.GONE else View.VISIBLE)
        }
        scrollToTop()

        tryAddVideoPlayerView()
    }

    override fun onScreenRotationButtonClicked() {
        // In tablet user experience will be better if screen will not be rotated
        // from landscape to portrait every time.
        // Just turn on fullscreen mode in landscape orientation
        // or portrait & unlocked global orientation
        val isLandscape = DeviceUtils.isLandscape(requireContext())
        if (DeviceUtils.isTablet(activity) &&
            (!PlayerHelper.globalScreenOrientationLocked(activity) || isLandscape)
        ) {
            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
                .ifPresent(Consumer { obj: MainPlayerUi? -> obj!!.toggleFullscreen() })
            return
        }

        val newOrientation = if (isLandscape)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        activity.setRequestedOrientation(newOrientation)
    }

    /*
     * Will scroll down to description view after long click on moreOptionsButton
     * */
    override fun onMoreOptionsLongClicked() {
        val params =
            binding!!.appBarLayout.getLayoutParams() as CoordinatorLayout.LayoutParams
        val behavior = params.getBehavior() as AppBarLayout.Behavior?
        val valueAnimator = ValueAnimator
            .ofInt(0, -binding!!.playerPlaceholder.getHeight())
        valueAnimator.setInterpolator(DecelerateInterpolator())
        valueAnimator.addUpdateListener(
            AnimatorUpdateListener { animation: ValueAnimator? ->
                behavior!!.setTopAndBottomOffset(animation!!.getAnimatedValue() as Int)
                binding!!.appBarLayout.requestLayout()
            }
        )
        valueAnimator.setInterpolator(DecelerateInterpolator())
        valueAnimator.setDuration(500)
        valueAnimator.start()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun showSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "showSystemUi() called")
        }

        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(0)
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.getWindow().setStatusBarColor(
            ThemeHelper.resolveColorFromAttr(
                requireContext(), android.R.attr.colorPrimary
            )
        )
    }

    private fun hideSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "hideSystemUi() called")
        }

        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        var visibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        // In multiWindow mode status bar is not transparent for devices with cutout
        // if I include this flag. So without it is better in this case
        val isInMultiWindow = DeviceUtils.isInMultiWindow(activity)
        if (!isInMultiWindow) {
            visibility = visibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(visibility)

        if (isInMultiWindow || this.isFullscreen) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT)
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT)
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    // Listener implementation
    override fun hideSystemUiIfNeeded() {
        if (this.isFullscreen &&
            bottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_EXPANDED
        ) {
            hideSystemUi()
        }
    }

    private val isFullscreen: Boolean
        get() = this.isPlayerAvailable && player!!.UIs()
            .getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
            .map<Boolean?>(Function { obj: VideoPlayerUi? -> obj!!.isFullscreen() })
            .orElse(false)

    private fun playerIsNotStopped(): Boolean {
        return this.isPlayerAvailable && !player!!.isStopped()
    }

    private fun restoreDefaultBrightness() {
        val lp = activity.getWindow().getAttributes()
        if (lp.screenBrightness == -1f) {
            return
        }

        // Restore the old  brightness when fragment.onPause() called or
        // when a player is in portrait
        lp.screenBrightness = -1f
        activity.getWindow().setAttributes(lp)
    }

    private fun setupBrightness() {
        if (activity == null) {
            return
        }

        val lp = activity.getWindow().getAttributes()
        if (!this.isFullscreen || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            // Apply system brightness when the player is not in fullscreen
            restoreDefaultBrightness()
        } else {
            // Do not restore if user has disabled brightness gesture
            if ((
                PlayerHelper.getActionForRightGestureSide(activity)
                    != getString(R.string.brightness_control_key)
                ) && (
                    PlayerHelper.getActionForLeftGestureSide(
                            activity
                        )
                        != getString(R.string.brightness_control_key)
                    )
            ) {
                return
            }
            // Restore already saved brightness level
            val brightnessLevel = PlayerHelper.getScreenBrightness(activity)
            if (brightnessLevel == lp.screenBrightness) {
                return
            }
            lp.screenBrightness = brightnessLevel
            activity.getWindow().setAttributes(lp)
        }
    }

    /**
     * Make changes to the UI to accommodate for better usability on bigger screens such as TVs
     * or in Android's desktop mode (DeX etc).
     */
    private fun accommodateForTvAndDesktopMode() {
        if (DeviceUtils.isTv(getContext())) {
            // remove ripple effects from detail controls
            val transparent = ContextCompat.getColor(
                requireContext(),
                R.color.transparent_background_color
            )
            binding!!.detailControlsPlaylistAppend.setBackgroundColor(transparent)
            binding!!.detailControlsBackground.setBackgroundColor(transparent)
            binding!!.detailControlsPopup.setBackgroundColor(transparent)
            binding!!.detailControlsDownload.setBackgroundColor(transparent)
            binding!!.detailControlsShare.setBackgroundColor(transparent)
            binding!!.detailControlsOpenInBrowser.setBackgroundColor(transparent)
            binding!!.detailControlsPlayWithKodi.setBackgroundColor(transparent)
        }
        if (DeviceUtils.isDesktopMode(requireContext())) {
            // Remove the "hover" overlay (since it is visible on all mouse events and interferes
            // with the video content being played)
            binding!!.detailThumbnailRootLayout.setForeground(null)
        }
    }

    private fun checkLandscape() {
        if ((!player!!.isPlaying() && player!!.getPlayQueue() !== playQueue) ||
            player!!.getPlayQueue() == null
        ) {
            setAutoPlay(true)
        }

        player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
            .ifPresent(Consumer { obj: MainPlayerUi? -> obj!!.checkLandscape() })
        // Let's give a user time to look at video information page if video is not playing
        if (PlayerHelper.globalScreenOrientationLocked(activity) && !player!!.isPlaying()) {
            player!!.play()
        }
    }

    /*
     * Means that the player fragment was swiped away via BottomSheetLayout
     * and is empty but ready for any new actions. See cleanUp()
     * */
    private fun wasCleared(): Boolean {
        return url == null
    }

    private fun findQueueInStack(queue: PlayQueue?): StackItem? {
        var item: StackItem? = null
        val iterator: MutableIterator<StackItem?> = stack.descendingIterator()
        while (iterator.hasNext()) {
            val next = iterator.next()!!
            if (next.getPlayQueue().equals(queue)) {
                item = next
                break
            }
        }
        return item
    }

    private fun replaceQueueIfUserConfirms(onAllow: Runnable) {
        val activeQueue = if (this.isPlayerAvailable) player!!.getPlayQueue() else null

        // Player will have STATE_IDLE when a user pressed back button
        if (PlayerHelper.isClearingQueueConfirmationRequired(activity) &&
            playerIsNotStopped() &&
            activeQueue != playQueue
        ) {
            showClearingQueueConfirmation(onAllow)
        } else {
            onAllow.run()
        }
    }

    private fun showClearingQueueConfirmation(onAllow: Runnable) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.clear_queue_confirmation_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    onAllow.run()
                    dialog!!.dismiss()
                }
            )
            .show()
    }

    private fun showExternalVideoPlaybackDialog() {
        if (currentInfo == null) {
            return
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.select_quality_external_players)
        builder.setNeutralButton(
            R.string.open_in_browser,
            DialogInterface.OnClickListener { dialog: DialogInterface?, i: Int ->
                ShareUtils.openUrlInBrowser(
                    requireActivity(),
                    url
                )
            }
        )

        val videoStreamsForExternalPlayers =
            ListHelper.getSortedStreamVideosList(
                activity,
                ListHelper.getUrlAndNonTorrentStreams<VideoStream?>(currentInfo!!.getVideoStreams()),
                ListHelper.getUrlAndNonTorrentStreams<VideoStream?>(currentInfo!!.getVideoOnlyStreams()),
                false,
                false
            )

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players)
            builder.setPositiveButton(R.string.ok, null)
        } else {
            val selectedVideoStreamIndexForExternalPlayers =
                ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers)
            val resolutions = videoStreamsForExternalPlayers.map {
                it!!.getResolution() as CharSequence
            }.toTypedArray()

            builder.setSingleChoiceItems(
                resolutions, selectedVideoStreamIndexForExternalPlayers,
                null
            )
            builder.setNegativeButton(R.string.cancel, null)
            builder.setPositiveButton(
                R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, i: Int ->
                    val index = (dialog as AlertDialog).getListView().getCheckedItemPosition()
                    // We don't have to manage the index validity because if there is no stream
                    // available for external players, this code will be not executed and if there is
                    // no stream which matches the default resolution, 0 is returned by
                    // ListHelper.getDefaultResolutionIndex.
                    // The index cannot be outside the bounds of the list as its always between 0 and
                    // the list size - 1, .
                    startOnExternalPlayer(
                        activity, currentInfo!!,
                        videoStreamsForExternalPlayers.get(index)!!
                    )
                }
            )
        }
        builder.show()
    }

    private fun showExternalAudioPlaybackDialog() {
        if (currentInfo == null) {
            return
        }

        val audioStreams = ListHelper.getUrlAndNonTorrentStreams<AudioStream?>(
            currentInfo!!.getAudioStreams()
        )
        val audioTracks =
            ListHelper.getFilteredAudioStreams(activity, audioStreams)

        if (audioTracks.isEmpty()) {
            Toast.makeText(
                activity, R.string.no_audio_streams_available_for_external_players,
                Toast.LENGTH_SHORT
            ).show()
        } else if (audioTracks.size == 1) {
            startOnExternalPlayer(activity, currentInfo!!, audioTracks.get(0)!!)
        } else {
            val selectedAudioStream =
                ListHelper.getDefaultAudioFormat(activity, audioTracks)
            val trackNames = audioTracks
                .map { audioStream: AudioStream? ->
                    Localization.audioTrackName(
                        activity,
                        audioStream
                    )
                }.toTypedArray()

            AlertDialog.Builder(activity)
                .setTitle(R.string.select_audio_track_external_players)
                .setNeutralButton(
                    R.string.open_in_browser,
                    DialogInterface.OnClickListener { dialog: DialogInterface?, i: Int ->
                        ShareUtils.openUrlInBrowser(
                            requireActivity(),
                            url
                        )
                    }
                )
                .setSingleChoiceItems(trackNames, selectedAudioStream, null)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                    R.string.ok,
                    DialogInterface.OnClickListener { dialog: DialogInterface?, i: Int ->
                        val index = (dialog as AlertDialog).getListView()
                            .getCheckedItemPosition()
                        startOnExternalPlayer(activity, currentInfo!!, audioTracks.get(index)!!)
                    }
                )
                .show()
        }
    }

    /*
     * Remove unneeded information while waiting for a next task
     * */
    private fun cleanUp() {
        // New beginning
        stack.clear()
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        playerHolder.stopService()
        setInitialData(0, null, "", null)
        currentInfo = null
        updateOverlayData(null, null, mutableListOf<Image>())
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Bottom mini player
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * That's for Android TV support. Move focus from main fragment to the player or back
     * based on what is currently selected
     *
     * @param toMain if true than the main fragment will be focused or the player otherwise
     */
    private fun moveFocusToMainFragment(toMain: Boolean) {
        setupBrightness()
        val mainFragment = requireActivity().findViewById<ViewGroup>(R.id.fragment_holder)
        // Hamburger button steels a focus even under bottomSheet
        val toolbar = requireActivity().findViewById<Toolbar>(R.id.toolbar)
        val afterDescendants = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val blockDescendants = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (toMain) {
            mainFragment.setDescendantFocusability(afterDescendants)
            toolbar.setDescendantFocusability(afterDescendants)
            (requireView() as ViewGroup).setDescendantFocusability(blockDescendants)
            // Only focus the mainFragment if the mainFragment (e.g. search-results)
            // or the toolbar (e.g. Textfield for search) don't have focus.
            // This was done to fix problems with the keyboard input, see also #7490
            if (!mainFragment.hasFocus() && !toolbar.hasFocus()) {
                mainFragment.requestFocus()
            }
        } else {
            mainFragment.setDescendantFocusability(blockDescendants)
            toolbar.setDescendantFocusability(blockDescendants)
            (requireView() as ViewGroup).setDescendantFocusability(afterDescendants)
            // Only focus the player if it not already has focus
            if (!binding!!.getRoot().hasFocus()) {
                binding!!.detailThumbnailRootLayout.requestFocus()
            }
        }
    }

    /**
     * When the mini player exists the view underneath it is not touchable.
     * Bottom padding should be equal to the mini player's height in this case
     *
     * @param showMore whether main fragment should be expanded or not
     */
    private fun manageSpaceAtTheBottom(showMore: Boolean) {
        val peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height)
        val holder = requireActivity().findViewById<ViewGroup>(R.id.fragment_holder)
        val newBottomPadding: Int
        if (showMore) {
            newBottomPadding = 0
        } else {
            newBottomPadding = peekHeight
        }
        if (holder.getPaddingBottom() == newBottomPadding) {
            return
        }
        holder.setPadding(
            holder.getPaddingLeft(),
            holder.getPaddingTop(),
            holder.getPaddingRight(),
            newBottomPadding
        )
    }

    private fun setupBottomPlayer() {
        val params =
            binding!!.appBarLayout.getLayoutParams() as CoordinatorLayout.LayoutParams
        val behavior = params.getBehavior() as AppBarLayout.Behavior?

        val bottomSheetLayout = activity.findViewById<FrameLayout>(R.id.fragment_player_holder)
        bottomSheetBehavior = BottomSheetBehavior.from<FrameLayout?>(bottomSheetLayout)
        bottomSheetBehavior!!.setState(lastStableBottomSheetState)
        updateBottomSheetState(lastStableBottomSheetState)

        val peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height)
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false)
            bottomSheetBehavior!!.setPeekHeight(peekHeight)
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding!!.overlayLayout.setAlpha(MAX_OVERLAY_ALPHA)
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                binding!!.overlayLayout.setAlpha(0f)
                setOverlayElementsClickable(false)
            }
        }

        bottomSheetCallback = object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateBottomSheetState(newState)

                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        moveFocusToMainFragment(true)
                        manageSpaceAtTheBottom(true)

                        bottomSheetBehavior!!.setPeekHeight(0)
                        cleanUp()
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> {
                        moveFocusToMainFragment(false)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior!!.setPeekHeight(peekHeight)
                        // Disable click because overlay buttons located on top of buttons
                        // from the player
                        setOverlayElementsClickable(false)
                        hideSystemUiIfNeeded()
                        // Conditions when the player should be expanded to fullscreen
                        if (DeviceUtils.isLandscape(requireContext()) &&
                            this@VideoDetailFragment.isPlayerAvailable &&
                            player!!.isPlaying() &&
                            !this@VideoDetailFragment.isFullscreen && !DeviceUtils.isTablet(activity)
                        ) {
                            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
                                .ifPresent(Consumer { obj: MainPlayerUi? -> obj!!.toggleFullscreen() })
                        }
                        setOverlayLook(binding!!.appBarLayout, behavior, 1f)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        moveFocusToMainFragment(true)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior!!.setPeekHeight(peekHeight)

                        // Re-enable clicks
                        setOverlayElementsClickable(true)
                        if (this@VideoDetailFragment.isPlayerAvailable) {
                            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java)
                                .ifPresent(Consumer { obj: MainPlayerUi? -> obj!!.closeItemsList() })
                        }
                        setOverlayLook(binding!!.appBarLayout, behavior, 0f)
                    }

                    BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        if (this@VideoDetailFragment.isFullscreen) {
                            showSystemUi()
                        }
                        if (this@VideoDetailFragment.isPlayerAvailable) {
                            player!!.UIs().getOpt<MainPlayerUi>(MainPlayerUi::class.java).ifPresent(
                                Consumer { ui: MainPlayerUi? ->
                                    if (ui!!.isControlsVisible()) {
                                        ui.hideControls(0, 0)
                                    }
                                }
                            )
                        }
                    }

                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                setOverlayLook(binding!!.appBarLayout, behavior, slideOffset)
            }
        }

        bottomSheetBehavior!!.addBottomSheetCallback(bottomSheetCallback!!)

        // User opened a new page and the player will hide itself
        activity.getSupportFragmentManager()
            .addOnBackStackChangedListener(
                FragmentManager.OnBackStackChangedListener {
                    if (bottomSheetBehavior!!.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
                    }
                }
            )
    }

    private fun updateOverlayPlayQueueButtonVisibility() {
        val isPlayQueueEmpty =
            player == null || // no player => no play queue :)
                player!!.getPlayQueue() == null || player!!.getPlayQueue()!!.isEmpty()
        if (binding != null) {
            // binding is null when rotating the device...
            binding!!.overlayPlayQueueButton.setVisibility(
                if (isPlayQueueEmpty) View.GONE else View.VISIBLE
            )
        }
    }

    private fun updateOverlayData(
        overlayTitle: String?,
        uploader: String?,
        thumbnails: MutableList<Image>
    ) {
        binding!!.overlayTitleTextView.setText(if (TextUtils.isEmpty(overlayTitle)) "" else overlayTitle)
        binding!!.overlayChannelTextView.setText(if (TextUtils.isEmpty(uploader)) "" else uploader)
        binding!!.overlayThumbnail.setImageDrawable(null)
        CoilHelper.loadDetailsThumbnail(binding!!.overlayThumbnail, thumbnails)
    }

    private fun setOverlayPlayPauseImage(playerIsPlaying: Boolean) {
        val drawable = if (playerIsPlaying)
            R.drawable.ic_pause
        else
            R.drawable.ic_play_arrow
        binding!!.overlayPlayPauseButton.setImageResource(drawable)
    }

    private fun setOverlayLook(
        appBar: AppBarLayout,
        behavior: AppBarLayout.Behavior?,
        slideOffset: Float
    ) {
        // SlideOffset < 0 when mini player is about to close via swipe.
        // Stop animation in this case
        if (behavior == null || slideOffset < 0) {
            return
        }
        binding!!.overlayLayout.setAlpha(
            min(
                MAX_OVERLAY_ALPHA.toDouble(),
                (1 - slideOffset).toDouble()
            ).toFloat()
        )
        // These numbers are not special. They just do a cool transition
        behavior.setTopAndBottomOffset(
            (-binding!!.detailThumbnailImageView.getHeight() * 2 * (1 - slideOffset) / 3).toInt()
        )
        appBar.requestLayout()
    }

    private fun setOverlayElementsClickable(enable: Boolean) {
        binding!!.overlayThumbnail.setClickable(enable)
        binding!!.overlayThumbnail.setLongClickable(enable)
        binding!!.overlayMetadataLayout.setClickable(enable)
        binding!!.overlayMetadataLayout.setLongClickable(enable)
        binding!!.overlayButtonsLayout.setClickable(enable)
        binding!!.overlayPlayQueueButton.setClickable(enable)
        binding!!.overlayPlayPauseButton.setClickable(enable)
        binding!!.overlayCloseButton.setClickable(enable)
    }

    val isPlayerAvailable: Boolean
        // helpers to check the state of player and playerService
        get() = player != null

    fun noPlayerServiceAvailable(): Boolean {
        return playerService == null
    }

    val isPlayerAndPlayerServiceAvailable: Boolean
        get() = player != null && playerService != null

    val root: Optional<View?>
        get() = Optional.ofNullable<Player?>(player)
            .flatMap<VideoPlayerUi?>(
                Function { player1: Player? ->
                    player1!!.UIs().getOpt<VideoPlayerUi>(VideoPlayerUi::class.java)
                }
            )
            .map<View?>(
                Function { playerUi: VideoPlayerUi? ->
                    playerUi!!.getBinding().getRoot()
                }
            )

    private fun updateBottomSheetState(newState: Int) {
        bottomSheetState = newState
        if (newState != BottomSheetBehavior.STATE_DRAGGING &&
            newState != BottomSheetBehavior.STATE_SETTLING
        ) {
            lastStableBottomSheetState = newState
        }
    }

    companion object {
        const val KEY_SWITCHING_PLAYERS: String = "switching_players"

        private const val MAX_OVERLAY_ALPHA = 0.9f
        private const val MAX_PLAYER_HEIGHT = 0.7f

        @JvmField
        val ACTION_SHOW_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER"
        @JvmField
        val ACTION_HIDE_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER"
        @JvmField
        val ACTION_PLAYER_STARTED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED"
        @JvmField
        val ACTION_VIDEO_FRAGMENT_RESUMED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED"
        @JvmField
        val ACTION_VIDEO_FRAGMENT_STOPPED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED"

        private const val COMMENTS_TAB_TAG = "COMMENTS"
        private const val RELATED_TAB_TAG = "NEXT VIDEO"
        private const val DESCRIPTION_TAB_TAG = "DESCRIPTION TAB"
        private const val EMPTY_TAB_TAG = "EMPTY TAB"

        /*//////////////////////////////////////////////////////////////////////// */
        @JvmStatic
        fun getInstance(
            serviceId: Int,
            url: String?,
            name: String,
            queue: PlayQueue?
        ): VideoDetailFragment {
            val instance = VideoDetailFragment()
            instance.setInitialData(serviceId, url, name, queue)
            return instance
        }

        @JvmStatic
        val instanceInCollapsedState: VideoDetailFragment
            get() {
                val instance = VideoDetailFragment()
                instance.updateBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
                return instance
            }

        /*//////////////////////////////////////////////////////////////////////////
   // OwnStack
   ////////////////////////////////////////////////////////////////////////// */
        /**
         * Stack that contains the "navigation history".<br></br>
         * The peek is the current video.
         */
        private var stack = LinkedList<StackItem?>()
    }
}
