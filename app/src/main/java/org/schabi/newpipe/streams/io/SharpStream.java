package org.schabi.newpipe.streams.io;

import java.io.IOException;

/**
 * based c#
 */
public abstract class SharpStream {

    public abstract int read() throws IOException;

    public abstract int read(byte buffer[]) throws IOException;

    public abstract int read(byte buffer[], int offset, int count) throws IOException;

    public abstract long skip(long amount) throws IOException;


    public abstract int available();

    public abstract void rewind() throws IOException;


    public abstract void dispose();

    public abstract boolean isDisposed();


    public abstract boolean canRewind();

    public abstract boolean canRead();

    public abstract boolean canWrite();


    public abstract void write(byte value) throws IOException;

    public abstract void write(byte[] buffer) throws IOException;

    public abstract void write(byte[] buffer, int offset, int count) throws IOException;

    public abstract void flush() throws IOException;

    public void setLength(long length) throws IOException {
        throw new IOException("Not implemented");
    }
}
