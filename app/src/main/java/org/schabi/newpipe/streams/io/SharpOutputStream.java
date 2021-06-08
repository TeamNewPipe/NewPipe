package org.schabi.newpipe.streams.io;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Simply wraps a writable {@link SharpStream} allowing it to be used with built-in Java stuff that
 * supports {@link OutputStream}.
 */
public class SharpOutputStream extends OutputStream {
    private final SharpStream stream;

    public SharpOutputStream(final SharpStream stream) throws IOException {
        if (!stream.canWrite()) {
            throw new IOException("SharpStream is not writable");
        }
        this.stream = stream;
    }

    @Override
    public void write(final int b) throws IOException {
        stream.write((byte) b);
    }

    @Override
    public void write(@NonNull final byte[] b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(@NonNull final byte[] b, final int off, final int len) throws IOException {
        stream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() {
        stream.close();
    }
}
