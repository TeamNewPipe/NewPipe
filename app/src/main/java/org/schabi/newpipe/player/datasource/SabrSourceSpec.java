package org.schabi.newpipe.player.datasource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Source metadata for one selected video format and one Media3-selectable audio codec group. */
public final class SabrSourceSpec {
    @NonNull private final String videoId;
    @NonNull private final YoutubeSabrInfo info;
    @NonNull private final YoutubeSabrInfo.Format bootstrapAudioFormat;
    @NonNull private final List<YoutubeSabrInfo.Format> audioFormats;
    @NonNull private final List<YoutubeSabrInfo.Format> videoFormats;
    @NonNull private final YoutubeSabrInfo.Format bootstrapVideoFormat;
    @NonNull private final Map<String, YoutubeSabrInfo.Format> formatsByKey;
    @NonNull private final Map<YoutubeSabrInfo.Format, String> keysByFormat;
    @NonNull private final Map<YoutubeSabrInfo.Format, byte[]> initializationData =
            new ConcurrentHashMap<>();
    @NonNull private final AtomicReference<List<SabrMediaSegment>> bootstrapMediaSegments;

    SabrSourceSpec(@NonNull final String videoId,
                   @NonNull final YoutubeSabrInfo info,
                   @NonNull final YoutubeSabrInfo.Format bootstrapAudioFormat,
                   @NonNull final List<YoutubeSabrInfo.Format> audioFormats,
                   @NonNull final List<YoutubeSabrInfo.Format> videoFormats,
                   @NonNull final YoutubeSabrInfo.Format bootstrapVideoFormat,
                   @Nullable final byte[] audioInitializationData,
                   @Nullable final byte[] videoInitializationData,
                   @Nullable final YoutubeSabrFormatTimeline audioTimeline,
                   @Nullable final YoutubeSabrFormatTimeline videoTimeline,
                   @NonNull final List<SabrMediaSegment> bootstrapMediaSegments) {
        if (audioFormats.isEmpty() || !audioFormats.contains(bootstrapAudioFormat)) {
            throw new IllegalArgumentException("SABR audio codec group is empty");
        }
        this.videoId = videoId;
        this.info = info;
        this.bootstrapAudioFormat = bootstrapAudioFormat;
        this.audioFormats = Collections.unmodifiableList(new ArrayList<>(audioFormats));
        if (videoFormats.isEmpty() || !videoFormats.contains(bootstrapVideoFormat)) {
            throw new IllegalArgumentException("SABR video codec group is empty");
        }
        this.videoFormats = Collections.unmodifiableList(new ArrayList<>(videoFormats));
        this.bootstrapVideoFormat = bootstrapVideoFormat;
        final Map<String, YoutubeSabrInfo.Format> byKey = new LinkedHashMap<>();
        final Map<YoutubeSabrInfo.Format, String> byFormat = new ConcurrentHashMap<>();
        for (int i = 0; i < videoFormats.size(); i++) {
            final String key = "v" + i;
            byKey.put(key, videoFormats.get(i));
            byFormat.put(videoFormats.get(i), key);
        }
        for (int i = 0; i < audioFormats.size(); i++) {
            final String key = "a" + i;
            byKey.put(key, audioFormats.get(i));
            byFormat.put(audioFormats.get(i), key);
        }
        formatsByKey = Collections.unmodifiableMap(byKey);
        keysByFormat = Collections.unmodifiableMap(byFormat);
        this.bootstrapMediaSegments = new AtomicReference<>(bootstrapMediaSegments);
        if (audioInitializationData != null) putInitializationData(bootstrapAudioFormat,
                audioInitializationData);
        if (videoInitializationData != null) putInitializationData(bootstrapVideoFormat,
                videoInitializationData);
    }

    SabrSourceSpec(@NonNull final String videoId, @NonNull final YoutubeSabrInfo info,
                   @NonNull final YoutubeSabrInfo.Format audio,
                   @NonNull final List<YoutubeSabrInfo.Format> audios,
                   @NonNull final YoutubeSabrInfo.Format video,
                   @Nullable final byte[] audioInit, @Nullable final byte[] videoInit,
                   @Nullable final YoutubeSabrFormatTimeline audioTimeline,
                   @Nullable final YoutubeSabrFormatTimeline videoTimeline,
                   @NonNull final List<SabrMediaSegment> segments) {
        this(videoId, info, audio, audios, Collections.singletonList(video), video,
                audioInit, videoInit, audioTimeline, videoTimeline, segments);
    }

    @NonNull public String getVideoId() { return videoId; }
    @NonNull public YoutubeSabrInfo getInfo() { return info; }
    @NonNull
    public YoutubeSabrInfo.Format getBootstrapAudioFormat() {
        return bootstrapAudioFormat;
    }
    @NonNull public List<YoutubeSabrInfo.Format> getAudioFormats() { return audioFormats; }
    @NonNull public List<YoutubeSabrInfo.Format> getVideoFormats() { return videoFormats; }
    @NonNull public YoutubeSabrInfo.Format getBootstrapVideoFormat() { return bootstrapVideoFormat; }
    /** Compatibility accessor; callers needing a group must use getVideoFormats(). */
    @NonNull public YoutubeSabrInfo.Format getVideoFormat() { return bootstrapVideoFormat; }

    @Nullable YoutubeSabrInfo.Format getFormat(@NonNull final String key) {
        return formatsByKey.get(key);
    }

    @NonNull String getFormatKey(@NonNull final YoutubeSabrInfo.Format format) {
        final String key = keysByFormat.get(format);
        if (key == null) throw new IllegalArgumentException("Unknown SABR format");
        return key;
    }

    @Nullable
    byte[] getInitializationData(@NonNull final YoutubeSabrInfo.Format format) {
        final byte[] data = initializationData.get(format);
        return data == null ? null : data.clone();
    }

    void putInitializationData(@NonNull final YoutubeSabrInfo.Format format,
                               @NonNull final byte[] data) {
        initializationData.putIfAbsent(format, data.clone());
    }

    long getDurationMs() {
        return Math.max(bootstrapAudioFormat.getApproxDurationMs(),
                bootstrapVideoFormat.getApproxDurationMs());
    }

    @NonNull
    List<SabrMediaSegment> takeBootstrapMediaSegments() {
        return bootstrapMediaSegments.getAndSet(Collections.emptyList());
    }
}
