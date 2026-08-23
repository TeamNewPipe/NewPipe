package org.schabi.newpipe.player.datasource;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.App;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.player.PlaybackStartupTrace;
import org.schabi.newpipe.util.ListHelper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/** Selects SABR formats and creates protocol sessions. */
public final class SabrSessionHelper {
    @Nullable
    private static volatile SessionObserver benchmarkSessionObserver;

    private SabrSessionHelper() {
    }

    /** Receives newly created playback sessions for instrumentation benchmarks only. */
    public interface SessionObserver {
        void onSessionCreated(@NonNull YoutubeSabrSession session);
    }

    /**
     * Installs a process-wide observer used by the SABR instrumentation benchmark.
     * Production playback must leave this unset.
     */
    public static void setBenchmarkSessionObserver(@Nullable final SessionObserver observer) {
        benchmarkSessionObserver = observer;
    }

    @NonNull
    public static SabrSourceSpec createSourceSpec(@NonNull final String videoId,
                                                  final int preferredVideoItag,
                                                  @NonNull final List<AudioStream> audioStreams,
                                                  @Nullable final YoutubeSabrInfo extractorInfo)
            throws IOException, ExtractionException {
        PlaybackStartupTrace.markForVideoId(videoId, "sabr_source_spec_started");
        if (!isUsableExtractorInfo(extractorInfo, videoId)) {
            throw new IOException("SABR extractor info is missing for " + videoId);
        }
        final YoutubeSabrInfo info = Objects.requireNonNull(extractorInfo);
        final AudioSelection audio = selectAudioGroup(App.getApp(), info, audioStreams);
        final YoutubeSabrInfo.Format preferredVideo = pickVideoFormat(info, preferredVideoItag);
        if (audio == null || preferredVideo == null) {
            throw new IOException("Could not select SABR formats for " + videoId);
        }
        final List<YoutubeSabrInfo.Format> videoFormats =
                Collections.singletonList(preferredVideo);
        PlaybackStartupTrace.markForVideoId(videoId, "sabr_source_spec_ready");
        return new SabrSourceSpec(videoId, info,
                audio.bootstrapFormat, audio.formats, videoFormats, preferredVideo,
                null, null, null, null, Collections.emptyList());
    }

    @NonNull
    static YoutubeSabrSession getOrCreateSession(@NonNull final Context context,
                                                 @NonNull final SabrSourceSpec spec)
            throws IOException, ExtractionException {
        final File spool = new File(context.getCacheDir(),
                "sabr-segments/" + spec.getVideoId() + '-' + System.nanoTime());
        final YoutubeSabrSession created = new YoutubeSabrSession(spec.getInfo(), spool);
        final byte[] resolvedToken = spec.getInfo().getPoToken();
        if (resolvedToken == null || resolvedToken.length == 0) {
            throw new SabrLogicException("SABR PO token provider returned no token for video="
                    + spec.getVideoId());
        }
        created.setPoToken(resolvedToken);
        final SessionObserver observer = benchmarkSessionObserver;
        if (observer != null) {
            observer.onSessionCreated(created);
        }
        return created;
    }

    private static boolean isUsableExtractorInfo(@Nullable final YoutubeSabrInfo info,
                                                 @NonNull final String videoId) {
        return info != null && videoId.equals(info.getVideoId())
                && info.getServerAbrStreamingUrl() != null
                && !info.getServerAbrStreamingUrl().isEmpty() && !info.getFormats().isEmpty();
    }

    @Nullable
    private static AudioSelection selectAudioGroup(@NonNull final Context context,
                                                    @NonNull final YoutubeSabrInfo info,
                                                    @NonNull final List<AudioStream> streams) {
        final List<AudioStream> candidates = new ArrayList<>();
        for (final AudioStream stream : streams) {
            if (stream.getDeliveryMethod() == DeliveryMethod.SABR
                    && stream.getDeliveryMethodInfo() instanceof YoutubeSabrInfo
                    && info.getVideoId().equals(((YoutubeSabrInfo)
                    stream.getDeliveryMethodInfo()).getVideoId())) {
                candidates.add(stream);
            }
        }
        final int selectedIndex = ListHelper.getDefaultAudioFormat(context, candidates);
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) return null;
        final AudioStream selected = candidates.get(selectedIndex);
        final String selectedCodec = codecGroup(selected);
        final List<YoutubeSabrInfo.Format> formats = new ArrayList<>();
        for (final AudioStream stream : candidates) {
            if (!selectedCodec.equals(codecGroup(stream))) continue;
            final YoutubeSabrInfo.Format format = findAudioFormat(info, stream);
            if (format != null && !formats.contains(format)) formats.add(format);
        }
        final YoutubeSabrInfo.Format bootstrap = findAudioFormat(info, selected);
        if (bootstrap == null || formats.isEmpty()) return null;
        formats.sort(Comparator
                .comparingInt((YoutubeSabrInfo.Format format) ->
                        Objects.equals(format.getAudioTrackId(), bootstrap.getAudioTrackId())
                                ? 0 : 1)
                .thenComparing(format ->
                        Objects.toString(format.getAudioTrackDisplayName(), ""))
                .thenComparingInt(YoutubeSabrInfo.Format::getBitrate));
        return new AudioSelection(bootstrap, formats);
    }

    @Nullable
    private static YoutubeSabrInfo.Format findAudioFormat(
            @NonNull final YoutubeSabrInfo info, @NonNull final AudioStream stream) {
        for (final YoutubeSabrInfo.Format format : info.getFormats()) {
            if (format.isAudio() && format.getItag() == stream.getItag()
                    && Objects.equals(format.getAudioTrackId(), stream.getAudioTrackId())) {
                return format;
            }
        }
        return null;
    }

    @NonNull
    private static String codecGroup(@NonNull final AudioStream stream) {
        final String codec = stream.getCodec();
        if (codec == null || codec.isEmpty()) {
            return Objects.toString(stream.getFormat(), "unknown");
        }
        final int separator = codec.indexOf('.');
        return (separator < 0 ? codec : codec.substring(0, separator))
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static final class AudioSelection {
        @NonNull private final YoutubeSabrInfo.Format bootstrapFormat;
        @NonNull private final List<YoutubeSabrInfo.Format> formats;

        AudioSelection(@NonNull final YoutubeSabrInfo.Format bootstrapFormat,
                       @NonNull final List<YoutubeSabrInfo.Format> formats) {
            this.bootstrapFormat = bootstrapFormat;
            this.formats = formats;
        }
    }

    private static YoutubeSabrInfo.Format pickVideoFormat(@NonNull final YoutubeSabrInfo info,
                                                           final int preferredItag) {
        for (final YoutubeSabrInfo.Format format : info.getFormats()) {
            if (format.isVideo() && format.getItag() == preferredItag) return format;
        }
        YoutubeSabrInfo.Format lowest = null;
        for (final YoutubeSabrInfo.Format format : info.getFormats()) {
            if (!format.isVideo()) continue;
            if (lowest == null || format.getHeight() < lowest.getHeight()
                    || format.getHeight() == lowest.getHeight()
                    && format.getBitrate() < lowest.getBitrate()) {
                lowest = format;
            }
        }
        return lowest;
    }

}
