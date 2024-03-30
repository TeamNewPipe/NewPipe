package org.schabi.newpipe.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.util.Log
import android.view.LayoutInflater
import androidx.core.math.MathUtils
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.text.CueGroup
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.video.VideoSize
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Target
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.disposables.SerialDisposable
import io.reactivex.rxjava3.functions.Action
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.CustomRenderersFactory
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationConstants
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.player.playback.MediaSourceManager
import org.schabi.newpipe.player.playback.PlaybackListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.QualityResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.player.ui.PopupPlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SerializedCache
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.IntPredicate
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.IntStream
import kotlin.math.max

class Player(//TODO try to remove and replace everything with context
        private val service: PlayerService) : PlaybackListener, com.google.android.exoplayer2.Player.Listener {
    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    ////////////////////////////////////////////////////////////////////////// */
    // play queue might be null e.g. while player is starting
    private var playQueue: PlayQueue? = null
    private var playQueueManager: MediaSourceManager? = null
    private var currentItem: PlayQueueItem? = null
    private var currentMetadata: MediaItemTag? = null
    private var currentThumbnail: Bitmap? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    ////////////////////////////////////////////////////////////////////////// */
    private var simpleExoPlayer: ExoPlayer? = null
    private var audioReactor: AudioReactor? = null
    private val trackSelector: DefaultTrackSelector
    private val loadController: LoadController
    private val renderFactory: DefaultRenderersFactory
    private val videoResolver: VideoPlaybackResolver
    private val audioResolver: AudioPlaybackResolver

    /*//////////////////////////////////////////////////////////////////////////
    // Player states
    ////////////////////////////////////////////////////////////////////////// */
    private var playerType: PlayerType = PlayerType.MAIN
    private var currentState: Int = STATE_PREFLIGHT

    // audio only mode does not mean that player type is background, but that the player was
    // minimized to background but will resume automatically to the original player type
    private var isAudioOnly: Boolean = false
    private var isPrepared: Boolean = false

    /*//////////////////////////////////////////////////////////////////////////
    // UIs, listeners and disposables
    ////////////////////////////////////////////////////////////////////////// */
    // keep the unusual member name
    private val UIs: PlayerUiList
    private var broadcastReceiver: BroadcastReceiver? = null
    private var intentFilter: IntentFilter? = null
    private var fragmentListener: PlayerServiceEventListener? = null
    private var activityListener: PlayerEventListener? = null
    private val progressUpdateDisposable: SerialDisposable = SerialDisposable()
    private val databaseUpdateDisposable: CompositeDisposable = CompositeDisposable()

    // This is the only listener we need for thumbnail loading, since there is always at most only
    // one thumbnail being loaded at a time. This field is also here to maintain a strong reference,
    // which would otherwise be garbage collected since Picasso holds weak references to targets.
    private val currentThumbnailTarget: Target

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private val context: Context
    private val prefs: SharedPreferences
    private val recordManager: HistoryRecordManager

    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    ////////////////////////////////////////////////////////////////////////// */
    //region Constructor
    init {
        context = service
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        recordManager = HistoryRecordManager(context)
        setupBroadcastReceiver()
        trackSelector = DefaultTrackSelector(context, PlayerHelper.getQualitySelector())
        val dataSource: PlayerDataSource = PlayerDataSource(context,
                DefaultBandwidthMeter.Builder(context).build())
        loadController = LoadController()
        renderFactory = if (prefs.getBoolean(
                        context.getString(
                                R.string.always_use_exoplayer_set_output_surface_workaround_key), false)) CustomRenderersFactory(context) else DefaultRenderersFactory(context)
        renderFactory.setEnableDecoderFallback(
                prefs.getBoolean(
                        context.getString(
                                R.string.use_exoplayer_decoder_fallback_key), false))
        videoResolver = VideoPlaybackResolver(context, dataSource, getQualityResolver())
        audioResolver = AudioPlaybackResolver(context, dataSource)
        currentThumbnailTarget = getCurrentThumbnailTarget()

        // The UIs added here should always be present. They will be initialized when the player
        // reaches the initialization step. Make sure the media session ui is before the
        // notification ui in the UIs list, since the notification depends on the media session in
        // PlayerUi#initPlayer(), and UIs.call() guarantees UI order is preserved.
        UIs = PlayerUiList(
                MediaSessionPlayerUi(this),
                NotificationPlayerUi(this)
        )
    }

    private fun getQualityResolver(): QualityResolver {
        return object : QualityResolver {
            public override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream?>): Int {
                return if (videoPlayerSelected()) ListHelper.getDefaultResolutionIndex(context, sortedVideos) else ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos)
            }

            public override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream?>,
                                                           playbackQuality: String?): Int {
                return if (videoPlayerSelected()) ListHelper.getResolutionIndex(context, sortedVideos, playbackQuality) else ListHelper.getPopupResolutionIndex(context, sortedVideos, playbackQuality)
            }
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback initialization via intent
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback initialization via intent
    fun handleIntent(intent: Intent) {
        // fail fast if no play queue was provided
        val queueCache: String? = intent.getStringExtra(PLAY_QUEUE_KEY)
        if (queueCache == null) {
            return
        }
        val newQueue: PlayQueue? = SerializedCache.Companion.getInstance().take<PlayQueue>(queueCache, PlayQueue::class.java)
        if (newQueue == null) {
            return
        }
        val oldPlayerType: PlayerType = playerType
        playerType = PlayerType.Companion.retrieveFromIntent(intent)
        initUIsForCurrentPlayerType()
        // We need to setup audioOnly before super(), see "sourceOf"
        isAudioOnly = audioPlayerSelected()
        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            videoResolver.setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY))
        }

        // Resolve enqueue intents
        if (intent.getBooleanExtra(ENQUEUE, false) && playQueue != null) {
            playQueue!!.append(newQueue.getStreams())
            return

            // Resolve enqueue next intents
        } else if (intent.getBooleanExtra(ENQUEUE_NEXT, false) && playQueue != null) {
            val currentIndex: Int = playQueue.getIndex()
            playQueue!!.append(newQueue.getStreams())
            playQueue!!.move(playQueue!!.size() - 1, currentIndex + 1)
            return
        }
        val savedParameters: PlaybackParameters? = PlayerHelper.retrievePlaybackParametersFromPrefs(this)
        val playbackSpeed: Float = savedParameters!!.speed
        val playbackPitch: Float = savedParameters.pitch
        val playbackSkipSilence: Boolean = getPrefs().getBoolean(getContext().getString(
                R.string.playback_skip_silence_key), getPlaybackSkipSilence())
        val samePlayQueue: Boolean = playQueue != null && playQueue!!.equalStreamsAndIndex(newQueue)
        val repeatMode: Int = intent.getIntExtra(REPEAT_MODE, getRepeatMode())
        val playWhenReady: Boolean = intent.getBooleanExtra(PLAY_WHEN_READY, true)
        val isMuted: Boolean = intent.getBooleanExtra(IS_MUTED, isMuted())

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
        if ((!exoPlayerIsNull()
                        && (newQueue.size() == 1) && (newQueue.getItem() != null
                        ) && (playQueue != null) && (playQueue!!.size() == 1) && (playQueue!!.getItem() != null
                        ) && (newQueue.getItem().getUrl() == playQueue!!.getItem().getUrl()) && (newQueue.getItem().getRecoveryPosition() != PlayQueueItem.Companion.RECOVERY_UNSET))) {
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if ((simpleExoPlayer!!.getPlaybackState()
                            == com.google.android.exoplayer2.Player.STATE_IDLE)) {
                simpleExoPlayer!!.prepare()
            }
            simpleExoPlayer!!.seekTo(playQueue.getIndex(), newQueue.getItem().getRecoveryPosition())
            simpleExoPlayer!!.setPlayWhenReady(playWhenReady)
        } else if ((!exoPlayerIsNull()
                        && samePlayQueue
                        && (playQueue != null
                        ) && !playQueue!!.isDisposed())) {
            // Do not re-init the same PlayQueue. Save time
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if ((simpleExoPlayer!!.getPlaybackState()
                            == com.google.android.exoplayer2.Player.STATE_IDLE)) {
                simpleExoPlayer!!.prepare()
            }
            simpleExoPlayer!!.setPlayWhenReady(playWhenReady)
        } else if ((intent.getBooleanExtra(RESUME_PLAYBACK, false)
                        && DependentPreferenceHelper.getResumePlaybackEnabled(context)
                        && !samePlayQueue
                        && !newQueue.isEmpty()
                        && (newQueue.getItem() != null
                        ) && (newQueue.getItem().getRecoveryPosition() == PlayQueueItem.Companion.RECOVERY_UNSET))) {
            databaseUpdateDisposable.add(recordManager.loadStreamState(newQueue.getItem())
                    .observeOn(AndroidSchedulers.mainThread()) // Do not place initPlayback() in doFinally() because
                    // it restarts playback after destroy()
                    //.doFinally()
                    .subscribe(
                            io.reactivex.rxjava3.functions.Consumer<StreamStateEntity?>({ state: StreamStateEntity? ->
                                if (!state!!.isFinished(newQueue.getItem().getDuration())) {
                                    // resume playback only if the stream was not played to the end
                                    newQueue.setRecovery(newQueue.getIndex(),
                                            state.getProgressMillis())
                                }
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted)
                            }),
                            io.reactivex.rxjava3.functions.Consumer({ error: Throwable? ->
                                if (DEBUG) {
                                    Log.w(TAG, "Failed to start playback", error)
                                }
                                // In case any error we can start playback without history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted)
                            }),
                            Action({
                                // Completed but not found in history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted)
                            })
                    ))
        } else {
            // Good to go...
            // In a case of equal PlayQueues we can re-init old one but only when it is disposed
            initPlayback((if (samePlayQueue) playQueue else newQueue)!!, repeatMode, playbackSpeed,
                    playbackPitch, playbackSkipSilence, playWhenReady, isMuted)
        }
        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            setRecovery()
            reloadPlayQueueManager()
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.setupAfterIntent() }))
        NavigationHelper.sendPlayerStartedEvent(context)
    }

    private fun initUIsForCurrentPlayerType() {
        if (((UIs.get((MainPlayerUi::class.java)).isPresent() && playerType == PlayerType.MAIN)
                        || (UIs.get((PopupPlayerUi::class.java)).isPresent() && playerType == PlayerType.POPUP))) {
            // correct UI already in place
            return
        }

        // try to reuse binding if possible
        val binding: PlayerBinding = UIs.get<VideoPlayerUi?>((VideoPlayerUi::class.java)).map<PlayerBinding?>(Function<VideoPlayerUi?, PlayerBinding?>({ obj: VideoPlayerUi? -> obj.getBinding() }))
                .orElseGet(Supplier<PlayerBinding?>({
                    if (playerType == PlayerType.AUDIO) {
                        return@orElseGet null
                    } else {
                        return@orElseGet PlayerBinding.inflate(LayoutInflater.from(context))
                    }
                }))
        when (playerType) {
            PlayerType.MAIN -> {
                UIs.destroyAll(PopupPlayerUi::class.java)
                UIs.addAndPrepare(MainPlayerUi(this, binding))
            }

            PlayerType.POPUP -> {
                UIs.destroyAll(MainPlayerUi::class.java)
                UIs.addAndPrepare(PopupPlayerUi(this, binding))
            }

            PlayerType.AUDIO -> UIs.destroyAll(VideoPlayerUi::class.java)
        }
    }

    private fun initPlayback(queue: PlayQueue,
                             repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int,
                             playbackSpeed: Float,
                             playbackPitch: Float,
                             playbackSkipSilence: Boolean,
                             playOnReady: Boolean,
                             isMuted: Boolean) {
        destroyPlayer()
        initPlayer(playOnReady)
        setRepeatMode(repeatMode)
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence)
        playQueue = queue
        playQueue!!.init()
        reloadPlayQueueManager()
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.initPlayback() }))
        simpleExoPlayer!!.setVolume((if (isMuted) 0 else 1).toFloat())
        notifyQueueUpdateToListeners()
    }

    private fun initPlayer(playOnReady: Boolean) {
        if (DEBUG) {
            Log.d(TAG, "initPlayer() called with: playOnReady = [" + playOnReady + "]")
        }
        simpleExoPlayer = ExoPlayer.Builder(context, renderFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadController)
                .setUsePlatformDiagnostics(false)
                .build()
        simpleExoPlayer!!.addListener(this)
        simpleExoPlayer!!.setPlayWhenReady(playOnReady)
        simpleExoPlayer!!.setSeekParameters(PlayerHelper.getSeekParameters(context))
        simpleExoPlayer!!.setWakeMode(C.WAKE_MODE_NETWORK)
        simpleExoPlayer!!.setHandleAudioBecomingNoisy(true)
        audioReactor = AudioReactor(context, simpleExoPlayer!!)
        registerBroadcastReceiver()

        // Setup UIs
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.initPlayer() }))

        // Disable media tunneling if requested by the user from ExoPlayer settings
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.disable_media_tunneling_key), false)) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true))
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Destroy and recovery
    ////////////////////////////////////////////////////////////////////////// */
    //region Destroy and recovery
    private fun destroyPlayer() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayer() called")
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.destroyPlayer() }))
        if (!exoPlayerIsNull()) {
            simpleExoPlayer!!.removeListener(this)
            simpleExoPlayer!!.stop()
            simpleExoPlayer!!.release()
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop()
        }
        if (playQueue != null) {
            playQueue!!.dispose()
        }
        if (audioReactor != null) {
            audioReactor!!.dispose()
        }
        if (playQueueManager != null) {
            playQueueManager!!.dispose()
        }
    }

    fun destroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called")
        }
        saveStreamProgressState()
        setRecovery()
        stopActivityBinding()
        destroyPlayer()
        unregisterBroadcastReceiver()
        databaseUpdateDisposable.clear()
        progressUpdateDisposable.set(null)
        cancelLoadingCurrentThumbnail()
        UIs.destroyAll(Any::class.java) // destroy every UI: obviously every UI extends Object
    }

    fun setRecovery() {
        if (playQueue == null || exoPlayerIsNull()) {
            return
        }
        val queuePos: Int = playQueue.getIndex()
        val windowPos: Long = simpleExoPlayer!!.getCurrentPosition()
        val duration: Long = simpleExoPlayer!!.getDuration()

        // No checks due to https://github.com/TeamNewPipe/NewPipe/pull/7195#issuecomment-962624380
        setRecovery(queuePos, MathUtils.clamp(windowPos, 0, duration))
    }

    private fun setRecovery(queuePos: Int, windowPos: Long) {
        if (playQueue == null || playQueue!!.size() <= queuePos) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos)
        }
        playQueue!!.setRecovery(queuePos, windowPos)
    }

    fun reloadPlayQueueManager() {
        if (playQueueManager != null) {
            playQueueManager!!.dispose()
        }
        if (playQueue != null) {
            playQueueManager = MediaSourceManager(this, playQueue!!)
        }
    }

    // own playback listener
    public override fun onPlaybackShutdown() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackShutdown() called")
        }
        // destroys the service, which in turn will destroy the player
        service.stopService()
    }

    fun smoothStopForImmediateReusing() {
        // Pausing would make transition from one stream to a new stream not smooth, so only stop
        simpleExoPlayer!!.stop()
        setRecovery()
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.smoothStopForImmediateReusing() }))
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    ////////////////////////////////////////////////////////////////////////// */
    //region Broadcast receiver
    /**
     * This function prepares the broadcast receiver and is called only in the constructor.
     * Therefore if you want any PlayerUi to receive a broadcast action, you should add it here,
     * even if that player ui might never be added to the player. In that case the received
     * broadcast would not do anything.
     */
    private fun setupBroadcastReceiver() {
        if (DEBUG) {
            Log.d(TAG, "setupBroadcastReceiver() called")
        }
        broadcastReceiver = object : BroadcastReceiver() {
            public override fun onReceive(ctx: Context, intent: Intent) {
                onBroadcastReceived(intent)
            }
        }
        intentFilter = IntentFilter()
        intentFilter!!.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        intentFilter!!.addAction(NotificationConstants.ACTION_CLOSE)
        intentFilter!!.addAction(NotificationConstants.ACTION_PLAY_PAUSE)
        intentFilter!!.addAction(NotificationConstants.ACTION_PLAY_PREVIOUS)
        intentFilter!!.addAction(NotificationConstants.ACTION_PLAY_NEXT)
        intentFilter!!.addAction(NotificationConstants.ACTION_FAST_REWIND)
        intentFilter!!.addAction(NotificationConstants.ACTION_FAST_FORWARD)
        intentFilter!!.addAction(NotificationConstants.ACTION_REPEAT)
        intentFilter!!.addAction(NotificationConstants.ACTION_SHUFFLE)
        intentFilter!!.addAction(NotificationConstants.ACTION_RECREATE_NOTIFICATION)
        intentFilter!!.addAction(VideoDetailFragment.Companion.ACTION_VIDEO_FRAGMENT_RESUMED)
        intentFilter!!.addAction(VideoDetailFragment.Companion.ACTION_VIDEO_FRAGMENT_STOPPED)
        intentFilter!!.addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        intentFilter!!.addAction(Intent.ACTION_SCREEN_ON)
        intentFilter!!.addAction(Intent.ACTION_SCREEN_OFF)
        intentFilter!!.addAction(Intent.ACTION_HEADSET_PLUG)
    }

    private fun onBroadcastReceived(intent: Intent?) {
        if (intent == null || intent.getAction() == null) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]")
        }
        when (intent.getAction()) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pause()
            NotificationConstants.ACTION_CLOSE -> service.stopService()
            NotificationConstants.ACTION_PLAY_PAUSE -> playPause()
            NotificationConstants.ACTION_PLAY_PREVIOUS -> playPrevious()
            NotificationConstants.ACTION_PLAY_NEXT -> playNext()
            NotificationConstants.ACTION_FAST_REWIND -> fastRewind()
            NotificationConstants.ACTION_FAST_FORWARD -> fastForward()
            NotificationConstants.ACTION_REPEAT -> cycleNextRepeatMode()
            NotificationConstants.ACTION_SHUFFLE -> toggleShuffleModeEnabled()
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                Localization.assureCorrectAppLanguage(service)
                if (DEBUG) {
                    Log.d(TAG, "ACTION_CONFIGURATION_CHANGED received")
                }
            }
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onBroadcastReceived(intent) }))
    }

    private fun registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver()
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (unregisteredException: IllegalArgumentException) {
            Log.w(TAG, ("Broadcast receiver already unregistered: "
                    + unregisteredException.message))
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail loading
    ////////////////////////////////////////////////////////////////////////// */
    //region Thumbnail loading
    private fun getCurrentThumbnailTarget(): Target {
        // a Picasso target is just a listener for thumbnail loading events
        return object : Target {
            public override fun onBitmapLoaded(bitmap: Bitmap, from: LoadedFrom) {
                if (DEBUG) {
                    Log.d(TAG, ("Thumbnail - onBitmapLoaded() called with: bitmap = [" + bitmap
                            + " -> " + bitmap.getWidth() + "x" + bitmap.getHeight() + "], from = ["
                            + from + "]"))
                }
                // there is a new thumbnail, so e.g. the end screen thumbnail needs to change, too.
                onThumbnailLoaded(bitmap)
            }

            public override fun onBitmapFailed(e: Exception, errorDrawable: Drawable) {
                Log.e(TAG, "Thumbnail - onBitmapFailed() called", e)
                // there is a new thumbnail, so e.g. the end screen thumbnail needs to change, too.
                onThumbnailLoaded(null)
            }

            public override fun onPrepareLoad(placeHolderDrawable: Drawable) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onPrepareLoad() called")
                }
            }
        }
    }

    private fun loadCurrentThumbnail(thumbnails: List<Image?>) {
        if (DEBUG) {
            Log.d(TAG, ("Thumbnail - loadCurrentThumbnail() called with thumbnails = ["
                    + thumbnails.size + "]"))
        }

        // first cancel any previous loading
        cancelLoadingCurrentThumbnail()

        // Unset currentThumbnail, since it is now outdated. This ensures it is not used in media
        // session metadata while the new thumbnail is being loaded by Picasso.
        onThumbnailLoaded(null)
        if (thumbnails.isEmpty()) {
            return
        }

        // scale down the notification thumbnail for performance
        PicassoHelper.loadScaledDownThumbnail(context, thumbnails)
                .tag(PICASSO_PLAYER_THUMBNAIL_TAG)
                .into(currentThumbnailTarget)
    }

    private fun cancelLoadingCurrentThumbnail() {
        // cancel the Picasso job associated with the player thumbnail, if any
        PicassoHelper.cancelTag(PICASSO_PLAYER_THUMBNAIL_TAG)
    }

    private fun onThumbnailLoaded(bitmap: Bitmap?) {
        // Avoid useless thumbnail updates, if the thumbnail has not actually changed. Based on the
        // thumbnail loading code, this if would be skipped only when both bitmaps are `null`, since
        // onThumbnailLoaded won't be called twice with the same nonnull bitmap by Picasso's target.
        if (currentThumbnail != bitmap) {
            currentThumbnail = bitmap
            UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onThumbnailLoaded(bitmap) }))
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback parameters
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback parameters
    fun getPlaybackSpeed(): Float {
        return getPlaybackParameters().speed
    }

    fun setPlaybackSpeed(speed: Float) {
        setPlaybackParameters(speed, getPlaybackPitch(), getPlaybackSkipSilence())
    }

    fun getPlaybackPitch(): Float {
        return getPlaybackParameters().pitch
    }

    fun getPlaybackSkipSilence(): Boolean {
        return !exoPlayerIsNull() && simpleExoPlayer!!.getSkipSilenceEnabled()
    }

    fun getPlaybackParameters(): PlaybackParameters {
        if (exoPlayerIsNull()) {
            return PlaybackParameters.DEFAULT
        }
        return simpleExoPlayer!!.getPlaybackParameters()
    }

    /**
     * Sets the playback parameters of the player, and also saves them to shared preferences.
     * Speed and pitch are rounded up to 2 decimal places before being used or saved.
     *
     * @param speed       the playback speed, will be rounded to up to 2 decimal places
     * @param pitch       the playback pitch, will be rounded to up to 2 decimal places
     * @param skipSilence skip silence during playback
     */
    fun setPlaybackParameters(speed: Float, pitch: Float,
                              skipSilence: Boolean) {
        val roundedSpeed: Float = Math.round(speed * 100.0f) / 100.0f
        val roundedPitch: Float = Math.round(pitch * 100.0f) / 100.0f
        PlayerHelper.savePlaybackParametersToPrefs(this, roundedSpeed, roundedPitch, skipSilence)
        simpleExoPlayer!!.setPlaybackParameters(
                PlaybackParameters(roundedSpeed, roundedPitch))
        simpleExoPlayer!!.setSkipSilenceEnabled(skipSilence)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    ////////////////////////////////////////////////////////////////////////// */
    //region Progress loop and updates
    private fun onUpdateProgress(currentProgress: Int,
                                 duration: Int,
                                 bufferPercent: Int) {
        if (isPrepared) {
            UIs.call(java.util.function.Consumer({ ui: PlayerUi -> ui.onUpdateProgress(currentProgress, duration, bufferPercent) }))
            notifyProgressUpdateToListeners(currentProgress, duration, bufferPercent)
        }
    }

    fun startProgressLoop() {
        progressUpdateDisposable.set(getProgressUpdateDisposable())
    }

    private fun stopProgressLoop() {
        progressUpdateDisposable.set(null)
    }

    fun isProgressLoopRunning(): Boolean {
        return progressUpdateDisposable.get() != null
    }

    fun triggerProgressUpdate() {
        if (exoPlayerIsNull()) {
            return
        }
        onUpdateProgress(max((simpleExoPlayer!!.getCurrentPosition().toInt()).toDouble(), 0.0).toInt(), simpleExoPlayer!!.getDuration().toInt(), simpleExoPlayer!!.getBufferedPercentage())
    }

    private fun getProgressUpdateDisposable(): Disposable {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS.toLong(), TimeUnit.MILLISECONDS,
                AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(io.reactivex.rxjava3.functions.Consumer({ ignored: Long? -> triggerProgressUpdate() }),
                        io.reactivex.rxjava3.functions.Consumer({ error: Throwable? -> Log.e(TAG, "Progress update failure: ", error) }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback states
    public override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onPlayWhenReadyChanged() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "reason = [" + reason + "]"))
        }
        val playbackState: Int = if (exoPlayerIsNull()) com.google.android.exoplayer2.Player.STATE_IDLE else simpleExoPlayer!!.getPlaybackState()
        updatePlaybackState(playWhenReady, playbackState)
    }

    public override fun onPlaybackStateChanged(playbackState: Int) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onPlaybackStateChanged() called with: "
                    + "playbackState = [" + playbackState + "]"))
        }
        updatePlaybackState(getPlayWhenReady(), playbackState)
    }

    private fun updatePlaybackState(playWhenReady: Boolean, playbackState: Int) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - updatePlaybackState() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "playbackState = [" + playbackState + "]"))
        }
        if (currentState == STATE_PAUSED_SEEK) {
            if (DEBUG) {
                Log.d(TAG, "updatePlaybackState() is currently blocked")
            }
            return
        }
        when (playbackState) {
            com.google.android.exoplayer2.Player.STATE_IDLE -> isPrepared = false
            com.google.android.exoplayer2.Player.STATE_BUFFERING -> if (isPrepared) {
                changeState(STATE_BUFFERING)
            }

            com.google.android.exoplayer2.Player.STATE_READY -> {
                if (!isPrepared) {
                    isPrepared = true
                    onPrepared(playWhenReady)
                }
                changeState(if (playWhenReady) STATE_PLAYING else STATE_PAUSED)
            }

            com.google.android.exoplayer2.Player.STATE_ENDED -> {
                changeState(STATE_COMPLETED)
                saveStreamProgressStateCompleted()
                isPrepared = false
            }
        }
    }

    // exoplayer listener
    public override fun onIsLoadingChanged(isLoading: Boolean) {
        if (!isLoading && (currentState == STATE_PAUSED) && isProgressLoopRunning()) {
            stopProgressLoop()
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop()
        }
    }

    // own playback listener
    public override fun onPlaybackBlock() {
        if (exoPlayerIsNull()) {
            return
        }
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackBlock() called")
        }
        currentItem = null
        currentMetadata = null
        simpleExoPlayer!!.stop()
        isPrepared = false
        changeState(STATE_BLOCKED)
    }

    // own playback listener
    public override fun onPlaybackUnblock(mediaSource: MediaSource?) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackUnblock() called")
        }
        if (exoPlayerIsNull()) {
            return
        }
        if (currentState == STATE_BLOCKED) {
            changeState(STATE_BUFFERING)
        }
        simpleExoPlayer!!.setMediaSource((mediaSource)!!, false)
        simpleExoPlayer!!.prepare()
    }

    fun changeState(state: Int) {
        if (DEBUG) {
            Log.d(TAG, "changeState() called with: state = [" + state + "]")
        }
        currentState = state
        when (state) {
            STATE_BLOCKED -> onBlocked()
            STATE_PLAYING -> onPlaying()
            STATE_BUFFERING -> onBuffering()
            STATE_PAUSED -> onPaused()
            STATE_PAUSED_SEEK -> onPausedSeek()
            STATE_COMPLETED -> onCompleted()
        }
        notifyPlaybackUpdateToListeners()
    }

    private fun onPrepared(playWhenReady: Boolean) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]")
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onPrepared() }))
        if (playWhenReady && !isMuted()) {
            audioReactor!!.requestAudioFocus()
        }
    }

    private fun onBlocked() {
        if (DEBUG) {
            Log.d(TAG, "onBlocked() called")
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop()
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onBlocked() }))
    }

    private fun onPlaying() {
        if (DEBUG) {
            Log.d(TAG, "onPlaying() called")
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop()
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onPlaying() }))
    }

    private fun onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called")
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onBuffering() }))
    }

    private fun onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called")
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop()
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onPaused() }))
    }

    private fun onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called")
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onPausedSeek() }))
    }

    private fun onCompleted() {
        if (DEBUG) {
            Log.d(TAG, "onCompleted() called" + (if (playQueue == null) ". playQueue is null" else ""))
        }
        if (playQueue == null) {
            return
        }
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onCompleted() }))
        if (playQueue.getIndex() < playQueue!!.size() - 1) {
            playQueue!!.offsetIndex(+1)
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop()
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    ////////////////////////////////////////////////////////////////////////// */
    //region Repeat and shuffle
    fun getRepeatMode(): @com.google.android.exoplayer2.Player.RepeatMode Int {
        return if (exoPlayerIsNull()) com.google.android.exoplayer2.Player.REPEAT_MODE_OFF else simpleExoPlayer!!.getRepeatMode()
    }

    fun setRepeatMode(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer!!.setRepeatMode(repeatMode)
        }
    }

    fun cycleNextRepeatMode() {
        setRepeatMode(PlayerHelper.nextRepeatMode(getRepeatMode()))
    }

    public override fun onRepeatModeChanged(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onRepeatModeChanged() called with: "
                    + "repeatMode = [" + repeatMode + "]"))
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onRepeatModeChanged(repeatMode) }))
        notifyPlaybackUpdateToListeners()
    }

    public override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onShuffleModeEnabledChanged() called with: "
                    + "mode = [" + shuffleModeEnabled + "]"))
        }
        if (playQueue != null) {
            if (shuffleModeEnabled) {
                playQueue!!.shuffle()
            } else {
                playQueue!!.unshuffle()
            }
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onShuffleModeEnabledChanged(shuffleModeEnabled) }))
        notifyPlaybackUpdateToListeners()
    }

    fun toggleShuffleModeEnabled() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer!!.setShuffleModeEnabled(!simpleExoPlayer!!.getShuffleModeEnabled())
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Mute / Unmute
    ////////////////////////////////////////////////////////////////////////// */
    //region Mute / Unmute
    fun toggleMute() {
        val wasMuted: Boolean = isMuted()
        simpleExoPlayer!!.setVolume((if (wasMuted) 1 else 0).toFloat())
        if (wasMuted) {
            audioReactor!!.requestAudioFocus()
        } else {
            audioReactor!!.abandonAudioFocus()
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onMuteUnmuteChanged(!wasMuted) }))
        notifyPlaybackUpdateToListeners()
    }

    fun isMuted(): Boolean {
        return !exoPlayerIsNull() && simpleExoPlayer!!.getVolume() == 0f
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer listeners (that didn't fit in other categories)
    ////////////////////////////////////////////////////////////////////////// */
    //region ExoPlayer listeners (that didn't fit in other categories)
    /**
     *
     * Listens for event or state changes on ExoPlayer. When any event happens, we check for
     * changes in the currently-playing metadata and update the encapsulating
     * [Player]. Downstream listeners are also informed.
     *
     *
     * When the renewed metadata contains any error, it is reported as a notification.
     * This is done because not all source resolution errors are [PlaybackException], which
     * are also captured by [ExoPlayer] and stops the playback.
     *
     * @param player The [com.google.android.exoplayer2.Player] whose state changed.
     * @param events The [com.google.android.exoplayer2.Player.Events] that has triggered
     * the player state changes.
     */
    public override fun onEvents(player: com.google.android.exoplayer2.Player,
                                 events: com.google.android.exoplayer2.Player.Events) {
        super<com.google.android.exoplayer2.Player.Listener>.onEvents(player, events)
        MediaItemTag.Companion.from(player.getCurrentMediaItem()).ifPresent(java.util.function.Consumer<MediaItemTag>({ tag: MediaItemTag ->
            if (tag === currentMetadata) {
                return@ifPresent  // we still have the same metadata, no need to do anything
            }
            val previousInfo: StreamInfo? = Optional.ofNullable(currentMetadata)
                    .flatMap(Function<MediaItemTag, Optional<out StreamInfo?>>({ obj: MediaItemTag -> obj.getMaybeStreamInfo() })).orElse(null)
            val previousAudioTrack: MediaItemTag.AudioTrack? = Optional.ofNullable(currentMetadata)
                    .flatMap(Function<MediaItemTag, Optional<out MediaItemTag.AudioTrack?>>({ obj: MediaItemTag -> obj.getMaybeAudioTrack() })).orElse(null)
            currentMetadata = tag
            if (!currentMetadata.getErrors().isEmpty()) {
                // new errors might have been added even if previousInfo == tag.getMaybeStreamInfo()
                val errorInfo: ErrorInfo = ErrorInfo(
                        currentMetadata.getErrors(),
                        UserAction.PLAY_STREAM,
                        ("Loading failed for [" + currentMetadata.getTitle()
                                + "]: " + currentMetadata.getStreamUrl()),
                        currentMetadata.getServiceId())
                createNotification(context, errorInfo)
            }
            currentMetadata.getMaybeStreamInfo().ifPresent(java.util.function.Consumer({ info: StreamInfo ->
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onEvents() update stream info: " + info.getName())
                }
                if (previousInfo == null || !(previousInfo.getUrl() == info.getUrl())) {
                    // only update with the new stream info if it has actually changed
                    updateMetadataWith(info)
                } else if ((previousAudioTrack == null
                                || tag.getMaybeAudioTrack()
                                .map(Function<MediaItemTag.AudioTrack?, Boolean>({ t: MediaItemTag.AudioTrack? ->
                                    t.getSelectedAudioStreamIndex()
                                    != previousAudioTrack.getSelectedAudioStreamIndex()
                                }))
                                .orElse(false))) {
                    notifyAudioTrackUpdateToListeners()
                }
            }))
        }))
    }

    public override fun onTracksChanged(tracks: Tracks) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onTracksChanged(), "
                    + "track group size = " + tracks.getGroups().size))
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onTextTracksChanged(tracks) }))
    }

    public override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - playbackParameters(), speed = [" + playbackParameters.speed
                    + "], pitch = [" + playbackParameters.pitch + "]"))
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onPlaybackParametersChanged(playbackParameters) }))
    }

    public override fun onPositionDiscontinuity(oldPosition: PositionInfo,
                                                newPosition: PositionInfo,
                                                discontinuityReason: @DiscontinuityReason Int) {
        if (DEBUG) {
            Log.d(TAG, ("ExoPlayer - onPositionDiscontinuity() called with "
                    + "oldPositionIndex = [" + oldPosition.mediaItemIndex + "], "
                    + "oldPositionMs = [" + oldPosition.positionMs + "], "
                    + "newPositionIndex = [" + newPosition.mediaItemIndex + "], "
                    + "newPositionMs = [" + newPosition.positionMs + "], "
                    + "discontinuityReason = [" + discontinuityReason + "]"))
        }
        if (playQueue == null) {
            return
        }

        // Refresh the playback if there is a transition to the next video
        val newIndex: Int = newPosition.mediaItemIndex
        when (discontinuityReason) {
            com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION, com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE -> {
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (getRepeatMode() == com.google.android.exoplayer2.Player.REPEAT_MODE_ONE && newIndex == playQueue.getIndex()) {
                    registerStreamViewed()
                    break
                }
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onSeekProcessed() called")
                }
                if (isPrepared) {
                    saveStreamProgressState()
                }
                // Player index may be invalid when playback is blocked
                if (getCurrentState() != STATE_BLOCKED && newIndex != playQueue.getIndex()) {
                    saveStreamProgressStateCompleted() // current stream has ended
                    playQueue.setIndex(newIndex)
                }
            }

            com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK -> {
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onSeekProcessed() called")
                }
                if (isPrepared) {
                    saveStreamProgressState()
                }
                if (getCurrentState() != STATE_BLOCKED && newIndex != playQueue.getIndex()) {
                    saveStreamProgressStateCompleted()
                    playQueue.setIndex(newIndex)
                }
            }

            com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT, com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL -> if (getCurrentState() != STATE_BLOCKED && newIndex != playQueue.getIndex()) {
                saveStreamProgressStateCompleted()
                playQueue.setIndex(newIndex)
            }

            com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SKIP -> {}
        }
    }

    public override fun onRenderedFirstFrame() {
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onRenderedFirstFrame() }))
    }

    public override fun onCues(cueGroup: CueGroup) {
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onCues(cueGroup.cues) }))
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Errors
    ////////////////////////////////////////////////////////////////////////// */
    //region Errors
    /**
     * Process exceptions produced by [ExoPlayer][com.google.android.exoplayer2.ExoPlayer].
     *
     * There are multiple types of errors:
     *
     *  * [BEHIND_LIVE_WINDOW][PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW]:
     * If the playback on livestreams are lagged too far behind the current playable
     * window. Then we seek to the latest timestamp and restart the playback.
     * This error is **catchable**.
     *
     *  * From [BAD_IO][PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE] to
     * [UNSUPPORTED_FORMATS][PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED]:
     * If the stream source is validated by the extractor but not recognized by the player,
     * then we can try to recover playback by signalling an error on the [PlayQueue].
     *  * For [PLAYER_TIMEOUT][PlaybackException.ERROR_CODE_TIMEOUT],
     * [MEDIA_SOURCE_RESOLVER_TIMEOUT][PlaybackException.ERROR_CODE_IO_UNSPECIFIED] and
     * [NO_NETWORK][PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED]:
     * We can keep set the recovery record and keep to player at the current state until
     * it is ready to play by restarting the [MediaSourceManager].
     *  * On any ExoPlayer specific issue internal to its device interaction, such as
     * [DECODER_ERROR][PlaybackException.ERROR_CODE_DECODER_INIT_FAILED]:
     * We terminate the playback.
     *  * For any other unspecified issue internal: We set a recovery and try to restart
     * the playback.
     * For any error above that is **not** explicitly **catchable**, the player will
     * create a notification so users are aware.
     *
     *
     * @see com.google.android.exoplayer2.Player.Listener.onPlayerError
     */
    // Any error code not explicitly covered here are either unrelated to NewPipe use case
    // (e.g. DRM) or not recoverable (e.g. Decoder error). In both cases, the player should
    // shutdown.
    public override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer - onPlayerError() called with:", error)
        saveStreamProgressState()
        var isCatchableException: Boolean = false
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                isCatchableException = true
                simpleExoPlayer!!.seekToDefaultPosition()
                simpleExoPlayer!!.prepare()
                // Inform the user that we are reloading the stream by
                // switching to the buffering state
                onBuffering()
            }

            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->                 // Source errors, signal on playQueue and move on:
                if (!exoPlayerIsNull() && playQueue != null) {
                    playQueue!!.error()
                }

            PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.ERROR_CODE_UNSPECIFIED -> {
                // Reload playback on unexpected errors:
                setRecovery()
                reloadPlayQueueManager()
            }

            else ->                 // API, remote and renderer errors belong here:
                onPlaybackShutdown()
        }
        if (!isCatchableException) {
            createErrorNotification(error)
        }
        if (fragmentListener != null) {
            fragmentListener!!.onPlayerError(error, isCatchableException)
        }
    }

    private fun createErrorNotification(error: PlaybackException) {
        val errorInfo: ErrorInfo
        if (currentMetadata == null) {
            errorInfo = ErrorInfo(error, UserAction.PLAY_STREAM,
                    ("Player error[type=" + error.getErrorCodeName()
                            + "] occurred, currentMetadata is null"))
        } else {
            errorInfo = ErrorInfo(error, UserAction.PLAY_STREAM,
                    ("Player error[type=" + error.getErrorCodeName()
                            + "] occurred while playing " + currentMetadata.getStreamUrl()),
                    currentMetadata.getServiceId())
        }
        createNotification(context, errorInfo)
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback position and seek
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback position and seek
    // own playback listener (this is a getter)
    public override fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (exoPlayerIsNull() || isLive() || !isPlaying()) {
            return false
        }
        val currentPositionMillis: Long = simpleExoPlayer!!.getCurrentPosition()
        val currentDurationMillis: Long = simpleExoPlayer!!.getDuration()
        return currentDurationMillis - currentPositionMillis < timeToEndMillis
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge.
     *
     * @return whether the livestream is playing at or beyond the edge
     */
    fun isLiveEdge(): Boolean {
        if (exoPlayerIsNull() || !isLive()) {
            return false
        }
        val currentTimeline: Timeline = simpleExoPlayer!!.getCurrentTimeline()
        val currentWindowIndex: Int = simpleExoPlayer!!.getCurrentMediaItemIndex()
        if (currentTimeline.isEmpty() || (currentWindowIndex < 0
                        ) || (currentWindowIndex >= currentTimeline.getWindowCount())) {
            return false
        }
        val timelineWindow: Timeline.Window = Timeline.Window()
        currentTimeline.getWindow(currentWindowIndex, timelineWindow)
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer!!.getCurrentPosition()
    }

    // own playback listener
    public override fun onPlaybackSynchronize(item: PlayQueueItem, wasBlocked: Boolean) {
        if (DEBUG) {
            Log.d(TAG, ("Playback - onPlaybackSynchronize(was blocked: " + wasBlocked
                    + ") called with item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]"))
        }
        if (exoPlayerIsNull() || (playQueue == null) || (currentItem === item)) {
            return  // nothing to synchronize
        }
        val playQueueIndex: Int = playQueue!!.indexOf(item)
        val playlistIndex: Int = simpleExoPlayer!!.getCurrentMediaItemIndex()
        val playlistSize: Int = simpleExoPlayer!!.getCurrentTimeline().getWindowCount()
        val removeThumbnailBeforeSync: Boolean = (currentItem == null
                ) || (currentItem.getServiceId() != item.getServiceId()
                ) || !(currentItem.getUrl() == item.getUrl())
        currentItem = item
        if (playQueueIndex != playQueue.getIndex()) {
            // wrong window (this should be impossible, as this method is called with
            // `item=playQueue.getItem()`, so the index of that item must be equal to `getIndex()`)
            Log.e(TAG, ("Playback - Play Queue may be not in sync: item index=["
                    + playQueueIndex + "], " + "queue index=[" + playQueue.getIndex() + "]"))
        } else if ((playlistSize > 0 && playQueueIndex >= playlistSize) || playQueueIndex < 0) {
            // the queue and the player's timeline are not in sync, since the play queue index
            // points outside of the timeline
            Log.e(TAG, ("Playback - Trying to seek to invalid index=[" + playQueueIndex
                    + "] with playlist length=[" + playlistSize + "]"))
        } else if (wasBlocked || (playlistIndex != playQueueIndex) || !isPlaying()) {
            // either the player needs to be unblocked, or the play queue index has just been
            // changed and needs to be synchronized, or the player is not playing
            if (DEBUG) {
                Log.d(TAG, ("Playback - Rewinding to correct index=[" + playQueueIndex + "], "
                        + "from=[" + playlistIndex + "], size=[" + playlistSize + "]."))
            }
            if (removeThumbnailBeforeSync) {
                // unset the current (now outdated) thumbnail to ensure it is not used during sync
                onThumbnailLoaded(null)
            }

            // sync the player index with the queue index, and seek to the correct position
            if (item.getRecoveryPosition() != PlayQueueItem.Companion.RECOVERY_UNSET) {
                simpleExoPlayer!!.seekTo(playQueueIndex, item.getRecoveryPosition())
                playQueue!!.unsetRecovery(playQueueIndex)
            } else {
                simpleExoPlayer!!.seekToDefaultPosition(playQueueIndex)
            }
        }
    }

    fun seekTo(positionMillis: Long) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]")
        }
        if (!exoPlayerIsNull()) {
            // prevent invalid positions when fast-forwarding/-rewinding
            simpleExoPlayer!!.seekTo(MathUtils.clamp(positionMillis, 0,
                    simpleExoPlayer!!.getDuration()))
        }
    }

    private fun seekBy(offsetMillis: Long) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]")
        }
        seekTo(simpleExoPlayer!!.getCurrentPosition() + offsetMillis)
    }

    fun seekToDefault() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer!!.seekToDefaultPosition()
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Player actions (play, pause, previous, fast-forward, ...)
    ////////////////////////////////////////////////////////////////////////// */
    //region Player actions (play, pause, previous, fast-forward, ...)
    fun play() {
        if (DEBUG) {
            Log.d(TAG, "play() called")
        }
        if ((audioReactor == null) || (playQueue == null) || exoPlayerIsNull()) {
            return
        }
        if (!isMuted()) {
            audioReactor!!.requestAudioFocus()
        }
        if (currentState == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault()
            } else {
                playQueue.setIndex(0)
            }
        }
        simpleExoPlayer!!.play()
        saveStreamProgressState()
    }

    fun pause() {
        if (DEBUG) {
            Log.d(TAG, "pause() called")
        }
        if (audioReactor == null || exoPlayerIsNull()) {
            return
        }
        audioReactor!!.abandonAudioFocus()
        simpleExoPlayer!!.pause()
        saveStreamProgressState()
    }

    fun playPause() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPause() called")
        }
        if ((getPlayWhenReady() // When state is completed (replay button is shown) then (re)play and do not pause
                        && currentState != STATE_COMPLETED)) {
            pause()
        } else {
            play()
        }
    }

    fun playPrevious() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPrevious() called")
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return
        }

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track. Also restart the track if the current track
         * is the first in a queue.*/if ((simpleExoPlayer!!.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS
                        || playQueue.getIndex() == 0)) {
            seekToDefault()
            playQueue!!.offsetIndex(0)
        } else {
            saveStreamProgressState()
            playQueue!!.offsetIndex(-1)
        }
        triggerProgressUpdate()
    }

    fun playNext() {
        if (DEBUG) {
            Log.d(TAG, "onPlayNext() called")
        }
        if (playQueue == null) {
            return
        }
        saveStreamProgressState()
        playQueue!!.offsetIndex(+1)
        triggerProgressUpdate()
    }

    fun fastForward() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called")
        }
        seekBy(PlayerHelper.retrieveSeekDurationFromPreferences(this).toLong())
        triggerProgressUpdate()
    }

    fun fastRewind() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called")
        }
        seekBy(-PlayerHelper.retrieveSeekDurationFromPreferences(this).toLong())
        triggerProgressUpdate()
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // StreamInfo history: views and progress
    ////////////////////////////////////////////////////////////////////////// */
    //region StreamInfo history: views and progress
    private fun registerStreamViewed() {
        getCurrentStreamInfo().ifPresent(java.util.function.Consumer({ info: StreamInfo? ->
            databaseUpdateDisposable
                    .add(recordManager.onViewed(info).onErrorComplete().subscribe())
        }))
    }

    private fun saveStreamProgressState(progressMillis: Long) {
        getCurrentStreamInfo().ifPresent(java.util.function.Consumer<StreamInfo>({ info: StreamInfo ->
            if (!prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
                return@ifPresent
            }
            if (DEBUG) {
                Log.d(TAG, ("saveStreamProgressState() called with: progressMillis=" + progressMillis
                        + ", currentMetadata=[" + info.getName() + "]"))
            }
            databaseUpdateDisposable.add(recordManager.saveStreamState(info, progressMillis)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(io.reactivex.rxjava3.functions.Consumer({ e: Throwable ->
                        if (DEBUG) {
                            e.printStackTrace()
                        }
                    }))
                    .onErrorComplete()
                    .subscribe())
        }))
    }

    fun saveStreamProgressState() {
        if (exoPlayerIsNull() || (currentMetadata == null) || (playQueue == null
                        ) || (playQueue.getIndex() != simpleExoPlayer!!.getCurrentMediaItemIndex())) {
            // Make sure play queue and current window index are equal, to prevent saving state for
            // the wrong stream on discontinuity (e.g. when the stream just changed but the
            // playQueue index and currentMetadata still haven't updated)
            return
        }
        // Save current position. It will help to restore this position once a user
        // wants to play prev or next stream from the queue
        playQueue!!.setRecovery(playQueue.getIndex(), simpleExoPlayer!!.getContentPosition())
        saveStreamProgressState(simpleExoPlayer!!.getCurrentPosition())
    }

    fun saveStreamProgressStateCompleted() {
        // current stream has ended, so the progress is its duration (+1 to overcome rounding)
        getCurrentStreamInfo().ifPresent(java.util.function.Consumer({ info: StreamInfo? -> saveStreamProgressState((info!!.getDuration() + 1) * 1000) }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Metadata
    ////////////////////////////////////////////////////////////////////////// */
    //region Metadata
    private fun updateMetadataWith(info: StreamInfo) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.getName())
        }
        if (exoPlayerIsNull()) {
            return
        }
        maybeAutoQueueNextStream(info)
        loadCurrentThumbnail(info.getThumbnails())
        registerStreamViewed()
        notifyMetadataUpdateToListeners()
        notifyAudioTrackUpdateToListeners()
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onMetadataChanged(info) }))
    }

    fun getVideoUrl(): String {
        return if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata.getStreamUrl()
    }

    fun getVideoUrlAtCurrentTime(): String {
        val timeSeconds: Long = simpleExoPlayer!!.getCurrentPosition() / 1000
        var videoUrl: String = getVideoUrl()
        if (!isLive() && (timeSeconds >= 0) && (currentMetadata != null
                        ) && (currentMetadata.getServiceId() == ServiceList.YouTube.getServiceId())) {
            // Timestamp doesn't make sense in a live stream so drop it
            videoUrl += ("&t=" + timeSeconds)
        }
        return videoUrl
    }

    fun getVideoTitle(): String {
        return if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata.getTitle()
    }

    fun getUploaderName(): String {
        return if (currentMetadata == null) context.getString(R.string.unknown_content) else currentMetadata.getUploaderName()
    }

    fun getThumbnail(): Bitmap? {
        return currentThumbnail
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    ////////////////////////////////////////////////////////////////////////// */
    //region Play queue, segments and streams
    private fun maybeAutoQueueNextStream(info: StreamInfo) {
        if ((playQueue == null) || (playQueue.getIndex() != playQueue!!.size() - 1
                        ) || (getRepeatMode() != com.google.android.exoplayer2.Player.REPEAT_MODE_OFF
                        ) || !PlayerHelper.isAutoQueueEnabled(context)) {
            return
        }
        // auto queue when starting playback on the last item when not repeating
        val autoQueue: PlayQueue? = PlayerHelper.autoQueueOf(info,
                playQueue!!.getStreams())
        if (autoQueue != null) {
            playQueue!!.append(autoQueue.getStreams())
        }
    }

    fun selectQueueItem(item: PlayQueueItem?) {
        if (playQueue == null || exoPlayerIsNull()) {
            return
        }
        val index: Int = playQueue!!.indexOf((item)!!)
        if (index == -1) {
            return
        }
        if (playQueue.getIndex() == index && simpleExoPlayer!!.getCurrentMediaItemIndex() == index) {
            seekToDefault()
        } else {
            saveStreamProgressState()
        }
        playQueue.setIndex(index)
    }

    public override fun onPlayQueueEdited() {
        notifyPlaybackUpdateToListeners()
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onPlayQueueEdited() }))
    }

    public override fun sourceOf(item: PlayQueueItem?, info: StreamInfo): MediaSource? {
        if (audioPlayerSelected()) {
            return audioResolver.resolve(info)
        }
        if (isAudioOnly && videoResolver.getStreamSourceType().orElse(
                        SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
                == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY) {
            // If the current info has only video streams with audio and if the stream is played as
            // audio, we need to use the audio resolver, otherwise the video stream will be played
            // in background.
            return audioResolver.resolve(info)
        }

        // Even if the stream is played in background, we need to use the video resolver if the
        // info played is separated video-only and audio-only streams; otherwise, if the audio
        // resolver was called when the app was in background, the app will only stream audio when
        // the user come back to the app and will never fetch the video stream.
        // Note that the video is not fetched when the app is in background because the video
        // renderer is fully disabled (see useVideoSource method), except for HLS streams
        // (see https://github.com/google/ExoPlayer/issues/9282).
        return videoResolver.resolve(info)
    }

    fun disablePreloadingOfCurrentTrack() {
        loadController.disablePreloadingOfCurrentTrack()
    }

    fun getSelectedVideoStream(): Optional<VideoStream?> {
        return Optional.ofNullable(currentMetadata)
                .flatMap(Function<MediaItemTag, Optional<out MediaItemTag.Quality?>>({ obj: MediaItemTag -> obj.getMaybeQuality() }))
                .filter(Predicate({ quality: MediaItemTag.Quality? ->
                    val selectedStreamIndex: Int = quality.getSelectedVideoStreamIndex()
                    (selectedStreamIndex >= 0
                            && selectedStreamIndex < quality.getSortedVideoStreams().size)
                }))
                .map(Function({ quality: MediaItemTag.Quality? ->
                    quality.getSortedVideoStreams()
                            .get(quality.getSelectedVideoStreamIndex())
                }))
    }

    fun getSelectedAudioStream(): Optional<AudioStream?> {
        return Optional.ofNullable(currentMetadata)
                .flatMap(Function<MediaItemTag, Optional<out MediaItemTag.AudioTrack?>>({ obj: MediaItemTag -> obj.getMaybeAudioTrack() }))
                .map(Function<MediaItemTag.AudioTrack?, AudioStream?>({ getSelectedAudioStream() }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    ////////////////////////////////////////////////////////////////////////// */
    //region Captions (text tracks)
    fun getCaptionRendererIndex(): Int {
        if (exoPlayerIsNull()) {
            return RENDERER_UNAVAILABLE
        }
        for (t in 0 until simpleExoPlayer!!.getRendererCount()) {
            if (simpleExoPlayer!!.getRendererType(t) == C.TRACK_TYPE_TEXT) {
                return t
            }
        }
        return RENDERER_UNAVAILABLE
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    ////////////////////////////////////////////////////////////////////////// */
    //region Video size
    // exoplayer listener
    public override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (DEBUG) {
            Log.d(TAG, ("onVideoSizeChanged() called with: "
                    + "width / height = [" + videoSize.width + " / " + videoSize.height
                    + " = " + ((videoSize.width.toFloat()) / videoSize.height) + "], "
                    + "unappliedRotationDegrees = [" + videoSize.unappliedRotationDegrees + "], "
                    + "pixelWidthHeightRatio = [" + videoSize.pixelWidthHeightRatio + "]"))
        }
        UIs.call(java.util.function.Consumer({ playerUi: PlayerUi -> playerUi.onVideoSizeChanged(videoSize) }))
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Activity / fragment binding
    ////////////////////////////////////////////////////////////////////////// */
    //region Activity / fragment binding
    fun setFragmentListener(listener: PlayerServiceEventListener?) {
        fragmentListener = listener
        UIs.call(java.util.function.Consumer({ obj: PlayerUi -> obj.onFragmentListenerSet() }))
        notifyQueueUpdateToListeners()
        notifyMetadataUpdateToListeners()
        notifyPlaybackUpdateToListeners()
        triggerProgressUpdate()
    }

    fun removeFragmentListener(listener: PlayerServiceEventListener) {
        if (fragmentListener === listener) {
            fragmentListener = null
        }
    }

    fun setActivityListener(listener: PlayerEventListener?) {
        activityListener = listener
        // TODO why not queue update?
        notifyMetadataUpdateToListeners()
        notifyPlaybackUpdateToListeners()
        triggerProgressUpdate()
    }

    fun removeActivityListener(listener: PlayerEventListener) {
        if (activityListener === listener) {
            activityListener = null
        }
    }

    fun stopActivityBinding() {
        if (fragmentListener != null) {
            fragmentListener!!.onServiceStopped()
            fragmentListener = null
        }
        if (activityListener != null) {
            activityListener!!.onServiceStopped()
            activityListener = null
        }
    }

    private fun notifyQueueUpdateToListeners() {
        if (fragmentListener != null && playQueue != null) {
            fragmentListener!!.onQueueUpdate(playQueue)
        }
        if (activityListener != null && playQueue != null) {
            activityListener!!.onQueueUpdate(playQueue)
        }
    }

    private fun notifyMetadataUpdateToListeners() {
        getCurrentStreamInfo().ifPresent(java.util.function.Consumer({ info: StreamInfo? ->
            if (fragmentListener != null) {
                fragmentListener!!.onMetadataUpdate(info, playQueue)
            }
            if (activityListener != null) {
                activityListener!!.onMetadataUpdate(info, playQueue)
            }
        }))
    }

    private fun notifyPlaybackUpdateToListeners() {
        if ((fragmentListener != null) && !exoPlayerIsNull() && (playQueue != null)) {
            fragmentListener!!.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue!!.isShuffled(), simpleExoPlayer!!.getPlaybackParameters())
        }
        if ((activityListener != null) && !exoPlayerIsNull() && (playQueue != null)) {
            activityListener!!.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue!!.isShuffled(), getPlaybackParameters())
        }
    }

    private fun notifyProgressUpdateToListeners(currentProgress: Int,
                                                duration: Int,
                                                bufferPercent: Int) {
        if (fragmentListener != null) {
            fragmentListener!!.onProgressUpdate(currentProgress, duration, bufferPercent)
        }
        if (activityListener != null) {
            activityListener!!.onProgressUpdate(currentProgress, duration, bufferPercent)
        }
    }

    private fun notifyAudioTrackUpdateToListeners() {
        if (fragmentListener != null) {
            fragmentListener!!.onAudioTrackUpdate()
        }
        if (activityListener != null) {
            activityListener!!.onAudioTrackUpdate()
        }
    }

    fun useVideoSource(videoEnabled: Boolean) {
        if (playQueue == null || audioPlayerSelected()) {
            return
        }
        isAudioOnly = !videoEnabled
        getCurrentStreamInfo().ifPresentOrElse(java.util.function.Consumer({ info: StreamInfo ->
            // In case we don't know the source type, fall back to either video-with-audio, or
            // audio-only source type
            val sourceType: SourceType? = videoResolver.getStreamSourceType()
                    .orElse(SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
            if (playQueueManagerReloadingNeeded(sourceType, info, getVideoRendererIndex())) {
                reloadPlayQueueManager()
            }
            setRecovery()

            // Disable or enable video and subtitles renderers depending of the videoEnabled value
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !videoEnabled)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoEnabled))
        }), Runnable({

            /*
            The current metadata may be null sometimes (for e.g. when using an unstable connection
            in livestreams) so we will be not able to execute the block below

            Reload the play queue manager in this case, which is the behavior when we don't know the
            index of the video renderer or playQueueManagerReloadingNeeded returns true
            */reloadPlayQueueManager()
            setRecovery()
        }))
    }

    /**
     * Return whether the play queue manager needs to be reloaded when switching player type.
     *
     *
     *
     * The play queue manager needs to be reloaded if the video renderer index is not known and if
     * the content is not an audio content, but also if none of the following cases is met:
     *
     *
     *  * the content is an [audio stream][StreamType.AUDIO_STREAM], an
     * [audio live stream][StreamType.AUDIO_LIVE_STREAM], or a
     * [ended audio live stream][StreamType.POST_LIVE_AUDIO_STREAM];
     *  * the content is a [live stream][StreamType.LIVE_STREAM] and the source type is a
     * [live source][SourceType.LIVE_STREAM];
     *  * the content's source is [a video stream][SourceType.VIDEO_WITH_SEPARATED_AUDIO] or has no audio-only streams available **and** is a
     * [video stream][StreamType.VIDEO_STREAM], an
     * [ended live stream][StreamType.POST_LIVE_STREAM], or a
     * [live stream][StreamType.LIVE_STREAM].
     *
     *
     *
     *
     * @param sourceType         the [SourceType] of the stream
     * @param streamInfo         the [StreamInfo] of the stream
     * @param videoRendererIndex the video renderer index of the video source, if that's a video
     * source (or [.RENDERER_UNAVAILABLE])
     * @return whether the play queue manager needs to be reloaded
     */
    private fun playQueueManagerReloadingNeeded(sourceType: SourceType?,
                                                streamInfo: StreamInfo,
                                                videoRendererIndex: Int): Boolean {
        val streamType: StreamType = streamInfo.getStreamType()
        val isStreamTypeAudio: Boolean = StreamTypeUtil.isAudio(streamType)
        if (videoRendererIndex == RENDERER_UNAVAILABLE && !isStreamTypeAudio) {
            return true
        }

        // The content is an audio stream, an audio live stream, or a live stream with a live
        // source: it's not needed to reload the play queue manager because the stream source will
        // be the same
        if (isStreamTypeAudio || ((streamType == StreamType.LIVE_STREAM
                        && sourceType == SourceType.LIVE_STREAM))) {
            return false
        }

        // The content's source is a video with separated audio or a video with audio -> the video
        // and its fetch may be disabled
        // The content's source is a video with embedded audio and the content has no separated
        // audio stream available: it's probably not needed to reload the play queue manager
        // because the stream source will be probably the same as the current played
        if ((sourceType == SourceType.VIDEO_WITH_SEPARATED_AUDIO
                        || (sourceType == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
                        && Utils.isNullOrEmpty(streamInfo.getAudioStreams())))) {
            // It's not needed to reload the play queue manager only if the content's stream type
            // is a video stream, a live stream or an ended live stream
            return !StreamTypeUtil.isVideo(streamType)
        }

        // Other cases: the play queue manager reload is needed
        return true
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    ////////////////////////////////////////////////////////////////////////// */
    //region Getters
    fun getCurrentStreamInfo(): Optional<StreamInfo?> {
        return Optional.ofNullable(currentMetadata).flatMap(Function<MediaItemTag, Optional<out StreamInfo?>>({ obj: MediaItemTag -> obj.getMaybeStreamInfo() }))
    }

    fun getCurrentState(): Int {
        return currentState
    }

    fun exoPlayerIsNull(): Boolean {
        return simpleExoPlayer == null
    }

    fun getExoPlayer(): ExoPlayer? {
        return simpleExoPlayer
    }

    fun isStopped(): Boolean {
        return exoPlayerIsNull() || simpleExoPlayer!!.getPlaybackState() == ExoPlayer.STATE_IDLE
    }

    fun isPlaying(): Boolean {
        return !exoPlayerIsNull() && simpleExoPlayer!!.isPlaying()
    }

    fun getPlayWhenReady(): Boolean {
        return !exoPlayerIsNull() && simpleExoPlayer!!.getPlayWhenReady()
    }

    fun isLoading(): Boolean {
        return !exoPlayerIsNull() && simpleExoPlayer!!.isLoading()
    }

    private fun isLive(): Boolean {
        try {
            return !exoPlayerIsNull() && simpleExoPlayer!!.isCurrentMediaItemDynamic()
        } catch (e: IndexOutOfBoundsException) {
            // Why would this even happen =(... but lets log it anyway, better safe than sorry
            if (DEBUG) {
                Log.d(TAG, "player.isCurrentWindowDynamic() failed: ", e)
            }
            return false
        }
    }

    fun setPlaybackQuality(quality: String?) {
        saveStreamProgressState()
        setRecovery()
        videoResolver.setPlaybackQuality(quality)
        reloadPlayQueueManager()
    }

    fun setAudioTrack(audioTrackId: String?) {
        saveStreamProgressState()
        setRecovery()
        videoResolver.setAudioTrack(audioTrackId)
        audioResolver.setAudioTrack(audioTrackId)
        reloadPlayQueueManager()
    }

    fun getContext(): Context {
        return context
    }

    fun getPrefs(): SharedPreferences {
        return prefs
    }

    fun getPlayerType(): PlayerType {
        return playerType
    }

    fun audioPlayerSelected(): Boolean {
        return playerType == PlayerType.AUDIO
    }

    fun videoPlayerSelected(): Boolean {
        return playerType == PlayerType.MAIN
    }

    fun popupPlayerSelected(): Boolean {
        return playerType == PlayerType.POPUP
    }

    fun getPlayQueue(): PlayQueue? {
        return playQueue
    }

    fun getAudioReactor(): AudioReactor? {
        return audioReactor
    }

    fun getService(): PlayerService {
        return service
    }

    fun isAudioOnly(): Boolean {
        return isAudioOnly
    }

    fun getTrackSelector(): DefaultTrackSelector {
        return trackSelector
    }

    fun getCurrentMetadata(): MediaItemTag? {
        return currentMetadata
    }

    fun getCurrentItem(): PlayQueueItem? {
        return currentItem
    }

    fun getFragmentListener(): Optional<PlayerServiceEventListener?> {
        return Optional.ofNullable(fragmentListener)
    }

    /**
     * @return the user interfaces connected with the player
     */
    // keep the unusual method name
    fun UIs(): PlayerUiList {
        return UIs
    }

    /**
     * Get the video renderer index of the current playing stream.
     *
     *
     * This method returns the video renderer index of the current
     * [MappingTrackSelector.MappedTrackInfo] or [.RENDERER_UNAVAILABLE] if the current
     * [MappingTrackSelector.MappedTrackInfo] is null or if there is no video renderer index.
     *
     * @return the video renderer index or [.RENDERER_UNAVAILABLE] if it cannot be get
     */
    private fun getVideoRendererIndex(): Int {
        val mappedTrackInfo: MappedTrackInfo? = trackSelector
                .getCurrentMappedTrackInfo()
        if (mappedTrackInfo == null) {
            return RENDERER_UNAVAILABLE
        }

        // Check every renderer
        return IntStream.range(0, mappedTrackInfo.getRendererCount()) // Check the renderer is a video renderer and has at least one track
                .filter(IntPredicate({ i: Int ->
                    (!mappedTrackInfo.getTrackGroups(i).isEmpty()
                            && simpleExoPlayer!!.getRendererType(i) == C.TRACK_TYPE_VIDEO)
                })) // Return the first index found (there is at most one renderer per renderer type)
                .findFirst() // No video renderer index with at least one track found: return unavailable index
                .orElse(RENDERER_UNAVAILABLE)
    } //endregion

    companion object {
        val DEBUG: Boolean = MainActivity.Companion.DEBUG
        val TAG: String = Player::class.java.getSimpleName()

        /*//////////////////////////////////////////////////////////////////////////
    // States
    ////////////////////////////////////////////////////////////////////////// */
        val STATE_PREFLIGHT: Int = -1
        val STATE_BLOCKED: Int = 123
        val STATE_PLAYING: Int = 124
        val STATE_BUFFERING: Int = 125
        val STATE_PAUSED: Int = 126
        val STATE_PAUSED_SEEK: Int = 127
        val STATE_COMPLETED: Int = 128

        /*//////////////////////////////////////////////////////////////////////////
    // Intent
    ////////////////////////////////////////////////////////////////////////// */
        val REPEAT_MODE: String = "repeat_mode"
        val PLAYBACK_QUALITY: String = "playback_quality"
        val PLAY_QUEUE_KEY: String = "play_queue_key"
        val ENQUEUE: String = "enqueue"
        val ENQUEUE_NEXT: String = "enqueue_next"
        val RESUME_PLAYBACK: String = "resume_playback"
        val PLAY_WHEN_READY: String = "play_when_ready"
        val PLAYER_TYPE: String = "player_type"
        val IS_MUTED: String = "is_muted"

        /*//////////////////////////////////////////////////////////////////////////
    // Time constants
    ////////////////////////////////////////////////////////////////////////// */
        val PLAY_PREV_ACTIVATION_LIMIT_MILLIS: Int = 5000 // 5 seconds
        val PROGRESS_LOOP_INTERVAL_MILLIS: Int = 1000 // 1 second

        /*//////////////////////////////////////////////////////////////////////////
    // Other constants
    ////////////////////////////////////////////////////////////////////////// */
        val RENDERER_UNAVAILABLE: Int = -1
        private val PICASSO_PLAYER_THUMBNAIL_TAG: String = "PICASSO_PLAYER_THUMBNAIL_TAG"
    }
}
