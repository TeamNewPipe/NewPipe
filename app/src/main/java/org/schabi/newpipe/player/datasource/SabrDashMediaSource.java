package org.schabi.newpipe.player.datasource;

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.CompositeMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline;
import org.schabi.newpipe.player.helper.PlayerDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public final class SabrDashMediaSource extends CompositeMediaSource<Integer> {
    private static final String TAG = "SabrDashMediaSource";
    private static final long SEEK_FORWARD_SYNC_TOLERANCE_US = 2_000_000L;
    private static final long START_POSITION_FORWARD_SNAP_US = 500_000L;
    private static final long END_SEEK_BACKOFF_US = 1_000L;

    private final MediaItem mediaItem;
    private final Context context;
    private final SabrSourceSpec spec;
    private final YoutubeSabrSession session;
    @Nullable private SabrMediaBridge bridge;
    private final long durationUs;
    private final DashMediaSource childSource;
    public SabrDashMediaSource(@NonNull final Context context,
                               @NonNull final MediaItem mediaItem,
                               @NonNull final SabrSourceSpec spec,
                               @NonNull final PlayerDataSource playerDataSource,
                               final long initialPositionMs) throws IOException {
        this.context = context.getApplicationContext();
        this.mediaItem = mediaItem;
        this.spec = spec;
        try {
            session = SabrSessionHelper.getOrCreateSession(context, spec);
        } catch (final ExtractionException e) {
            throw new IOException("Could not create SABR session for " + spec.getVideoId(), e);
        }
        try {
            final long durationMs = spec.getDurationMs();
            this.durationUs = durationMs > 0 ? durationMs * 1000L : C.TIME_UNSET;
            final SabrMediaBridge preparationBridge = getOrCreateBridge();
            if (!preparationBridge.hasTimelines()) {
                try {
                    preparationBridge.prepareTimelines(initialPositionMs);
                } catch (final ExtractionException error) {
                    throw new IOException("Could not prepare SABR fragments", error);
                }
                if (!preparationBridge.hasTimelines()) {
                    throw new IOException("SABR fragments did not provide initialization");
                }
            }
            final DataSource.Factory sabrDataSourceFactory =
                    playerDataSource.getCacheDataSourceFactory(
                            this::createDataSource, this::buildCacheKey);
            final DashManifest manifest = buildManifest(spec, durationMs, preparationBridge);
            this.childSource = new DashMediaSource.Factory(
                    new DefaultDashChunkSource.Factory(sabrDataSourceFactory),
                    /* manifestDataSourceFactory= */ null)
                    .setLoadErrorHandlingPolicy(new SabrLoadErrorHandlingPolicy())
                    .createMediaSource(manifest, mediaItem);
            Log.d(TAG, "create source video=" + spec.getVideoId()
                    + " videoItag=" + spec.getBootstrapVideoFormat().getItag()
                    + " bootstrapAudioItag=" + spec.getBootstrapAudioFormat().getItag()
                    + " initialPositionMs=" + initialPositionMs);
        } catch (final IOException | RuntimeException | Error e) {
            throw e;
        }
    }

    @NonNull
    @Override
    public MediaItem getMediaItem() {
        return mediaItem;
    }

    int getMaxStreamProtectionStatus() {
        return session.getMaxStreamProtectionStatus();
    }

    @Override
    protected void prepareSourceInternal(@Nullable final TransferListener mediaTransferListener) {
        getOrCreateBridge();
        super.prepareSourceInternal(mediaTransferListener);
        prepareChildSource(0, childSource);
    }

    @Override
    protected void onChildSourceInfoRefreshed(final Integer id,
                                              final MediaSource mediaSource,
                                              final Timeline timeline) {
        refreshSourceInfo(timeline);
    }

    @Override
    public MediaPeriod createPeriod(final MediaPeriodId id, final Allocator allocator,
                                    final long startPositionUs) {
        final MediaPeriod child = childSource.createPeriod(id, allocator, startPositionUs);
        final SabrDashMediaPeriod period = new SabrDashMediaPeriod(child);
        Log.d(TAG, "createPeriod video=" + spec.getVideoId()
                + " startUs=" + startPositionUs);
        return period;
    }

    @Override
    public void releasePeriod(final MediaPeriod mediaPeriod) {
        Log.d(TAG, "releasePeriod video=" + spec.getVideoId());
        final SabrDashMediaPeriod period = (SabrDashMediaPeriod) mediaPeriod;
        period.release();
        childSource.releasePeriod(period.child);
    }

    @Override
    protected void releaseSourceInternal() {
        Log.d(TAG, "release source video=" + spec.getVideoId());
        final SabrMediaBridge bridgeToStop;
        synchronized (this) {
            bridgeToStop = bridge;
            bridge = null;
        }
        if (bridgeToStop != null) bridgeToStop.stop();
    }

    @NonNull
    private DataSource createDataSource() {
        return new SabrSegmentDataSource(spec, getOrCreateBridge());
    }

    @NonNull
    private synchronized SabrMediaBridge getOrCreateBridge() {
        if (bridge == null) {
            bridge = new SabrMediaBridge(context, session, spec);
            bridge.seedSegments(spec.takeBootstrapMediaSegments());
        }
        return bridge;
    }

    @NonNull
    private String buildCacheKey(@NonNull final DataSpec dataSpec) {
        try {
            return SabrSegmentDataSource.requestFromUri(spec, dataSpec.uri)
                    .getCacheKey(spec.getVideoId());
        } catch (final IOException error) {
            throw new IllegalArgumentException("Bad SABR cache URI: " + dataSpec.uri, error);
        }
    }

    private static DashManifest buildManifest(final SabrSourceSpec spec,
                                              final long durationMs,
                                              final SabrMediaBridge bridge)
            throws IOException {
        final String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" "
                + "profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" "
                + "minBufferTime=\"PT1.5S\" mediaPresentationDuration=\""
                + formatDuration(durationMs) + "\">"
                + "<Period id=\"0\" start=\"PT0S\">"
                + videoAdaptationSets(spec, bridge)
                + audioAdaptationSets(spec, bridge)
                + "</Period></MPD>";
        try {
            return new DashManifestParser().parse(Uri.parse("sabr://" + spec.getVideoId()),
                    new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8)));
        } catch (final IOException e) {
            throw new IOException("Error when parsing generated SABR DASH manifest", e);
        }
    }

    private static String audioAdaptationSets(final SabrSourceSpec spec,
                                              final SabrMediaBridge bridge) {
        final Map<String, List<YoutubeSabrInfo.Format>> tracks = new LinkedHashMap<>();
        for (final YoutubeSabrInfo.Format format : spec.getAudioFormats()) {
            tracks.computeIfAbsent(java.util.Objects.toString(format.getAudioTrackId(), "default"),
                    ignored -> new ArrayList<>()).add(format);
        }
        final StringBuilder result = new StringBuilder();
        int index = 0;
        for (final Map.Entry<String, List<YoutubeSabrInfo.Format>> track : tracks.entrySet()) {
                result.append(adaptationSet(spec, bridge, track.getValue(), C.TRACK_TYPE_AUDIO,
                        String.valueOf(++index)));
        }
        return result.toString();
    }

    private static String videoAdaptationSets(final SabrSourceSpec spec,
                                              final SabrMediaBridge bridge) {
        return adaptationSet(spec, bridge, spec.getVideoFormats(), C.TRACK_TYPE_VIDEO, "0");
    }

    private static String adaptationSet(final SabrSourceSpec spec,
                                        final SabrMediaBridge bridge,
                                        final List<YoutubeSabrInfo.Format> formats,
                                        final int trackType,
                                        final String adaptationId) {
        final YoutubeSabrInfo.Format first = formats.get(0);
        final String mime = containerMimeType(first);
        final String contentType = trackType == C.TRACK_TYPE_AUDIO ? "audio" : "video";
        final StringBuilder builder = new StringBuilder()
                .append("<AdaptationSet id=\"").append(xml(adaptationId))
                .append("\" contentType=\"").append(contentType)
                .append("\" mimeType=\"").append(xml(mime))
                .append("\" segmentAlignment=\"true\" startWithSAP=\"1\"");
        if (trackType == C.TRACK_TYPE_AUDIO) {
            final String language = audioLanguage(first);
            if (language != null) builder.append(" lang=\"").append(xml(language)).append("\"");
            final String label = first.getAudioTrackDisplayName();
            builder.append('>');
            if (label != null && !label.isEmpty()) {
                builder.append("<Label>").append(xml(label)).append("</Label>");
            }
            if (first.isOriginalAudio()) {
                builder.append("<Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>");
            }
        } else {
            builder.append('>');
        }
        for (final YoutubeSabrInfo.Format format : formats) {
            builder.append("<Representation id=\"").append(spec.getFormatKey(format))
                    .append("\" bandwidth=\"").append(Math.max(1, format.getBitrate()))
                    .append("\"");
            final String codecs = codecs(format);
            if (codecs != null && !codecs.isEmpty()) {
                builder.append(" codecs=\"").append(xml(codecs)).append("\"");
            }
            if (trackType == C.TRACK_TYPE_VIDEO) {
                builder.append(" width=\"").append(Math.max(1, format.getWidth()))
                        .append("\" height=\"").append(Math.max(1, format.getHeight()))
                        .append("\"");
            } else {
                builder.append(" audioSamplingRate=\"48000\"");
            }
            builder.append("><BaseURL>sabrseg://").append(spec.getFormatKey(format))
                    .append("/</BaseURL>")
                    .append(segmentTemplate(format, bridge.getTimeline(format)))
                    .append("</Representation>");
        }
        builder.append("</AdaptationSet>");
        return builder.toString();
    }

    @Nullable
    private static String audioLanguage(final YoutubeSabrInfo.Format format) {
        final String trackId = format.getAudioTrackId();
        if (trackId == null || trackId.isEmpty()) return null;
        return trackId.split("[._-]", 2)[0];
    }

    private static String segmentTemplate(final YoutubeSabrInfo.Format format,
                                          final YoutubeSabrFormatTimeline timeline) {
        final long endSegment = timeline.getEndSequence();
        if (endSegment <= 0 || endSegment > 10_000) {
            throw new IllegalStateException("Invalid exact SABR segment count: itag="
                    + format.getItag() + ", count=" + endSegment);
        }
        final StringBuilder builder = new StringBuilder()
                .append("<SegmentTemplate timescale=\"1000\" startNumber=\"1\" ")
                .append("initialization=\"init\" media=\"$Number$\">")
                .append("<SegmentTimeline>");
        for (int sequence = 1; sequence <= endSegment; sequence++) {
            final long startMs = timeline.getStartMs(sequence);
            final long endMs = timeline.getEndMs(sequence);
            final long durationMs = Math.max(1, endMs - startMs);
            builder.append("<S t=\"").append(Math.max(0, startMs))
                    .append("\" d=\"").append(durationMs).append("\"/>");
        }
        return builder.append("</SegmentTimeline></SegmentTemplate>").toString();
    }

    private static String formatDuration(final long durationMs) {
        final long safeDurationMs = Math.max(1, durationMs);
        return "PT" + (safeDurationMs / 1000) + "."
                + String.format(java.util.Locale.US, "%03d", safeDurationMs % 1000) + "S";
    }

    private static String containerMimeType(final YoutubeSabrInfo.Format format) {
        final String mime = format.getMimeType();
        if (mime == null || mime.isEmpty()) {
            return format.isAudio() ? MimeTypes.AUDIO_MP4 : MimeTypes.VIDEO_MP4;
        }
        final int semicolon = mime.indexOf(';');
        return semicolon >= 0 ? mime.substring(0, semicolon).trim() : mime.trim();
    }

    @Nullable
    private static String codecs(final YoutubeSabrInfo.Format format) {
        final String mime = format.getMimeType();
        if (mime == null) {
            return null;
        }
        final int start = mime.indexOf("codecs=");
        if (start < 0) {
            return null;
        }
        return mime.substring(start + "codecs=".length()).replace("\"", "").trim();
    }

    private static String xml(final String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private final class SabrDashMediaPeriod implements MediaPeriod {
        private final MediaPeriod child;
        @Nullable
        private Callback callback;
        private long preparedPositionUs = C.TIME_UNSET;
        private boolean initialPositionApplied;

        SabrDashMediaPeriod(final MediaPeriod child) {
            this.child = child;
        }

        @Override
        public void prepare(final Callback cb, final long positionUs) {
            this.callback = cb;
            this.preparedPositionUs = positionUs;
            child.prepare(new Callback() {
                @Override
                public void onPrepared(final MediaPeriod mediaPeriod) {
                    cb.onPrepared(SabrDashMediaPeriod.this);
                }

                @Override
                public void onContinueLoadingRequested(final MediaPeriod source) {
                    cb.onContinueLoadingRequested(SabrDashMediaPeriod.this);
                }
            }, positionUs);
        }

        @Override
        public void maybeThrowPrepareError() throws IOException {
            child.maybeThrowPrepareError();
        }

        @Override
        public TrackGroupArray getTrackGroups() {
            return child.getTrackGroups();
        }

        @Override
        public List<StreamKey> getStreamKeys(final List<ExoTrackSelection> trackSelections) {
            return child.getStreamKeys(trackSelections);
        }

        @Override
        public long selectTracks(final ExoTrackSelection[] selections,
                                 final boolean[] mayRetainStreamFlags,
                                 final SampleStream[] streams,
                                 final boolean[] streamResetFlags,
                                 final long positionUs) {
            final boolean hasActiveTracks = updateActiveTracks(selections);
            // Initial mid-starts near the next video boundary are cheaper if SABR starts on that
            // boundary; keep regular seeks on Media3's requested position/tolerance path.
            final long normalizedPositionUs = initialPositionApplied || !hasActiveTracks
                    ? normalizeSeekPositionUs(positionUs)
                    : normalizeInitialStartPositionUs(positionUs);
            applyInitialStartPosition(normalizedPositionUs, hasActiveTracks);
            return child.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags,
                    normalizedPositionUs);
        }

        private boolean updateActiveTracks(final ExoTrackSelection[] selections) {
            boolean videoActive = false;
            boolean audioActive = false;
            YoutubeSabrInfo.Format currentVideo = null;
            YoutubeSabrInfo.Format currentAudio = null;
            for (final ExoTrackSelection selection : selections) {
                if (selection == null) {
                    continue;
                }
                final Format format = selection.getSelectedFormat();
                if (format != null) {
                    final YoutubeSabrInfo.Format selected = spec.getFormat(format.id);
                    if (selected != null && selected.isVideo()) {
                        videoActive = true;
                        currentVideo = selected;
                    } else if (selected != null) {
                        audioActive = true;
                        currentAudio = selected;
                    }
                }
            }
            Log.d(TAG, "activeTracks video=" + spec.getVideoId()
                    + " video=" + videoActive + " audio=" + audioActive);
            getOrCreateBridge().setSelection(currentAudio, currentVideo,
                    audioActive, videoActive);
            return videoActive || audioActive;
        }

        private void applyInitialStartPosition(final long positionUs,
                                               final boolean hasActiveTracks) {
            if (initialPositionApplied || !hasActiveTracks) {
                return;
            }
            initialPositionApplied = true;
            final long targetUs = Math.max(validPositionUs(preparedPositionUs),
                    validPositionUs(positionUs));
            if (targetUs <= 0) {
                return;
            }
            final long normalizedTargetUs = normalizeSeekPositionUs(targetUs);
            Log.d(TAG, "initialStart video=" + spec.getVideoId()
                    + " positionUs=" + normalizedTargetUs);
        }

        private long validPositionUs(final long positionUs) {
            return positionUs == C.TIME_UNSET ? 0 : Math.max(0, positionUs);
        }

        @Override
        public void discardBuffer(final long positionUs, final boolean toKeyframe) {
            child.discardBuffer(positionUs, toKeyframe);
        }

        @Override
        public long readDiscontinuity() {
            return child.readDiscontinuity();
        }

        @Override
        public long seekToUs(final long positionUs) {
            final long normalizedPositionUs = normalizeSeekPositionUs(positionUs);
            return child.seekToUs(normalizedPositionUs);
        }

        @Override
        public long getAdjustedSeekPositionUs(final long positionUs,
                                              final SeekParameters seekParameters) {
            final long normalizedPositionUs = normalizeSeekPositionUs(positionUs);
            return child.getAdjustedSeekPositionUs(
                    adjustSeekForwardToNearSegmentBoundary(normalizedPositionUs, seekParameters),
                    seekParameters);
        }

        private long normalizeSeekPositionUs(final long positionUs) {
            final long normalizedPositionUs = Math.max(0, positionUs);
            if (durationUs == C.TIME_UNSET || durationUs <= 0
                    || normalizedPositionUs < durationUs) {
                return normalizedPositionUs;
            }
            return Math.max(0, durationUs - END_SEEK_BACKOFF_US);
        }

        private long normalizeInitialStartPositionUs(final long positionUs) {
            return snapForwardToNearSegmentBoundary(normalizeSeekPositionUs(positionUs),
                    START_POSITION_FORWARD_SNAP_US);
        }

        private long adjustSeekForwardToNearSegmentBoundary(final long positionUs,
                                                           final SeekParameters seekParameters) {
            if (seekParameters.toleranceAfterUs <= 0) {
                return positionUs;
            }
            return snapForwardToNearSegmentBoundary(positionUs, Math.min(
                    SEEK_FORWARD_SYNC_TOLERANCE_US, seekParameters.toleranceAfterUs));
        }

        private long snapForwardToNearSegmentBoundary(final long positionUs,
                                                      final long toleranceUs) {
            if (toleranceUs <= 0) {
                return positionUs;
            }
            final long positionMs = Math.max(0, positionUs / 1000L);
            final YoutubeSabrFormatTimeline timeline = getOrCreateBridge().getTimeline(
                    spec.getBootstrapVideoFormat());
            final int currentSequence = timeline.getSequenceAt(positionMs);
            final long nextStartMs = timeline.getStartMs(currentSequence + 1);
            final long nextStartUs = nextStartMs * 1000L;
            if (nextStartUs > positionUs
                    && nextStartUs - positionUs <= toleranceUs) {
                return nextStartUs;
            }
            return positionUs;
        }

        @Override
        public long getBufferedPositionUs() {
            return child.getBufferedPositionUs();
        }

        @Override
        public long getNextLoadPositionUs() {
            return child.getNextLoadPositionUs();
        }

        @Override
        public boolean continueLoading(final long positionUs) {
            return child.continueLoading(positionUs);
        }

        @Override
        public boolean isLoading() {
            return child.isLoading();
        }

        @Override
        public void reevaluateBuffer(final long positionUs) {
            child.reevaluateBuffer(positionUs);
        }

        private void release() {
            if (callback != null) {
                callback = null;
            }
        }
    }

}
