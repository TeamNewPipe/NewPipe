package org.schabi.newpipe.player.ui

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnLongClickListener
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.BitmapCompat
import androidx.core.graphics.Insets
import androidx.core.math.MathUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.ResizeMode
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.video.VideoSize
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateRotation
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.DisplayPortion
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playback.SurfaceHolderCallback
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay.PerformListener
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay.PerformListener.FastSeekDirection
import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntSupplier
import java.util.function.Predicate
import java.util.stream.Collectors

abstract class VideoPlayerUi protected constructor(player: Player,
                                                   playerBinding: PlayerBinding) : PlayerUi(player), OnSeekBarChangeListener, PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    private enum class PlayButtonAction {
        PLAY,
        PAUSE,
        REPLAY
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
        // Getters
        ////////////////////////////////////////////////////////////////////////// */
    //region Getters
    /*//////////////////////////////////////////////////////////////////////////
         // Views
         ////////////////////////////////////////////////////////////////////////// */
    var binding: PlayerBinding?
        protected set
    private val controlsVisibilityHandler: Handler = Handler(Looper.getMainLooper())
    private var surfaceHolderCallback: SurfaceHolderCallback? = null
    var surfaceIsSetup: Boolean = false
    var isSomePopupMenuVisible: Boolean = false
        protected set
    private var qualityPopupMenu: PopupMenu? = null
    private var audioTrackPopupMenu: PopupMenu? = null
    protected var playbackSpeedPopupMenu: PopupMenu? = null
    private var captionPopupMenu: PopupMenu? = null

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
         // Gestures
         ////////////////////////////////////////////////////////////////////////// */
    var gestureDetector: GestureDetector? = null
        private set
    private var playerGestureListener: BasePlayerGestureListener? = null
    private var onLayoutChangeListener: OnLayoutChangeListener? = null
    private val seekbarPreviewThumbnailHolder: SeekbarPreviewThumbnailHolder = SeekbarPreviewThumbnailHolder()

    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    ////////////////////////////////////////////////////////////////////////// */
    //region Constructor, setup, destroy
    init {
        binding = playerBinding
        setupFromView()
    }

    fun setupFromView() {
        initViews()
        initListeners()
        setupPlayerSeekOverlay()
    }

    private fun initViews() {
        setupSubtitleView()
        binding!!.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding!!.surfaceView.getResizeMode()))
        binding!!.playbackSeekBar.getThumb()
                .setColorFilter(PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN))
        binding!!.playbackSeekBar.getProgressDrawable()
                .setColorFilter(PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY))
        val themeWrapper: ContextThemeWrapper = ContextThemeWrapper(context,
                R.style.DarkPopupMenu)
        qualityPopupMenu = PopupMenu(themeWrapper, binding!!.qualityTextView)
        audioTrackPopupMenu = PopupMenu(themeWrapper, binding!!.audioTrackTextView)
        playbackSpeedPopupMenu = PopupMenu(context, binding!!.playbackSpeed)
        captionPopupMenu = PopupMenu(themeWrapper, binding!!.captionTextView)
        binding!!.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY))
        binding!!.titleTextView.setSelected(true)
        binding!!.channelTextView.setSelected(true)

        // Prevent hiding of bottom sheet via swipe inside queue
        binding!!.itemsList.setNestedScrollingEnabled(false)
    }

    abstract fun buildGestureListener(): BasePlayerGestureListener
    protected open fun initListeners() {
        binding!!.qualityTextView.setOnClickListener(makeOnClickListener(Runnable({ onQualityClicked() })))
        binding!!.audioTrackTextView.setOnClickListener(
                makeOnClickListener(Runnable({ onAudioTracksClicked() })))
        binding!!.playbackSpeed.setOnClickListener(makeOnClickListener(Runnable({ onPlaybackSpeedClicked() })))
        binding!!.playbackSeekBar.setOnSeekBarChangeListener(this)
        binding!!.captionTextView.setOnClickListener(makeOnClickListener(Runnable({ onCaptionClicked() })))
        binding!!.resizeTextView.setOnClickListener(makeOnClickListener(Runnable({ onResizeClicked() })))
        binding!!.playbackLiveSync.setOnClickListener(makeOnClickListener(Runnable({ player.seekToDefault() })))
        playerGestureListener = buildGestureListener()
        gestureDetector = GestureDetector(context, playerGestureListener!!)
        binding!!.getRoot().setOnTouchListener(playerGestureListener)
        binding!!.repeatButton.setOnClickListener(View.OnClickListener({ v: View? -> onRepeatClicked() }))
        binding!!.shuffleButton.setOnClickListener(View.OnClickListener({ v: View? -> onShuffleClicked() }))
        binding!!.playPauseButton.setOnClickListener(makeOnClickListener(Runnable({ player.playPause() })))
        binding!!.playPreviousButton.setOnClickListener(makeOnClickListener(Runnable({ player.playPrevious() })))
        binding!!.playNextButton.setOnClickListener(makeOnClickListener(Runnable({ player.playNext() })))
        binding!!.moreOptionsButton.setOnClickListener(
                makeOnClickListener(Runnable({ onMoreOptionsClicked() })))
        binding!!.share.setOnClickListener(makeOnClickListener(Runnable({
            val currentItem: PlayQueueItem? = player.getCurrentItem()
            if (currentItem != null) {
                ShareUtils.shareText(context, currentItem.getTitle(),
                        player.getVideoUrlAtCurrentTime(), currentItem.getThumbnails())
            }
        })))
        binding!!.share.setOnLongClickListener(OnLongClickListener({ v: View? ->
            ShareUtils.copyToClipboard(context, player.getVideoUrlAtCurrentTime())
            true
        }))
        binding!!.fullScreenButton.setOnClickListener(makeOnClickListener(Runnable({
            player.setRecovery()
            NavigationHelper.playOnMainPlayer(context,
                    Objects.requireNonNull(player.getPlayQueue()), true)
        })))
        binding!!.playWithKodi.setOnClickListener(makeOnClickListener(Runnable({ onPlayWithKodiClicked() })))
        binding!!.openInBrowser.setOnClickListener(makeOnClickListener(Runnable({ onOpenInBrowserClicked() })))
        binding!!.playerCloseButton.setOnClickListener(makeOnClickListener(Runnable({ // set package to this app's package to prevent the intent from being seen outside
            context.sendBroadcast(Intent(VideoDetailFragment.Companion.ACTION_HIDE_MAIN_PLAYER)
                    .setPackage(App.Companion.PACKAGE_NAME))
        })
        ))
        binding!!.switchMute.setOnClickListener(makeOnClickListener(Runnable({ player.toggleMute() })))
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.itemsListPanel, OnApplyWindowInsetsListener({ view: View, windowInsets: WindowInsetsCompat ->
            val cutout: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            if (!(cutout == Insets.NONE)) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            }
            windowInsets
        }))

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays and fast_seek_overlay too. Without it they will be off-centered.
        onLayoutChangeListener = OnLayoutChangeListener({ v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int ->
            binding!!.playerOverlays.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), v.getPaddingBottom())

            // If we added padding to the fast seek overlay, too, it would not go under the
            // system ui. Instead we apply negative margins equal to the window insets of
            // the opposite side, so that the view covers all of the player (overflowing on
            // some sides) and its center coincides with the center of other controls.
            val fastSeekParams: RelativeLayout.LayoutParams = binding!!.fastSeekOverlay.getLayoutParams() as RelativeLayout.LayoutParams
            fastSeekParams.leftMargin = -v.getPaddingRight()
            fastSeekParams.topMargin = -v.getPaddingBottom()
            fastSeekParams.rightMargin = -v.getPaddingLeft()
            fastSeekParams.bottomMargin = -v.getPaddingTop()
        })
        binding!!.playbackControlRoot.addOnLayoutChangeListener(onLayoutChangeListener)
    }

    protected open fun deinitListeners() {
        binding!!.qualityTextView.setOnClickListener(null)
        binding!!.audioTrackTextView.setOnClickListener(null)
        binding!!.playbackSpeed.setOnClickListener(null)
        binding!!.playbackSeekBar.setOnSeekBarChangeListener(null)
        binding!!.captionTextView.setOnClickListener(null)
        binding!!.resizeTextView.setOnClickListener(null)
        binding!!.playbackLiveSync.setOnClickListener(null)
        binding!!.getRoot().setOnTouchListener(null)
        playerGestureListener = null
        gestureDetector = null
        binding!!.repeatButton.setOnClickListener(null)
        binding!!.shuffleButton.setOnClickListener(null)
        binding!!.playPauseButton.setOnClickListener(null)
        binding!!.playPreviousButton.setOnClickListener(null)
        binding!!.playNextButton.setOnClickListener(null)
        binding!!.moreOptionsButton.setOnClickListener(null)
        binding!!.moreOptionsButton.setOnLongClickListener(null)
        binding!!.share.setOnClickListener(null)
        binding!!.share.setOnLongClickListener(null)
        binding!!.fullScreenButton.setOnClickListener(null)
        binding!!.screenRotationButton.setOnClickListener(null)
        binding!!.playWithKodi.setOnClickListener(null)
        binding!!.openInBrowser.setOnClickListener(null)
        binding!!.playerCloseButton.setOnClickListener(null)
        binding!!.switchMute.setOnClickListener(null)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.itemsListPanel, null)
        binding!!.playbackControlRoot.removeOnLayoutChangeListener(onLayoutChangeListener)
    }

    /**
     * Initializes the Fast-For/Backward overlay.
     */
    private fun setupPlayerSeekOverlay() {
        binding!!.fastSeekOverlay
                .seekSecondsSupplier({ PlayerHelper.retrieveSeekDurationFromPreferences(player) / 1000 })
                .performListener(object : PerformListener {
                    public override fun onDoubleTap() {
                        binding!!.fastSeekOverlay.animate(true, SEEK_OVERLAY_DURATION.toLong())
                    }

                    public override fun onDoubleTapEnd() {
                        binding!!.fastSeekOverlay.animate(false, SEEK_OVERLAY_DURATION.toLong())
                    }

                    public override fun getFastSeekDirection(
                            portion: DisplayPortion
                    ): FastSeekDirection {
                        if (player.exoPlayerIsNull()) {
                            // Abort seeking
                            playerGestureListener!!.endMultiDoubleTap()
                            return FastSeekDirection.NONE
                        }
                        if (portion == DisplayPortion.LEFT) {
                            // Check if it's possible to rewind
                            // Small puffer to eliminate infinite rewind seeking
                            if (player.getExoPlayer()!!.getCurrentPosition() < 500L) {
                                return FastSeekDirection.NONE
                            }
                            return FastSeekDirection.BACKWARD
                        } else if (portion == DisplayPortion.RIGHT) {
                            // Check if it's possible to fast-forward
                            if ((player.getCurrentState() == Player.Companion.STATE_COMPLETED
                                            || player.getExoPlayer()!!.getCurrentPosition()
                                            >= player.getExoPlayer()!!.getDuration())) {
                                return FastSeekDirection.NONE
                            }
                            return FastSeekDirection.FORWARD
                        }
                        /* portion == DisplayPortion.MIDDLE */return FastSeekDirection.NONE
                    }

                    public override fun seek(forward: Boolean) {
                        playerGestureListener!!.keepInDoubleTapMode()
                        if (forward) {
                            player.fastForward()
                        } else {
                            player.fastRewind()
                        }
                    }
                })
        playerGestureListener!!.doubleTapControls(binding!!.fastSeekOverlay)
    }

    fun deinitPlayerSeekOverlay() {
        binding!!.fastSeekOverlay
                .seekSecondsSupplier(null)
                .performListener(null)
    }

    public override fun setupAfterIntent() {
        super.setupAfterIntent()
        setupElementsVisibility()
        setupElementsSize(context.getResources())
        binding!!.getRoot().setVisibility(View.VISIBLE)
        binding!!.playPauseButton.requestFocus()
    }

    public override fun initPlayer() {
        super.initPlayer()
        setupVideoSurfaceIfNeeded()
    }

    public override fun initPlayback() {
        super.initPlayback()

        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(player.getExoPlayer()!!.getShuffleModeEnabled())
    }

    abstract fun removeViewFromParent()
    public override fun destroyPlayer() {
        super.destroyPlayer()
        clearVideoSurface()
    }

    public override fun destroy() {
        super.destroy()
        binding!!.endScreen.setImageDrawable(null)
        deinitPlayerSeekOverlay()
        deinitListeners()
    }

    protected open fun setupElementsVisibility() {
        setMuteButton(player.isMuted())
        binding!!.moreOptionsButton.animateRotation(DEFAULT_CONTROLS_DURATION, 0)
    }

    protected abstract fun setupElementsSize(resources: Resources)
    protected fun setupElementsSize(buttonsMinWidth: Int,
                                    playerTopPad: Int,
                                    controlsPad: Int,
                                    buttonsPad: Int) {
        binding!!.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0)
        binding!!.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0)
        binding!!.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad)
        binding!!.audioTrackTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad)
        binding!!.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad)
        binding!!.playbackSpeed.setMinimumWidth(buttonsMinWidth)
        binding!!.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    ////////////////////////////////////////////////////////////////////////// */
    //region Broadcast receiver
    public override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if ((Intent.ACTION_CONFIGURATION_CHANGED == intent.getAction())) {
            // When the orientation changes, the screen height might be smaller. If the end screen
            // thumbnail is not re-scaled, it can be larger than the current screen height and thus
            // enlarging the whole player. This causes the seekbar to be out of the visible area.
            updateEndScreenThumbnail(player.getThumbnail())
        }
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail
    ////////////////////////////////////////////////////////////////////////// */
    //region Thumbnail
    /**
     * Scale the player audio / end screen thumbnail down if necessary.
     *
     *
     * This is necessary when the thumbnail's height is larger than the device's height
     * and thus is enlarging the player's height
     * causing the bottom playback controls to be out of the visible screen.
     *
     */
    public override fun onThumbnailLoaded(bitmap: Bitmap?) {
        super.onThumbnailLoaded(bitmap)
        updateEndScreenThumbnail(bitmap)
    }

    private fun updateEndScreenThumbnail(thumbnail: Bitmap?) {
        if (thumbnail == null) {
            // remove end screen thumbnail
            binding!!.endScreen.setImageDrawable(null)
            return
        }
        val endScreenHeight: Float = calculateMaxEndScreenThumbnailHeight(thumbnail)
        val endScreenBitmap: Bitmap = BitmapCompat.createScaledBitmap(
                thumbnail, (thumbnail.getWidth() / (thumbnail.getHeight() / endScreenHeight)).toInt(), endScreenHeight.toInt(),
                null,
                true)
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("Thumbnail - onThumbnailLoaded() called with: "
                    + "currentThumbnail = [" + thumbnail + "], "
                    + thumbnail.getWidth() + "x" + thumbnail.getHeight()
                    + ", scaled end screen height = " + endScreenHeight
                    + ", scaled end screen width = " + endScreenBitmap.getWidth()))
        }
        binding!!.endScreen.setImageBitmap(endScreenBitmap)
    }

    protected abstract fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    ////////////////////////////////////////////////////////////////////////// */
    //region Progress loop and updates
    public override fun onUpdateProgress(currentProgress: Int,
                                         duration: Int,
                                         bufferPercent: Int) {
        if (duration != binding!!.playbackSeekBar.getMax()) {
            setVideoDurationToControls(duration)
        }
        if (player.getCurrentState() != Player.Companion.STATE_PAUSED) {
            updatePlayBackElementsCurrentDuration(currentProgress)
        }
        if (player.isLoading() || bufferPercent > 90) {
            binding!!.playbackSeekBar.setSecondaryProgress((binding!!.playbackSeekBar.getMax() * (bufferPercent.toFloat() / 100)).toInt())
        }
        if (MainActivity.Companion.DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, ("notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]"))
        }
        binding!!.playbackLiveSync.setClickable(!player.isLiveEdge())
    }

    /**
     * Sets the current duration into the corresponding elements.
     *
     * @param currentProgress the current progress, in milliseconds
     */
    private fun updatePlayBackElementsCurrentDuration(currentProgress: Int) {
        // Don't set seekbar progress while user is seeking
        if (player.getCurrentState() != Player.Companion.STATE_PAUSED_SEEK) {
            binding!!.playbackSeekBar.setProgress(currentProgress)
        }
        binding!!.playbackCurrentTime.setText(PlayerHelper.getTimeString(currentProgress))
    }

    /**
     * Sets the video duration time into all control components (e.g. seekbar).
     *
     * @param duration the video duration, in milliseconds
     */
    private fun setVideoDurationToControls(duration: Int) {
        binding!!.playbackEndTime.setText(PlayerHelper.getTimeString(duration))
        binding!!.playbackSeekBar.setMax(duration)
        // This is important for Android TVs otherwise it would apply the default from
        // setMax/Min methods which is (max - min) / 20
        binding!!.playbackSeekBar.setKeyProgressIncrement(
                PlayerHelper.retrieveSeekDurationFromPreferences(player))
    }

    // seekbar listener
    public override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                                          fromUser: Boolean) {
        // Currently we don't need method execution when fromUser is false
        if (!fromUser) {
            return
        }
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]"))
        }
        binding!!.currentDisplaySeek.setText(PlayerHelper.getTimeString(progress))

        // Seekbar Preview Thumbnail
        SeekbarPreviewThumbnailHelper.tryResizeAndSetSeekbarPreviewThumbnail(
                player.getContext(),
                seekbarPreviewThumbnailHolder.getBitmapAt(progress).orElse(null),
                binding!!.currentSeekbarPreviewThumbnail, IntSupplier({ binding!!.subtitleView.getWidth() }))
        adjustSeekbarPreviewContainer()
    }

    private fun adjustSeekbarPreviewContainer() {
        try {
            // Should only be required when an error occurred before
            // and the layout was positioned in the center
            binding!!.bottomSeekbarPreviewLayout.setGravity(Gravity.NO_GRAVITY)

            // Calculate the current left position of seekbar progress in px
            // More info: https://stackoverflow.com/q/20493577
            val currentSeekbarLeft: Int = (binding!!.playbackSeekBar.getLeft()
                    + binding!!.playbackSeekBar.getPaddingLeft()
                    + binding!!.playbackSeekBar.getThumb().getBounds().left)

            // Calculate the (unchecked) left position of the container
            val uncheckedContainerLeft: Int = currentSeekbarLeft - (binding!!.seekbarPreviewContainer.getWidth() / 2)

            // Fix the position so it's within the boundaries
            val checkedContainerLeft: Int = MathUtils.clamp(uncheckedContainerLeft,
                    0, (binding!!.playbackWindowRoot.getWidth()
                    - binding!!.seekbarPreviewContainer.getWidth()))

            // See also: https://stackoverflow.com/a/23249734
            val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                    binding!!.seekbarPreviewContainer.getLayoutParams())
            params.setMarginStart(checkedContainerLeft)
            binding!!.seekbarPreviewContainer.setLayoutParams(params)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to adjust seekbarPreviewContainer", ex)
            // Fallback - position in the middle
            binding!!.bottomSeekbarPreviewLayout.setGravity(Gravity.CENTER)
        }
    }

    // seekbar listener
    public override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]")
        }
        if (player.getCurrentState() != Player.Companion.STATE_PAUSED_SEEK) {
            player.changeState(Player.Companion.STATE_PAUSED_SEEK)
        }
        showControls(0)
        binding!!.currentDisplaySeek.animate(true, DEFAULT_CONTROLS_DURATION, AnimationType.SCALE_AND_ALPHA)
        binding!!.currentSeekbarPreviewThumbnail.animate(true, DEFAULT_CONTROLS_DURATION, AnimationType.SCALE_AND_ALPHA)
    }

    // seekbar listener
    public override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]")
        }
        player.seekTo(seekBar.getProgress().toLong())
        if (player.getExoPlayer()!!.getDuration() == seekBar.getProgress().toLong()) {
            player.getExoPlayer()!!.play()
        }
        binding!!.playbackCurrentTime.setText(PlayerHelper.getTimeString(seekBar.getProgress()))
        binding!!.currentDisplaySeek.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
        binding!!.currentSeekbarPreviewThumbnail.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
        if (player.getCurrentState() == Player.Companion.STATE_PAUSED_SEEK) {
            player.changeState(Player.Companion.STATE_BUFFERING)
        }
        if (!player.isProgressLoopRunning()) {
            player.startProgressLoop()
        }
        showControlsThenHide()
    }

    val isControlsVisible: Boolean
        //endregion
        get() {
            return binding != null && binding!!.playbackControlRoot.getVisibility() == View.VISIBLE
        }

    fun showControlsThenHide() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "showControlsThenHide() called")
        }
        showOrHideButtons()
        showSystemUIPartially()
        val hideTime: Long = if (binding!!.playbackControlRoot.isInTouchMode()) DEFAULT_CONTROLS_HIDE_TIME else DPAD_CONTROLS_HIDE_TIME
        showHideShadow(true, DEFAULT_CONTROLS_DURATION)
        binding!!.playbackControlRoot.animate(true, DEFAULT_CONTROLS_DURATION, AnimationType.ALPHA, 0, Runnable({ hideControls(DEFAULT_CONTROLS_DURATION, hideTime) }))
    }

    fun showControls(duration: Long) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "showControls() called")
        }
        showOrHideButtons()
        showSystemUIPartially()
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        showHideShadow(true, duration)
        binding!!.playbackControlRoot.animate(true, duration)
    }

    fun hideControls(duration: Long, delay: Long) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]"))
        }
        showOrHideButtons()
        controlsVisibilityHandler.removeCallbacksAndMessages(null)
        controlsVisibilityHandler.postDelayed(Runnable({
            showHideShadow(false, duration)
            binding!!.playbackControlRoot.animate(false, duration, AnimationType.ALPHA, 0, Runnable({ hideSystemUIIfNeeded() }))
        }), delay)
    }

    fun showHideShadow(show: Boolean, duration: Long) {
        binding!!.playbackControlsShadow.animate(show, duration, AnimationType.ALPHA, 0, null)
        binding!!.playerTopShadow.animate(show, duration, AnimationType.ALPHA, 0, null)
        binding!!.playerBottomShadow.animate(show, duration, AnimationType.ALPHA, 0, null)
    }

    protected open fun showOrHideButtons() {
        val playQueue: PlayQueue? = player.getPlayQueue()
        if (playQueue == null) {
            return
        }
        val showPrev: Boolean = playQueue.getIndex() != 0
        val showNext: Boolean = playQueue.getIndex() + 1 != playQueue.getStreams().size
        binding!!.playPreviousButton.setVisibility(if (showPrev) View.VISIBLE else View.INVISIBLE)
        binding!!.playPreviousButton.setAlpha(if (showPrev) 1.0f else 0.0f)
        binding!!.playNextButton.setVisibility(if (showNext) View.VISIBLE else View.INVISIBLE)
        binding!!.playNextButton.setAlpha(if (showNext) 1.0f else 0.0f)
    }

    protected open fun showSystemUIPartially() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected open fun hideSystemUIIfNeeded() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected open val isAnyListViewOpen: Boolean
        protected get() {
            // only MainPlayerUi has list views for the queue and for segments, so overridden there
            return false
        }
    open val isFullscreen: Boolean
        get() {
            // only MainPlayerUi can be in fullscreen, so overridden there
            return false
        }

    /**
     * Update the play/pause button ([R.id.playPauseButton]) to reflect the action
     * that will be performed when the button is clicked..
     * @param action the action that is performed when the play/pause button is clicked
     */
    private fun updatePlayPauseButton(action: PlayButtonAction) {
        val button: AppCompatImageButton = binding!!.playPauseButton
        when (action) {
            PlayButtonAction.PLAY -> {
                button.setContentDescription(context.getString(R.string.play))
                button.setImageResource(R.drawable.ic_play_arrow)
            }

            PlayButtonAction.PAUSE -> {
                button.setContentDescription(context.getString(R.string.pause))
                button.setImageResource(R.drawable.ic_pause)
            }

            PlayButtonAction.REPLAY -> {
                button.setContentDescription(context.getString(R.string.replay))
                button.setImageResource(R.drawable.ic_replay)
            }
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback states
    public override fun onPrepared() {
        super.onPrepared()
        setVideoDurationToControls(player.getExoPlayer()!!.getDuration().toInt())
        binding!!.playbackSpeed.setText(PlayerHelper.formatSpeed(player.getPlaybackSpeed().toDouble()))
    }

    public override fun onBlocked() {
        super.onBlocked()

        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0)
        binding!!.playbackSeekBar.setEnabled(false)
        binding!!.playbackSeekBar.getThumb()
                .setColorFilter(PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN))
        binding!!.loadingPanel.setBackgroundColor(Color.BLACK)
        binding!!.loadingPanel.animate(true, 0)
        binding!!.surfaceForeground.animate(true, 100)
        updatePlayPauseButton(PlayButtonAction.PLAY)
        animatePlayButtons(false, 100)
        binding!!.getRoot().setKeepScreenOn(false)
    }

    public override fun onPlaying() {
        super.onPlaying()
        updateStreamRelatedViews()
        binding!!.playbackSeekBar.setEnabled(true)
        binding!!.playbackSeekBar.getThumb()
                .setColorFilter(PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN))
        binding!!.loadingPanel.setVisibility(View.GONE)
        binding!!.currentDisplaySeek.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
        binding!!.playPauseButton.animate(false, 80, AnimationType.SCALE_AND_ALPHA, 0, Runnable({
            updatePlayPauseButton(PlayButtonAction.PAUSE)
            animatePlayButtons(true, 200)
            if (!isAnyListViewOpen) {
                binding!!.playPauseButton.requestFocus()
            }
        }))
        binding!!.getRoot().setKeepScreenOn(true)
    }

    public override fun onBuffering() {
        super.onBuffering()
        binding!!.loadingPanel.setBackgroundColor(Color.TRANSPARENT)
        binding!!.loadingPanel.setVisibility(View.VISIBLE)
        binding!!.getRoot().setKeepScreenOn(true)
    }

    public override fun onPaused() {
        super.onPaused()

        // Don't let UI elements popup during double tap seeking. This state is entered sometimes
        // during seeking/loading. This if-else check ensures that the controls aren't popping up.
        if (!playerGestureListener!!.isDoubleTapping) {
            showControls(400)
            binding!!.loadingPanel.setVisibility(View.GONE)
            binding!!.playPauseButton.animate(false, 80, AnimationType.SCALE_AND_ALPHA, 0, Runnable({
                updatePlayPauseButton(PlayButtonAction.PLAY)
                animatePlayButtons(true, 200)
                if (!isAnyListViewOpen) {
                    binding!!.playPauseButton.requestFocus()
                }
            }))
        }
        binding!!.getRoot().setKeepScreenOn(false)
    }

    public override fun onPausedSeek() {
        super.onPausedSeek()
        animatePlayButtons(false, 100)
        binding!!.getRoot().setKeepScreenOn(true)
    }

    public override fun onCompleted() {
        super.onCompleted()
        binding!!.playPauseButton.animate(false, 0, AnimationType.SCALE_AND_ALPHA, 0, Runnable({
            updatePlayPauseButton(PlayButtonAction.REPLAY)
            animatePlayButtons(true, DEFAULT_CONTROLS_DURATION)
        }))
        binding!!.getRoot().setKeepScreenOn(false)

        // When a (short) video ends the elements have to display the correct values - see #6180
        updatePlayBackElementsCurrentDuration(binding!!.playbackSeekBar.getMax())
        showControls(500)
        binding!!.currentDisplaySeek.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
        binding!!.loadingPanel.setVisibility(View.GONE)
        binding!!.surfaceForeground.animate(true, 100)
    }

    private fun animatePlayButtons(show: Boolean, duration: Long) {
        binding!!.playPauseButton.animate(show, duration, AnimationType.SCALE_AND_ALPHA)
        val playQueue: PlayQueue? = player.getPlayQueue()
        if (playQueue == null) {
            return
        }
        if (!show || playQueue.getIndex() > 0) {
            binding!!.playPreviousButton.animate(show, duration, AnimationType.SCALE_AND_ALPHA)
        }
        if (!show || playQueue.getIndex() + 1 < playQueue.getStreams().size) {
            binding!!.playNextButton.animate(show, duration, AnimationType.SCALE_AND_ALPHA)
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Repeat, shuffle, mute
    ////////////////////////////////////////////////////////////////////////// */
    //region Repeat, shuffle, mute
    fun onRepeatClicked() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onRepeatClicked() called")
        }
        player.cycleNextRepeatMode()
    }

    fun onShuffleClicked() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onShuffleClicked() called")
        }
        player.toggleShuffleModeEnabled()
    }

    public override fun onRepeatModeChanged(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {
        super.onRepeatModeChanged(repeatMode)
        if (repeatMode == com.google.android.exoplayer2.Player.REPEAT_MODE_ALL) {
            binding!!.repeatButton.setImageResource(R.drawable.exo_controls_repeat_all)
        } else if (repeatMode == com.google.android.exoplayer2.Player.REPEAT_MODE_ONE) {
            binding!!.repeatButton.setImageResource(R.drawable.exo_controls_repeat_one)
        } else  /* repeatMode == REPEAT_MODE_OFF */ {
            binding!!.repeatButton.setImageResource(R.drawable.exo_controls_repeat_off)
        }
    }

    public override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        setShuffleButton(shuffleModeEnabled)
    }

    public override fun onMuteUnmuteChanged(isMuted: Boolean) {
        super.onMuteUnmuteChanged(isMuted)
        setMuteButton(isMuted)
    }

    private fun setMuteButton(isMuted: Boolean) {
        binding!!.switchMute.setImageDrawable(AppCompatResources.getDrawable(context, if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up))
    }

    private fun setShuffleButton(shuffled: Boolean) {
        binding!!.shuffleButton.setImageAlpha(if (shuffled) 255 else 77)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Other player listeners
    ////////////////////////////////////////////////////////////////////////// */
    //region Other player listeners
    public override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        binding!!.playbackSpeed.setText(PlayerHelper.formatSpeed(playbackParameters.speed.toDouble()))
    }

    public override fun onRenderedFirstFrame() {
        super.onRenderedFirstFrame()
        //TODO check if this causes black screen when switching to fullscreen
        binding!!.surfaceForeground.animate(false, DEFAULT_CONTROLS_DURATION)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Metadata & stream related views
    ////////////////////////////////////////////////////////////////////////// */
    //region Metadata & stream related views
    public override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        updateStreamRelatedViews()
        binding!!.titleTextView.setText(info.getName())
        binding!!.channelTextView.setText(info.getUploaderName())
        seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), info.getPreviewFrames())
    }

    private fun updateStreamRelatedViews() {
        player.getCurrentStreamInfo().ifPresent(Consumer({ info: StreamInfo? ->
            binding!!.qualityTextView.setVisibility(View.GONE)
            binding!!.audioTrackTextView.setVisibility(View.GONE)
            binding!!.playbackSpeed.setVisibility(View.GONE)
            binding!!.playbackEndTime.setVisibility(View.GONE)
            binding!!.playbackLiveSync.setVisibility(View.GONE)
            when (info!!.getStreamType()) {
                StreamType.AUDIO_STREAM, StreamType.POST_LIVE_AUDIO_STREAM -> {
                    binding!!.surfaceView.setVisibility(View.GONE)
                    binding!!.endScreen.setVisibility(View.VISIBLE)
                    binding!!.playbackEndTime.setVisibility(View.VISIBLE)
                }

                StreamType.AUDIO_LIVE_STREAM -> {
                    binding!!.surfaceView.setVisibility(View.GONE)
                    binding!!.endScreen.setVisibility(View.VISIBLE)
                    binding!!.playbackLiveSync.setVisibility(View.VISIBLE)
                }

                StreamType.LIVE_STREAM -> {
                    binding!!.surfaceView.setVisibility(View.VISIBLE)
                    binding!!.endScreen.setVisibility(View.GONE)
                    binding!!.playbackLiveSync.setVisibility(View.VISIBLE)
                }

                StreamType.VIDEO_STREAM, StreamType.POST_LIVE_STREAM -> {
                    if ((player.getCurrentMetadata() != null
                                    && player.getCurrentMetadata().getMaybeQuality().isEmpty()
                                    || (info.getVideoStreams().isEmpty()
                                    && info.getVideoOnlyStreams().isEmpty()))) {
                        break
                    }
                    buildQualityMenu()
                    buildAudioTrackMenu()
                    binding!!.qualityTextView.setVisibility(View.VISIBLE)
                    binding!!.surfaceView.setVisibility(View.VISIBLE)
                    binding!!.endScreen.setVisibility(View.GONE)
                    binding!!.playbackEndTime.setVisibility(View.VISIBLE)
                }

                else -> {
                    binding!!.endScreen.setVisibility(View.GONE)
                    binding!!.playbackEndTime.setVisibility(View.VISIBLE)
                }
            }
            buildPlaybackSpeedMenu()
            binding!!.playbackSpeed.setVisibility(View.VISIBLE)
        }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    ////////////////////////////////////////////////////////////////////////// */
    //region Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    private fun buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return
        }
        qualityPopupMenu!!.getMenu().removeGroup(POPUP_MENU_ID_QUALITY)
        val availableStreams: List<VideoStream>? = Optional.ofNullable<MediaItemTag>(player.getCurrentMetadata())
                .flatMap<MediaItemTag.Quality?>(Function<MediaItemTag, Optional<out MediaItemTag.Quality?>>({ obj: MediaItemTag -> obj.getMaybeQuality() }))
                .map<List<VideoStream>?>(Function<MediaItemTag.Quality?, List<VideoStream>?>({ getSortedVideoStreams() }))
                .orElse(null)
        if (availableStreams == null) {
            return
        }
        for (i in availableStreams.indices) {
            val videoStream: VideoStream = availableStreams.get(i)
            qualityPopupMenu!!.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.getResolution())
        }
        qualityPopupMenu!!.setOnMenuItemClickListener(this)
        qualityPopupMenu!!.setOnDismissListener(this)
        player.getSelectedVideoStream()
                .ifPresent(Consumer({ s: VideoStream? -> binding!!.qualityTextView.setText(s!!.getResolution()) }))
    }

    private fun buildAudioTrackMenu() {
        if (audioTrackPopupMenu == null) {
            return
        }
        audioTrackPopupMenu!!.getMenu().removeGroup(POPUP_MENU_ID_AUDIO_TRACK)
        val availableStreams: List<AudioStream>? = Optional.ofNullable<MediaItemTag>(player.getCurrentMetadata())
                .flatMap<MediaItemTag.AudioTrack?>(Function<MediaItemTag, Optional<out MediaItemTag.AudioTrack?>>({ obj: MediaItemTag -> obj.getMaybeAudioTrack() }))
                .map<List<AudioStream>?>(Function<MediaItemTag.AudioTrack?, List<AudioStream>?>({ getAudioStreams() }))
                .orElse(null)
        if (availableStreams == null || availableStreams.size < 2) {
            return
        }
        for (i in availableStreams.indices) {
            val audioStream: AudioStream = availableStreams.get(i)
            audioTrackPopupMenu!!.getMenu().add(POPUP_MENU_ID_AUDIO_TRACK, i, Menu.NONE,
                    Localization.audioTrackName(context, audioStream))
        }
        player.getSelectedAudioStream()
                .ifPresent(Consumer({ s: AudioStream? ->
                    binding!!.audioTrackTextView.setText(
                            Localization.audioTrackName(context, s))
                }))
        binding!!.audioTrackTextView.setVisibility(View.VISIBLE)
        audioTrackPopupMenu!!.setOnMenuItemClickListener(this)
        audioTrackPopupMenu!!.setOnDismissListener(this)
    }

    private fun buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return
        }
        playbackSpeedPopupMenu!!.getMenu().removeGroup(POPUP_MENU_ID_PLAYBACK_SPEED)
        for (i in PLAYBACK_SPEEDS.indices) {
            playbackSpeedPopupMenu!!.getMenu().add(POPUP_MENU_ID_PLAYBACK_SPEED, i, Menu.NONE,
                    PlayerHelper.formatSpeed(PLAYBACK_SPEEDS.get(i).toDouble()))
        }
        binding!!.playbackSpeed.setText(PlayerHelper.formatSpeed(player.getPlaybackSpeed().toDouble()))
        playbackSpeedPopupMenu!!.setOnMenuItemClickListener(this)
        playbackSpeedPopupMenu!!.setOnDismissListener(this)
    }

    private fun buildCaptionMenu(availableLanguages: List<String?>) {
        if (captionPopupMenu == null) {
            return
        }
        captionPopupMenu!!.getMenu().removeGroup(POPUP_MENU_ID_CAPTION)
        captionPopupMenu!!.setOnDismissListener(this)

        // Add option for turning off caption
        val captionOffItem: MenuItem = captionPopupMenu!!.getMenu().add(POPUP_MENU_ID_CAPTION,
                0, Menu.NONE, R.string.caption_none)
        captionOffItem.setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener({ menuItem: MenuItem? ->
            val textRendererIndex: Int = player.getCaptionRendererIndex()
            if (textRendererIndex != Player.Companion.RENDERER_UNAVAILABLE) {
                player.getTrackSelector().setParameters(player.getTrackSelector()
                        .buildUponParameters().setRendererDisabled(textRendererIndex, true))
            }
            player.getPrefs().edit()
                    .remove(context.getString(R.string.caption_user_set_key)).apply()
            true
        }))

        // Add all available captions
        for (i in availableLanguages.indices) {
            val captionLanguage: String = (availableLanguages.get(i))!!
            val captionItem: MenuItem = captionPopupMenu!!.getMenu().add(POPUP_MENU_ID_CAPTION,
                    i + 1, Menu.NONE, captionLanguage)
            captionItem.setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener({ menuItem: MenuItem? ->
                val textRendererIndex: Int = player.getCaptionRendererIndex()
                if (textRendererIndex != Player.Companion.RENDERER_UNAVAILABLE) {
                    // DefaultTrackSelector will select for text tracks in the following order.
                    // When multiple tracks share the same rank, a random track will be chosen.
                    // 1. ANY track exactly matching preferred language name
                    // 2. ANY track exactly matching preferred language stem
                    // 3. ROLE_FLAG_CAPTION track matching preferred language stem
                    // 4. ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND track matching preferred language stem
                    // This means if a caption track of preferred language is not available,
                    // then an auto-generated track of that language will be chosen automatically.
                    player.getTrackSelector().setParameters(player.getTrackSelector()
                            .buildUponParameters()
                            .setPreferredTextLanguages(captionLanguage,
                                    PlayerHelper.captionLanguageStemOf(captionLanguage))
                            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                            .setRendererDisabled(textRendererIndex, false))
                    player.getPrefs().edit().putString(context.getString(
                            R.string.caption_user_set_key), captionLanguage).apply()
                }
                true
            }))
        }
        captionPopupMenu!!.setOnDismissListener(this)

        // apply caption language from previous user preference
        val textRendererIndex: Int = player.getCaptionRendererIndex()
        if (textRendererIndex == Player.Companion.RENDERER_UNAVAILABLE) {
            return
        }

        // If user prefers to show no caption, then disable the renderer.
        // Otherwise, DefaultTrackSelector may automatically find an available caption
        // and display that.
        val userPreferredLanguage: String? = player.getPrefs().getString(context.getString(R.string.caption_user_set_key), null)
        if (userPreferredLanguage == null) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setRendererDisabled(textRendererIndex, true))
            return
        }

        // Only set preferred language if it does not match the user preference,
        // otherwise there might be an infinite cycle at onTextTracksChanged.
        val selectedPreferredLanguages: List<String> = player.getTrackSelector().getParameters().preferredTextLanguages
        if (!selectedPreferredLanguages.contains(userPreferredLanguage)) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setPreferredTextLanguages(userPreferredLanguage,
                            PlayerHelper.captionLanguageStemOf(userPreferredLanguage))
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false))
        }
    }

    protected abstract fun onPlaybackSpeedClicked()
    private fun onQualityClicked() {
        qualityPopupMenu!!.show()
        isSomePopupMenuVisible = true
        player.getSelectedVideoStream()
                .map(Function({ s: VideoStream? -> MediaFormat.getNameById(s!!.getFormatId()) + " " + s.getResolution() }))
                .ifPresent(Consumer({ text: String? -> binding!!.qualityTextView.setText(text) }))
    }

    private fun onAudioTracksClicked() {
        audioTrackPopupMenu!!.show()
        isSomePopupMenuVisible = true
    }

    /**
     * Called when an item of the quality selector or the playback speed selector is selected.
     */
    public override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("onMenuItemClick() called with: "
                    + "menuItem = [" + menuItem + "], "
                    + "menuItem.getItemId = [" + menuItem.getItemId() + "]"))
        }
        if (menuItem.getGroupId() == POPUP_MENU_ID_QUALITY) {
            onQualityItemClick(menuItem)
            return true
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_AUDIO_TRACK) {
            onAudioTrackItemClick(menuItem)
            return true
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_PLAYBACK_SPEED) {
            val speedIndex: Int = menuItem.getItemId()
            val speed: Float = PLAYBACK_SPEEDS.get(speedIndex)
            player.setPlaybackSpeed(speed)
            binding!!.playbackSpeed.setText(PlayerHelper.formatSpeed(speed.toDouble()))
        }
        return false
    }

    private fun onQualityItemClick(menuItem: MenuItem) {
        val menuItemIndex: Int = menuItem.getItemId()
        val currentMetadata: MediaItemTag? = player.getCurrentMetadata()
        if (currentMetadata == null || currentMetadata.getMaybeQuality().isEmpty()) {
            return
        }
        val quality: MediaItemTag.Quality = currentMetadata.getMaybeQuality().get()
        val availableStreams: List<VideoStream?> = quality.getSortedVideoStreams()
        val selectedStreamIndex: Int = quality.getSelectedVideoStreamIndex()
        if (selectedStreamIndex == menuItemIndex || availableStreams.size <= menuItemIndex) {
            return
        }
        val newResolution: String = availableStreams.get(menuItemIndex)!!.getResolution()
        player.setPlaybackQuality(newResolution)
        binding!!.qualityTextView.setText(menuItem.getTitle())
    }

    private fun onAudioTrackItemClick(menuItem: MenuItem) {
        val menuItemIndex: Int = menuItem.getItemId()
        val currentMetadata: MediaItemTag? = player.getCurrentMetadata()
        if (currentMetadata == null || currentMetadata.getMaybeAudioTrack().isEmpty()) {
            return
        }
        val audioTrack: MediaItemTag.AudioTrack = currentMetadata.getMaybeAudioTrack().get()
        val availableStreams: List<AudioStream?> = audioTrack.getAudioStreams()
        val selectedStreamIndex: Int = audioTrack.getSelectedAudioStreamIndex()
        if (selectedStreamIndex == menuItemIndex || availableStreams.size <= menuItemIndex) {
            return
        }
        val newAudioTrack: String? = availableStreams.get(menuItemIndex)!!.getAudioTrackId()
        player.setAudioTrack(newAudioTrack)
        binding!!.audioTrackTextView.setText(menuItem.getTitle())
    }

    /**
     * Called when some popup menu is dismissed.
     */
    public override fun onDismiss(menu: PopupMenu?) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]")
        }
        isSomePopupMenuVisible = false //TODO check if this works
        player.getSelectedVideoStream()
                .ifPresent(Consumer({ s: VideoStream? -> binding!!.qualityTextView.setText(s!!.getResolution()) }))
        if (player.isPlaying()) {
            hideControls(DEFAULT_CONTROLS_DURATION, 0)
            hideSystemUIIfNeeded()
        }
    }

    private fun onCaptionClicked() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onCaptionClicked() called")
        }
        captionPopupMenu!!.show()
        isSomePopupMenuVisible = true
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    ////////////////////////////////////////////////////////////////////////// */
    //region Captions (text tracks)
    public override fun onTextTracksChanged(currentTracks: Tracks) {
        super.onTextTracksChanged(currentTracks)
        val trackTypeTextSupported: Boolean = (!currentTracks.containsType(C.TRACK_TYPE_TEXT)
                || currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT, false))
        if ((getPlayer().getTrackSelector().getCurrentMappedTrackInfo() == null
                        || !trackTypeTextSupported)) {
            binding!!.captionTextView.setVisibility(View.GONE)
            return
        }

        // Extract all loaded languages
        val textTracks: List<Tracks.Group> = currentTracks
                .getGroups()
                .stream()
                .filter(Predicate({ trackGroupInfo: Tracks.Group -> C.TRACK_TYPE_TEXT == trackGroupInfo.getType() }))
                .collect(Collectors.toList())
        val availableLanguages: List<String?> = textTracks.stream()
                .map<TrackGroup>(Function<Tracks.Group, TrackGroup>({ Tracks.Group.getMediaTrackGroup() }))
                .filter(Predicate<TrackGroup>({ textTrack: TrackGroup -> textTrack.length > 0 }))
                .map<String?>(Function<TrackGroup, String?>({ textTrack: TrackGroup -> textTrack.getFormat(0).language }))
                .collect(Collectors.toList<String?>())

        // Find selected text track
        val selectedTracks: Optional<Format> = textTracks.stream()
                .filter(Predicate<Tracks.Group>({ Tracks.Group.isSelected() }))
                .filter(Predicate<Tracks.Group>({ info: Tracks.Group -> info.getMediaTrackGroup().length >= 1 }))
                .map<Format>(Function<Tracks.Group, Format>({ info: Tracks.Group -> info.getMediaTrackGroup().getFormat(0) }))
                .findFirst()

        // Build UI
        buildCaptionMenu(availableLanguages)
        if (player.getTrackSelector().getParameters().getRendererDisabled(
                        player.getCaptionRendererIndex()) || selectedTracks.isEmpty()) {
            binding!!.captionTextView.setText(R.string.caption_none)
        } else {
            binding!!.captionTextView.setText(selectedTracks.get().language)
        }
        binding!!.captionTextView.setVisibility(
                if (availableLanguages.isEmpty()) View.GONE else View.VISIBLE)
    }

    public override fun onCues(cues: List<Cue?>) {
        super.onCues(cues)
        binding!!.subtitleView.setCues(cues)
    }

    private fun setupSubtitleView() {
        setupSubtitleView(PlayerHelper.getCaptionScale(context))
        val captionStyle: CaptionStyleCompat = PlayerHelper.getCaptionStyle(context)
        binding!!.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT)
        binding!!.subtitleView.setStyle(captionStyle)
    }

    protected abstract fun setupSubtitleView(captionScale: Float)
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    ////////////////////////////////////////////////////////////////////////// */
    //region Click listeners
    /**
     * Create on-click listener which manages the player controls after the view on-click action.
     *
     * @param runnable The action to be executed.
     * @return The view click listener.
     */
    protected fun makeOnClickListener(runnable: Runnable): View.OnClickListener {
        return View.OnClickListener({ v: View ->
            if (MainActivity.Companion.DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]")
            }
            runnable.run()

            // Manages the player controls after handling the view click.
            if (player.getCurrentState() == Player.Companion.STATE_COMPLETED) {
                return@OnClickListener
            }
            controlsVisibilityHandler.removeCallbacksAndMessages(null)
            showHideShadow(true, DEFAULT_CONTROLS_DURATION)
            binding!!.playbackControlRoot.animate(true, DEFAULT_CONTROLS_DURATION, AnimationType.ALPHA, 0, Runnable({
                if (player.getCurrentState() == Player.Companion.STATE_PLAYING && !isSomePopupMenuVisible) {
                    if ((v === binding!!.playPauseButton // Hide controls in fullscreen immediately
                                    || (v === binding!!.screenRotationButton && isFullscreen))) {
                        hideControls(0, 0)
                    } else {
                        hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME)
                    }
                }
            }))
        })
    }

    open fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> if (DeviceUtils.isTv(context) && isControlsVisible) {
                hideControls(0, 0)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (((binding!!.getRoot().hasFocus() && !binding!!.playbackControlRoot.hasFocus())
                                || isAnyListViewOpen)) {
                    // do not interfere with focus in playlist and play queue etc.
                    break
                }
                if (player.getCurrentState() == Player.Companion.STATE_BLOCKED) {
                    return true
                }
                if (isControlsVisible) {
                    hideControls(DEFAULT_CONTROLS_DURATION, DPAD_CONTROLS_HIDE_TIME)
                } else {
                    binding!!.playPauseButton.requestFocus()
                    showControlsThenHide()
                    showSystemUIPartially()
                    return true
                }
            }

            else -> {}
        }
        return false
    }

    private fun onMoreOptionsClicked() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "onMoreOptionsClicked() called")
        }
        val isMoreControlsVisible: Boolean = binding!!.secondaryControls.getVisibility() == View.VISIBLE
        binding!!.moreOptionsButton.animateRotation(DEFAULT_CONTROLS_DURATION, if (isMoreControlsVisible) 0 else 180)
        binding!!.secondaryControls.animate(!isMoreControlsVisible, DEFAULT_CONTROLS_DURATION, AnimationType.SLIDE_AND_ALPHA, 0, Runnable({
            // Fix for a ripple effect on background drawable.
            // When view returns from GONE state it takes more milliseconds than returning
            // from INVISIBLE state. And the delay makes ripple background end to fast
            if (isMoreControlsVisible) {
                binding!!.secondaryControls.setVisibility(View.INVISIBLE)
            }
        }))
        showControls(DEFAULT_CONTROLS_DURATION)
    }

    private fun onPlayWithKodiClicked() {
        if (player.getCurrentMetadata() != null) {
            player.pause()
            KoreUtils.playWithKore(context, Uri.parse(player.getVideoUrl()))
        }
    }

    private fun onOpenInBrowserClicked() {
        player.getCurrentStreamInfo().ifPresent(Consumer({ streamInfo: StreamInfo? -> ShareUtils.openUrlInBrowser(player.getContext(), streamInfo!!.getOriginalUrl()) }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    ////////////////////////////////////////////////////////////////////////// */
    //region Video size
    protected fun setResizeMode(resizeMode: @ResizeMode Int) {
        binding!!.surfaceView.setResizeMode(resizeMode)
        binding!!.resizeTextView.setText(PlayerHelper.resizeTypeOf(context, resizeMode))
    }

    fun onResizeClicked() {
        setResizeMode(PlayerHelper.nextResizeModeAndSaveToPrefs(player, binding!!.surfaceView.getResizeMode()))
    }

    public override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        binding!!.surfaceView.setAspectRatio((videoSize.width.toFloat()) / videoSize.height)
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // SurfaceHolderCallback helpers
    ////////////////////////////////////////////////////////////////////////// */
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
    fun setupVideoSurfaceIfNeeded() {
        if (!surfaceIsSetup && (player.getExoPlayer() != null
                        ) && (binding!!.getRoot().getParent() != null)) {
            // make sure there is nothing left over from previous calls
            clearVideoSurface()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // >=API23
                surfaceHolderCallback = SurfaceHolderCallback(context, player.getExoPlayer())
                binding!!.surfaceView.getHolder().addCallback(surfaceHolderCallback)

                // ensure player is using an unreleased surface, which the surfaceView might not be
                // when starting playback on background or during player switching
                if (binding!!.surfaceView.getHolder().getSurface().isValid()) {
                    // initially set the surface manually otherwise
                    // onRenderedFirstFrame() will not be called
                    player.getExoPlayer()!!.setVideoSurfaceHolder(binding!!.surfaceView.getHolder())
                }
            } else {
                player.getExoPlayer()!!.setVideoSurfaceView(binding!!.surfaceView)
            }
            surfaceIsSetup = true
        }
    }

    private fun clearVideoSurface() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M // >=API23
                        && surfaceHolderCallback != null)) {
            binding!!.surfaceView.getHolder().removeCallback(surfaceHolderCallback)
            surfaceHolderCallback!!.release()
            surfaceHolderCallback = null
        }
        Optional.ofNullable(player.getExoPlayer()).ifPresent(Consumer({ obj: ExoPlayer -> obj.clearVideoSurface() }))
        surfaceIsSetup = false
    }

    companion object {
        private val TAG: String = VideoPlayerUi::class.java.getSimpleName()

        // time constants
        val DEFAULT_CONTROLS_DURATION: Long = 300 // 300 millis
        val DEFAULT_CONTROLS_HIDE_TIME: Long = 2000 // 2 Seconds
        val DPAD_CONTROLS_HIDE_TIME: Long = 7000 // 7 Seconds
        val SEEK_OVERLAY_DURATION: Int = 450 // 450 millis

        // other constants (TODO remove playback speeds and use normal menu for popup, too)
        private val PLAYBACK_SPEEDS: FloatArray = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    ////////////////////////////////////////////////////////////////////////// */
        private val POPUP_MENU_ID_QUALITY: Int = 69
        private val POPUP_MENU_ID_AUDIO_TRACK: Int = 70
        private val POPUP_MENU_ID_PLAYBACK_SPEED: Int = 79
        private val POPUP_MENU_ID_CAPTION: Int = 89
    }
}
