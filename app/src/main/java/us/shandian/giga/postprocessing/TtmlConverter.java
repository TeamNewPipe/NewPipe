package us.shandian.giga.postprocessing;

import android.util.Log;

import org.schabi.newpipe.streams.SrtFromTtmlWriter;
import org.schabi.newpipe.streams.io.SharpStream;
import org.schabi.newpipe.util.subtitle.SubtitleDeduplicator;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author kapodamy
 */
class TtmlConverter extends Postprocessing {
    private static final String TAG = "TtmlConverter";

    TtmlConverter() {
        // due how XmlPullParser works, the xml is fully loaded on the ram
        super(false, true, ALGORITHM_TTML_CONVERTER);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        // check if the subtitle is already in srt and copy, this should never happen
        String format = getArgumentAt(0, null);
        boolean ignoreEmptyFrames = getArgumentAt(1, "true").equals("true");
        if (format == null || format.equals("ttml")) {
            SrtFromTtmlWriter writer = new SrtFromTtmlWriter(out, ignoreEmptyFrames);
            try {
                final String subtitleContent =
                        readSharpStreamToString(sources[0]);
                final String deduplicated =
                        SubtitleDeduplicator.deduplicateContent(subtitleContent);
                final SharpStream stream =
                        new ByteArraySharpStream(
                                deduplicated.getBytes(StandardCharsets.UTF_8));
                writer.build(stream);
            } catch (IOException err) {
                Log.e(TAG, "subtitle conversion failed due to I/O error", err);
                throw err;
            } catch (Exception err) {
                Log.e(TAG, "subtitle conversion failed", err);
                throw new IOException("TTML to SRT conversion failed", err);
            }

            return OK_RESULT;
        } else if (format.equals("srt")) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = sources[0].read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return OK_RESULT;
        }

        throw new UnsupportedOperationException("Can't convert this subtitle, unimplemented format: " + format);
    }

    private static String readSharpStreamToString(final SharpStream stream) throws IOException {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];

        int read;

        // Note: `> 0` is required here because ChunkFileInputStream.read()
        // returns 0 at EOF instead of -1. Using `!= -1` would result in
        // an infinite loop in that case.
        //
        // Standard Java InputStream.read() returns -1 at EOF.
        //
        // Reference implementation:
        // - ChunkFileInputStream.java
        //
        // Future note:
        // - If ChunkFileInputStream changes to return -1 at EOF, this loop
        //   can safely be switched back to `read != -1`. Keeping `> 0` is
        //   also safe and will continue to work.
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }

        final String result = out.toString(StandardCharsets.UTF_8);

        return result;
    }

    /**
     * Minimal SharpStream backed by a byte array.
     */
    private static final class ByteArraySharpStream extends SharpStream {
        private final ByteArrayInputStream in;

        ByteArraySharpStream(byte[] data) {
            this.in = new ByteArrayInputStream(data);
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(byte[] buffer) {
            return in.read(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int offset, int count) {
            return in.read(buffer, offset, count);
        }

        @Override
        public long skip(long amount) {
            return in.skip(amount);
        }

        @Override
        public long available() {
            return in.available();
        }

        @Override
        public void rewind() {
            in.reset();
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}

        @Override
        public boolean canRewind() { return true; }

        @Override
        public boolean canRead() { return true; }

        @Override
        public boolean canWrite() { return false; }

        @Override
        public void write(byte value) throws IOException {
            // This stream is read-only
            // and used only for reading subtitle data.
            throw new IOException("Stream is read-only");
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            throw new IOException("Stream is read-only");
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            throw new IOException("Stream is read-only");
        }
    }
}
