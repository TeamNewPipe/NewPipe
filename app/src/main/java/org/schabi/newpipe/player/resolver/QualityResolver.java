package org.schabi.newpipe.player.resolver;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public interface QualityResolver {
    int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

    int getOverrideResolutionIndex(List<VideoStream> sortedVideos,
                                   String selectedResolution,
                                   @Nullable String selectedCodec);

    int getCurrentAudioQualityIndex(List<AudioStream> audioStreams);
}
