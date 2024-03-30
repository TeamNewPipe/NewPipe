package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.video.VideoSize
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player

/**
 * A player UI is a component that can seamlessly connect and disconnect from the [Player] and
 * provide a user interface of some sort. Try to extend this class instead of adding more code to
 * [Player]!
 */
abstract class PlayerUi protected constructor(
        /**
         * @return the player instance this UI was constructed with
         */
        val player: Player) {
    protected val context: Context

    /**
     * @param player the player instance that will be usable throughout the lifetime of this UI; its
     * context should already have been initialized
     */
    init {
        context = player.getContext()
    }

    /**
     * Called after the player received an intent and processed it.
     */
    open fun setupAfterIntent() {}

    /**
     * Called right after the exoplayer instance is constructed, or right after this UI is
     * constructed if the exoplayer is already available then. Note that the exoplayer instance
     * could be built and destroyed multiple times during the lifetime of the player, so this method
     * might be called multiple times.
     */
    open fun initPlayer() {}

    /**
     * Called when playback in the exoplayer is about to start, or right after this UI is
     * constructed if the exoplayer and the play queue are already available then. The play queue
     * will therefore always be not null.
     */
    open fun initPlayback() {}

    /**
     * Called when the exoplayer instance is about to be destroyed. Note that the exoplayer instance
     * could be built and destroyed multiple times during the lifetime of the player, so this method
     * might be called multiple times. Be sure to unset any video surface view or play queue
     * listeners! This will also be called when this UI is being discarded, just before [ ][.destroy].
     */
    open fun destroyPlayer() {}

    /**
     * Called when this UI is being discarded, either because the player is switching to a different
     * UI or because the player is shutting down completely.
     */
    open fun destroy() {}

    /**
     * Called when the player is smooth-stopping, that is, transitioning smoothly to a new play
     * queue after the user tapped on a new video stream while a stream was playing in the video
     * detail fragment.
     */
    open fun smoothStopForImmediateReusing() {}

    /**
     * Called when the video detail fragment listener is connected with the player, or right after
     * this UI is constructed if the listener is already connected then.
     */
    open fun onFragmentListenerSet() {}

    /**
     * Broadcasts that the player receives will also be notified to UIs here. If you want to
     * register new broadcast actions to receive here, add them to [ ][Player.setupBroadcastReceiver].
     * @param intent the broadcast intent received by the player
     */
    open fun onBroadcastReceived(intent: Intent) {}

    /**
     * Called when stream progress (i.e. the current time in the seekbar) or stream duration change.
     * Will surely be called every [Player.PROGRESS_LOOP_INTERVAL_MILLIS] while a stream is
     * playing.
     * @param currentProgress the current progress in milliseconds
     * @param duration        the duration of the stream being played
     * @param bufferPercent   the percentage of stream already buffered, see [                        ][com.google.android.exoplayer2.BasePlayer.getBufferedPercentage]
     */
    open fun onUpdateProgress(currentProgress: Int,
                              duration: Int,
                              bufferPercent: Int) {
    }

    open fun onPrepared() {}
    open fun onBlocked() {}
    open fun onPlaying() {}
    open fun onBuffering() {}
    open fun onPaused() {}
    open fun onPausedSeek() {}
    open fun onCompleted() {}
    open fun onRepeatModeChanged(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {}
    open fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}
    open fun onMuteUnmuteChanged(isMuted: Boolean) {}

    /**
     * @see com.google.android.exoplayer2.Player.Listener.onTracksChanged
     * @param currentTracks the available tracks information
     */
    open fun onTextTracksChanged(currentTracks: Tracks) {}

    /**
     * @see com.google.android.exoplayer2.Player.Listener.onPlaybackParametersChanged
     *
     * @param playbackParameters the new playback parameters
     */
    open fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}

    /**
     * @see com.google.android.exoplayer2.Player.Listener.onRenderedFirstFrame
     */
    open fun onRenderedFirstFrame() {}

    /**
     * @see com.google.android.exoplayer2.text.TextOutput.onCues
     *
     * @param cues the cues to pass to the subtitle view
     */
    open fun onCues(cues: List<Cue?>) {}

    /**
     * Called when the stream being played changes.
     * @param info the [StreamInfo] metadata object, along with data about the selected and
     * available video streams (to be used to build the resolution menus, for example)
     */
    open fun onMetadataChanged(info: StreamInfo) {}

    /**
     * Called when the thumbnail for the current metadata was loaded.
     * @param bitmap the thumbnail to process, or null if there is no thumbnail or there was an
     * error when loading the thumbnail
     */
    open fun onThumbnailLoaded(bitmap: Bitmap?) {}

    /**
     * Called when the play queue was edited: a stream was appended, moved or removed.
     */
    open fun onPlayQueueEdited() {}

    /**
     * @param videoSize the new video size, useful to set the surface aspect ratio
     * @see com.google.android.exoplayer2.Player.Listener.onVideoSizeChanged
     */
    open fun onVideoSizeChanged(videoSize: VideoSize) {}
}
