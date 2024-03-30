package org.schabi.newpipe.player.mediasession

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.MediaButtonEventHandler
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.MediaMetadataProvider
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.notification.NotificationActionData
import org.schabi.newpipe.player.notification.NotificationConstants
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.StreamTypeUtil
import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntFunction
import java.util.function.IntUnaryOperator
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.IntStream

class MediaSessionPlayerUi(player: Player) : PlayerUi(player), OnSharedPreferenceChangeListener {
    private var mediaSession: MediaSessionCompat? = null
    private var sessionConnector: MediaSessionConnector? = null
    private val ignoreHardwareMediaButtonsKey: String
    private var shouldIgnoreHardwareMediaButtons: Boolean = false

    // used to check whether any notification action changed, before sending costly updates
    private var prevNotificationActions: List<NotificationActionData?> = listOf<NotificationActionData>()

    init {
        ignoreHardwareMediaButtonsKey = context.getString(R.string.ignore_hardware_media_buttons_key)
    }

    public override fun initPlayer() {
        super.initPlayer()
        destroyPlayer() // release previously used resources
        mediaSession = MediaSessionCompat(context, TAG)
        mediaSession!!.setActive(true)
        sessionConnector = MediaSessionConnector(mediaSession!!)
        sessionConnector!!.setQueueNavigator(PlayQueueNavigator(mediaSession!!, player))
        sessionConnector!!.setPlayer(getForwardingPlayer())

        // It seems like events from the Media Control UI in the notification area don't go through
        // this function, so it's safe to just ignore all events in case we want to ignore the
        // hardware media buttons. Returning true stops all further event processing of the system.
        sessionConnector!!.setMediaButtonEventHandler(MediaButtonEventHandler({ p: com.google.android.exoplayer2.Player?, i: Intent? -> shouldIgnoreHardwareMediaButtons }))

        // listen to changes to ignore_hardware_media_buttons_key
        updateShouldIgnoreHardwareMediaButtons(player.getPrefs())
        player.getPrefs().registerOnSharedPreferenceChangeListener(this)
        sessionConnector!!.setMetadataDeduplicationEnabled(true)
        sessionConnector!!.setMediaMetadataProvider(MediaMetadataProvider({ exoPlayer: com.google.android.exoplayer2.Player? -> buildMediaMetadata() }))

        // force updating media session actions by resetting the previous ones
        prevNotificationActions = listOf<NotificationActionData>()
        updateMediaSessionActions()
    }

    public override fun destroyPlayer() {
        super.destroyPlayer()
        player.getPrefs().unregisterOnSharedPreferenceChangeListener(this)
        if (sessionConnector != null) {
            sessionConnector!!.setMediaButtonEventHandler(null)
            sessionConnector!!.setPlayer(null)
            sessionConnector!!.setQueueNavigator(null)
            sessionConnector = null
        }
        if (mediaSession != null) {
            mediaSession!!.setActive(false)
            mediaSession!!.release()
            mediaSession = null
        }
        prevNotificationActions = listOf<NotificationActionData>()
    }

    public override fun onThumbnailLoaded(bitmap: Bitmap?) {
        super.onThumbnailLoaded(bitmap)
        if (sessionConnector != null) {
            // the thumbnail is now loaded: invalidate the metadata to trigger a metadata update
            sessionConnector!!.invalidateMediaSessionMetadata()
        }
    }

    public override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                                  key: String?) {
        if (key == null || (key == ignoreHardwareMediaButtonsKey)) {
            updateShouldIgnoreHardwareMediaButtons(sharedPreferences)
        }
    }

    fun updateShouldIgnoreHardwareMediaButtons(sharedPreferences: SharedPreferences) {
        shouldIgnoreHardwareMediaButtons = sharedPreferences.getBoolean(ignoreHardwareMediaButtonsKey, false)
    }

    fun handleMediaButtonIntent(intent: Intent?) {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
    }

    fun getSessionToken(): Optional<MediaSessionCompat.Token> {
        return Optional.ofNullable(mediaSession).map(Function({ obj: MediaSessionCompat -> obj.getSessionToken() }))
    }

    private fun getForwardingPlayer(): ForwardingPlayer {
        // ForwardingPlayer means that all media session actions called on this player are
        // forwarded directly to the connected exoplayer, except for the overridden methods. So
        // override play and pause since our player adds more functionality to them over exoplayer.
        return object : ForwardingPlayer((player.getExoPlayer())!!) {
            public override fun play() {
                player.play()
                // hide the player controls even if the play command came from the media session
                player.UIs().get((VideoPlayerUi::class.java)).ifPresent(Consumer({ ui: VideoPlayerUi? -> ui!!.hideControls(0, 0) }))
            }

            public override fun pause() {
                player.pause()
            }
        }
    }

    private fun buildMediaMetadata(): MediaMetadataCompat {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "buildMediaMetadata called")
        }

        // set title and artist
        val builder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName())

        // set duration (-1 for livestreams or if unknown, see the METADATA_KEY_DURATION docs)
        val duration: Long = player.getCurrentStreamInfo()
                .filter(Predicate({ info: StreamInfo? -> !StreamTypeUtil.isLiveStream(info!!.getStreamType()) }))
                .map(Function({ info: StreamInfo? -> info!!.getDuration() * 1000L }))
                .orElse(-1L)
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        // set album art, unless the user asked not to, or there is no thumbnail available
        val showThumbnail: Boolean = player.getPrefs().getBoolean(
                context.getString(R.string.show_thumbnail_key), true)
        Optional.ofNullable(player.getThumbnail())
                .filter(Predicate({ bitmap: Bitmap? -> showThumbnail }))
                .ifPresent(Consumer({ bitmap: Bitmap? ->
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                }))
        return builder.build()
    }

    private fun updateMediaSessionActions() {
        // On Android 13+ (or Android T or API 33+) the actions in the player notification can't be
        // controlled directly anymore, but are instead derived from custom media session actions.
        // However the system allows customizing only two of these actions, since the other three
        // are fixed to play-pause-buffering, previous, next.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Although setting media session actions on older android versions doesn't seem to
            // cause any trouble, it also doesn't seem to do anything, so we don't do anything to
            // save battery. Check out NotificationUtil.updateActions() to see what happens on
            // older android versions.
            return
        }

        // only use the fourth and fifth actions (the settings page also shows only the last 2 on
        // Android 13+)
        val newNotificationActions: List<NotificationActionData?> = IntStream.of(3, 4)
                .map(IntUnaryOperator({ i: Int ->
                    player.getPrefs().getInt(
                            player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS.get(i)),
                            NotificationConstants.SLOT_DEFAULTS.get(i))
                }))
                .mapToObj<NotificationActionData?>(IntFunction<NotificationActionData?>({ action: Int -> NotificationActionData.Companion.fromNotificationActionEnum(player, action) }))
                .filter(Predicate<NotificationActionData?>({ obj: NotificationActionData? -> Objects.nonNull(obj) }))
                .collect(Collectors.toList<NotificationActionData?>())

        // avoid costly notification actions update, if nothing changed from last time
        if (!(newNotificationActions == prevNotificationActions)) {
            prevNotificationActions = newNotificationActions
            sessionConnector!!.setCustomActionProviders(
                    *newNotificationActions.stream()
                            .map<SessionConnectorActionProvider>(Function<NotificationActionData?, SessionConnectorActionProvider>({ data: NotificationActionData? -> SessionConnectorActionProvider(data, context) }))
                            .toArray<SessionConnectorActionProvider>(IntFunction<Array<SessionConnectorActionProvider>>({ _Dummy_.__Array__() })))
        }
    }

    public override fun onBlocked() {
        super.onBlocked()
        updateMediaSessionActions()
    }

    public override fun onPlaying() {
        super.onPlaying()
        updateMediaSessionActions()
    }

    public override fun onBuffering() {
        super.onBuffering()
        updateMediaSessionActions()
    }

    public override fun onPaused() {
        super.onPaused()
        updateMediaSessionActions()
    }

    public override fun onPausedSeek() {
        super.onPausedSeek()
        updateMediaSessionActions()
    }

    public override fun onCompleted() {
        super.onCompleted()
        updateMediaSessionActions()
    }

    public override fun onRepeatModeChanged(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {
        super.onRepeatModeChanged(repeatMode)
        updateMediaSessionActions()
    }

    public override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        updateMediaSessionActions()
    }

    public override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if ((NotificationConstants.ACTION_RECREATE_NOTIFICATION == intent.getAction())) {
            // the notification actions changed
            updateMediaSessionActions()
        }
    }

    public override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        updateMediaSessionActions()
    }

    public override fun onPlayQueueEdited() {
        super.onPlayQueueEdited()
        updateMediaSessionActions()
    }

    companion object {
        private val TAG: String = "MediaSessUi"
    }
}
