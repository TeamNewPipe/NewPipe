package org.schabi.newpipe.player.mediaitem

import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaItem.LocalConfiguration
import com.google.android.exoplayer2.MediaItem.RequestMetadata
import com.google.android.exoplayer2.MediaMetadata
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Optional
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate

/**
 * Metadata container and accessor used by player internals.
 *
 * This interface ensures consistency of fetching metadata on each stream,
 * which is encapsulated in a [MediaItem] and delivered via ExoPlayer's
 * [Player.Listener] on event triggers to the downstream users.
 */
open interface MediaItemTag {
    val errors: List<Exception>
    val serviceId: Int
    val title: String
    val uploaderName: String
    val durationSeconds: Long
    val streamUrl: String
    val thumbnailUrl: String?
    val uploaderUrl: String?
    val streamType: StreamType
    val maybeStreamInfo: Optional<StreamInfo>
        get() {
            return Optional.empty()
        }
    val maybeQuality: Optional<Quality?>
        get() {
            return Optional.empty()
        }
    val maybeAudioTrack: Optional<AudioTrack?>
        get() {
            return Optional.empty()
        }

    fun <T> getMaybeExtras(type: Class<T>): Optional<T>?
    fun <T> withExtras(extra: T): MediaItemTag
    fun makeMediaId(): String {
        return UUID.randomUUID().toString() + "[" + title + "]"
    }

    fun asMediaItem(): MediaItem {
        val thumbnailUrl: String? = thumbnailUrl
        val mediaMetadata: MediaMetadata = MediaMetadata.Builder()
                .setArtworkUri(if (thumbnailUrl == null) null else Uri.parse(thumbnailUrl))
                .setArtist(uploaderName)
                .setDescription(title)
                .setDisplayTitle(title)
                .setTitle(title)
                .build()
        val requestMetaData: RequestMetadata = RequestMetadata.Builder()
                .setMediaUri(Uri.parse(streamUrl))
                .build()
        return MediaItem.fromUri(streamUrl)
                .buildUpon()
                .setMediaId(makeMediaId())
                .setMediaMetadata(mediaMetadata)
                .setRequestMetadata(requestMetaData)
                .setTag(this)
                .build()
    }

    class Quality private constructor(val sortedVideoStreams: List<VideoStream?>,
                                      val selectedVideoStreamIndex: Int) {

        val selectedVideoStream: VideoStream?
            get() {
                return if ((selectedVideoStreamIndex < 0
                                || selectedVideoStreamIndex >= sortedVideoStreams.size)) null else sortedVideoStreams.get(selectedVideoStreamIndex)
            }

        companion object {
            fun of(sortedVideoStreams: List<VideoStream?>,
                   selectedVideoStreamIndex: Int): Quality {
                return Quality(sortedVideoStreams, selectedVideoStreamIndex)
            }
        }
    }

    class AudioTrack private constructor(val audioStreams: List<AudioStream?>,
                                         val selectedAudioStreamIndex: Int) {

        val selectedAudioStream: AudioStream?
            get() {
                return if ((selectedAudioStreamIndex < 0
                                || selectedAudioStreamIndex >= audioStreams.size)) null else audioStreams.get(selectedAudioStreamIndex)
            }

        companion object {
            fun of(audioStreams: List<AudioStream?>,
                   selectedAudioStreamIndex: Int): AudioTrack {
                return AudioTrack(audioStreams, selectedAudioStreamIndex)
            }
        }
    }

    companion object {
        fun from(mediaItem: MediaItem?): Optional<MediaItemTag> {
            return Optional.ofNullable(mediaItem)
                    .map(Function({ item: MediaItem -> item.localConfiguration }))
                    .map(Function({ localConfiguration: LocalConfiguration? -> localConfiguration!!.tag }))
                    .filter(Predicate({ obj: Any? -> MediaItemTag::class.java.isInstance(obj) }))
                    .map(Function({ obj: Any? -> MediaItemTag::class.java.cast(obj) }))
        }
    }
}
