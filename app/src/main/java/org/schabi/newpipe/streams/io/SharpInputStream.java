package org.schabi.newpipe.streams.io;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simply wraps a readable {@link SharpStream} allowing it to be used with built-in Java stuff that
 * supports {@link InputStream}.
 */
public class SharpInputStream extends InputStream {
    private final SharpStream stream;

    public SharpInputStream(final SharpStream stream) throws IOException {
        if (!stream.canRead()) {
            throw new IOException("SharpStream is not readable");
        }
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(@NonNull final byte[] b) throws IOException {
        return stream.read(b);
    }

    @Override
    public int read(@NonNull final byte[] b, final int off, final int len) throws IOException {
        return stream.read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return stream.skip(n);
    }

    @Override
    public int available() {
        final long res = stream.available();
        return res > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) res;
    }

    @Override
    public void close() {
        stream.close();
    }
}
