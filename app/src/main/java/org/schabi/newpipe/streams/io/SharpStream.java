package org.schabi.newpipe.streams.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Based on C#'s Stream class. SharpStream is a wrapper around the 2 different APIs for SAF
 * ({@link us.shandian.giga.io.FileStreamSAF}) and non-SAF ({@link us.shandian.giga.io.FileStream}).
 * It has both input and output like in C#, while in Java those are usually different classes.
 * {@link SharpInputStream} and {@link SharpOutputStream} are simple classes that wrap
 * {@link SharpStream} and extend respectively {@link java.io.InputStream} and
 * {@link java.io.OutputStream}, since unfortunately a class can only extend one class, so that a
 * sharp stream can be used with built-in Java stuff that supports {@link java.io.InputStream}
 * or {@link java.io.OutputStream}.
 */
public abstract class SharpStream implements Closeable, Flushable {
    public abstract int read() throws IOException;

    public abstract int read(byte[] buffer) throws IOException;

    public abstract int read(byte[] buffer, int offset, int count) throws IOException;

    public abstract long skip(long amount) throws IOException;

    public abstract long available();

    public abstract void rewind() throws IOException;

    public abstract boolean isClosed();

    @Override
    public abstract void close();

    public abstract boolean canRewind();

    public abstract boolean canRead();

    public abstract boolean canWrite();

    public boolean canSetLength() {
        return false;
    }

    public boolean canSeek() {
        return false;
    }

    public abstract void write(byte value) throws IOException;

    public abstract void write(byte[] buffer) throws IOException;

    public abstract void write(byte[] buffer, int offset, int count) throws IOException;

    public void flush() throws IOException {
        // STUB
    }

    public void setLength(final long length) throws IOException {
        throw new IOException("Not implemented");
    }

    public void seek(final long offset) throws IOException {
        throw new IOException("Not implemented");
    }

    public long length() throws IOException {
        throw new UnsupportedOperationException("Unsupported operation");
    }
}
