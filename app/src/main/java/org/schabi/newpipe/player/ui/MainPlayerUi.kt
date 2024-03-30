package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.video.VideoSize
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.QueueItemMenuUtil
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.info_list.StreamSegmentAdapter
import org.schabi.newpipe.info_list.StreamSegmentAdapter.StreamSegmentListener
import org.schabi.newpipe.info_list.StreamSegmentItem
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.notification.NotificationConstants
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min

class MainPlayerUi  /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    ////////////////////////////////////////////////////////////////////////// */
//region Constructor, setup, destroy
(player: Player,
 playerBinding: PlayerBinding) : VideoPlayerUi(player, playerBinding), OnLayoutChangeListener {
    override var isFullscreen: Boolean = false
        private set
    var isVerticalVideo: Boolean = false
        private set
    private var fragmentIsVisible: Boolean = false
    private var settingsContentObserver: ContentObserver? = null
    private var playQueueAdapter: PlayQueueAdapter? = null
    private var segmentAdapter: StreamSegmentAdapter? = null
    private var isQueueVisible: Boolean = false
    private var areSegmentsVisible: Boolean = false

    // fullscreen player
    private var itemTouchHelper: ItemTouchHelper? = null

    /**
     * Open fullscreen on tablets where the option to have the main player start automatically in
     * fullscreen mode is on. Rotating the device to landscape is already done in [ ][VideoDetailFragment.openVideoPlayer] when the thumbnail is clicked, and that's
     * enough for phones, but not for tablets since the mini player can be also shown in landscape.
     */
    private fun directlyOpenFullscreenIfNeeded() {
        if ((PlayerHelper.isStartMainPlayerFullscreenEnabled(player.getService())
                        && DeviceUtils.isTablet(player.getService())
                        && PlayerHelper.globalScreenOrientationLocked(player.getService()))) {
            player.getFragmentListener().ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.onScreenRotationButtonClicked() }))
        }
    }

    public override fun setupAfterIntent() {
        // needed for tablets, check the function for a better explanation
        directlyOpenFullscreenIfNeeded()
        super.setupAfterIntent()
        initVideoPlayer()
        // Android TV: without it focus will frame the whole player
        binding!!.playPauseButton.requestFocus()

        // Note: This is for automatically playing (when "Resume playback" is off), see #6179
        if (player.getPlayWhenReady()) {
            player.play()
        } else {
            player.pause()
        }
    }

    public override fun buildGestureListener(): BasePlayerGestureListener {
        return MainPlayerGestureListener(this)
    }

    override fun initListeners() {
        super.initListeners()
        binding!!.screenRotationButton.setOnClickListener(makeOnClickListener(Runnable({
            // Only if it's not a vertical video or vertical video but in landscape with locked
            // orientation a screen orientation can be changed automatically
            if (!isVerticalVideo || (isLandscape && PlayerHelper.globalScreenOrientationLocked(context))) {
                player.getFragmentListener()
                        .ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.onScreenRotationButtonClicked() }))
            } else {
                toggleFullscreen()
            }
        })))
        binding!!.queueButton.setOnClickListener(View.OnClickListener({ v: View? -> onQueueClicked() }))
        binding!!.segmentsButton.setOnClickListener(View.OnClickListener({ v: View? -> onSegmentsClicked() }))
        binding!!.addToPlaylistButton.setOnClickListener(View.OnClickListener({ v: View? ->
            parentActivity.map<FragmentManager>(Function<AppCompatActivity?, FragmentManager>({ obj: AppCompatActivity? -> obj!!.getSupportFragmentManager() }))
                    .ifPresent(Consumer<FragmentManager>({ fragmentManager: FragmentManager -> PlaylistDialog.Companion.showForPlayQueue(player, fragmentManager) }))
        }))
        settingsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            public override fun onChange(selfChange: Boolean) {
                setupScreenRotationButton()
            }
        }
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver)
        binding!!.getRoot().addOnLayoutChangeListener(this)
        binding!!.moreOptionsButton.setOnLongClickListener(OnLongClickListener({ v: View? ->
            player.getFragmentListener()
                    .ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.onMoreOptionsLongClicked() }))
            hideControls(0, 0)
            hideSystemUIIfNeeded()
            true
        }))
    }

    override fun deinitListeners() {
        super.deinitListeners()
        binding!!.queueButton.setOnClickListener(null)
        binding!!.segmentsButton.setOnClickListener(null)
        binding!!.addToPlaylistButton.setOnClickListener(null)
        context.getContentResolver().unregisterContentObserver((settingsContentObserver)!!)
        binding!!.getRoot().removeOnLayoutChangeListener(this)
    }

    public override fun initPlayback() {
        super.initPlayback()
        if (playQueueAdapter != null) {
            playQueueAdapter!!.dispose()
        }
        playQueueAdapter = PlayQueueAdapter(context,
                Objects.requireNonNull(player.getPlayQueue()))
        segmentAdapter = StreamSegmentAdapter(streamSegmentListener)
    }

    public override fun removeViewFromParent() {
        // view was added to fragment
        val parent: ViewParent = binding!!.getRoot().getParent()
        if (parent is ViewGroup) {
            parent.removeView(binding!!.getRoot())
        }
    }

    public override fun destroy() {
        super.destroy()

        // Exit from fullscreen when user closes the player via notification
        if (isFullscreen) {
            toggleFullscreen()
        }
        removeViewFromParent()
    }

    public override fun destroyPlayer() {
        super.destroyPlayer()
        if (playQueueAdapter != null) {
            playQueueAdapter!!.unsetSelectedListener()
            playQueueAdapter!!.dispose()
        }
    }

    public override fun smoothStopForImmediateReusing() {
        super.smoothStopForImmediateReusing()
        // Android TV will handle back button in case controls will be visible
        // (one more additional unneeded click while the player is hidden)
        hideControls(0, 0)
        closeItemsList()
    }

    private fun initVideoPlayer() {
        // restore last resize mode
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(player))
        binding!!.getRoot().setLayoutParams(FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun setupElementsVisibility() {
        super.setupElementsVisibility()
        closeItemsList()
        showHideKodiButton()
        binding!!.fullScreenButton.setVisibility(View.GONE)
        setupScreenRotationButton()
        binding!!.resizeTextView.setVisibility(View.VISIBLE)
        binding!!.getRoot().findViewById<View>(R.id.metadataView).setVisibility(View.VISIBLE)
        binding!!.moreOptionsButton.setVisibility(View.VISIBLE)
        binding!!.topControls.setOrientation(LinearLayout.VERTICAL)
        binding!!.primaryControls.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT
        binding!!.secondaryControls.setVisibility(View.INVISIBLE)
        binding!!.moreOptionsButton.setImageDrawable(AppCompatResources.getDrawable(context,
                R.drawable.ic_expand_more))
        binding!!.share.setVisibility(View.VISIBLE)
        binding!!.openInBrowser.setVisibility(View.VISIBLE)
        binding!!.switchMute.setVisibility(View.VISIBLE)
        binding!!.playerCloseButton.setVisibility(if (isFullscreen) View.GONE else View.VISIBLE)
        // Top controls have a large minHeight which is allows to drag the player
        // down in fullscreen mode (just larger area to make easy to locate by finger)
        binding!!.topControls.setClickable(true)
        binding!!.topControls.setFocusable(true)
        binding!!.titleTextView.setVisibility(if (isFullscreen) View.VISIBLE else View.GONE)
        binding!!.channelTextView.setVisibility(if (isFullscreen) View.VISIBLE else View.GONE)
    }

    override fun setupElementsSize(resources: Resources) {
        setupElementsSize(
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_min_width),
                resources.getDimensionPixelSize(R.dimen.player_main_top_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_main_buttons_padding)
        )
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    ////////////////////////////////////////////////////////////////////////// */
    //region Broadcast receiver
    public override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if ((Intent.ACTION_CONFIGURATION_CHANGED == intent.getAction())) {
            // Close it because when changing orientation from portrait
            // (in fullscreen mode) the size of queue layout can be larger than the screen size
            closeItemsList()
        } else if ((NotificationConstants.ACTION_PLAY_PAUSE == intent.getAction())) {
            // Ensure that we have audio-only stream playing when a user
            // started to play from notification's play button from outside of the app
            if (!fragmentIsVisible) {
                onFragmentStopped()
            }
        } else if ((VideoDetailFragment.Companion.ACTION_VIDEO_FRAGMENT_STOPPED == intent.getAction())) {
            fragmentIsVisible = false
            onFragmentStopped()
        } else if ((VideoDetailFragment.Companion.ACTION_VIDEO_FRAGMENT_RESUMED == intent.getAction())) {
            // Restore video source when user returns to the fragment
            fragmentIsVisible = true
            player.useVideoSource(true)

            // When a user returns from background, the system UI will always be shown even if
            // controls are invisible: hide it in that case
            if (!isControlsVisible()) {
                hideSystemUIIfNeeded()
            }
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Fragment binding
    ////////////////////////////////////////////////////////////////////////// */
    //region Fragment binding
    public override fun onFragmentListenerSet() {
        super.onFragmentListenerSet()
        fragmentIsVisible = true
        // Apply window insets because Android will not do it when orientation changes
        // from landscape to portrait
        if (!isFullscreen) {
            binding!!.playbackControlRoot.setPadding(0, 0, 0, 0)
        }
        binding!!.itemsListPanel.setPadding(0, 0, 0, 0)
        player.getFragmentListener().ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.onViewCreated() }))
    }

    /**
     * This will be called when a user goes to another app/activity, turns off a screen.
     * We don't want to interrupt playback and don't want to see notification so
     * next lines of code will enable audio-only playback only if needed
     */
    private fun onFragmentStopped() {
        if (player.isPlaying() || player.isLoading()) {
            when (PlayerHelper.getMinimizeOnExitAction(context)) {
                MinimizeMode.Companion.MINIMIZE_ON_EXIT_MODE_BACKGROUND -> player.useVideoSource(false)
                MinimizeMode.Companion.MINIMIZE_ON_EXIT_MODE_POPUP -> parentActivity.ifPresent(Consumer({ activity: AppCompatActivity? ->
                    player.setRecovery()
                    NavigationHelper.playOnPopupPlayer(activity, player.getPlayQueue(), true)
                }))

                MinimizeMode.Companion.MINIMIZE_ON_EXIT_MODE_NONE -> player.pause()
                else -> player.pause()
            }
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback states
    public override fun onUpdateProgress(currentProgress: Int,
                                         duration: Int,
                                         bufferPercent: Int) {
        super.onUpdateProgress(currentProgress, duration, bufferPercent)
        if (areSegmentsVisible) {
            segmentAdapter!!.selectSegmentAt(getNearestStreamSegmentPosition(currentProgress.toLong()))
        }
        if (isQueueVisible) {
            updateQueueTime(currentProgress)
        }
    }

    public override fun onPlaying() {
        super.onPlaying()
        checkLandscape()
    }

    public override fun onCompleted() {
        super.onCompleted()
        if (isFullscreen) {
            toggleFullscreen()
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    ////////////////////////////////////////////////////////////////////////// */
    //region Controls showing / hiding
    override fun showOrHideButtons() {
        super.showOrHideButtons()
        val playQueue: PlayQueue? = player.getPlayQueue()
        if (playQueue == null) {
            return
        }
        val showQueue: Boolean = !playQueue.getStreams().isEmpty()
        val showSegment: Boolean = !player.getCurrentStreamInfo()
                .map(Function({ obj: StreamInfo? -> obj!!.getStreamSegments() }))
                .map(Function({ obj: List<StreamSegment> -> obj.isEmpty() }))
                .orElse( /*no stream info=*/true)
        binding!!.queueButton.setVisibility(if (showQueue) View.VISIBLE else View.GONE)
        binding!!.queueButton.setAlpha(if (showQueue) 1.0f else 0.0f)
        binding!!.segmentsButton.setVisibility(if (showSegment) View.VISIBLE else View.GONE)
        binding!!.segmentsButton.setAlpha(if (showSegment) 1.0f else 0.0f)
    }

    public override fun showSystemUIPartially() {
        if (isFullscreen) {
            parentActivity.map(Function({ obj: AppCompatActivity? -> obj!!.getWindow() })).ifPresent(Consumer({ window: Window ->
                window.setStatusBarColor(Color.TRANSPARENT)
                window.setNavigationBarColor(Color.TRANSPARENT)
                val visibility: Int = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                window.getDecorView().setSystemUiVisibility(visibility)
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }))
        }
    }

    public override fun hideSystemUIIfNeeded() {
        player.getFragmentListener().ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.hideSystemUiIfNeeded() }))
    }

    /**
     * Calculate the maximum allowed height for the [R.id.endScreen]
     * to prevent it from enlarging the player.
     *
     *
     * The calculating follows these rules:
     *
     *  *
     * Show at least stream title and content creator on TVs and tablets when in landscape
     * (always the case for TVs) and not in fullscreen mode. This requires to have at least
     * [.DETAIL_ROOT_MINIMUM_HEIGHT] free space for [R.id.detail_root] and
     * additional space for the stream title text size ([R.id.detail_title_root_layout]).
     * The text size is [.DETAIL_TITLE_TEXT_SIZE_TABLET] on tablets and
     * [.DETAIL_TITLE_TEXT_SIZE_TV] on TVs, see [R.id.titleTextView].
     *
     *  *
     * Otherwise, the max thumbnail height is the screen height.
     *
     *
     *
     * @param bitmap the bitmap that needs to be resized to fit the end screen
     * @return the maximum height for the end screen thumbnail
     */
    override fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float {
        val screenHeight: Int = context.getResources().getDisplayMetrics().heightPixels
        if (DeviceUtils.isTv(context) && !isFullscreen) {
            val videoInfoHeight: Int = (DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context)
                    + DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TV, context))
            return min(bitmap.getHeight().toDouble(), (screenHeight - videoInfoHeight).toDouble()).toFloat()
        } else if (DeviceUtils.isTablet(context) && isLandscape && !isFullscreen) {
            val videoInfoHeight: Int = (DeviceUtils.dpToPx(DETAIL_ROOT_MINIMUM_HEIGHT, context)
                    + DeviceUtils.spToPx(DETAIL_TITLE_TEXT_SIZE_TABLET, context))
            return min(bitmap.getHeight().toDouble(), (screenHeight - videoInfoHeight).toDouble()).toFloat()
        } else { // fullscreen player: max height is the device height
            return min(bitmap.getHeight().toDouble(), screenHeight.toDouble()).toFloat()
        }
    }

    private fun showHideKodiButton() {
        // show kodi button if it supports the current service and it is enabled in settings
        val playQueue: PlayQueue? = player.getPlayQueue()
        binding!!.playWithKodi.setVisibility(if ((playQueue != null) && (playQueue.getItem() != null
                        ) && KoreUtils.shouldShowPlayWithKodi(context, playQueue.getItem().getServiceId())) View.VISIBLE else View.GONE)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    ////////////////////////////////////////////////////////////////////////// */
    //region Captions (text tracks)
    override fun setupSubtitleView(captionScale: Float) {
        val metrics: DisplayMetrics = context.getResources().getDisplayMetrics()
        val minimumLength: Int = min(metrics.heightPixels.toDouble(), metrics.widthPixels.toDouble()).toInt()
        val captionRatioInverse: Float = 20f + 4f * (1.0f - captionScale)
        binding!!.subtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_PX, minimumLength / captionRatioInverse)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    ////////////////////////////////////////////////////////////////////////// */
    //region Gestures
    public override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int,
                                       ol: Int, ot: Int, or: Int, ob: Int) {
        if ((l != ol) || (t != ot) || (r != or) || (b != ob)) {
            // Use a smaller value to be consistent across screen orientations, and to make usage
            // easier. Multiply by 3/4 to ensure the user does not need to move the finger up to the
            // screen border, in order to reach the maximum volume/brightness.
            val width: Int = r - l
            val height: Int = b - t
            val min: Int = min(width.toDouble(), height.toDouble()).toInt()
            val maxGestureLength: Int = (min * 0.75).toInt()
            if (MainActivity.Companion.DEBUG) {
                Log.d(TAG, "maxGestureLength = " + maxGestureLength)
            }
            binding!!.volumeProgressBar.setMax(maxGestureLength)
            binding!!.brightnessProgressBar.setMax(maxGestureLength)
            setInitialGestureValues()
            binding!!.itemsListPanel.getLayoutParams().height = height - binding!!.itemsListPanel.getTop()
        }
    }

    private fun setInitialGestureValues() {
        if (player.getAudioReactor() != null) {
            val currentVolumeNormalized: Float = (player.getAudioReactor().getVolume().toFloat() / player.getAudioReactor().getMaxVolume())
            binding!!.volumeProgressBar.setProgress((binding!!.volumeProgressBar.getMax() * currentVolumeNormalized).toInt())
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    ////////////////////////////////////////////////////////////////////////// */
    //region Play queue, segments and streams
    public override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        showHideKodiButton()
        if (areSegmentsVisible) {
            if (segmentAdapter!!.setItems(info)) {
                val adapterPosition: Int = getNearestStreamSegmentPosition(
                        player.getExoPlayer()!!.getCurrentPosition())
                segmentAdapter!!.selectSegmentAt(adapterPosition)
                binding!!.itemsList.scrollToPosition(adapterPosition)
            } else {
                closeItemsList()
            }
        }
    }

    public override fun onPlayQueueEdited() {
        super.onPlayQueueEdited()
        showOrHideButtons()
    }

    private fun onQueueClicked() {
        isQueueVisible = true
        hideSystemUIIfNeeded()
        buildQueue()
        binding!!.itemsListHeaderTitle.setVisibility(View.GONE)
        binding!!.itemsListHeaderDuration.setVisibility(View.VISIBLE)
        binding!!.shuffleButton.setVisibility(View.VISIBLE)
        binding!!.repeatButton.setVisibility(View.VISIBLE)
        binding!!.addToPlaylistButton.setVisibility(View.VISIBLE)
        hideControls(0, 0)
        binding!!.itemsListPanel.requestFocus()
        binding!!.itemsListPanel.animate(true, VideoPlayerUi.Companion.DEFAULT_CONTROLS_DURATION, AnimationType.SLIDE_AND_ALPHA)
        val playQueue: PlayQueue? = player.getPlayQueue()
        if (playQueue != null) {
            binding!!.itemsList.scrollToPosition(playQueue.getIndex())
        }
        updateQueueTime(player.getExoPlayer()!!.getCurrentPosition().toInt())
    }

    private fun buildQueue() {
        binding!!.itemsList.setAdapter(playQueueAdapter)
        binding!!.itemsList.setClickable(true)
        binding!!.itemsList.setLongClickable(true)
        binding!!.itemsList.clearOnScrollListeners()
        binding!!.itemsList.addOnScrollListener(queueScrollListener)
        itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper!!.attachToRecyclerView(binding!!.itemsList)
        playQueueAdapter!!.setSelectedListener(onSelectedListener)
        binding!!.itemsListClose.setOnClickListener(View.OnClickListener({ view: View? -> closeItemsList() }))
    }

    private fun onSegmentsClicked() {
        areSegmentsVisible = true
        hideSystemUIIfNeeded()
        buildSegments()
        binding!!.itemsListHeaderTitle.setVisibility(View.VISIBLE)
        binding!!.itemsListHeaderDuration.setVisibility(View.GONE)
        binding!!.shuffleButton.setVisibility(View.GONE)
        binding!!.repeatButton.setVisibility(View.GONE)
        binding!!.addToPlaylistButton.setVisibility(View.GONE)
        hideControls(0, 0)
        binding!!.itemsListPanel.requestFocus()
        binding!!.itemsListPanel.animate(true, VideoPlayerUi.Companion.DEFAULT_CONTROLS_DURATION, AnimationType.SLIDE_AND_ALPHA)
        val adapterPosition: Int = getNearestStreamSegmentPosition(
                player.getExoPlayer()!!.getCurrentPosition())
        segmentAdapter!!.selectSegmentAt(adapterPosition)
        binding!!.itemsList.scrollToPosition(adapterPosition)
    }

    private fun buildSegments() {
        binding!!.itemsList.setAdapter(segmentAdapter)
        binding!!.itemsList.setClickable(true)
        binding!!.itemsList.setLongClickable(true)
        binding!!.itemsList.clearOnScrollListeners()
        if (itemTouchHelper != null) {
            itemTouchHelper!!.attachToRecyclerView(null)
        }
        player.getCurrentStreamInfo().ifPresent(Consumer({ info: StreamInfo? -> segmentAdapter!!.setItems((info)!!) }))
        binding!!.shuffleButton.setVisibility(View.GONE)
        binding!!.repeatButton.setVisibility(View.GONE)
        binding!!.addToPlaylistButton.setVisibility(View.GONE)
        binding!!.itemsListClose.setOnClickListener(View.OnClickListener({ view: View? -> closeItemsList() }))
    }

    fun closeItemsList() {
        if (isQueueVisible || areSegmentsVisible) {
            isQueueVisible = false
            areSegmentsVisible = false
            if (itemTouchHelper != null) {
                itemTouchHelper!!.attachToRecyclerView(null)
            }
            binding!!.itemsListPanel.animate(false, VideoPlayerUi.Companion.DEFAULT_CONTROLS_DURATION, AnimationType.SLIDE_AND_ALPHA, 0, Runnable({ // Even when queueLayout is GONE it receives touch events
                // and ruins normal behavior of the app. This line fixes it
                binding!!.itemsListPanel.setTranslationY(
                        -binding!!.itemsListPanel.getHeight() * 5.0f)
            }))

            // clear focus, otherwise a white rectangle remains on top of the player
            binding!!.itemsListClose.clearFocus()
            binding!!.playPauseButton.requestFocus()
        }
    }

    private val queueScrollListener: OnScrollBelowItemsListener
        private get() {
            return object : OnScrollBelowItemsListener() {
                public override fun onScrolledDown(recyclerView: RecyclerView?) {
                    val playQueue: PlayQueue? = player.getPlayQueue()
                    if (playQueue != null && !playQueue.isComplete()) {
                        playQueue.fetch()
                    } else if (binding != null) {
                        binding!!.itemsList.clearOnScrollListeners()
                    }
                }
            }
        }
    private val streamSegmentListener: StreamSegmentListener
        private get() {
            return object : StreamSegmentListener {
                public override fun onItemClick(item: StreamSegmentItem, seconds: Int) {
                    segmentAdapter!!.selectSegment(item)
                    player.seekTo(seconds * 1000L)
                    player.triggerProgressUpdate()
                }

                public override fun onItemLongClick(item: StreamSegmentItem, seconds: Int) {
                    val currentMetadata: MediaItemTag? = player.getCurrentMetadata()
                    if ((currentMetadata == null
                                    || currentMetadata.getServiceId() != ServiceList.YouTube.getServiceId())) {
                        return
                    }
                    val currentItem: PlayQueueItem? = player.getCurrentItem()
                    if (currentItem != null) {
                        var videoUrl: String? = player.getVideoUrl()
                        videoUrl += ("&t=" + seconds)
                        ShareUtils.shareText(context, currentItem.getTitle(),
                                videoUrl, currentItem.getThumbnails())
                    }
                }
            }
        }

    private fun getNearestStreamSegmentPosition(playbackPosition: Long): Int {
        var nearestPosition: Int = 0
        val segments: List<StreamSegment> = player.getCurrentStreamInfo()
                .map(Function({ obj: StreamInfo? -> obj!!.getStreamSegments() }))
                .orElse(emptyList())
        for (i in segments.indices) {
            if (segments.get(i).getStartTimeSeconds() * 1000L > playbackPosition) {
                break
            }
            nearestPosition++
        }
        return max(0.0, (nearestPosition - 1).toDouble()).toInt()
    }

    private val itemTouchCallback: ItemTouchHelper.SimpleCallback
        private get() {
            return object : PlayQueueItemTouchCallback() {
                public override fun onMove(sourceIndex: Int, targetIndex: Int) {
                    val playQueue: PlayQueue? = player.getPlayQueue()
                    if (playQueue != null) {
                        playQueue.move(sourceIndex, targetIndex)
                    }
                }

                public override fun onSwiped(index: Int) {
                    val playQueue: PlayQueue? = player.getPlayQueue()
                    if (playQueue != null && index != -1) {
                        playQueue.remove(index)
                    }
                }
            }
        }
    private val onSelectedListener: PlayQueueItemBuilder.OnSelectedListener
        private get() {
            return object : PlayQueueItemBuilder.OnSelectedListener {
                public override fun selected(item: PlayQueueItem?, view: View?) {
                    player.selectQueueItem(item)
                }

                public override fun held(item: PlayQueueItem, view: View?) {
                    val playQueue: PlayQueue? = player.getPlayQueue()
                    val parentActivity: AppCompatActivity? = parentActivity.orElse(null)
                    if ((playQueue != null) && (parentActivity != null) && (playQueue.indexOf(item) != -1)) {
                        QueueItemMenuUtil.openPopupMenu(player.getPlayQueue(), item, view, true,
                                parentActivity.getSupportFragmentManager(), context)
                    }
                }

                public override fun onStartDrag(viewHolder: PlayQueueItemHolder?) {
                    if (itemTouchHelper != null) {
                        itemTouchHelper!!.startDrag((viewHolder)!!)
                    }
                }
            }
        }

    private fun updateQueueTime(currentTime: Int) {
        val playQueue: PlayQueue? = player.getPlayQueue()
        if (playQueue == null) {
            return
        }
        val currentStream: Int = playQueue.getIndex()
        var before: Int = 0
        var after: Int = 0
        val streams: List<PlayQueueItem?> = playQueue.getStreams()
        val nStreams: Int = streams.size
        for (i in 0 until nStreams) {
            if (i < currentStream) {
                before += streams.get(i).getDuration().toInt()
            } else {
                after += streams.get(i).getDuration().toInt()
            }
        }
        before *= 1000
        after *= 1000
        binding!!.itemsListHeaderDuration.setText(String.format("%s/%s",
                PlayerHelper.getTimeString(currentTime + before),
                PlayerHelper.getTimeString(before + after)
        ))
    }

    protected override val isAnyListViewOpen: Boolean
        protected get() {
            return isQueueVisible || areSegmentsVisible
        }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    ////////////////////////////////////////////////////////////////////////// */
    //region Click listeners
    override fun onPlaybackSpeedClicked() {
        parentActivity.ifPresent(Consumer<AppCompatActivity?>({ activity: AppCompatActivity? ->
            PlaybackParameterDialog.Companion.newInstance(player.getPlaybackSpeed().toDouble(),
                    player.getPlaybackPitch().toDouble(), player.getPlaybackSkipSilence(), PlaybackParameterDialog.Callback({ speed: Float, pitch: Float, skipSilence: Boolean -> player.setPlaybackParameters(speed, pitch, skipSilence) }))
                    .show(activity!!.getSupportFragmentManager(), null)
        }))
    }

    public override fun onKeyDown(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE && isFullscreen) {
            player.playPause()
            if (player.isPlaying()) {
                hideControls(0, 0)
            }
            return true
        }
        return super.onKeyDown(keyCode)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Video size, orientation, fullscreen
    ////////////////////////////////////////////////////////////////////////// */
    //region Video size, orientation, fullscreen
    private fun setupScreenRotationButton() {
        binding!!.screenRotationButton.setVisibility(if ((PlayerHelper.globalScreenOrientationLocked(context)
                        || isVerticalVideo || DeviceUtils.isTablet(context))) View.VISIBLE else View.GONE)
        binding!!.screenRotationButton.setImageDrawable(AppCompatResources.getDrawable(context,
                if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen))
    }

    public override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        isVerticalVideo = videoSize.width < videoSize.height
        if ((PlayerHelper.globalScreenOrientationLocked(context)
                        && isFullscreen
                        && (isLandscape == isVerticalVideo
                        ) && !DeviceUtils.isTv(context)
                        && !DeviceUtils.isTablet(context))) {
            // set correct orientation
            player.getFragmentListener().ifPresent(Consumer({ obj: PlayerServiceEventListener? -> obj!!.onScreenRotationButtonClicked() }))
        }
        setupScreenRotationButton()
    }

    fun toggleFullscreen() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "toggleFullscreen() called")
        }
        val fragmentListener: PlayerServiceEventListener? = player.getFragmentListener()
                .orElse(null)
        if (fragmentListener == null || player.exoPlayerIsNull()) {
            return
        }
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            // Android needs tens milliseconds to send new insets but a user is able to see
            // how controls changes it's position from `0` to `nav bar height` padding.
            // So just hide the controls to hide this visual inconsistency
            hideControls(0, 0)
        } else {
            // Apply window insets because Android will not do it when orientation changes
            // from landscape to portrait (open vertical video to reproduce)
            binding!!.playbackControlRoot.setPadding(0, 0, 0, 0)
        }
        fragmentListener.onFullscreenStateChanged(isFullscreen)
        binding!!.titleTextView.setVisibility(if (isFullscreen) View.VISIBLE else View.GONE)
        binding!!.channelTextView.setVisibility(if (isFullscreen) View.VISIBLE else View.GONE)
        binding!!.playerCloseButton.setVisibility(if (isFullscreen) View.GONE else View.VISIBLE)
        setupScreenRotationButton()
    }

    fun checkLandscape() {
        // check if landscape is correct
        val videoInLandscapeButNotInFullscreen: Boolean = (isLandscape
                && !isFullscreen
                && !player.isAudioOnly())
        val notPaused: Boolean = (player.getCurrentState() != Player.Companion.STATE_COMPLETED
                && player.getCurrentState() != Player.Companion.STATE_PAUSED)
        if ((videoInLandscapeButNotInFullscreen
                        && notPaused
                        && !DeviceUtils.isTablet(context))) {
            toggleFullscreen()
        }
    }

    private val parentContext: Optional<Context?>
        //endregion
        private get() {
            return Optional.ofNullable(binding!!.getRoot().getParent())
                    .filter(Predicate({ obj: ViewParent? -> ViewGroup::class.java.isInstance(obj) }))
                    .map(Function({ parent: ViewParent -> (parent as ViewGroup).getContext() }))
        }
    val parentActivity: Optional<AppCompatActivity?>
        get() {
            return parentContext
                    .filter(Predicate({ obj: Context? -> AppCompatActivity::class.java.isInstance(obj) }))
                    .map(Function({ obj: Context? -> AppCompatActivity::class.java.cast(obj) }))
        }
    val isLandscape: Boolean
        get() {
            // DisplayMetrics from activity context knows about MultiWindow feature
            // while DisplayMetrics from app context doesn't
            return DeviceUtils.isLandscape(parentContext.orElse(player.getService()))
        } //endregion

    companion object {
        private val TAG: String = MainPlayerUi::class.java.getSimpleName()

        // see the Javadoc of calculateMaxEndScreenThumbnailHeight for information
        private val DETAIL_ROOT_MINIMUM_HEIGHT: Int = 85 // dp
        private val DETAIL_TITLE_TEXT_SIZE_TV: Int = 16 // sp
        private val DETAIL_TITLE_TEXT_SIZE_TABLET: Int = 15 // sp
    }
}
