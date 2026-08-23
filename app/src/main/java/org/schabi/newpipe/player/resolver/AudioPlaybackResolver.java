package org.schabi.newpipe.player.resolver;

import static org.schabi.newpipe.util.ListHelper.removeTorrentStreams;
import static org.schabi.newpipe.util.ListHelper.filterUnsupportedFormats;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.ListHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AudioPlaybackResolver implements PlaybackResolver {
    private static final String TAG = AudioPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    private List<String> blacklistUrls = new ArrayList<>();
    @Nullable
    private String audioTrack;

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

        List<AudioStream> audioStreams = info.getAudioStreams()
                .stream().filter(s -> !blacklistUrls.contains(s.getContent())).collect(Collectors.toList());
        removeTorrentStreams(audioStreams);
        audioStreams = filterUnsupportedFormats(audioStreams, context);

        final int index = ListHelper.getAudioFormatIndex(context, audioStreams, audioTrack);
        if (index < 0 || index >= audioStreams.size()) {
            return null;
        }

        final AudioStream audio = audioStreams.get(index);
        final MediaItemTag tag = StreamInfoTag.of(info);

        try {
            return PlaybackResolver.buildMediaSource(
                    dataSource, audio, info, PlayerHelper.cacheKeyOf(info, audio), tag);
        } catch (final IOException e) {
            Log.e(TAG, "Unable to create audio source:", e);
            return null;
        }
    }
    public void addBlacklistUrl(@NonNull final String url) {
        blacklistUrls.add(url);
    }

    public List<String> getBlacklistUrls() {
        return blacklistUrls;
    }

    @Nullable
    public String getAudioTrack() {
        return audioTrack;
    }

    public void setAudioTrack(@Nullable final String audioTrack) {
        this.audioTrack = audioTrack;
    }
}
