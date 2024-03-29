package org.schabi.newpipe.player.mediaitem;

import com.google.android.exoplayer2.MediaItem;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This {@link MediaItemTag} object contains metadata for a resolved stream
 * that is ready for playback. This object guarantees the {@link StreamInfo}
 * is available and may provide the {@link Quality} of video stream used in
 * the {@link MediaItem}.
 **/
public final class StreamInfoTag implements MediaItemTag {
    @NonNull
    private final StreamInfo streamInfo;
    @Nullable
    private final MediaItemTag.Quality quality;
    @Nullable
    private final MediaItemTag.AudioTrack audioTrack;
    @Nullable
    private final Object extras;

    private StreamInfoTag(@NonNull final StreamInfo streamInfo,
                          @Nullable final MediaItemTag.Quality quality,
                          @Nullable final MediaItemTag.AudioTrack audioTrack,
                          @Nullable final Object extras) {
        this.streamInfo = streamInfo;
        this.quality = quality;
        this.audioTrack = audioTrack;
        this.extras = extras;
    }

    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo,
                                   @NonNull final List<VideoStream> sortedVideoStreams,
                                   final int selectedVideoStreamIndex,
                                   @NonNull final List<AudioStream> audioStreams,
                                   final int selectedAudioStreamIndex) {
        final Quality quality = Quality.of(sortedVideoStreams, selectedVideoStreamIndex);
        final AudioTrack audioTrack =
                AudioTrack.of(audioStreams, selectedAudioStreamIndex);
        return new StreamInfoTag(streamInfo, quality, audioTrack, null);
    }

    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo,
                                   @NonNull final List<AudioStream> audioStreams,
                                   final int selectedAudioStreamIndex) {
        final AudioTrack audioTrack =
                AudioTrack.of(audioStreams, selectedAudioStreamIndex);
        return new StreamInfoTag(streamInfo, null, audioTrack, null);
    }

    public static StreamInfoTag of(@NonNull final StreamInfo streamInfo) {
        return new StreamInfoTag(streamInfo, null, null, null);
    }

    @Override
    public List<Exception> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public int getServiceId() {
        return streamInfo.getServiceId();
    }

    @Override
    public String getTitle() {
        return streamInfo.getName();
    }

    @Override
    public String getUploaderName() {
        return streamInfo.getUploaderName();
    }

    @Override
    public long getDurationSeconds() {
        return streamInfo.getDuration();
    }

    @Override
    public String getStreamUrl() {
        return streamInfo.getUrl();
    }

    @Override
    public String getThumbnailUrl() {
        return ImageStrategy.choosePreferredImage(streamInfo.getThumbnails());
    }

    @Override
    public String getUploaderUrl() {
        return streamInfo.getUploaderUrl();
    }

    @Override
    public StreamType getStreamType() {
        return streamInfo.getStreamType();
    }

    @NonNull
    @Override
    public Optional<StreamInfo> getMaybeStreamInfo() {
        return Optional.of(streamInfo);
    }

    @NonNull
    @Override
    public Optional<Quality> getMaybeQuality() {
        return Optional.ofNullable(quality);
    }

    @NonNull
    @Override
    public Optional<AudioTrack> getMaybeAudioTrack() {
        return Optional.ofNullable(audioTrack);
    }

    @Override
    public <T> Optional<T> getMaybeExtras(@NonNull final Class<T> type) {
        return Optional.ofNullable(extras).map(type::cast);
    }

    @Override
    public StreamInfoTag withExtras(@NonNull final Object extra) {
        return new StreamInfoTag(streamInfo, quality, audioTrack, extra);
    }
}
