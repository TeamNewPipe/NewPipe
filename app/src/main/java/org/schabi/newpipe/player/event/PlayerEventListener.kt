@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.event

import androidx.media3.common.PlaybackParameters
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueue

interface PlayerEventListener {
    fun onQueueUpdate(queue: PlayQueue?)
    fun onPlaybackUpdate(state: Int, repeatMode: Int, shuffled: Boolean, parameters: PlaybackParameters?)
    fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int)
    fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?)
    fun onAudioTrackUpdate() {}
    fun onServiceStopped()
}
