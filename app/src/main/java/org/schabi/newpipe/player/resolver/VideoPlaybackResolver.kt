package org.schabi.newpipe.player.resolver

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.RoleFlags
import com.google.android.exoplayer2.MediaItem.SubtitleConfiguration
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.mediaitem.StreamInfoTag
import org.schabi.newpipe.player.resolver.PlaybackResolver.ResolverException
import org.schabi.newpipe.util.ListHelper
import java.util.Optional
import java.util.function.Function

class VideoPlaybackResolver(private val context: Context,
                            private val dataSource: PlayerDataSource,
                            private val qualityResolver: QualityResolver) : PlaybackResolver {
    private var streamSourceType: SourceType? = null
    var playbackQuality: String? = null
    var audioTrack: String? = null

    enum class SourceType {
        LIVE_STREAM,
        VIDEO_WITH_SEPARATED_AUDIO,
        VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
    }

    public override fun resolve(info: StreamInfo): MediaSource? {
        val liveSource: MediaSource? = PlaybackResolver.Companion.maybeBuildLiveMediaSource(dataSource, info)
        if (liveSource != null) {
            streamSourceType = SourceType.LIVE_STREAM
            return liveSource
        }
        val mediaSources: MutableList<MediaSource?> = ArrayList()

        // Create video stream source
        val videoStreamsList: List<VideoStream?> = ListHelper.getSortedStreamVideosList(context,
                ListHelper.getPlayableStreams(info.getVideoStreams(), info.getServiceId()),
                ListHelper.getPlayableStreams(info.getVideoOnlyStreams(), info.getServiceId()), false, true)
        val audioStreamsList: List<AudioStream?>? = ListHelper.getFilteredAudioStreams(context, info.getAudioStreams())
        val videoIndex: Int
        if (videoStreamsList.isEmpty()) {
            videoIndex = -1
        } else if (playbackQuality == null) {
            videoIndex = qualityResolver.getDefaultResolutionIndex(videoStreamsList)
        } else {
            videoIndex = qualityResolver.getOverrideResolutionIndex(videoStreamsList,
                    playbackQuality)
        }
        val audioIndex: Int = ListHelper.getAudioFormatIndex(context, audioStreamsList, audioTrack)
        val tag: MediaItemTag = StreamInfoTag.Companion.of(info, videoStreamsList, videoIndex, (audioStreamsList)!!, audioIndex)
        val video: VideoStream? = tag.getMaybeQuality()
                .map<VideoStream?>(Function<MediaItemTag.Quality?, VideoStream?>({ getSelectedVideoStream() }))
                .orElse(null)
        val audio: AudioStream? = tag.getMaybeAudioTrack()
                .map<AudioStream?>(Function<MediaItemTag.AudioTrack?, AudioStream?>({ getSelectedAudioStream() }))
                .orElse(null)
        if (video != null) {
            try {
                val streamSource: MediaSource? = PlaybackResolver.Companion.buildMediaSource(
                        dataSource, video, info, PlaybackResolver.Companion.cacheKeyOf(info, video), tag)
                mediaSources.add(streamSource)
            } catch (e: ResolverException) {
                Log.e(TAG, "Unable to create video source", e)
                return null
            }
        }

        // Use the audio stream if there is no video stream, or
        // merge with audio stream in case if video does not contain audio
        if (audio != null && ((video == null) || video.isVideoOnly() || (audioTrack != null))) {
            try {
                val audioSource: MediaSource? = PlaybackResolver.Companion.buildMediaSource(
                        dataSource, audio, info, PlaybackResolver.Companion.cacheKeyOf(info, audio), tag)
                mediaSources.add(audioSource)
                streamSourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO
            } catch (e: ResolverException) {
                Log.e(TAG, "Unable to create audio source", e)
                return null
            }
        } else {
            streamSourceType = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
        }

        // If there is no audio or video sources, then this media source cannot be played back
        if (mediaSources.isEmpty()) {
            return null
        }

        // Below are auxiliary media sources

        // Create subtitle sources
        val subtitlesStreams: List<SubtitlesStream>? = info.getSubtitles()
        if (subtitlesStreams != null) {
            // Torrent and non URL subtitles are not supported by ExoPlayer
            val nonTorrentAndUrlStreams: List<SubtitlesStream?> = ListHelper.getUrlAndNonTorrentStreams(
                    subtitlesStreams)
            for (subtitle: SubtitlesStream? in nonTorrentAndUrlStreams) {
                val mediaFormat: MediaFormat? = subtitle!!.getFormat()
                if (mediaFormat != null) {
                    val textRoleFlag: @RoleFlags Int = if (subtitle.isAutoGenerated()) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else C.ROLE_FLAG_CAPTION
                    val textMediaItem: SubtitleConfiguration = SubtitleConfiguration.Builder(
                            Uri.parse(subtitle.getContent()))
                            .setMimeType(mediaFormat.getMimeType())
                            .setRoleFlags(textRoleFlag)
                            .setLanguage(PlayerHelper.captionLanguageOf(context, (subtitle)))
                            .build()
                    val textSource: MediaSource = dataSource.getSingleSampleMediaSourceFactory()
                            .createMediaSource(textMediaItem, C.TIME_UNSET)
                    mediaSources.add(textSource)
                }
            }
        }
        if (mediaSources.size == 1) {
            return mediaSources.get(0)
        } else {
            return MergingMediaSource(true, *mediaSources.toTypedArray<MediaSource?>())
        }
    }

    /**
     * Returns the last resolved [StreamInfo]'s [source type][SourceType].
     *
     * @return [Optional.empty] if nothing was resolved, otherwise the [SourceType]
     * of the last resolved [StreamInfo] inside an [Optional]
     */
    fun getStreamSourceType(): Optional<SourceType?> {
        return Optional.ofNullable(streamSourceType)
    }

    open interface QualityResolver {
        fun getDefaultResolutionIndex(sortedVideos: List<VideoStream?>): Int
        fun getOverrideResolutionIndex(sortedVideos: List<VideoStream?>, playbackQuality: String?): Int
    }

    companion object {
        private val TAG: String = VideoPlaybackResolver::class.java.getSimpleName()
    }
}
