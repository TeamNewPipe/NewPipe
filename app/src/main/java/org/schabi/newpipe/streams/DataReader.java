package org.schabi.newpipe.streams;

import java.io.EOFException;
import java.io.IOException;

import org.schabi.newpipe.streams.io.SharpStream;

/**
 * @author kapodamy
 */
public class DataReader {

    public final static int SHORT_SIZE = 2;
    public final static int LONG_SIZE = 8;
    public final static int INTEGER_SIZE = 4;
    public final static int FLOAT_SIZE = 4;

    private long pos;
    public final SharpStream stream;
    private final boolean rewind;

    public DataReader(SharpStream stream) {
        this.rewind = stream.canRewind();
        this.stream = stream;
        this.pos = 0L;
    }

    public long position() {
        return pos;
    }

    public final int readInt() throws IOException {
        primitiveRead(INTEGER_SIZE);
        return primitive[0] << 24 | primitive[1] << 16 | primitive[2] << 8 | primitive[3];
    }

    public final int read() throws IOException {
        int value = stream.read();
        if (value == -1) {
            throw new EOFException();
        }

        pos++;
        return value;
    }

    public final long skipBytes(long amount) throws IOException {
        amount = stream.skip(amount);
        pos += amount;
        return amount;
    }

    public final long readLong() throws IOException {
        primitiveRead(LONG_SIZE);
        long high = primitive[0] << 24 | primitive[1] << 16 | primitive[2] << 8 | primitive[3];
        long low = primitive[4] << 24 | primitive[5] << 16 | primitive[6] << 8 | primitive[7];
        return high << 32 | low;
    }

    public final short readShort() throws IOException {
        primitiveRead(SHORT_SIZE);
        return (short) (primitive[0] << 8 | primitive[1]);
    }

    public final int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    public final int read(byte[] buffer, int offset, int count) throws IOException {
        int res = stream.read(buffer, offset, count);
        pos += res;

        return res;
    }

    public final boolean available() {
        return stream.available() > 0;
    }

    public void rewind() throws IOException {
        stream.rewind();
        pos = 0;
    }

    public boolean canRewind() {
        return rewind;
    }

    private short[] primitive = new short[LONG_SIZE];

    private void primitiveRead(int amount) throws IOException {
        byte[] buffer = new byte[amount];
        int read = stream.read(buffer, 0, amount);
        pos += read;
        if (read != amount) {
            throw new EOFException("Truncated data, missing " + String.valueOf(amount - read) + " bytes");
        }

        for (int i = 0; i < buffer.length; i++) {
            primitive[i] = (short) (buffer[i] & 0xFF);// the "byte" datatype is signed and is very annoying
        }
    }
}
