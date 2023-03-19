package org.schabi.newpipe.player.mediaitem;

import android.net.Uri;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaItem.RequestMetadata;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Metadata container and accessor used by player internals.
 *
 * This interface ensures consistency of fetching metadata on each stream,
 * which is encapsulated in a {@link MediaItem} and delivered via ExoPlayer's
 * {@link Player.Listener} on event triggers to the downstream users.
 **/
public interface MediaItemTag {

    List<Exception> getErrors();

    int getServiceId();

    String getTitle();

    String getUploaderName();

    long getDurationSeconds();

    String getStreamUrl();

    String getThumbnailUrl();

    String getUploaderUrl();

    StreamType getStreamType();

    @NonNull
    default Optional<StreamInfo> getMaybeStreamInfo() {
        return Optional.empty();
    }

    @NonNull
    default Optional<Quality> getMaybeQuality() {
        return Optional.empty();
    }

    @NonNull
    default Optional<AudioTrack> getMaybeAudioTrack() {
        return Optional.empty();
    }

    <T> Optional<T> getMaybeExtras(@NonNull Class<T> type);

    <T> MediaItemTag withExtras(@NonNull T extra);

    @NonNull
    static Optional<MediaItemTag> from(@Nullable final MediaItem mediaItem) {
        return Optional.ofNullable(mediaItem)
                .map(item -> item.localConfiguration)
                .map(localConfiguration -> localConfiguration.tag)
                .filter(MediaItemTag.class::isInstance)
                .map(MediaItemTag.class::cast);
    }

    @NonNull
    default String makeMediaId() {
        return UUID.randomUUID().toString() + "[" + getTitle() + "]";
    }

    @NonNull
    default MediaItem asMediaItem() {
        final MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setArtworkUri(Uri.parse(getThumbnailUrl()))
                .setArtist(getUploaderName())
                .setDescription(getTitle())
                .setDisplayTitle(getTitle())
                .setTitle(getTitle())
                .build();

        final RequestMetadata requestMetaData = new RequestMetadata.Builder()
                .setMediaUri(Uri.parse(getStreamUrl()))
                .build();

        return MediaItem.fromUri(getStreamUrl())
                .buildUpon()
                .setMediaId(makeMediaId())
                .setMediaMetadata(mediaMetadata)
                .setRequestMetadata(requestMetaData)
                .setTag(this)
                .build();
    }

    final class Quality {
        @NonNull
        private final List<VideoStream> sortedVideoStreams;
        private final int selectedVideoStreamIndex;

        private Quality(@NonNull final List<VideoStream> sortedVideoStreams,
                        final int selectedVideoStreamIndex) {
            this.sortedVideoStreams = sortedVideoStreams;
            this.selectedVideoStreamIndex = selectedVideoStreamIndex;
        }

        static Quality of(@NonNull final List<VideoStream> sortedVideoStreams,
                          final int selectedVideoStreamIndex) {
            return new Quality(sortedVideoStreams, selectedVideoStreamIndex);
        }

        @NonNull
        public List<VideoStream> getSortedVideoStreams() {
            return sortedVideoStreams;
        }

        public int getSelectedVideoStreamIndex() {
            return selectedVideoStreamIndex;
        }

        @Nullable
        public VideoStream getSelectedVideoStream() {
            return selectedVideoStreamIndex < 0
                    || selectedVideoStreamIndex >= sortedVideoStreams.size()
                    ? null : sortedVideoStreams.get(selectedVideoStreamIndex);
        }
    }

    final class AudioTrack {
        @NonNull
        private final List<AudioStream> audioStreams;
        private final int selectedAudioStreamIndex;

        private AudioTrack(@NonNull final List<AudioStream> audioStreams,
                           final int selectedAudioStreamIndex) {
            this.audioStreams = audioStreams;
            this.selectedAudioStreamIndex = selectedAudioStreamIndex;
        }

        static AudioTrack of(@NonNull final List<AudioStream> audioStreams,
                             final int selectedAudioStreamIndex) {
            return new AudioTrack(audioStreams, selectedAudioStreamIndex);
        }

        @NonNull
        public List<AudioStream> getAudioStreams() {
            return audioStreams;
        }

        public int getSelectedAudioStreamIndex() {
            return selectedAudioStreamIndex;
        }

        @Nullable
        public AudioStream getSelectedAudioStream() {
            return selectedAudioStreamIndex < 0
                    || selectedAudioStreamIndex >= audioStreams.size()
                    ? null : audioStreams.get(selectedAudioStreamIndex);
        }
    }
}
