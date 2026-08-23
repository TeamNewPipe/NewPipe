package org.schabi.newpipe.player.datasource;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** Serves Media3's exact format/sequence demand from SABR responses. */
public final class SabrSegmentDataSource implements DataSource {
    private static final String TAG = "SabrSegmentDataSource";
    private final SabrSourceSpec spec;
    private final SabrMediaBridge bridge;

    @Nullable private Uri uri;
    @Nullable private byte[] data;
    @Nullable private InputStream dataStream;
    @Nullable private SabrSegmentKey openedRequest;
    private long bytesRemaining;
    private int position;

    SabrSegmentDataSource(final SabrSourceSpec spec,
                          final SabrMediaBridge bridge) {
        this.spec = spec;
        this.bridge = bridge;
    }

    @Override
    public void addTransferListener(final TransferListener transferListener) {
        // Network transfer happens inside YoutubeSabrSession, not through this DataSource.
    }

    @Override
    public long open(final DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        closeDataStream();
        data = null;
        position = (int) Math.max(0, dataSpec.position);

        final SabrSegmentKey request = requestFromUri(spec, dataSpec.uri);
        openedRequest = request;
        final int totalBytes;
        final long available;
        if (request.isInitialization()) {
            data = initializationData(request.getFormat());
            totalBytes = data.length;
            available = Math.max(0, totalBytes - position);
        } else {
            SabrMediaSegment segment = awaitSegment(request);
            try {
                dataStream = segment.openStream();
            } catch (final FileNotFoundException error) {
                bridge.discard(request);
                segment = awaitSegment(request);
                dataStream = segment.openStream();
            }
            final long skipped = skipFully(dataStream, dataSpec.position);
            position = (int) Math.min(Integer.MAX_VALUE, skipped);
            totalBytes = segment.getLength();
            available = Math.max(0, totalBytes - skipped);
        }
        bytesRemaining = dataSpec.length == C.LENGTH_UNSET
                ? available : Math.min(dataSpec.length, available);
        Log.d(TAG, "open video=" + spec.getVideoId()
                + " itag=" + request.getFormat().getItag()
                + " seq=" + (request.isInitialization() ? "init" : request.getSequenceNumber())
                + " bytes=" + totalBytes);
        return bytesRemaining;
    }

    private byte[] initializationData(final YoutubeSabrInfo.Format format) throws IOException {
        final byte[] data = spec.getInitializationData(format);
        if (data == null) {
            throw new SabrLogicException("SABR initialization is missing after timeline "
                    + "preparation: itag=" + format.getItag());
        }
        return data;
    }

    @Override
    public int read(final byte[] target, final int offset, final int length) throws IOException {
        if (length == 0) return 0;
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT;
        if (data != null) {
            if (position >= data.length) return C.RESULT_END_OF_INPUT;
            final int count = (int) Math.min(Math.min(length, data.length - position),
                    bytesRemaining);
            System.arraycopy(data, position, target, offset, count);
            position += count;
            bytesRemaining -= count;
            return count;
        }
        if (dataStream == null) return C.RESULT_END_OF_INPUT;
        final int count = dataStream.read(target, offset, (int) Math.min(length, bytesRemaining));
        if (count < 0) {
            bytesRemaining = 0;
            return C.RESULT_END_OF_INPUT;
        }
        position += count;
        bytesRemaining -= count;
        return count;
    }

    static SabrSegmentKey requestFromUri(final SabrSourceSpec spec,
                                         final Uri value) throws IOException {
        final String host = value.getHost();
        final String segment = value.getLastPathSegment();
        if (host == null || segment == null) {
            throw new SabrLogicException("Bad SABR segment URI: " + value);
        }
        final YoutubeSabrInfo.Format format = spec.getFormat(host);
        if (format == null) throw new SabrLogicException("Unknown SABR format=" + host);
        if ("init".equals(segment)) return SabrSegmentKey.initialization(format);
        try {
            return SabrSegmentKey.media(format, Integer.parseInt(segment));
        } catch (final NumberFormatException error) {
            throw new SabrLogicException("Bad SABR sequence in URI: " + value, error);
        }
    }

    private SabrMediaSegment awaitSegment(final SabrSegmentKey request) throws IOException {
        if (request.getSequenceNumber()
                > bridge.getTimeline(request.getFormat()).getEndSequence()) {
            throw new SabrLogicException("SABR segment is beyond the timeline: itag="
                    + request.getFormat().getItag() + ", seq=" + request.getSequenceNumber());
        }
        try {
            return bridge.awaitSegment(request);
        } catch (final org.schabi.newpipe.extractor.exceptions.ExtractionException error) {
            throw new IOException("SABR segment extraction failed: " + error.getMessage(), error);
        }
    }

    private static long skipFully(final InputStream input, final long requested) throws IOException {
        long remaining = Math.max(0, requested);
        final byte[] buffer = new byte[8192];
        while (remaining > 0) {
            final long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                final int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                remaining -= read;
            }
        }
        return requested - remaining;
    }

    private void closeDataStream() throws IOException {
        if (dataStream != null) dataStream.close();
        dataStream = null;
    }

    @Nullable
    @Override
    public Uri getUri() { return uri; }

    @Override
    public void close() {
        data = null;
        try {
            closeDataStream();
        } catch (final IOException error) {
            Log.w(TAG, "Could not close SABR segment stream", error);
        }
        final SabrSegmentKey request = openedRequest;
        openedRequest = null;
        if (request != null && !request.isInitialization()) {
            bridge.discard(request);
        }
    }
}
