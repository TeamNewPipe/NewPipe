@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.media3.common.*
import androidx.media3.common.Player.Listener

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.*
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.LoadController
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.player.notification.NotificationPlayerUi
import org.schabi.newpipe.player.playback.MediaSourceManager
import org.schabi.newpipe.player.playback.PlaybackListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.util.ListHelper
import java.util.*

class Player(
    private val service: PlayerService
) : PlaybackListener, Listener {

    companion object {
        private val TAG = Player::class.java.simpleName
        const val STATE_IDLE = 0
        const val STATE_PREFLIGHT = 1
        const val STATE_BLOCKED = 2
        const val STATE_BUFFERING = 3
        const val STATE_PLAYING = 4
        const val STATE_PAUSED = 5
        const val STATE_COMPLETED = 6
        const val RESUME_PLAYBACK = "resume_playback"

        const val PLAY_QUEUE_KEY = "play_queue_key"
        const val PLAYER_TYPE = "player_type"
        const val PLAYER_INTENT_TYPE = "player_intent_type"
        const val PLAYER_INTENT_DATA = "player_intent_data"
    }

    val context: Context = service
    var playerType = PlayerType.MAIN
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    var exoPlayer: ExoPlayer? = null
        private set
        
    val trackSelector: DefaultTrackSelector
    private val videoResolver: VideoPlaybackResolver
    private val audioResolver: AudioPlaybackResolver
    private var mediaSourceManager: MediaSourceManager? = null
    private var audioReactor: AudioReactor? = null
    private val historyRecordManager = HistoryRecordManager(context)
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onBroadcastReceived(intent)
        }
    }
    
    private val uis = PlayerUiList()
    private var uiListener: PlayerServiceEventListener? = null
    
    var currentMetadata: StreamInfo? = null
        private set
    var playQueue: PlayQueue? = null
        private set

    val videoTitle: String? get() = currentMetadata?.name
    val uploaderName: String? get() = currentMetadata?.uploaderName
    val currentStreamInfo: Optional<StreamInfo> get() = Optional.ofNullable(currentMetadata)
    
    val playbackSpeed: Float get() = exoPlayer?.playbackParameters?.speed ?: 1f
    val playbackPitch: Float get() = exoPlayer?.playbackParameters?.pitch ?: 1f

    var currentState = STATE_IDLE
        private set

    val mediaSessionToken: MediaSessionCompat.Token?
        get() {
            var token: MediaSessionCompat.Token? = null
            uis.call { if (it is MediaSessionPlayerUi) token = it.sessionToken }
            return token
        }

    init {
        trackSelector = DefaultTrackSelector(context)
        val dataSource = PlayerDataSource(context, DefaultBandwidthMeter.Builder(context).build())
        
        videoResolver = VideoPlaybackResolver(context, dataSource, object : VideoPlaybackResolver.QualityResolver {
            override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>) = ListHelper.getDefaultResolutionIndex(context, sortedVideos)
            override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>, playbackQuality: String) = ListHelper.getResolutionIndex(context, sortedVideos, playbackQuality)
        })
        audioResolver = AudioPlaybackResolver(context, dataSource)

        uis.add(MediaSessionPlayerUi(this))
        uis.add(NotificationPlayerUi(this))
        
        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(LoadController())
            .build().apply {
                addListener(this@Player)
            }
        
        audioReactor = AudioReactor(context, exoPlayer!!)
        
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        ContextCompat.registerReceiver(context, broadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun setup(queue: PlayQueue, playWhenReady: Boolean) {
        this.playQueue = queue
        mediaSourceManager?.dispose()
        mediaSourceManager = MediaSourceManager(this, queue)
        exoPlayer?.playWhenReady = playWhenReady
        uis.call { it.initPlayback() }
    }

    fun setUiListener(listener: PlayerServiceEventListener?) {
        uiListener = listener
    }

    fun removeUiListener(listener: PlayerServiceEventListener?) {
        if (uiListener === listener) {
            uiListener = null
        }
    }

    fun play() { exoPlayer?.play() }
    fun pause() { exoPlayer?.pause() }
    fun stop() { exoPlayer?.stop() }

    fun destroy() {
        playerScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        mediaSourceManager?.dispose()
        audioReactor?.dispose()
        context.unregisterReceiver(broadcastReceiver)
        uis.call { it.destroy() }
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    fun onPlaybackUpdate() {
        uis.call { it.onUpdateProgress(exoPlayer?.currentPosition?.toInt() ?: 0, exoPlayer?.duration?.toInt() ?: 0, exoPlayer?.bufferedPercentage ?: 0) }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Player error", error)
    }

    private fun onBroadcastReceived(intent: Intent) {
        when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pause()
        }
        uis.call { it.onBroadcastReceived(intent) }
    }

    override fun onPlaybackBlock() { currentState = STATE_BLOCKED }
    override fun onPlaybackUnblock(mediaSource: MediaSource) {
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
    }
    override fun onPlaybackSynchronize(item: PlayQueueItem, wasBlocked: Boolean) {}
    override fun onPlaybackShutdown() { service.destroyPlayerAndStopService() }
    override fun onPlayQueueEdited() { uis.call { it.onPlayQueueEdited() } }
    override fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean = false

    override fun sourceOf(item: PlayQueueItem, info: StreamInfo): MediaSource? {
        return if (playerType == PlayerType.AUDIO) {
            audioResolver.resolve(info)
        } else {
            videoResolver.resolve(info)
        }
    }

    fun onPrepare() { exoPlayer?.prepare() }
    
    fun startNotification() {
        uis.call { if (it is NotificationPlayerUi) it.createNotificationAndStartForeground() }
    }

    fun handleIntent(intent: Intent?) {
        // Handle incoming player intents
    }

    fun handleIntentPost(oldPlayerType: PlayerType) {
        uis.call { it.setupAfterIntent() }
    }

    fun handleMediaButtonIntent(intent: Intent?) {
        if (intent == null) return
        uis.call { if (it is MediaSessionPlayerUi) it.handleMediaButtonIntent(intent) }
    }

    fun exoPlayerIsNull(): Boolean = exoPlayer == null
    fun smoothStopForImmediateReusing() { exoPlayer?.stop() }

    fun videoPlayerSelected(): Boolean = playerType == PlayerType.MAIN
    fun audioPlayerSelected(): Boolean = playerType == PlayerType.AUDIO
    fun popupPlayerSelected(): Boolean = playerType == PlayerType.POPUP

    fun playPrevious() { exoPlayer?.seekToPrevious() }
    fun playNext() { exoPlayer?.seekToNext() }

    fun selectQueueItem(index: Int) {
        exoPlayer?.seekTo(index, 0)
    }

    fun getThumbnail(): Bitmap? = null
    
    val repeatMode: Int get() = exoPlayer?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF

    fun getService(): PlayerService = service

    fun setPlaybackQuality(quality: String?) {
        videoResolver.playbackQuality = quality
    }

    fun setAudioTrack(track: String?) {
        videoResolver.audioTrack = track
        audioResolver.audioTrack = track
    }
}
