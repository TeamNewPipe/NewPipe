package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.util.ListHelper.getFilteredAudioStreams;
import static org.schabi.newpipe.util.ListHelper.getPlayableStreams;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.ListHelper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AudioPlaybackResolver implements PlaybackResolver {
    private static final String TAG = AudioPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @Nullable
    private String audioTrack;

    public AudioPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource) {
        this.context = context;
        this.dataSource = dataSource;
    }

    /**
     * Get a media source providing audio. If a service has no separate {@link AudioStream}s we
     * use a video stream as audio source to support audio background playback.
     *
     * @param info of the stream
     * @return the audio source to use or null if none could be found
     */
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            return liveSource;
        }

        final List<AudioStream> audioStreams =
                getFilteredAudioStreams(
                        context,
                        // TODO: getAudioStreams should be @NonNull
                        Objects.requireNonNullElse(info.getAudioStreams(), Collections.emptyList())
                );
        final Stream stream;
        final MediaItemTag tag;

        if (!audioStreams.isEmpty()) {
            final int audioIndex =
                    ListHelper.getAudioFormatIndex(context, audioStreams, audioTrack);
            assert audioIndex != -1;
            final MediaItemTag.AudioTrack audio =
                    new MediaItemTag.AudioTrack(audioStreams, audioIndex);
            tag = new StreamInfoTag(info, null, audio, null);
            stream = audio.getSelectedAudioStream();
        } else {
            final List<VideoStream> videoStreams =
                    getPlayableStreams(info.getVideoStreams(), info.getServiceId());
            if (!videoStreams.isEmpty()) {
                final int videoIndex = ListHelper.getDefaultResolutionIndex(context, videoStreams);
                assert videoIndex != -1;
                final MediaItemTag.Quality video =
                        new MediaItemTag.Quality(videoStreams, videoIndex);
                // why are we not passing `video` as quality here?
                tag = new StreamInfoTag(info, null, null, null);
                stream = video.getSelectedVideoStream();
            } else {
                return null;
            }
        }

        try {
            return PlaybackResolver.buildMediaSource(
                    dataSource, stream, info, PlaybackResolver.cacheKeyOf(info, stream), tag);
        } catch (final ResolverException e) {
            Log.e(TAG, "Unable to create audio source", e);
            return null;
        }
    }

    /** Set audio track to be used the next time {@link #resolve(StreamInfo)} is called.
     *
     * @param audioTrack the {@link AudioStream} audioTrackId that should be selected on resolve
     */
    public void setAudioTrack(@Nullable final String audioTrack) {
        this.audioTrack = audioTrack;
    }
}
