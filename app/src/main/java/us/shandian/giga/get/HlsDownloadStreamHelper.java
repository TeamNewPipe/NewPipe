package us.shandian.giga.get;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Locale;

public final class HlsDownloadStreamHelper {
    public static final String HLS_MANIFEST_RESOLUTION = "HLS";

    private static final String HLS_MANIFEST_STREAM_ID = "hls-manifest";
    private static final String HLS_AUDIO_FALLBACK_STREAM_ID = "hls-audio-fallback";
    private static final String HLS_MANIFEST_CODEC = "hls";

    private HlsDownloadStreamHelper() {
    }

    public static boolean addManifestFallbackIfNeeded(final List<VideoStream> streams,
                                                      final StreamInfo info) {
        if (streams == null || info == null || info.getStreamType() != StreamType.VIDEO_STREAM) {
            return false;
        }

        final String hlsUrl = info.getHlsUrl();
        if (hlsUrl == null || hlsUrl.isEmpty() || hasNonHlsStream(streams)
                || hasHlsStream(streams, hlsUrl)) {
            return false;
        }

        streams.add(createManifestFallback(hlsUrl));
        return true;
    }

    public static boolean addAudioFallbackIfNeeded(final List<AudioStream> streams,
                                                   final StreamInfo info) {
        if (streams == null || info == null || info.getStreamType() != StreamType.VIDEO_STREAM
                || !streams.isEmpty()) {
            return false;
        }

        final AudioStream fallback = createAudioFallback(info.getVideoStreams(), info.getHlsUrl(),
                null);
        if (fallback == null) {
            return false;
        }

        streams.add(fallback);
        return true;
    }

    public static AudioStream createAudioFallback(final List<VideoStream> streams,
                                                  final String hlsUrl,
                                                  final String audioTrackId) {
        final VideoStream source = findAudioFallbackSource(streams, audioTrackId);
        if (source != null) {
            final String manifestUrl = source.getManifestUrl() != null
                    ? source.getManifestUrl() : source.getContent();
            return new AudioStream.Builder()
                    .setId(HLS_AUDIO_FALLBACK_STREAM_ID + "-" + source.getId())
                    .setContent(source.getContent(), true)
                    .setMediaFormat(MediaFormat.M4A)
                    .setDeliveryMethod(DeliveryMethod.HLS)
                    .setManifestUrl(manifestUrl)
                    .setAverageBitrate(AudioStream.UNKNOWN_BITRATE)
                    .setQuality(HLS_MANIFEST_RESOLUTION)
                    .setAudioTrackId(source.getAudioTrackId())
                    .setAudioTrackName(source.getAudioTrackName())
                    .setAudioLocale(source.getAudioLocale())
                    .build();
        }

        if (hlsUrl == null || hlsUrl.isEmpty()) {
            return null;
        }

        return new AudioStream.Builder()
                .setId(HLS_AUDIO_FALLBACK_STREAM_ID)
                .setContent(hlsUrl, true)
                .setMediaFormat(MediaFormat.M4A)
                .setDeliveryMethod(DeliveryMethod.HLS)
                .setManifestUrl(hlsUrl)
                .setAverageBitrate(AudioStream.UNKNOWN_BITRATE)
                .setQuality(HLS_MANIFEST_RESOLUTION)
                .build();
    }

    public static VideoStream createManifestFallback(final String hlsUrl) {
        return new VideoStream.Builder()
                .setId(HLS_MANIFEST_STREAM_ID)
                .setContent(hlsUrl, true)
                .setIsVideoOnly(false)
                .setResolution(HLS_MANIFEST_RESOLUTION)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(DeliveryMethod.HLS)
                .setManifestUrl(hlsUrl)
                .setCodec(HLS_MANIFEST_CODEC)
                .build();
    }

    public static boolean isManifestFallbackRecovery(final MissionRecoveryInfo recovery) {
        return recovery != null
                && recovery.getKind() == 'v'
                && !recovery.isDesired2()
                && recovery.getFormat() == MediaFormat.MPEG_4
                && HLS_MANIFEST_RESOLUTION.equals(recovery.getDesired());
    }

    public static String[] buildResourceDeliveryMethods(final Stream selectedStream,
                                                        final Stream secondaryStream) {
        if (secondaryStream == null) {
            return new String[]{selectedStream.getDeliveryMethod().name()};
        }
        return new String[]{
                selectedStream.getDeliveryMethod().name(),
                secondaryStream.getDeliveryMethod().name()
        };
    }

    public static String[] buildResourceManifestUrls(final Stream selectedStream,
                                                     final Stream secondaryStream) {
        if (secondaryStream == null) {
            return new String[]{selectedStream.getManifestUrl()};
        }
        return new String[]{selectedStream.getManifestUrl(), secondaryStream.getManifestUrl()};
    }

    public static boolean[] buildResourceIsUrls(final Stream selectedStream,
                                                final Stream secondaryStream) {
        if (secondaryStream == null) {
            return new boolean[]{selectedStream.isUrl()};
        }
        return new boolean[]{selectedStream.isUrl(), secondaryStream.isUrl()};
    }

    public static boolean containsHlsResource(final String[] deliveryMethods,
                                             final String[] manifestUrls,
                                             final String[] urls) {
        return containsHlsMethod(deliveryMethods)
                || containsHlsValue(manifestUrls)
                || containsHlsValue(urls);
    }

    public static boolean looksLikeHls(final String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(".m3u8");
    }

    private static boolean hasNonHlsStream(final List<VideoStream> streams) {
        for (final VideoStream stream : streams) {
            if (stream.getDeliveryMethod() != DeliveryMethod.HLS) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHlsStream(final List<VideoStream> streams, final String hlsUrl) {
        for (final VideoStream stream : streams) {
            if (stream.getDeliveryMethod() == DeliveryMethod.HLS
                    || hlsUrl.equals(stream.getContent())
                    || hlsUrl.equals(stream.getManifestUrl())) {
                return true;
            }
        }
        return false;
    }

    private static VideoStream findAudioFallbackSource(final List<VideoStream> streams,
                                                       final String audioTrackId) {
        if (streams == null) {
            return null;
        }

        VideoStream best = null;
        for (final VideoStream stream : streams) {
            if (stream.getDeliveryMethod() != DeliveryMethod.HLS || stream.isVideoOnly()
                    || !stream.isUrl()) {
                continue;
            }
            if (audioTrackId != null && !audioTrackId.equals(stream.getAudioTrackId())) {
                continue;
            }
            if (best == null || isSmallerVariant(stream, best)) {
                best = stream;
            }
        }
        return best;
    }

    private static boolean isSmallerVariant(final VideoStream candidate,
                                            final VideoStream existing) {
        final int candidateHeight = heightOf(candidate.getResolution());
        final int existingHeight = heightOf(existing.getResolution());
        if (candidateHeight != existingHeight) {
            return candidateHeight < existingHeight;
        }

        final int candidateBitrate = candidate.getBitrate();
        final int existingBitrate = existing.getBitrate();
        return candidateBitrate > 0 && (existingBitrate <= 0 || candidateBitrate < existingBitrate);
    }

    private static int heightOf(final String resolution) {
        if (resolution == null) {
            return Integer.MAX_VALUE;
        }
        final int pIndex = resolution.toLowerCase(Locale.ROOT).indexOf('p');
        if (pIndex <= 0) {
            return Integer.MAX_VALUE;
        }
        int start = pIndex - 1;
        while (start > 0 && Character.isDigit(resolution.charAt(start - 1))) {
            start--;
        }
        try {
            return Integer.parseInt(resolution.substring(start, pIndex));
        } catch (final NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean containsHlsMethod(final String[] values) {
        if (values == null) {
            return false;
        }
        for (final String value : values) {
            if (DeliveryMethod.HLS.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHlsValue(final String[] values) {
        if (values == null) {
            return false;
        }
        for (final String value : values) {
            if (looksLikeHls(value)) {
                return true;
            }
        }
        return false;
    }
}
