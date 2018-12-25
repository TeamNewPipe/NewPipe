package org.schabi.newpipe.streams;

import java.io.InputStream;
import java.io.IOException;

public class TrackDataChunk extends InputStream {

    private final DataReader base;
    private int size;

    public TrackDataChunk(DataReader base, int size) {
        this.base = base;
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        if (size < 1) {
            return -1;
        }

        int res = base.read();

        if (res >= 0) {
            size--;
        }

        return res;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        count = Math.min(size, count);
        int read = base.read(buffer, offset, count);
        size -= count;
        return read;
    }

    @Override
    public long skip(long amount) throws IOException {
        long res = base.skipBytes(Math.min(amount, size));
        size -= res;
        return res;
    }

    @Override
    public int available() {
        return size;
    }

    @Override
    public void close() {
        size = 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
