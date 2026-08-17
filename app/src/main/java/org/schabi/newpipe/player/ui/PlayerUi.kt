@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.VideoSize
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player

abstract class PlayerUi(val player: Player) {
    val context: Context = player.context

    open fun initPlayer() {}
    open fun destroyPlayer() {}
    open fun initPlayback() {}
    open fun setupAfterIntent() {}
    open fun destroy() {}
    open fun onPrepared() {}
    open fun onBlocked() {}
    open fun onPlaying() {}
    open fun onBuffering() {}
    open fun onPaused() {}
    open fun onPausedSeek() {}
    open fun onCompleted() {}
    open fun onThumbnailLoaded(bitmap: Bitmap?) {}
    open fun onUpdateProgress(currentProgress: Int, duration: Int, bufferPercent: Int) {}
    open fun onRenderedFirstFrame() {}
    open fun onRepeatModeChanged(repeatMode: Int) {}
    open fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { }
    open fun onBroadcastReceived(intent: Intent) {}
    open fun onMetadataChanged(info: StreamInfo) {}
    open fun onPlayQueueEdited() {}
    open fun onMuteUnmuteChanged(muted: Boolean) {}
    open fun onTextTracksChanged(tracks: Tracks) {}
    open fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
    open fun onVideoSizeChanged(videoSize: VideoSize) {}
    open fun onCues(cues: List<Cue>) {}
    open fun onFragmentListenerSet() {}
}
