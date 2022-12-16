package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.util.ListHelper.getNonTorrentStreams;

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

import java.util.List;

public class AudioPlaybackResolver implements PlaybackResolver {
    private static final String TAG = AudioPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;

    public AudioPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource) {
        this.context = context;
        this.dataSource = dataSource;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            return liveSource;
        }

        final Stream stream = getAudioSource(info);
        if (stream == null) {
            return null;
        }

        final MediaItemTag tag = StreamInfoTag.of(info);

        try {
            return PlaybackResolver.buildMediaSource(
                    dataSource, stream, info, PlaybackResolver.cacheKeyOf(info, stream), tag);
        } catch (final ResolverException e) {
            Log.e(TAG, "Unable to create audio source", e);
            return null;
        }
    }

    /**
     * Get a stream to be played as audio. If a service has no separate {@link AudioStream}s we
     * use a video stream as audio source to support audio background playback.
     *
     * @param info of the stream
     * @return the audio source to use or null if none could be found
     */
    @Nullable
    private Stream getAudioSource(@NonNull final StreamInfo info) {
        final List<AudioStream> audioStreams = getNonTorrentStreams(info.getAudioStreams());
        if (!audioStreams.isEmpty()) {
            final int index = ListHelper.getDefaultAudioFormat(context, audioStreams);
            return getStreamForIndex(index, audioStreams);
        } else {
            final List<VideoStream> videoStreams = getNonTorrentStreams(info.getVideoStreams());
            if (!videoStreams.isEmpty()) {
                final int index = ListHelper.getDefaultResolutionIndex(context, videoStreams);
                return getStreamForIndex(index, videoStreams);
            }
        }
        return null;
    }

    @Nullable
    Stream getStreamForIndex(final int index, @NonNull final List<? extends Stream> streams) {
        if (index >= 0 && index < streams.size()) {
            return streams.get(index);
        }
        return null;
    }
}
