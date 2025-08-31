package org.schabi.newpipe.fragments.detail

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.os.postDelayed
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import coil3.util.CoilUtils
import com.evernote.android.state.State
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.App
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorPanelHelper
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.EmptyFragment
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment
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
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.PermissionHelper.checkStoragePermissions
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.viewmodels.VideoDetailViewModel
import org.schabi.newpipe.viewmodels.util.Resource
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VideoDetailFragment :
    Fragment(),
    BackPressable,
    PlayerServiceExtendedEventListener,
    OnKeyDownListener {
    private val viewModel: VideoDetailViewModel by viewModels()
    private var currentInfo: StreamInfo? = null

    // player objects
    @JvmField @State var autoPlayEnabled = true
    private var playerService: PlayerService? = null
    private var player: Player? = null

    // views
    // can't make this lateinit because it needs to be set to null when the view is destroyed
    private var nullableBinding: FragmentVideoDetailBinding? = null
    private val binding: FragmentVideoDetailBinding get() = nullableBinding!!
    private lateinit var pageAdapter: TabAdapter
    private lateinit var settingsContentObserver: ContentObserver

    // tabs
    private var showComments = false
    private var showRelatedItems = false
    private var showDescription = false
    private lateinit var selectedTabTag: String
    @AttrRes val tabIcons = ArrayList<Int>()
    @StringRes val tabContentDescriptions = ArrayList<Int>()
    private var tabSettingsChanged = false
    private var lastAppBarVerticalOffset = Int.MAX_VALUE // prevents useless updates

    private val preferenceChangeListener =
        OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (getString(R.string.show_comments_key) == key) {
                showComments = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_next_video_key) == key) {
                showRelatedItems = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            } else if (getString(R.string.show_description_key) == key) {
                showDescription = sharedPreferences.getBoolean(key, true)
                tabSettingsChanged = true
            }
        }

    // bottom sheet
    @JvmField @State var bottomSheetState: Int = BottomSheetBehavior.STATE_EXPANDED
    @JvmField @State var lastStableBottomSheetState: Int = BottomSheetBehavior.STATE_EXPANDED
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout?>
    private lateinit var bottomSheetCallback: BottomSheetCallback
    private lateinit var broadcastReceiver: BroadcastReceiver

    // disposables
    private val disposables = CompositeDisposable()
    private var positionSubscriber: Disposable? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    ////////////////////////////////////////////////////////////////////////// */
    override fun onServiceConnected(connectedPlayerService: PlayerService) {
        playerService = connectedPlayerService
    }

    override fun onPlayerConnected(connectedPlayer: Player, playAfterConnect: Boolean) {
        player = connectedPlayer
        val context = requireContext()

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded()

        if (player?.videoPlayerSelected() != true && !playAfterConnect) {
            return
        }

        val mainUi = player?.UIs()?.get(MainPlayerUi::class)
        if (DeviceUtils.isLandscape(context)) {
            // If the video is playing but orientation changed
            // let's make the video in fullscreen again
            checkLandscape()
        } else if (mainUi != null && mainUi.isFullscreen && !mainUi.isVerticalVideo &&
            // Tablet UI has orientation-independent fullscreen
            !DeviceUtils.isTablet(context)
        ) {
            // Device is in portrait orientation after rotation but UI is in fullscreen.
            // Return back to non-fullscreen state
            mainUi.toggleFullscreen()
        }

        if (playAfterConnect || (currentInfo != null && this.isAutoplayEnabled && mainUi == null)) {
            autoPlayEnabled = true // forcefully start playing
            openVideoPlayerAutoFullscreen()
        }
        updateOverlayPlayQueueButtonVisibility()
    }

    override fun onPlayerDisconnected() {
        player = null
        // the binding could be null at this point, if the app is finishing
        if (nullableBinding != null) {
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

        val activity = requireActivity()
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true)
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true)
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true)
        selectedTabTag = prefs.getString(
            getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG
        )!!
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        setupBroadcastReceiver()

        settingsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!PlayerHelper.globalScreenOrientationLocked(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                }
            }
        }
        requireActivity().contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
            settingsContentObserver
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentVideoDetailBinding.inflate(inflater, container, false)
        nullableBinding = binding

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        pageAdapter = TabAdapter(childFragmentManager)
        binding.viewPager.setAdapter(pageAdapter)
        binding.tabLayout.setupWithViewPager(this@VideoDetailFragment.binding.viewPager)

        binding.detailThumbnailRootLayout.requestFocus()

        binding.detailControlsPlayWithKodi.isVisible =
            KoreUtils.shouldShowPlayWithKodi(requireContext(), viewModel.serviceId)
        binding.detailControlsCrashThePlayer.isVisible =
            BuildConfig.DEBUG && prefs.getBoolean(getString(R.string.show_crash_the_player_key), false)

        accommodateForTvAndDesktopMode()
        setupBottomPlayer()
        initTabs()

        lifecycleScope.launch {
            viewModel.streamState.collectLatest {
                when (val state = viewModel.streamState.value) {
                    is Resource.Loading -> {
                        showLoading()
                    }

                    is Resource.Success -> {
                        val info = state.data

                        hideMainPlayerOnLoadingNewStream()
                        if (info.ageLimit != StreamExtractor.NO_AGE_LIMIT &&
                            !prefs.getBoolean(getString(R.string.show_age_restricted_content), false)
                        ) {
                            hideAgeRestrictedContent()
                        } else {
                            handleResult(info)
                            showContent()
                            if (viewModel.addToBackStack) {
                                if (viewModel.playQueue == null) {
                                    viewModel.playQueue = SinglePlayQueue(info)
                                }
                                if (stack.peek()?.playQueue != viewModel.playQueue) {
                                    // also if stack empty (!)
                                    val item = StackItem(viewModel.serviceId, viewModel.url,
                                        viewModel.title, viewModel.playQueue)
                                    stack.push(item)
                                }
                            }

                            if (isAutoplayEnabled) {
                                openVideoPlayerAutoFullscreen()
                            }
                        }
                    }

                    is Resource.Error -> {
                        binding.detailThumbnailImageView.setImageResource(R.drawable.not_available_monkey)
                        binding.detailThumbnailImageView.animate(false, 0, AnimationType.ALPHA, 0) {
                            binding.detailThumbnailImageView.animate(true, 500)
                        }

                        // hide related streams for tablets
                        binding.relatedItemsLayout?.isInvisible = true

                        // hide comments / related streams / description tabs
                        binding.viewPager.visibility = View.GONE
                        binding.tabLayout.visibility = View.GONE
                    }
                }
            }
        }

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        restoreDefaultBrightness()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putString(
                getString(R.string.stream_info_selected_tab_key),
                pageAdapter.getItemTitle(binding.viewPager.currentItem)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onResume() called")
        }

        requireActivity().sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_RESUMED))

        updateOverlayPlayQueueButtonVisibility()

        setupBrightness()

        if (tabSettingsChanged) {
            tabSettingsChanged = false
            initTabs()
            currentInfo?.let { updateTabs(it) }
        }
    }

    override fun onStop() {
        super.onStop()

        val activity = requireActivity()
        if (!activity.isChangingConfigurations) {
            activity.sendBroadcast(Intent(ACTION_VIDEO_FRAGMENT_STOPPED))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val activity = requireActivity()
        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing && player?.videoPlayerSelected() == true) {
            PlayerHolder.stopService()
        } else {
            PlayerHolder.setListener(null)
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        activity.unregisterReceiver(broadcastReceiver)
        activity.contentResolver.unregisterContentObserver(settingsContentObserver)

        positionSubscriber?.dispose()
        disposables.clear()
        positionSubscriber = null
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)

        if (activity.isFinishing) {
            stack.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nullableBinding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                NavigationHelper.openVideoDetailFragment(
                    requireContext(), parentFragmentManager,
                    viewModel.serviceId, viewModel.url, viewModel.title,
                    null, false
                )
            } else {
                Log.e(TAG, "ReCaptcha failed")
            }
        } else {
            Log.e(TAG, "Request code from activity not supported [$requestCode]")
        }
    }

    private fun hideAgeRestrictedContent() {
        val errorHelper = ErrorPanelHelper(this, binding.root, viewModel::startLoading)
        errorHelper.showTextError(
            getString(
                R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)
            )
        )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    ////////////////////////////////////////////////////////////////////////// */
    private fun setOnClickListeners(info: StreamInfo) {
        binding.detailTitleRootLayout.setOnClickListener { toggleTitleAndSecondaryControls() }
        binding.detailUploaderRootLayout.setOnClickListener {
            if (info.subChannelUrl.isEmpty()) {
                if (info.uploaderUrl.isNotEmpty()) {
                    openChannel(info.uploaderUrl, info.uploaderName, info.serviceId)
                } else if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Can't open sub-channel because we got no channel URL")
                }
            } else {
                openChannel(info.subChannelUrl, info.subChannelName, info.serviceId)
            }
        }
        binding.detailThumbnailRootLayout.setOnClickListener {
            autoPlayEnabled = true // forcefully start playing
            // FIXME Workaround #7427
            player?.setRecovery()
            openVideoPlayerAutoFullscreen()
        }

        binding.detailControlsBackground.setOnClickListener { openBackgroundPlayer(false) }
        binding.detailControlsPopup.setOnClickListener { openPopupPlayer(false) }
        binding.detailControlsPlaylistAppend.setOnClickListener {
            val fragment = parentFragmentManager.findFragmentById(R.id.fragment_holder)

            // commit previous pending changes to database
            if (fragment is LocalPlaylistFragment) {
                fragment.saveImmediate()
            } else if (fragment is MainFragment) {
                fragment.commitPlaylistTabs()
            }

            disposables.add(
                PlaylistDialog.createCorrespondingDialog(
                    requireContext(),
                    listOf(StreamEntity(info))
                ) { dialog -> dialog.show(getParentFragmentManager(), TAG) }
            )
        }
        binding.detailControlsDownload.setOnClickListener {
            if (checkStoragePermissions(activity, PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                openDownloadDialog()
            }
        }
        binding.detailControlsShare.setOnClickListener {
            ShareUtils.shareText(requireContext(), info.name, info.url, info.thumbnails)
        }
        binding.detailControlsOpenInBrowser.setOnClickListener {
            ShareUtils.openUrlInBrowser(requireContext(), info.url)
        }
        binding.detailControlsPlayWithKodi.setOnClickListener {
            KoreUtils.playWithKore(requireContext(), info.url.toUri())
        }
        if (BuildConfig.DEBUG) {
            binding.detailControlsCrashThePlayer.setOnClickListener {
                VideoDetailPlayerCrasher.onCrashThePlayer(requireContext(), player)
            }
        }

        val overlayListener = View.OnClickListener {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        binding.overlayThumbnail.setOnClickListener(overlayListener)
        binding.overlayMetadataLayout.setOnClickListener(overlayListener)
        binding.overlayButtonsLayout.setOnClickListener(overlayListener)
        binding.overlayCloseButton.setOnClickListener {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }
        binding.overlayPlayQueueButton.setOnClickListener {
            NavigationHelper.openPlayQueue(requireContext())
        }
        binding.overlayPlayPauseButton.setOnClickListener {
            if (!playerIsStopped) {
                player!!.playPause()
                player!!.UIs().get(VideoPlayerUi::class)?.hideControls(0, 0)
                showSystemUi()
            } else {
                autoPlayEnabled = true // forcefully start playing
                openVideoPlayer(false)
            }
            setOverlayPlayPauseImage(player?.isPlaying == true)
        }
    }

    private fun setOnLongClickListeners(info: StreamInfo) {
        binding.detailTitleRootLayout.setOnLongClickListener {
            binding.detailVideoTitleView.text?.toString()?.let {
                if (!it.isBlank()) {
                    ShareUtils.copyToClipboard(requireContext(), it)
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener false
        }
        binding.detailUploaderRootLayout.setOnLongClickListener {
            if (info.subChannelUrl.isEmpty()) {
                Log.w(TAG, "Can't open parent channel because we got no parent channel URL")
            } else {
                openChannel(info.uploaderUrl, info.uploaderName, info.serviceId)
            }
            return@setOnLongClickListener true
        }

        binding.detailControlsBackground.setOnLongClickListener {
            openBackgroundPlayer(true)
            return@setOnLongClickListener true
        }
        binding.detailControlsPopup.setOnLongClickListener {
            openPopupPlayer(true)
            return@setOnLongClickListener true
        }
        binding.detailControlsDownload.setOnLongClickListener {
            NavigationHelper.openDownloads(activity)
            return@setOnLongClickListener true
        }

        val overlayListener = OnLongClickListener {
            openChannel(info.uploaderUrl, info.uploaderName, info.serviceId)
            return@OnLongClickListener true
        }
        binding.overlayThumbnail.setOnLongClickListener(overlayListener)
        binding.overlayMetadataLayout.setOnLongClickListener(overlayListener)
    }

    private fun openChannel(subChannelUrl: String?, subChannelName: String, serviceId: Int) {
        try {
            NavigationHelper.openChannelFragment(parentFragmentManager, serviceId, subChannelUrl, subChannelName)
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Opening channel fragment", e)
        }
    }

    private fun toggleTitleAndSecondaryControls() {
        if (binding.detailSecondaryControlPanel.isGone) {
            binding.detailVideoTitleView.setMaxLines(10)
            binding.detailToggleSecondaryControlsView
                .animateRotation(VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 180)
            binding.detailSecondaryControlPanel.visibility = View.VISIBLE
        } else {
            binding.detailVideoTitleView.setMaxLines(1)
            binding.detailToggleSecondaryControlsView
                .animateRotation(VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 0)
            binding.detailSecondaryControlPanel.visibility = View.GONE
        }
        // view pager height has changed, update the tab layout
        updateTabLayoutVisibility()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */

    private fun initListeners(info: StreamInfo) {
        setOnClickListeners(info)
        setOnLongClickListeners(info)

        val controlsTouchListener = OnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN &&
                PlayButtonHelper.shouldShowHoldToAppendTip(requireContext())
            ) {
                binding.touchAppendDetail.animate(true, 250, AnimationType.ALPHA, 0) {
                    binding.touchAppendDetail.animate(false, 1500, AnimationType.ALPHA, 1000)
                }
            }
            false
        }
        binding.detailControlsBackground.setOnTouchListener(controlsTouchListener)
        binding.detailControlsPopup.setOnTouchListener(controlsTouchListener)

        binding.appBarLayout.addOnOffsetChangedListener { layout, verticalOffset ->
            // prevent useless updates to tab layout visibility if nothing changed
            if (verticalOffset != lastAppBarVerticalOffset) {
                lastAppBarVerticalOffset = verticalOffset
                // the view was scrolled
                updateTabLayoutVisibility()
            }
        }

        setupBottomPlayer()
        if (!PlayerHolder.isBound) {
            setHeightThumbnail()
        } else {
            PlayerHolder.startService(false, this)
        }
    }

    override fun onKeyDown(keyCode: Int): Boolean {
        return player?.UIs()?.get(VideoPlayerUi::class)?.onKeyDown(keyCode) == true
    }

    override fun onBackPressed(): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onBackPressed() called")
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (this.isFullscreen) {
            if (!DeviceUtils.isTablet(requireActivity())) {
                player!!.pause()
            }
            restoreDefaultOrientation()
            setAutoPlay(false)
            return true
        }

        // If we have something in history of played items we replay it here
        if (player?.videoPlayerSelected() == true && player?.playQueue?.previous() == true) {
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
        setupFromHistoryItem(stack.peek()!!)

        return true
    }

    private fun setupFromHistoryItem(item: StackItem) {
        setAutoPlay(false)
        hideMainPlayerOnLoadingNewStream()

        viewModel.updateData(item.serviceId, item.url, item.title.orEmpty(),
            item.playQueue)
        viewModel.startLoading(false)

        // Maybe an item was deleted in background activity
        if (item.playQueue.item == null) {
            return
        }

        val playQueueItem = item.playQueue.item
        // Update title, url, uploader from the last item in the stack (it's current now)
        if (playQueueItem != null && playerIsStopped) {
            updateOverlayData(playQueueItem.title, playQueueItem.uploader, playQueueItem.thumbnails)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    ////////////////////////////////////////////////////////////////////////// */
    fun selectAndLoadVideo(
        newServiceId: Int,
        newUrl: String?,
        newTitle: String,
        newQueue: PlayQueue?
    ) {
        if (newQueue != null && viewModel.playQueue?.item?.url != newUrl) {
            // Preloading can be disabled since playback is surely being replaced.
            player?.disablePreloadingOfCurrentTrack()
        }

        viewModel.updateData(newServiceId, newUrl, newTitle, newQueue)
        viewModel.addToBackStack = true
        viewModel.startLoading(false)
    }

    private fun prepareAndHandleInfoIfNeededAfterDelay(
        info: StreamInfo,
        scrollToTop: Boolean,
        delay: Long
    ) {
        Handler(Looper.getMainLooper()).postDelayed(delay) {
            if (activity == null) {
                return@postDelayed
            }
            // Data can already be drawn, don't spend time twice
            if (info.name == binding.detailVideoTitleView.getText().toString()) {
                return@postDelayed
            }
            prepareAndHandleInfo(info, scrollToTop)
        }
    }

    private fun prepareAndHandleInfo(info: StreamInfo, scrollToTop: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "prepareAndHandleInfo(info=[$info], scrollToTop=[$scrollToTop]) called")
        }

        showLoading()
        initTabs()

        if (scrollToTop) {
            scrollToTop()
        }
        handleResult(info)
        showContent()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    ////////////////////////////////////////////////////////////////////////// */
    private fun initTabs() {
        pageAdapter.getItemTitle(binding.viewPager.currentItem)
            ?.let { tag -> selectedTabTag = tag }

        pageAdapter.clearAllItems()
        tabIcons.clear()
        tabContentDescriptions.clear()

        if (shouldShowComments()) {
            pageAdapter.addFragment(CommentsFragment(), COMMENTS_TAB_TAG)
            tabIcons.add(R.drawable.ic_comment)
            tabContentDescriptions.add(R.string.comments_tab_description)
        }

        if (showRelatedItems && binding.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), RELATED_TAB_TAG)
            tabIcons.add(R.drawable.ic_art_track)
            tabContentDescriptions.add(R.string.related_items_tab_description)
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), DESCRIPTION_TAB_TAG)
            tabIcons.add(R.drawable.ic_description)
            tabContentDescriptions.add(R.string.description_tab_description)
        }

        if (pageAdapter.count == 0) {
            pageAdapter.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG)
        }
        pageAdapter.notifyDataSetUpdate()

        if (pageAdapter.count >= 2) {
            val position = pageAdapter.getItemPositionByTitle(selectedTabTag)
            if (position != -1) {
                binding.viewPager.setCurrentItem(position)
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
            val tab = binding.tabLayout.getTabAt(i)
            if (tab != null) {
                tab.setIcon(tabIcons[i])
                tab.setContentDescription(tabContentDescriptions[i])
            }
        }
    }

    private fun updateTabs(info: StreamInfo) {
        if (showRelatedItems) {
            when (val relatedItemsLayout = binding.relatedItemsLayout) {
                null -> pageAdapter.updateItem(RELATED_TAB_TAG, RelatedItemsFragment()) // phone
                else -> { // tablet + TV
                    childFragmentManager.commit(allowStateLoss = true) {
                        replace<RelatedItemsFragment>(R.id.relatedItemsLayout)
                    }
                    relatedItemsLayout.isVisible = !this.isFullscreen
                }
            }
        }

        if (showDescription) {
            pageAdapter.updateItem(DESCRIPTION_TAB_TAG, DescriptionFragment(info))
        }

        binding.viewPager.visibility = View.VISIBLE
        // make sure the tab layout is visible
        updateTabLayoutVisibility()
        pageAdapter.notifyDataSetUpdate()
        updateTabIconsAndContentDescriptions()
    }

    private fun shouldShowComments(): Boolean {
        return showComments && try {
            NewPipe.getService(viewModel.serviceId).serviceInfo.mediaCapabilities
                .contains(MediaCapability.COMMENTS)
        } catch (_: ExtractionException) {
            false
        }
    }

    fun updateTabLayoutVisibility() {
        if (nullableBinding == null) {
            // If binding is null we do not need to and should not do anything with its object(s)
            return
        }

        if (pageAdapter.count < 2 || binding.viewPager.visibility != View.VISIBLE) {
            // hide tab layout if there is only one tab or if the view pager is also hidden
            binding.tabLayout.visibility = View.GONE
        } else {
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            binding.tabLayout.post {
                activity?.let { activity ->
                    val pagerHitRect = Rect()
                    binding.viewPager.getHitRect(pagerHitRect)

                    val height = DeviceUtils.getWindowHeight(activity.windowManager)
                    val viewPagerVisibleHeight = height - pagerHitRect.top
                    // see TabLayout.DEFAULT_HEIGHT, which is equal to 48dp
                    val tabLayoutHeight = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics
                    )

                    if (viewPagerVisibleHeight > tabLayoutHeight * 2) {
                        // no translation at all when viewPagerVisibleHeight > tabLayout.height * 3
                        binding.tabLayout.translationY =
                            max(0.0f, tabLayoutHeight * 3 - viewPagerVisibleHeight)
                        binding.tabLayout.visibility = View.VISIBLE
                    } else {
                        // view pager is not visible enough
                        binding.tabLayout.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun scrollToTop() {
        binding.appBarLayout.setExpanded(true, true)
        // notify tab layout of scrolling
        updateTabLayoutVisibility()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun toggleFullscreenIfInFullscreenMode() {
        // If a user watched video inside fullscreen mode and than chose another player
        // return to non-fullscreen mode
        player?.UIs()?.get(MainPlayerUi::class)?.let {
            if (it.isFullscreen) {
                it.toggleFullscreen()
            }
        }
    }

    private fun openBackgroundPlayer(append: Boolean) {
        val activity = requireActivity()
        val useExternalAudioPlayer = PreferenceManager
            .getDefaultSharedPreferences(activity)
            .getBoolean(activity.getString(R.string.use_external_audio_player_key), false)

        toggleFullscreenIfInFullscreenMode()

        // FIXME Workaround #7427
        player?.setRecovery()

        if (useExternalAudioPlayer) {
            showExternalAudioPlaybackDialog()
        } else {
            openNormalBackgroundPlayer(append)
        }
    }

    private fun openPopupPlayer(append: Boolean) {
        val activity = requireActivity()
        if (!PermissionHelper.isPopupEnabledElseAsk(activity)) {
            return
        }

        // See UI changes while remote playQueue changes
        if (player == null) {
            PlayerHolder.startService(false, this)
        } else {
            // FIXME Workaround #7427
            player?.setRecovery()
        }

        toggleFullscreenIfInFullscreenMode()

        val queue = setupPlayQueueForIntent(append)
        if (append) { // resumePlayback: false
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.POPUP)
        } else {
            replaceQueueIfUserConfirms { NavigationHelper.playOnPopupPlayer(activity, queue, true) }
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
        val context = requireContext()
        if (directlyFullscreenIfApplicable &&
            !DeviceUtils.isLandscape(context) &&
            PlayerHelper.globalScreenOrientationLocked(context)
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

        if (PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(this.getString(R.string.use_external_video_player_key), false)
        ) {
            showExternalVideoPlaybackDialog()
        } else {
            replaceQueueIfUserConfirms { this.openMainPlayer() }
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
        if (player == null) {
            PlayerHolder.startService(false, this)
        }

        val queue = setupPlayQueueForIntent(append)
        if (append) {
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.AUDIO)
        } else {
            replaceQueueIfUserConfirms {
                NavigationHelper.playOnBackgroundPlayer(activity, queue, true)
            }
        }
    }

    private fun openMainPlayer() {
        if (playerService == null) {
            PlayerHolder.startService(autoPlayEnabled, this)
            return
        }
        if (currentInfo == null) {
            return
        }

        val queue = setupPlayQueueForIntent(false)
        tryAddVideoPlayerView()

        val playerIntent = NavigationHelper.getPlayerIntent(
            requireContext(), PlayerService::class.java, queue, true, autoPlayEnabled
        )
        ContextCompat.startForegroundService(requireContext(), playerIntent)
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
        if (root == null || playerService == null || player?.videoPlayerSelected() != true) {
            return
        }

        removeVideoPlayerView()
        if (this.isAutoplayEnabled) {
            playerService?.stopForImmediateReusing()
            root.visibility = View.GONE
        } else {
            PlayerHolder.stopService()
        }
    }

    private fun setupPlayQueueForIntent(append: Boolean): PlayQueue {
        if (append) {
            return SinglePlayQueue(currentInfo)
        }

        var queue = viewModel.playQueue
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty) {
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
            context, info.name, info.subChannelName, selectedStream
        )

        val recordManager = HistoryRecordManager(requireContext())
        disposables.add(
            recordManager.onViewed(info)
                .subscribe(
                    { /* successful */ },
                    { throwable -> Log.e(TAG, "Register view failure: ", throwable) }
                )
        )
    }

    private val isExternalPlayerEnabled: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.use_external_video_player_key), false)

    @Suppress("NullableBooleanElvis") // ?: true is clearer than != false
    private val isAutoplayEnabled: Boolean
        // This method overrides default behaviour when setAutoPlay() is called.
        get() = autoPlayEnabled &&
            !this.isExternalPlayerEnabled &&
            (player?.videoPlayerSelected() ?: true) && // if no player present, consider it video
            bottomSheetState != BottomSheetBehavior.STATE_HIDDEN &&
            PlayerHelper.isAutoplayAllowedByUser(requireContext())

    private fun tryAddVideoPlayerView() {
        if (player != null && view != null) {
            // Setup the surface view height, so that it fits the video correctly; this is done also
            // here, and not only in the Handler, to avoid a choppy fullscreen rotation animation.
            setHeightThumbnail()
        }

        // do all the null checks in the posted lambda, too, since the player, the binding and the
        // view could be set or unset before the lambda gets executed on the next main thread cycle
        Handler(Looper.getMainLooper()).post {
            if (player == null || view == null) {
                return@post
            }

            // setup the surface view height, so that it fits the video correctly
            setHeightThumbnail()

            player?.UIs()?.get(MainPlayerUi::class)?.let { playerUi ->
                // sometimes binding would be null here, even though getView() != null above u.u
                nullableBinding?.let { b ->
                    // prevent from re-adding a view multiple times
                    playerUi.removeViewFromParent()
                    b.playerPlaceholder.addView(playerUi.getBinding().getRoot())
                    playerUi.setupVideoSurfaceIfNeeded()
                }
            }
        }
    }

    private fun removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder()
        player?.UIs()?.get(VideoPlayerUi::class)?.removeViewFromParent()
    }

    private fun makeDefaultHeightForVideoPlaceholder() {
        if (view == null) {
            return
        }

        binding.playerPlaceholder.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
        binding.playerPlaceholder.requestLayout()
    }

    private val preDrawListener: OnPreDrawListener = OnPreDrawListener {
        val activity = requireActivity()
        view?.let { view ->
            val decorView = if (DeviceUtils.isInMultiWindow(activity))
                view
            else
                activity.window.decorView
            setHeightThumbnail(decorView.height, resources.displayMetrics)
            view.getViewTreeObserver().removeOnPreDrawListener(preDrawListener)
        }
        return@OnPreDrawListener false
    }

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * [.MAX_PLAYER_HEIGHT])
     */
    private fun setHeightThumbnail() {
        val metrics = resources.displayMetrics
        val activity = requireActivity()
        requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener)

        if (this.isFullscreen) {
            val height = (
                if (DeviceUtils.isInMultiWindow(activity))
                    requireView()
                else
                    activity.window.decorView
                ).height
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics)
            } else {
                requireView().getViewTreeObserver().addOnPreDrawListener(preDrawListener)
            }
        } else {
            val isPortrait = metrics.heightPixels > metrics.widthPixels
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
        binding.detailThumbnailImageView.setLayoutParams(
            FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, newHeight)
        )
        binding.detailThumbnailImageView.setMinimumHeight(newHeight)
        player?.UIs()?.get(VideoPlayerUi::class)?.let {
            val maxHeight = (metrics.heightPixels * MAX_PLAYER_HEIGHT).toInt()
            it.binding.surfaceView.setHeights(
                newHeight,
                if (it.isFullscreen) newHeight else maxHeight
            )
        }
    }

    private fun showContent() {
        binding.detailContentRootHiding.visibility = View.VISIBLE
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when (intent.action) {
                    ACTION_SHOW_MAIN_PLAYER -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
                    ACTION_HIDE_MAIN_PLAYER -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
                    ACTION_PLAYER_STARTED -> {
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!PlayerHolder.isBound) {
                            PlayerHolder.startService(false, this@VideoDetailFragment)
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER)
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER)
        intentFilter.addAction(ACTION_PLAYER_STARTED)
        ContextCompat.registerReceiver(
            requireContext(), broadcastReceiver, intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    ////////////////////////////////////////////////////////////////////////// */
    private fun restoreDefaultOrientation() {
        if (player?.videoPlayerSelected() == true) {
            toggleFullscreenIfInFullscreenMode()
        }

        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        val activity = activity
        if (activity != null && !DeviceUtils.isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    private fun showLoading() {
        // if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if (!ExtractorHelper.isCached(viewModel.serviceId, viewModel.url, InfoCache.Type.STREAM)) {
            binding.detailContentRootHiding.visibility = View.INVISIBLE
        }

        binding.detailThumbnailPlayButton.animate(false, 50)
        binding.detailDurationView.animate(false, 100)
        binding.detailPositionView.visibility = View.GONE
        binding.positionView.visibility = View.GONE

        binding.detailVideoTitleView.text = viewModel.title
        binding.detailVideoTitleView.setMaxLines(1)
        binding.detailVideoTitleView.animate(true, 0)

        binding.detailToggleSecondaryControlsView.visibility = View.GONE
        binding.detailTitleRootLayout.isClickable = false
        binding.detailSecondaryControlPanel.visibility = View.GONE

        binding.relatedItemsLayout?.isVisible = showRelatedItems && !this.isFullscreen

        CoilUtils.dispose(binding.detailThumbnailImageView)
        CoilUtils.dispose(binding.detailSubChannelThumbnailView)
        CoilUtils.dispose(binding.overlayThumbnail)
        CoilUtils.dispose(binding.detailUploaderThumbnailView)

        binding.detailThumbnailImageView.setImageBitmap(null)
        binding.detailSubChannelThumbnailView.setImageBitmap(null)
    }

    fun handleResult(info: StreamInfo) {
        currentInfo = info

        val activity = requireActivity()
        updateTabs(info)
        initListeners(info)

        binding.detailThumbnailPlayButton.animate(true, 200)
        binding.detailVideoTitleView.text = viewModel.title

        binding.detailSubChannelThumbnailView.visibility = View.GONE

        if (info.subChannelName.isEmpty()) {
            displayUploaderAsSubChannel(info)
        } else {
            displayBothUploaderAndSubChannel(info)
        }

        if (info.viewCount >= 0) {
            binding.detailViewCountView.text =
                if (info.streamType == StreamType.AUDIO_LIVE_STREAM) {
                    Localization.listeningCount(activity, info.viewCount)
                } else if (info.streamType == StreamType.LIVE_STREAM) {
                    Localization.localizeWatchingCount(activity, info.viewCount)
                } else {
                    Localization.localizeViewCount(activity, info.viewCount)
                }
            binding.detailViewCountView.visibility = View.VISIBLE
        } else {
            binding.detailViewCountView.visibility = View.GONE
        }

        if (info.dislikeCount == -1L && info.likeCount == -1L) {
            binding.detailThumbsDownImgView.visibility = View.VISIBLE
            binding.detailThumbsUpImgView.visibility = View.VISIBLE
            binding.detailThumbsUpCountView.visibility = View.GONE
            binding.detailThumbsDownCountView.visibility = View.GONE
            binding.detailThumbsDisabledView.visibility = View.VISIBLE
        } else {
            if (info.dislikeCount >= 0) {
                binding.detailThumbsDownCountView.text =
                    Localization.shortCount(activity, info.dislikeCount)
                binding.detailThumbsDownCountView.visibility = View.VISIBLE
                binding.detailThumbsDownImgView.visibility = View.VISIBLE
            } else {
                binding.detailThumbsDownCountView.visibility = View.GONE
                binding.detailThumbsDownImgView.visibility = View.GONE
            }

            if (info.likeCount >= 0) {
                binding.detailThumbsUpCountView.text =
                    Localization.shortCount(activity, info.likeCount)
                binding.detailThumbsUpCountView.visibility = View.VISIBLE
                binding.detailThumbsUpImgView.visibility = View.VISIBLE
            } else {
                binding.detailThumbsUpCountView.visibility = View.GONE
                binding.detailThumbsUpImgView.visibility = View.GONE
            }
            binding.detailThumbsDisabledView.visibility = View.GONE
        }

        if (info.duration > 0) {
            binding.detailDurationView.text = Localization.getDurationString(info.duration)
            binding.detailDurationView.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.duration_background_color)
            )
            binding.detailDurationView.animate(true, 100)
        } else if (info.streamType == StreamType.LIVE_STREAM) {
            binding.detailDurationView.setText(R.string.duration_live)
            binding.detailDurationView.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.live_duration_background_color)
            )
            binding.detailDurationView.animate(true, 100)
        } else {
            binding.detailDurationView.visibility = View.GONE
        }

        binding.detailTitleRootLayout.isClickable = true
        binding.detailToggleSecondaryControlsView.rotation = 0f
        binding.detailToggleSecondaryControlsView.visibility = View.VISIBLE
        binding.detailSecondaryControlPanel.visibility = View.GONE

        checkUpdateProgressInfo(info)
        CoilHelper.loadDetailsThumbnail(binding.detailThumbnailImageView, info.thumbnails)
        ExtractorHelper.showMetaInfoInTextView(
            info.metaInfo, binding.detailMetaInfoTextView,
            binding.detailMetaInfoSeparator, disposables
        )

        if (playerIsStopped) {
            updateOverlayData(info.name, info.uploaderName, info.thumbnails)
        }

        if (!info.errors.isEmpty()) {
            // Bandcamp fan pages are not yet supported and thus a ContentNotAvailableException is
            // thrown. This is not an error and thus should not be shown to the user.
            info.errors.removeIf {
                it is ContentNotSupportedException && "Fan pages are not supported" == it.message
            }

            if (!info.errors.isEmpty()) {
                showSnackbar(
                    this,
                    ErrorInfo(info.errors, UserAction.REQUESTED_STREAM, info.url, info)
                )
            }
        }

        binding.detailControlsDownload.isVisible = !StreamTypeUtil.isLiveStream(info.streamType)

        val hasAudioStreams = info.videoStreams.isNotEmpty() || info.audioStreams.isNotEmpty()
        binding.detailControlsBackground.isVisible = hasAudioStreams

        val hasVideoStreams = info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty()
        binding.detailControlsPopup.isVisible = hasVideoStreams
        binding.detailThumbnailPlayButton.setImageResource(
            if (hasVideoStreams) R.drawable.ic_play_arrow_shadow else R.drawable.ic_headset_shadow
        )
    }

    private fun displayUploaderAsSubChannel(info: StreamInfo) {
        val activity = requireActivity()
        binding.detailSubChannelTextView.text = info.uploaderName
        binding.detailSubChannelTextView.visibility = View.VISIBLE
        binding.detailSubChannelTextView.setSelected(true)

        if (info.uploaderSubscriberCount > -1) {
            binding.detailUploaderTextView.text =
                Localization.shortSubscriberCount(activity, info.uploaderSubscriberCount)
            binding.detailUploaderTextView.visibility = View.VISIBLE
        } else {
            binding.detailUploaderTextView.visibility = View.GONE
        }

        CoilHelper.loadAvatar(binding.detailSubChannelThumbnailView, info.uploaderAvatars)
        binding.detailSubChannelThumbnailView.visibility = View.VISIBLE
        binding.detailUploaderThumbnailView.visibility = View.GONE
    }

    private fun displayBothUploaderAndSubChannel(info: StreamInfo) {
        val activity = requireActivity()
        binding.detailSubChannelTextView.text = info.subChannelName
        binding.detailSubChannelTextView.visibility = View.VISIBLE
        binding.detailSubChannelTextView.setSelected(true)

        val subText = StringBuilder()
        if (info.uploaderName.isNotEmpty()) {
            subText.append(getString(R.string.video_detail_by, info.uploaderName))
        }
        if (info.uploaderSubscriberCount > -1) {
            if (subText.isNotEmpty()) {
                subText.append(Localization.DOT_SEPARATOR)
            }
            subText.append(
                Localization.shortSubscriberCount(activity, info.uploaderSubscriberCount)
            )
        }

        if (subText.isEmpty()) {
            binding.detailUploaderTextView.visibility = View.GONE
        } else {
            binding.detailUploaderTextView.text = subText
            binding.detailUploaderTextView.visibility = View.VISIBLE
            binding.detailUploaderTextView.setSelected(true)
        }

        CoilHelper.loadAvatar(binding.detailSubChannelThumbnailView, info.subChannelAvatars)
        binding.detailSubChannelThumbnailView.visibility = View.VISIBLE
        CoilHelper.loadAvatar(binding.detailUploaderThumbnailView, info.uploaderAvatars)
        binding.detailUploaderThumbnailView.visibility = View.VISIBLE
    }

    fun openDownloadDialog() {
        val info = currentInfo ?: return
        val activity = requireActivity()

        try {
            val downloadDialog = DownloadDialog(activity, info)
            downloadDialog.show(activity.supportFragmentManager, "downloadDialog")
        } catch (e: Exception) {
            showSnackbar(
                activity,
                ErrorInfo(e, UserAction.DOWNLOAD_OPEN_DIALOG, "Showing download dialog", info)
            )
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    ////////////////////////////////////////////////////////////////////////// */
    private fun checkUpdateProgressInfo(info: StreamInfo) {
        positionSubscriber?.dispose()
        if (!DependentPreferenceHelper.getResumePlaybackEnabled(activity)) {
            binding.positionView.visibility = View.GONE
            binding.detailPositionView.visibility = View.GONE
            return
        }
        val recordManager = HistoryRecordManager(requireContext())
        positionSubscriber = recordManager.loadStreamState(info)
            .subscribeOn(Schedulers.io())
            .onErrorComplete()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { state -> updatePlaybackProgress(state.progressMillis, info.duration * 1000) },
                { throwable -> /* impossible due to the onErrorComplete() */ },
                { /* onComplete */
                    binding.positionView.visibility = View.GONE
                    binding.detailPositionView.visibility = View.GONE
                }
            )
    }

    private fun updatePlaybackProgress(progress: Long, duration: Long) {
        if (!DependentPreferenceHelper.getResumePlaybackEnabled(activity)) {
            return
        }
        val progressSeconds = TimeUnit.MILLISECONDS.toSeconds(progress).toInt()
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(duration).toInt()
        binding.positionView.setMax(durationSeconds)
        // If the old and the new progress values have a big difference then use animation.
        // Otherwise don't because it affects CPU
        if (abs(binding.positionView.progress - progressSeconds) > 2) {
            binding.positionView.setProgressAnimated(progressSeconds)
        } else {
            binding.positionView.progress = progressSeconds
        }
        val position = Localization.getDurationString(progressSeconds.toLong())
        if (position != binding.detailPositionView.getText()) {
            binding.detailPositionView.text = position
        }
        if (binding.positionView.visibility != View.VISIBLE) {
            binding.positionView.animate(true, 100)
            binding.detailPositionView.animate(true, 100)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    ////////////////////////////////////////////////////////////////////////// */
    override fun onViewCreated() {
        tryAddVideoPlayerView()
    }

    override fun onQueueUpdate(queue: PlayQueue) {
        viewModel.playQueue = queue
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onQueueUpdate() called with: serviceId = [${viewModel.serviceId}]" +
                    ", url = [${viewModel.url}], name = [${viewModel.title}]" +
                    ", playQueue = [${viewModel.playQueue}]"
            )
        }

        // Register broadcast receiver to listen to playQueue changes
        // and hide the overlayPlayQueueButton when the playQueue is empty / destroyed.
        viewModel.playQueue?.broadcastReceiver?.subscribe { updateOverlayPlayQueueButtonVisibility() }
            ?.let { disposables.add(it) }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        if (stack.peek()?.playQueue?.equals(queue) == false) {
            queue.item?.let { queueItem ->
                stack.push(StackItem(queueItem.serviceId, queueItem.url, queueItem.title, queue))
                return@onQueueUpdate
            } // if queue.item == null continue below
        }

        // On every MainPlayer service's destroy() playQueue gets disposed and
        // no longer able to track progress. That's why we update our cached disposed
        // queue with the new one that is active and have the same history.
        // Without that the cached playQueue will have an old recovery position
        findQueueInStack(queue)?.playQueue = queue
    }

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters?
    ) {
        setOverlayPlayPauseImage(player?.isPlaying == true)

        if (state == Player.STATE_PLAYING && binding.positionView.alpha != 1.0f &&
            player?.playQueue?.item?.url == viewModel.url
        ) {
            binding.positionView.animate(true, 100)
            binding.detailPositionView.animate(true, 100)
        }
    }

    override fun onProgressUpdate(
        currentProgress: Int,
        duration: Int,
        bufferPercent: Int
    ) {
        // Progress updates are received every second even if media is paused. It's useless until
        // playing, hence the `player?.isPlaying == true` check.
        if (player?.isPlaying == true && player?.playQueue?.item?.url == viewModel.url) {
            updatePlaybackProgress(currentProgress.toLong(), duration.toLong())
        }
    }

    override fun onMetadataUpdate(info: StreamInfo, queue: PlayQueue) {
        findQueueInStack(queue)?.let { item ->
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.title = info.name
            item.url = info.url
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (queue != viewModel.playQueue) {
            return
        }

        updateOverlayData(info.name, info.uploaderName, info.thumbnails)
        if (info.url == currentInfo?.url) {
            return
        }

        viewModel.updateStreamState(info)
        viewModel.updateData(info.serviceId, info.url, info.name, queue)
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
        if (nullableBinding != null) {
            setOverlayPlayPauseImage(false)
            currentInfo?.let { updateOverlayData(it.name, it.uploaderName, it.thumbnails) }
            updateOverlayPlayQueueButtonVisibility()
        }
    }

    override fun onFullscreenStateChanged(fullscreen: Boolean) {
        setupBrightness()
        if (playerService == null ||
            player?.UIs()?.get(MainPlayerUi::class) == null ||
            this.root?.parent == null
        ) {
            return
        }

        if (fullscreen) {
            hideSystemUiIfNeeded()
            binding.overlayPlayPauseButton.requestFocus()
        } else {
            showSystemUi()
        }

        binding.relatedItemsLayout?.isVisible = !fullscreen
        scrollToTop()

        tryAddVideoPlayerView()
    }

    override fun onScreenRotationButtonClicked() {
        // In tablet user experience will be better if screen will not be rotated
        // from landscape to portrait every time.
        // Just turn on fullscreen mode in landscape orientation
        // or portrait & unlocked global orientation
        val activity = requireActivity()
        val isLandscape = DeviceUtils.isLandscape(activity)
        if (DeviceUtils.isTablet(activity) &&
            (!PlayerHelper.globalScreenOrientationLocked(activity) || isLandscape)
        ) {
            player!!.UIs().get(MainPlayerUi::class)?.toggleFullscreen()
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
        val params = binding.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as AppBarLayout.Behavior
        val valueAnimator = ValueAnimator.ofInt(0, -binding.playerPlaceholder.height)
        valueAnimator.addUpdateListener { animation ->
            behavior.setTopAndBottomOffset(animation.getAnimatedValue() as Int)
            binding.appBarLayout.requestLayout()
        }
        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.duration = 500
        valueAnimator.start()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun showSystemUi() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "showSystemUi() called")
        }

        val activity = activity
        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity.window.decorView.systemUiVisibility = 0
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.window.statusBarColor = ThemeHelper.resolveColorFromAttr(
            requireContext(), android.R.attr.colorPrimary
        )
    }

    private fun hideSystemUi() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "hideSystemUi() called")
        }

        val activity = activity
        if (activity == null) {
            return
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
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
        activity.window.decorView.systemUiVisibility = visibility

        if (isInMultiWindow || this.isFullscreen) {
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.navigationBarColor = Color.TRANSPARENT
        }
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    // Listener implementation
    override fun hideSystemUiIfNeeded() {
        if (this.isFullscreen &&
            bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
        ) {
            hideSystemUi()
        }
    }

    private val isFullscreen: Boolean
        get() = player?.UIs()?.get(VideoPlayerUi::class)?.isFullscreen == true

    /**
     * @return true if the player is null, or if the player is nonnull but is stopped.
     */
    @Suppress("NullableBooleanElvis") // rewriting as "!= false" creates more confusion
    private val playerIsStopped
        get() = player?.isStopped ?: true

    private fun restoreDefaultBrightness() {
        val activity = requireActivity()
        val lp = activity.window.attributes
        if (lp.screenBrightness == -1f) {
            return
        }

        // Restore the old  brightness when fragment.onPause() called or
        // when a player is in portrait
        lp.screenBrightness = -1f
        activity.window.setAttributes(lp)
    }

    private fun setupBrightness() {
        val activity = activity ?: return

        val lp = activity.window.attributes
        if (!this.isFullscreen || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            // Apply system brightness when the player is not in fullscreen
            restoreDefaultBrightness()
        } else {
            // Do not restore if user has disabled brightness gesture
            val brightnessControlKey = getString(R.string.brightness_control_key)
            if (PlayerHelper.getActionForRightGestureSide(activity) != brightnessControlKey &&
                PlayerHelper.getActionForLeftGestureSide(activity) != brightnessControlKey
            ) {
                return
            }
            // Restore already saved brightness level
            val brightnessLevel = PlayerHelper.getScreenBrightness(activity)
            if (brightnessLevel == lp.screenBrightness) {
                return
            }
            lp.screenBrightness = brightnessLevel
            activity.window.setAttributes(lp)
        }
    }

    /**
     * Make changes to the UI to accommodate for better usability on bigger screens such as TVs
     * or in Android's desktop mode (DeX etc).
     */
    private fun accommodateForTvAndDesktopMode() {
        if (DeviceUtils.isTv(context)) {
            // remove ripple effects from detail controls
            val transparent = ContextCompat.getColor(
                requireContext(),
                R.color.transparent_background_color
            )
            binding.detailControlsPlaylistAppend.setBackgroundColor(transparent)
            binding.detailControlsBackground.setBackgroundColor(transparent)
            binding.detailControlsPopup.setBackgroundColor(transparent)
            binding.detailControlsDownload.setBackgroundColor(transparent)
            binding.detailControlsShare.setBackgroundColor(transparent)
            binding.detailControlsOpenInBrowser.setBackgroundColor(transparent)
            binding.detailControlsPlayWithKodi.setBackgroundColor(transparent)
        }
        if (DeviceUtils.isDesktopMode(requireContext())) {
            // Remove the "hover" overlay (since it is visible on all mouse events and interferes
            // with the video content being played)
            binding.detailThumbnailRootLayout.setForeground(null)
        }
    }

    private fun checkLandscape() {
        if ((!player!!.isPlaying && player!!.playQueue !== viewModel.playQueue) ||
            player!!.playQueue == null
        ) {
            setAutoPlay(true)
        }

        player!!.UIs().get(MainPlayerUi::class)?.checkLandscape()
        // Let's give a user time to look at video information page if video is not playing
        if (PlayerHelper.globalScreenOrientationLocked(activity) && !player!!.isPlaying) {
            player!!.play()
        }
    }

    private fun findQueueInStack(queue: PlayQueue): StackItem? {
        return stack.descendingIterator().asSequence()
            .firstOrNull { it?.playQueue?.equals(queue) == true }
    }

    private fun replaceQueueIfUserConfirms(onAllow: Runnable) {
        // Player will have STATE_IDLE when a user pressed back button
        if (PlayerHelper.isClearingQueueConfirmationRequired(requireContext()) &&
            !playerIsStopped && player?.playQueue != viewModel.playQueue
        ) {
            showClearingQueueConfirmation(onAllow)
        } else {
            onAllow.run()
        }
    }

    private fun showClearingQueueConfirmation(onAllow: Runnable) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_queue_confirmation_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { dialog, which ->
                onAllow.run()
                dialog?.dismiss()
            }
            .show()
    }

    private fun showExternalVideoPlaybackDialog() {
        val info = currentInfo ?: return
        val activity = requireActivity()

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.select_quality_external_players)
            .setNeutralButton(R.string.open_in_browser) { dialog, which ->
                ShareUtils.openUrlInBrowser(requireActivity(), viewModel.url)
            }

        val videoStreamsForExternalPlayers = ListHelper.getSortedStreamVideosList(
            activity,
            ListHelper.getUrlAndNonTorrentStreams(info.videoStreams),
            ListHelper.getUrlAndNonTorrentStreams(info.videoOnlyStreams),
            false,
            false
        )

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players)
                .setPositiveButton(R.string.ok, null)
        } else {
            val selectedVideoStreamIndexForExternalPlayers =
                ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers)
            val resolutions = videoStreamsForExternalPlayers
                .map { it.getResolution() as CharSequence }
                .toTypedArray()

            builder
                .setSingleChoiceItems(resolutions, selectedVideoStreamIndexForExternalPlayers, null)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { dialog, which ->
                    val index = (dialog as AlertDialog).listView.getCheckedItemPosition()
                    // We don't have to manage the index validity because if there is no stream
                    // available for external players, this code will be not executed and if there is
                    // no stream which matches the default resolution, 0 is returned by
                    // ListHelper.getDefaultResolutionIndex.
                    // The index cannot be outside the bounds of the list as its always between 0 and
                    // the list size - 1, .
                    startOnExternalPlayer(activity, info, videoStreamsForExternalPlayers[index])
                }
        }
        builder.show()
    }

    private fun showExternalAudioPlaybackDialog() {
        val info = currentInfo ?: return
        val activity = requireActivity()

        val audioStreams = ListHelper.getUrlAndNonTorrentStreams(info.audioStreams)
        val audioTracks = ListHelper.getFilteredAudioStreams(activity, audioStreams)

        if (audioTracks.isEmpty()) {
            Toast.makeText(
                activity, R.string.no_audio_streams_available_for_external_players,
                Toast.LENGTH_SHORT
            ).show()
        } else if (audioTracks.size == 1) {
            startOnExternalPlayer(activity, info, audioTracks[0])
        } else {
            val selectedAudioStream = ListHelper.getDefaultAudioFormat(activity, audioTracks)
            val trackNames = audioTracks.map { Localization.audioTrackName(activity, it) }

            AlertDialog.Builder(activity)
                .setTitle(R.string.select_audio_track_external_players)
                .setNeutralButton(R.string.open_in_browser) { dialog, which ->
                    ShareUtils.openUrlInBrowser(requireActivity(), viewModel.url)
                }
                .setSingleChoiceItems(trackNames.toTypedArray(), selectedAudioStream, null)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { dialog, which ->
                    val index = (dialog as AlertDialog).listView.getCheckedItemPosition()
                    startOnExternalPlayer(activity, info, audioTracks[index])
                }
                .show()
        }
    }

    /*
     * Remove unneeded information while waiting for a next task
     * */
    private fun cleanUp() {
        // New beginning
        stack.clear()
        PlayerHolder.stopService()
        currentInfo = null
        updateOverlayData(null, null, listOf())
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
            // or the toolbar (e.g. TextField for search) don't have focus.
            // This was done to fix problems with the keyboard input, see also #7490
            if (!mainFragment.hasFocus() && !toolbar.hasFocus()) {
                mainFragment.requestFocus()
            }
        } else {
            mainFragment.setDescendantFocusability(blockDescendants)
            toolbar.setDescendantFocusability(blockDescendants)
            (requireView() as ViewGroup).setDescendantFocusability(afterDescendants)
            // Only focus the player if it not already has focus
            if (!binding.getRoot().hasFocus()) {
                binding.detailThumbnailRootLayout.requestFocus()
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
        val peekHeight = resources.getDimensionPixelSize(R.dimen.mini_player_height)
        val holder = requireActivity().findViewById<ViewGroup>(R.id.fragment_holder)
        val newBottomPadding = if (showMore) 0 else peekHeight
        if (holder.paddingBottom == newBottomPadding) {
            return
        }
        holder.setPadding(
            holder.getPaddingLeft(),
            holder.paddingTop,
            holder.getPaddingRight(),
            newBottomPadding
        )
    }

    private fun setupBottomPlayer() {
        val params = binding.appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as AppBarLayout.Behavior?
        val activity = requireActivity()

        val bottomSheetLayout = activity.findViewById<FrameLayout>(R.id.fragment_player_holder)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        bottomSheetBehavior.state = lastStableBottomSheetState
        updateBottomSheetState(lastStableBottomSheetState)

        val peekHeight = resources.getDimensionPixelSize(R.dimen.mini_player_height)
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false)
            bottomSheetBehavior.peekHeight = peekHeight
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.overlayLayout.alpha = MAX_OVERLAY_ALPHA
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                binding.overlayLayout.alpha = 0f
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

                        bottomSheetBehavior.peekHeight = 0
                        cleanUp()
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> {
                        moveFocusToMainFragment(false)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior.peekHeight = peekHeight
                        // Disable click because overlay buttons located on top of buttons
                        // from the player
                        setOverlayElementsClickable(false)
                        hideSystemUiIfNeeded()
                        // Conditions when the player should be expanded to fullscreen
                        if (DeviceUtils.isLandscape(requireContext()) &&
                            player?.isPlaying == true &&
                            !this@VideoDetailFragment.isFullscreen &&
                            !DeviceUtils.isTablet(requireActivity())
                        ) {
                            player?.UIs()?.get(MainPlayerUi::class)?.toggleFullscreen()
                        }
                        setOverlayLook(binding.appBarLayout, behavior, 1f)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        moveFocusToMainFragment(true)
                        manageSpaceAtTheBottom(false)

                        bottomSheetBehavior.peekHeight = peekHeight

                        // Re-enable clicks
                        setOverlayElementsClickable(true)
                        player?.UIs()?.get(MainPlayerUi::class)?.closeItemsList()
                        setOverlayLook(binding.appBarLayout, behavior, 0f)
                    }

                    BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        if (this@VideoDetailFragment.isFullscreen) {
                            showSystemUi()
                        }
                        player?.UIs()?.get(MainPlayerUi::class)?.let {
                            if (it.isControlsVisible) {
                                it.hideControls(0, 0)
                            }
                        }
                    }

                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                setOverlayLook(binding.appBarLayout, behavior, slideOffset)
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)

        // User opened a new page and the player will hide itself
        activity.supportFragmentManager.addOnBackStackChangedListener {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
    }

    private fun updateOverlayPlayQueueButtonVisibility() {
        // hide the button if the queue is empty; no player => no play queue :)
        nullableBinding?.overlayPlayQueueButton?.isVisible = player?.playQueue?.isEmpty == false
    }

    private fun updateOverlayData(
        overlayTitle: String?,
        uploader: String?,
        thumbnails: List<Image>
    ) {
        binding.overlayTitleTextView.text = overlayTitle ?: ""
        binding.overlayChannelTextView.text = uploader ?: ""
        binding.overlayThumbnail.setImageDrawable(null)
        CoilHelper.loadDetailsThumbnail(binding.overlayThumbnail, thumbnails)
    }

    private fun setOverlayPlayPauseImage(playerIsPlaying: Boolean) {
        val drawable = if (playerIsPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        binding.overlayPlayPauseButton.setImageResource(drawable)
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
        binding.overlayLayout.alpha = min(MAX_OVERLAY_ALPHA, 1 - slideOffset)
        // These numbers are not special. They just do a cool transition
        behavior.setTopAndBottomOffset(
            (-binding.detailThumbnailImageView.height * 2 * (1 - slideOffset) / 3).toInt()
        )
        appBar.requestLayout()
    }

    private fun setOverlayElementsClickable(enable: Boolean) {
        binding.overlayThumbnail.isClickable = enable
        binding.overlayThumbnail.isLongClickable = enable
        binding.overlayMetadataLayout.isClickable = enable
        binding.overlayMetadataLayout.isLongClickable = enable
        binding.overlayButtonsLayout.isClickable = enable
        binding.overlayPlayQueueButton.isClickable = enable
        binding.overlayPlayPauseButton.isClickable = enable
        binding.overlayCloseButton.isClickable = enable
    }

    val root: View?
        get() = player?.UIs()?.get(VideoPlayerUi::class)?.binding?.root

    private fun updateBottomSheetState(newState: Int) {
        bottomSheetState = newState
        if (newState != BottomSheetBehavior.STATE_DRAGGING &&
            newState != BottomSheetBehavior.STATE_SETTLING
        ) {
            lastStableBottomSheetState = newState
        }
    }

    companion object {
        private val TAG = VideoDetailFragment::class.java.simpleName
        const val KEY_SWITCHING_PLAYERS = "switching_players"

        private const val MAX_OVERLAY_ALPHA = 0.9f
        private const val MAX_PLAYER_HEIGHT = 0.7f

        const val ACTION_SHOW_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER"
        const val ACTION_HIDE_MAIN_PLAYER: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER"
        const val ACTION_PLAYER_STARTED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED"
        const val ACTION_VIDEO_FRAGMENT_RESUMED: String =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED"
        const val ACTION_VIDEO_FRAGMENT_STOPPED: String =
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
        ) = VideoDetailFragment().apply {
            arguments = bundleOf(
                KEY_SERVICE_ID to serviceId,
                KEY_URL to url,
                KEY_TITLE to name,
                VideoDetailViewModel.KEY_PLAY_QUEUE to queue
            )
        }

        @JvmStatic
        fun getInstanceInCollapsedState(): VideoDetailFragment {
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
        private val stack = LinkedList<StackItem>()
    }
}
