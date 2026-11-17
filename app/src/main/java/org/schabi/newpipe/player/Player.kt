/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.LayoutInflater
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Optional
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.equalizer.EqualizerState
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.CustomRenderersFactory
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.SleepTimer
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.player.playback.PlaybackListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.ui.BackgroundPlayerUi
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.player.ui.PlayerUiTheme
import org.schabi.newpipe.player.ui.PopupPlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.player.video.VideoAdjustmentController
import org.schabi.newpipe.player.video.VideoAdjustmentPreferences
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor
import org.schabi.newpipe.util.ListHelper

/** Coordinates playback controllers and exposes the player API used by services and UIs. */
class Player(
    private val playerService: PlayerService,
    mediaSession: MediaLibrarySession,
    browserPlayer: Media3Player
) : PlaybackListener, Media3Player.Listener {
    val sphericalPlayback = org.schabi.newpipe.player.video.SphericalPlaybackState()

    companion object {
        @JvmField
        val DEBUG: Boolean = MainActivity.DEBUG

        @JvmField
        val TAG: String = Player::class.java.simpleName

        const val STATE_PREFLIGHT = -1
        const val STATE_BLOCKED = 123
        const val STATE_PLAYING = 124
        const val STATE_BUFFERING = 125
        const val STATE_PAUSED = 126
        const val STATE_PAUSED_SEEK = 127
        const val STATE_COMPLETED = 128

        const val PLAYBACK_QUALITY = "playback_quality"
        const val PLAY_QUEUE_KEY = "play_queue_key"
        const val RESUME_PLAYBACK = "resume_playback"
        const val PLAY_WHEN_READY = "play_when_ready"
        const val PLAYER_TYPE = "player_type"
        const val PLAYBACK_PRESENTATION_MODE = "playback_presentation_mode"
        const val PLAYER_INTENT_TYPE = "player_intent_type"
        const val PLAYER_INTENT_DATA = "player_intent_data"

        const val PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000
        const val PROGRESS_LOOP_INTERVAL_MILLIS = 1000
        const val RENDERER_UNAVAILABLE = -1
    }

    private val appContext: Context = playerService
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)

    internal val queueRecovery = org.schabi.newpipe.player.recovery.QueueRecoveryController(this)

    private var activePlayQueue: PlayQueue? = null
    private var activeItem: PlayQueueItem? = null
    val abRepeat = ABRepeat(preferences)
    val playbackInformation = org.schabi.newpipe.player.info.PlaybackInformation()
    private var media3Player: ExoPlayer? = null
    private var activeAudioReactor: AudioReactor? = null
    private var activePlayerType: PlayerType = PlayerType.MAIN

    private val popupPlayerReturnState = PopupPlayerReturnState()
    private val streamItemDisposable = CompositeDisposable()

    private val videoAdjustmentPreferences = VideoAdjustmentPreferences(preferences)
    val videoAdjustments = VideoAdjustmentController(
        videoAdjustmentPreferences.load(),
        videoAdjustmentPreferences::save,
        { restartForVideoAdjustments() },
        {
            android.widget.Toast.makeText(
                appContext,
                R.string.video_adjustments_failed,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    )

    private val audioController = PlayerAudioController(this)
    private val historyController = PlayerHistoryController(this)
    private val sponsorBlockController = SponsorBlockPlaybackController(this)
    internal val channelSkipController = ChannelSkipPlaybackController(this)
    private val playbackParametersController = PlaybackParametersController(this)
    private val sleepTimerController = SleepTimerPlaybackController(this)
    private val queueModeController = PlayerQueueModeController(this, sleepTimerController)
    private val eventDispatcher = PlayerEventDispatcher(this)
    private val progressController = PlayerProgressController(
        this,
        historyController,
        sponsorBlockController,
        eventDispatcher
    )
    private val stateController = PlayerStateController(
        this,
        historyController,
        sleepTimerController,
        sponsorBlockController,
        progressController,
        eventDispatcher
    )
    private val seekController = PlayerSeekController(this)
    private val transportController = PlayerTransportController(this, sleepTimerController)
    private val thumbnailController = PlayerThumbnailController(this)
    private val queueSynchronizer = PlayerQueueSynchronizer(
        this,
        historyController,
        playbackParametersController,
        thumbnailController
    )
    private val localMetadataController = PlayerLocalMetadataController(this)
    private val metadataController = PlayerMetadataController(
        this,
        appContext,
        historyController,
        playbackParametersController,
        sponsorBlockController,
        thumbnailController,
        localMetadataController
    )
    private val broadcastController = PlayerBroadcastController(this)

    private var activeTrackSelector: DefaultTrackSelector? = null
    private var activeLoadController: LoadController? = null
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext).build()
    private val dataSource = PlayerDataSource(appContext, bandwidthMeter)
    private val playerVisualizerAudioProcessor = VisualizerAudioProcessor()
    private val renderFactory: CustomRenderersFactory = CustomRenderersFactory(
        appContext,
        preferences.getBoolean(
            appContext.getString(R.string.always_use_exoplayer_set_output_surface_workaround_key),
            false
        ),
        playerVisualizerAudioProcessor
    ).apply {
        setEnableDecoderFallback(
            preferences.getBoolean(
                appContext.getString(R.string.use_exoplayer_decoder_fallback_key),
                false
            )
        )
    }

    val subtitleTiming: SubtitleTiming get() = renderFactory.subtitleTiming

    private val videoResolver = VideoPlaybackResolver(appContext, dataSource, qualityResolver())
    private val audioResolver = AudioPlaybackResolver(appContext, dataSource)
    val liveQualityController = LiveQualityController(this)
    private val captionController =
        PlayerCaptionController(this, appContext, preferences) { activeTrackSelector }
    private val streamController = PlayerStreamController(
        this,
        appContext,
        audioResolver,
        videoResolver,
        dataSource
    )
    private val presentationController = PlayerPresentationController(
        this,
        videoResolver,
        { activeTrackSelector },
        playerVisualizerAudioProcessor
    )
    private val intentController = PlayerIntentController(
        this,
        appContext,
        historyController,
        presentationController,
        popupPlayerReturnState,
        videoResolver,
        streamItemDisposable
    )
    private val errorController = PlayerErrorController(this, eventDispatcher, videoResolver)
    private val media3ListenerController = PlayerMedia3ListenerController(
        this,
        audioController,
        metadataController,
        playbackParametersController,
        sponsorBlockController,
        errorController,
        sleepTimerController
    )

    private val playerUis = PlayerUiList(
        MediaSessionPlayerUi(this, mediaSession, browserPlayer),
        NotificationPlayerUi(this)
    )

    private val lifecycleController = PlayerLifecycleController(
        this,
        appContext,
        playerService,
        renderFactory,
        audioController,
        broadcastController,
        errorController,
        historyController,
        thumbnailController,
        localMetadataController,
        sleepTimerController,
        progressController,
        playbackParametersController,
        streamItemDisposable
    )

    private fun qualityResolver() = object : VideoPlaybackResolver.QualityResolver {
        override fun getDefaultResolutionIndex(
            sortedVideos: MutableList<VideoStream>
        ): Int = if (videoPlayerSelected()) {
            ListHelper.getDefaultResolutionIndex(appContext, sortedVideos)
        } else {
            ListHelper.getPopupDefaultResolutionIndex(appContext, sortedVideos)
        }

        override fun getOverrideResolutionIndex(
            sortedVideos: MutableList<VideoStream>,
            playbackQuality: String
        ): Int = if (videoPlayerSelected()) {
            ListHelper.getResolutionIndex(appContext, sortedVideos, playbackQuality)
        } else {
            ListHelper.getPopupResolutionIndex(appContext, sortedVideos, playbackQuality)
        }

        override fun getAutoFallbackResolutionIndex(
            sortedVideos: MutableList<VideoStream>
        ): Int {
            return ListHelper.getAutoResolutionFallbackIndex(appContext, sortedVideos)
        }

        override fun isDefaultAutoQuality(): Boolean {
            if (!videoPlayerSelected()) {
                return false
            }
            val selected = preferences.getString(
                appContext.getString(R.string.default_resolution_key),
                appContext.getString(R.string.default_resolution_value)
            )
            return selected == appContext.getString(R.string.auto_resolution_key)
        }

        override fun isAutoQuality(playbackQuality: String): Boolean {
            return playbackQuality == appContext.getString(R.string.auto_resolution_key)
        }
    }

    fun handleIntent(intent: Intent) = intentController.handle(intent)

    fun handleIntentPost(oldPlayerType: PlayerType) = intentController.handlePost(oldPlayerType)

    fun initUIsForCurrentPlayerType() {
        val correctUiAlreadyPresent = when (activePlayerType) {
            PlayerType.MAIN -> playerUis.get(MainPlayerUi::class.java).isPresent
            PlayerType.AUDIO -> playerUis.get(BackgroundPlayerUi::class.java).isPresent
            PlayerType.POPUP -> playerUis.get(PopupPlayerUi::class.java).isPresent
        }
        if (correctUiAlreadyPresent) return

        val existingVideoUi = playerUis.get(VideoPlayerUi::class.java)
        val binding: PlayerBinding? = when {
            existingVideoUi.isPresent -> existingVideoUi.get().binding
            activePlayerType == PlayerType.AUDIO -> null
            else -> PlayerBinding.inflate(LayoutInflater.from(PlayerUiTheme.createContext(appContext)))
        }

        when (activePlayerType) {
            PlayerType.MAIN -> {
                playerUis.destroyAll(PopupPlayerUi::class.java)
                playerUis.destroyAll(BackgroundPlayerUi::class.java)
                playerUis.addAndPrepare(MainPlayerUi(this, requireNotNull(binding)))
            }

            PlayerType.POPUP -> {
                playerUis.destroyAll(MainPlayerUi::class.java)
                playerUis.destroyAll(BackgroundPlayerUi::class.java)
                playerUis.addAndPrepare(PopupPlayerUi(this, requireNotNull(binding)))
            }

            PlayerType.AUDIO -> {
                playerUis.destroyAll(VideoPlayerUi::class.java)
                playerUis.addAndPrepare(BackgroundPlayerUi(this))
            }
        }
    }

    fun initPlayback(queue: PlayQueue, playOnReady: Boolean) {
        queueRecovery.checkpoint(true)
        lifecycleController.initPlayback(queue, playOnReady)
    }

    fun setPlayQueueForLifecycle(queue: PlayQueue) {
        activePlayQueue = queue
        queueRecovery.bind(queue)
    }

    fun setExoPlayerForLifecycle(exoPlayer: ExoPlayer) {
        media3Player = exoPlayer
    }

    fun clearExoPlayerForLifecycle() {
        media3Player = null
    }

    fun setTrackSelectorForLifecycle(trackSelector: DefaultTrackSelector) {
        activeTrackSelector = trackSelector
    }

    fun getTrackSelectorForLifecycle(): DefaultTrackSelector? = activeTrackSelector

    fun getBandwidthMeterForLifecycle(): DefaultBandwidthMeter = bandwidthMeter

    fun clearTrackSelectorForLifecycle(trackSelector: DefaultTrackSelector) {
        if (activeTrackSelector === trackSelector) {
            activeTrackSelector = null
        }
    }

    fun setLoadControllerForLifecycle(loadController: LoadController) {
        activeLoadController = loadController
    }

    fun getLoadControllerForLifecycle(): LoadController? = activeLoadController

    fun clearLoadControllerForLifecycle(loadController: LoadController) {
        if (activeLoadController === loadController) {
            activeLoadController = null
        }
    }

    fun setAudioReactorForLifecycle(reactor: AudioReactor) {
        activeAudioReactor = reactor
    }

    fun destroy() {
        queueRecovery.close()
        lifecycleController.destroy()
    }

    fun setRecovery() = lifecycleController.setRecovery()

    fun reloadPlayQueueManager() = lifecycleController.reloadPlayQueueManager()

    override fun onPlaybackShutdown() = lifecycleController.shutdown()

    fun smoothStopForImmediateReusing() = lifecycleController.smoothStopForImmediateReusing()

    val playbackSpeed: Float
        get() = playbackParametersController.speed

    fun setPlaybackSpeed(speed: Float) = playbackParametersController.setSpeed(speed)

    fun setPlaybackSpeedTemporarily(speed: Float) = playbackParametersController.setSpeedTemporarily(speed)

    val playbackPitch: Float
        get() = playbackParametersController.pitch

    val playbackSkipSilence: Boolean
        get() = playbackParametersController.skipSilence

    val playbackSkipSilenceAvailable: Boolean
        get() = playbackParametersController.skipSilenceAvailable

    val playbackParameters: PlaybackParameters
        get() = playbackParametersController.parameters

    fun setPlaybackParameters(speed: Float, pitch: Float, skipSilence: Boolean) = playbackParametersController.setParameters(speed, pitch, skipSilence)

    fun startProgressLoop() = progressController.start()

    fun stopProgressLoop() = progressController.stop()

    val isProgressLoopRunning: Boolean
        get() = progressController.isRunning()

    fun triggerProgressUpdate() = progressController.trigger()

    val isPreparedForProgressUpdates: Boolean
        get() = stateController.isPrepared

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        stateController.onPlayWhenReadyChanged(playWhenReady, reason)
        errorController.onPlayWhenReadyChanged(playWhenReady)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        stateController.onPlaybackStateChanged(playbackState)
        errorController.onPlaybackStateChanged(playbackState)
    }

    override fun onIsLoadingChanged(isLoading: Boolean) = stateController.onIsLoadingChanged(isLoading)

    override fun onPlaybackBlock() = stateController.block()

    override fun onPlaybackUnblock(mediaSource: MediaSource) = stateController.unblock(mediaSource)

    fun changeState(state: Int) = stateController.changeState(state)

    fun onBuffering() = stateController.onBuffering()

    val repeatMode: Int
        get() = queueModeController.getRepeatMode()

    fun cycleNextRepeatMode() = queueModeController.cycleNextRepeatMode()

    override fun onRepeatModeChanged(repeatMode: Int) = queueModeController.onRepeatModeChanged(repeatMode)

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = queueModeController.onShuffleModeEnabledChanged(shuffleModeEnabled)

    fun toggleShuffleModeEnabled() = queueModeController.toggleShuffleModeEnabled()

    fun toggleMute() = audioController.toggleMute()

    val isMuted: Boolean
        get() = audioController.isMuted

    val equalizerState: EqualizerState
        get() = audioController.equalizerState

    val isEqualizerAvailable: Boolean
        get() = audioController.isEqualizerAvailable

    val isEqualizerOperational: Boolean
        get() = audioController.isEqualizerOperational

    fun previewEqualizerState(state: EqualizerState) = audioController.previewEqualizerState(state)

    fun updateEqualizerState(state: EqualizerState) = audioController.updateEqualizerState(state)

    var volumeBoostGainMb: Int
        get() = audioController.volumeBoostGainMb
        set(value) = audioController.updateVolumeBoostGainMb(value)

    val isVolumeBoostAvailable: Boolean
        get() = audioController.isVolumeBoostAvailable

    val isVolumeBoostOperational: Boolean
        get() = audioController.isVolumeBoostOperational

    private fun restartForVideoAdjustments() = lifecycleController.restartForVideoAdjustments()

    internal fun enableAudioDecoderFallback(item: PlayQueueItem) = streamController.enableAudioDecoderFallback(item)

    internal fun clearAudioDecoderFallback() = streamController.clearAudioDecoderFallback()

    fun restartForAudioTrackRecovery() = lifecycleController.restartForAudioTrackRecovery()

    fun restartForSurfaceRecovery() {
        renderFactory.enableSurfaceWorkaround()
        lifecycleController.restartForSurfaceRecovery()
    }

    fun updateAudioTunneling() {
        val trackSelector = activeTrackSelector ?: return
        val tunnelingEnabled = !preferences.getBoolean(
            appContext.getString(R.string.disable_media_tunneling_key),
            false
        ) && !audioController.volume.isEnabled && !audioController.equalizerState.isEnabled &&
            !audioController.isVolumeBoostEnabled &&
            !playbackPresentationMode.allowsVisualizer() &&
            !videoAdjustments.effectsPipelinePrepared
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(tunnelingEnabled)
            .build()
    }

    val isIndependentPlayerVolumeEnabled: Boolean
        get() = audioController.volume.isEnabled

    var independentPlayerVolume: Float
        get() = audioController.volume.level
        set(value) {
            audioController.volume.level = value
        }

    fun applyPlayerVolume() {
        val exoPlayer = media3Player ?: return
        val equalizerHeadroom = audioController.equalizerHeadroomMultiplier
        exoPlayer.volume = audioController.volume.effectiveVolume(
            audioController.isMuted,
            sleepTimerController.volumeMultiplier,
            equalizerHeadroom
        )
    }

    fun startSleepTimer(durationMillis: Long, fadeOut: Boolean) = sleepTimerController.startDuration(durationMillis, fadeOut)

    fun startSleepTimerAtEndOfCurrent(fadeOut: Boolean): Boolean = sleepTimerController.startAtEndOfCurrent(fadeOut)

    fun startSleepTimerAtEndOfQueue(fadeOut: Boolean): Boolean = sleepTimerController.startAtEndOfQueue(fadeOut)

    fun cancelSleepTimer() = sleepTimerController.cancel()

    val sleepTimerRemainingMillis: Long
        get() = sleepTimerController.remainingMillis()

    val isSleepTimerActive: Boolean
        get() = sleepTimerController.isActive

    val sleepTimerMode: SleepTimer.Mode
        get() = sleepTimerController.mode

    val isSleepTimerFadeOutEnabled: Boolean
        get() = sleepTimerController.isFadeOutEnabled

    override fun onAudioSessionIdChanged(audioSessionId: Int) = media3ListenerController.onAudioSessionIdChanged(audioSessionId)

    override fun onMetadata(metadata: androidx.media3.common.Metadata) = historyController.onRadioMetadata(metadata)

    override fun onEvents(player: Media3Player, events: Media3Player.Events) {
        media3ListenerController.onEvents(player)
        historyController.onPlaybackEvents()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = media3ListenerController.onTimelineChanged(timeline, reason)

    override fun onTracksChanged(tracks: Tracks) = media3ListenerController.onTracksChanged(tracks)

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = media3ListenerController.onPlaybackParametersChanged(playbackParameters)

    override fun onPositionDiscontinuity(
        oldPosition: PositionInfo,
        newPosition: PositionInfo,
        reason: Int
    ) = media3ListenerController.onPositionDiscontinuity(oldPosition, newPosition, reason)

    override fun onRenderedFirstFrame() = media3ListenerController.onRenderedFirstFrame()

    override fun onCues(cueGroup: CueGroup) = media3ListenerController.onCues(cueGroup)

    override fun onPlayerError(error: PlaybackException) = errorController.onPlayerError(error)

    override fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean = seekController.isApproachingPlaybackEdge(timeToEndMillis)

    val isLiveEdge: Boolean
        get() = seekController.isLiveEdge()

    override fun onPlaybackSynchronize(item: PlayQueueItem, wasBlocked: Boolean) = queueSynchronizer.synchronize(item, wasBlocked)

    fun seekTo(positionMillis: Long) = seekController.seekTo(positionMillis)

    fun isNormalLive(): Boolean = isLive && dataSource.isNormalLive(videoUrl)

    fun setNormalLive(normal: Boolean) {
        if (!isLive) return
        val queue = playQueue ?: return
        dataSource.setNormalLive(videoUrl, normal)
        videoResolver.clearLiveHlsFallback()
        // DASH and HLS have different window origins. Start the new source at its live edge.
        queue.setRecovery(queue.index, PlayQueueItem.RECOVERY_UNSET)
        reloadPlayQueueManager()
    }

    fun seekToDefault() = seekController.seekToDefault()

    fun play() = transportController.play()

    fun pause() = transportController.pause()

    fun playPause() = transportController.playPause()

    fun playPrevious() = transportController.playPrevious()

    fun playNext() = transportController.playNext()

    fun fastForward() = seekController.fastForward()

    fun fastRewind() = seekController.fastRewind()

    fun registerStreamViewed() = historyController.registerViewed()

    private fun saveStreamProgressState(progressMillis: Long) = historyController.saveProgress(progressMillis)

    fun saveStreamProgressState() {
        queueRecovery.checkpoint(true)
        val exoPlayer = media3Player ?: return
        val queue = activePlayQueue ?: return
        if (currentMetadata == null || queue.index != exoPlayer.currentMediaItemIndex) return

        queue.setRecovery(queue.index, exoPlayer.contentPosition)
        saveStreamProgressState(exoPlayer.currentPosition)
    }

    fun saveStreamProgressStateCompleted() {
        val item = activeItem
        if (item != null && item.isLocalMedia) {
            saveStreamProgressState((item.duration + 1) * 1000)
        } else {
            currentStreamInfo.ifPresent { info ->
                saveStreamProgressState((info.duration + 1) * 1000)
            }
        }
    }

    val videoUrl: String
        get() = metadataController.videoUrl()

    val videoUrlAtCurrentTime: String
        get() = metadataController.videoUrlAtCurrentTime()

    val videoTitle: String
        get() = metadataController.videoTitle()

    val uploaderName: String
        get() = metadataController.uploaderName()

    val thumbnail: Bitmap?
        get() = metadataController.thumbnail()

    fun selectQueueItem(item: PlayQueueItem) {
        val queue = activePlayQueue ?: return
        val exoPlayer = media3Player ?: return
        val index = queue.indexOf(item)
        if (index == -1) return

        if (queue.index == index && exoPlayer.currentMediaItemIndex == index) {
            seekToDefault()
        } else {
            saveStreamProgressState()
        }
        queue.index = index
        sleepTimerController.onQueueItemSelected(item)
    }

    override fun onPlayQueueEdited() {
        sleepTimerController.onQueueEdited()
        notifyPlaybackUpdateToListeners()
        notifySleepTimerUpdateToListeners()
        playerUis.call(PlayerUi::onPlayQueueEdited)
    }

    override fun sourceOf(item: PlayQueueItem, info: StreamInfo): MediaSource? = streamController.sourceOf(info)

    override fun sourceOfLocal(item: PlayQueueItem): MediaSource? = streamController.sourceOfLocal(item)

    override fun sourceOfDownloaded(item: PlayQueueItem): MediaSource? = streamController.sourceOfDownloaded(item)

    fun disablePreloadingOfCurrentTrack() = streamController.disablePreloadingOfCurrentTrack()

    val selectedVideoStream: Optional<VideoStream>
        get() = metadataController.selectedVideoStream()

    val isAutoQualitySelected: Boolean
        get() = videoResolver.isAutoQualitySelected

    val isAdaptiveQualityActive: Boolean
        get() = videoResolver.isAdaptiveQualityActive

    val selectedAudioStream: Optional<AudioStream>
        get() = metadataController.selectedAudioStream()

    val captionRendererIndex: Int
        get() = captionController.rendererIndex()

    val captionPreference: String?
        get() = captionController.preference()

    fun setCaptionPreference(language: String?) = captionController.setPreference(language)

    override fun onVideoSizeChanged(videoSize: VideoSize) = media3ListenerController.onVideoSizeChanged(videoSize)

    fun setFragmentListener(listener: PlayerServiceEventListener) = eventDispatcher.setFragmentListener(listener)

    fun removeFragmentListener(listener: PlayerServiceEventListener) = eventDispatcher.removeFragmentListener(listener)

    fun setActivityListener(listener: PlayerEventListener) = eventDispatcher.setActivityListener(listener)

    fun removeActivityListener(listener: PlayerEventListener) = eventDispatcher.removeActivityListener(listener)

    fun stopActivityBinding() = eventDispatcher.stopBindings()

    fun notifyQueueUpdateToListeners() = eventDispatcher.notifyQueueUpdate()

    fun notifyMetadataUpdateToListeners() = eventDispatcher.notifyMetadataUpdate()

    fun notifyPlaybackUpdateToListeners() = eventDispatcher.notifyPlaybackUpdate()

    fun notifyAudioTrackUpdateToListeners() = eventDispatcher.notifyAudioTrackUpdate()

    fun notifySleepTimerUpdateToListeners() = eventDispatcher.notifySleepTimerUpdate()

    fun useVideoAndSubtitles(videoAndSubtitlesEnabled: Boolean) = presentationController.useVideoAndSubtitles(videoAndSubtitlesEnabled)

    fun useVideoAndSubtitlesForTemporaryBackground() = presentationController.useVideoAndSubtitles(false, temporaryBackground = true)

    fun setPlaybackPresentationMode(newMode: PlaybackPresentationMode) = presentationController.setMode(newMode)

    val currentStreamInfo: Optional<StreamInfo>
        get() = metadataController.currentStreamInfo()

    val currentState: Int
        get() = stateController.currentState

    fun onChannelSkipSettingsChanged(info: StreamInfo) {
        channelSkipController.settingsChanged(info)
    }

    fun exoPlayerIsNull(): Boolean = media3Player == null

    @get:JvmName("requireExoPlayer")
    val exoPlayer: ExoPlayer
        get() = checkNotNull(media3Player)

    fun getExoPlayer(): ExoPlayer? = media3Player

    val isStopped: Boolean
        get() = media3Player?.playbackState == null ||
            media3Player?.playbackState == ExoPlayer.STATE_IDLE

    val isPlaying: Boolean
        get() = media3Player?.isPlaying == true

    val playWhenReady: Boolean
        get() = media3Player?.playWhenReady == true

    val isLoading: Boolean
        get() = media3Player?.isLoading == true

    val isLive: Boolean
        get() = seekController.isLive()

    fun isLiveQualityPlayback(): Boolean = liveQualityController.isLivePlayback

    fun isAutoLiveQualitySelected(): Boolean = liveQualityController.isAutoSelected

    fun hasSelectableLiveQualities(): Boolean = liveQualityController.hasSelectableOptions()

    fun getLiveQualityOptions(): List<LiveQualityController.Option> = liveQualityController.options

    fun getCurrentLiveQualityLabel(): String = liveQualityController.currentLabel

    fun selectLiveQuality(index: Int): String? = liveQualityController.select(index)

    fun setPlaybackQuality(quality: String?) = streamController.setPlaybackQuality(quality)

    fun setAudioTrack(audioTrackId: String?) = streamController.setAudioTrack(audioTrackId)

    val context: Context
        get() = appContext

    val prefs: SharedPreferences
        get() = preferences

    val playerType: PlayerType
        get() = activePlayerType

    fun setPlayerTypeForIntent(newPlayerType: PlayerType) {
        activePlayerType = newPlayerType
    }

    fun rememberMainPlayerFullscreenBeforePopup(fullscreen: Boolean) = popupPlayerReturnState.remember(fullscreen)

    fun consumeMainPlayerFullscreenBeforePopup(fallback: Boolean): Boolean = popupPlayerReturnState.consume(fallback)

    fun audioPlayerSelected(): Boolean = activePlayerType == PlayerType.AUDIO

    fun videoPlayerSelected(): Boolean = activePlayerType == PlayerType.MAIN

    fun popupPlayerSelected(): Boolean = activePlayerType == PlayerType.POPUP

    val playQueue: PlayQueue?
        get() = activePlayQueue

    val audioReactor: AudioReactor?
        get() = activeAudioReactor

    val service: PlayerService
        get() = playerService

    val isAudioOnly: Boolean
        get() = presentationController.isAudioOnly

    val playbackPresentationMode: PlaybackPresentationMode
        get() = presentationController.mode

    val visualizerAudioProcessor: VisualizerAudioProcessor
        get() = playerVisualizerAudioProcessor

    val trackSelector: DefaultTrackSelector
        get() = checkNotNull(activeTrackSelector) {
            "Track selector is unavailable without an active playback engine"
        }

    val loadController: LoadController
        get() = checkNotNull(activeLoadController) {
            "Load controller is unavailable without an active playback engine"
        }

    val currentMetadata: MediaItemTag?
        get() = metadataController.currentMetadata

    val currentItem: PlayQueueItem?
        get() = activeItem

    fun setCurrentItemForPlaybackSynchronization(item: PlayQueueItem) {
        activeItem = item
    }

    fun clearCurrentPlaybackForBlock() {
        activeItem = null
        metadataController.clear()
    }

    val fragmentListener: Optional<PlayerServiceEventListener>
        get() = eventDispatcher.getFragmentListener()

    @Suppress("FunctionName")
    fun UIs(): PlayerUiList = playerUis

    val isScreenOn: Boolean
        get() = broadcastController.isScreenOn
}
