package org.schabi.newpipe.player.datasource;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;

import java.util.Objects;

/** Identifies one initialization or media segment requested by Media3. */
final class SabrSegmentKey {
    @NonNull private final YoutubeSabrInfo.Format format;
    private final boolean initialization;
    private final int sequenceNumber;

    private SabrSegmentKey(@NonNull final YoutubeSabrInfo.Format format,
                           final boolean initialization,
                           final int sequenceNumber) {
        this.format = format;
        this.initialization = initialization;
        this.sequenceNumber = sequenceNumber;
    }

    static SabrSegmentKey initialization(@NonNull final YoutubeSabrInfo.Format format) {
        return new SabrSegmentKey(format, true, -1);
    }

    static SabrSegmentKey media(@NonNull final YoutubeSabrInfo.Format format,
                                final int sequenceNumber) {
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("SABR media sequence number must be positive");
        }
        return new SabrSegmentKey(format, false, sequenceNumber);
    }

    @NonNull YoutubeSabrInfo.Format getFormat() { return format; }
    boolean isInitialization() { return initialization; }
    int getSequenceNumber() { return sequenceNumber; }

    @NonNull
    String getCacheKey(@NonNull final String videoId) {
        final String xtags = Objects.toString(format.getXtags(), "");
        return "sabr:" + videoId + ':' + format.getItag() + ':' + format.getLastModified()
                + ':' + xtags.length() + ':' + xtags + ':'
                + (initialization ? "init" : sequenceNumber);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (!(other instanceof SabrSegmentKey)) return false;
        final SabrSegmentKey that = (SabrSegmentKey) other;
        return format.getItag() == that.format.getItag()
                && format.getLastModified() == that.format.getLastModified()
                && Objects.equals(format.getXtags(), that.format.getXtags())
                && initialization == that.initialization
                && sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(format.getItag(), format.getLastModified(), format.getXtags(),
                initialization, sequenceNumber);
    }
}
