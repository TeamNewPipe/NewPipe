package org.schabi.newpipe.player.event

import com.google.android.exoplayer2.PlaybackParameters
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueue

open interface PlayerEventListener {
    fun onQueueUpdate(queue: PlayQueue?)
    fun onPlaybackUpdate(state: Int, repeatMode: Int, shuffled: Boolean,
                         parameters: PlaybackParameters?)

    fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int)
    fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?)
    fun onAudioTrackUpdate() {}
    fun onServiceStopped()
}
