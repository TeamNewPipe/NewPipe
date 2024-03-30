package org.schabi.newpipe.player.resolver

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.source.MediaSource
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.StreamInfoTag
import org.schabi.newpipe.player.resolver.PlaybackResolver.ResolverException
import org.schabi.newpipe.util.ListHelper

class AudioPlaybackResolver(private val context: Context,
                            private val dataSource: PlayerDataSource) : PlaybackResolver {
    var audioTrack: String? = null

    /**
     * Get a media source providing audio. If a service has no separate [AudioStream]s we
     * use a video stream as audio source to support audio background playback.
     *
     * @param info of the stream
     * @return the audio source to use or null if none could be found
     */
    public override fun resolve(info: StreamInfo): MediaSource? {
        val liveSource: MediaSource? = PlaybackResolver.Companion.maybeBuildLiveMediaSource(dataSource, info)
        if (liveSource != null) {
            return liveSource
        }
        val audioStreams: List<AudioStream?>? = ListHelper.getFilteredAudioStreams(context, info.getAudioStreams())
        val stream: Stream?
        val tag: MediaItemTag
        if (!audioStreams!!.isEmpty()) {
            val audioIndex: Int = ListHelper.getAudioFormatIndex(context, audioStreams, audioTrack)
            stream = getStreamForIndex(audioIndex, (audioStreams))
            tag = StreamInfoTag.Companion.of(info, (audioStreams), audioIndex)
        } else {
            val videoStreams: List<VideoStream?> = ListHelper.getPlayableStreams(info.getVideoStreams(), info.getServiceId())
            if (!videoStreams.isEmpty()) {
                val index: Int = ListHelper.getDefaultResolutionIndex(context, videoStreams)
                stream = getStreamForIndex(index, videoStreams)
                tag = StreamInfoTag.Companion.of(info)
            } else {
                return null
            }
        }
        try {
            return PlaybackResolver.Companion.buildMediaSource(
                    dataSource, stream, info, PlaybackResolver.Companion.cacheKeyOf(info, stream), tag)
        } catch (e: ResolverException) {
            Log.e(TAG, "Unable to create audio source", e)
            return null
        }
    }

    fun getStreamForIndex(index: Int, streams: List<Stream?>): Stream? {
        if (index >= 0 && index < streams.size) {
            return streams.get(index)
        }
        return null
    }

    companion object {
        private val TAG: String = AudioPlaybackResolver::class.java.getSimpleName()
    }
}
