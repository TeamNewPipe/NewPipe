@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.resolver

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.source.MediaSource
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.StreamInfoTag
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.ListHelper.getFilteredAudioStreams
import org.schabi.newpipe.util.ListHelper.getPlayableStreams

class AudioPlaybackResolver(
    private val context: Context,
    private val dataSource: PlayerDataSource
) : PlaybackResolver {

    var audioTrack: String? = null

    /**
     * Get a media source providing audio. If a service has no separate [AudioStream]s we
     * use a video stream as audio source to support audio background playback.
     *
     * @param info of the stream
     * @return the audio source to use or null if none could be found
     */
    override fun resolve(info: StreamInfo): MediaSource? {
        val liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info)
        if (liveSource != null) {
            return liveSource
        }

        val audioStreams = getFilteredAudioStreams(context, info.audioStreams)
        val stream: Stream?
        val tag: MediaItemTag

        if (audioStreams.isNotEmpty()) {
            val audioIndex = ListHelper.getAudioFormatIndex(context, audioStreams, audioTrack)
            stream = getStreamForIndex(audioIndex, audioStreams)
            tag = StreamInfoTag.of(info, audioStreams, audioIndex)
        } else {
            val videoStreams = getPlayableStreams(info.videoStreams, info.serviceId)
            if (videoStreams.isNotEmpty()) {
                val index = ListHelper.getDefaultResolutionIndex(context, videoStreams)
                stream = getStreamForIndex(index, videoStreams)
                tag = StreamInfoTag.of(info)
            } else {
                return null
            }
        }

        if (stream == null) return null

        return try {
            PlaybackResolver.buildMediaSource(
                dataSource,
                stream,
                info,
                PlaybackResolver.cacheKeyOf(info, stream),
                tag
            )
        } catch (e: PlaybackResolver.ResolverException) {
            Log.e(TAG, "Unable to create audio source", e)
            null
        }
    }

    private fun getStreamForIndex(index: Int, streams: List<out Stream>): Stream? {
        return if (index in streams.indices) {
            streams[index]
        } else {
            null
        }
    }

    companion object {
        private val TAG = AudioPlaybackResolver::class.java.simpleName
    }
}
