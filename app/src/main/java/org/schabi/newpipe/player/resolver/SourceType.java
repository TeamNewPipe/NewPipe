package org.schabi.newpipe.player.resolver;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;

/**
 * Enum representing the different source types created by {@link Resolver}s.
 */
public enum SourceType {

    /**
     * Placeholder value for {@link MediaItemTag}s, which represents that the {@code SourceType} is
     * not known yet or not applicable for the {@link MediaItemTag} type used.
     */
    UNKNOWN,

    /**
     * {@code SourceType} for media sources generated from manifests URL.
     *
     * <p>
     * This value is returned when a source is not generated from an {@link AudioStream} or a
     * {@link VideoStream} but from a manifest URL (the ones returned by
     * {@link StreamInfo#getDashMpdUrl()} and{@link StreamInfo#getHlsUrl()}).
     * This is the main behavior used on livestreams.
     * </p>
     */
    MANIFEST,

    /**
     * {@code SourceType} for media sources generated from {@link VideoStream}s which are
     * {@link VideoStream#isVideoOnly() video-only} with an {@link AudioStream} used as the audio
     * of this {@link VideoStream VideoStream}.
     */
    VIDEO_WITH_SEPARATED_AUDIO,

    /**
     * {@code SourceType} for media sources generated from {@link VideoStream}s which are
     * {@link VideoStream#isVideoOnly() video-only} without any audio (embedded or from an external
     * {@link AudioStream}).
     */
    VIDEO_ONLY,

    /**
     * {@code SourceType} for media sources generated from {@link VideoStream}s which are not
     * {@link VideoStream#isVideoOnly() video-only} with an audio source embedded in it.
     */
    VIDEO_WITH_AUDIO,

    /**
     * {@code SourceType} for media sources generated from {@link AudioStream}s only.
     */
    AUDIO_ONLY
}
