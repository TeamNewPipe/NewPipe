package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author kapodamy
 */
public class DataReader {

    public final static int SHORT_SIZE = 2;
    public final static int LONG_SIZE = 8;
    public final static int INTEGER_SIZE = 4;
    public final static int FLOAT_SIZE = 4;

    private final static int BUFFER_SIZE = 128 * 1024;// 128 KiB

    private long position = 0;
    private final SharpStream stream;

    private InputStream view;
    private int viewSize;

    public DataReader(SharpStream stream) {
        this.stream = stream;
        this.readOffset = this.readBuffer.length;
    }

    public long position() {
        return position;
    }

    public int read() throws IOException {
        if (fillBuffer()) {
            return -1;
        }

        position++;
        readCount--;

        return readBuffer[readOffset++] & 0xFF;
    }

    public long skipBytes(long amount) throws IOException {
        if (readCount < 0) {
            return 0;
        } else if (readCount == 0) {
            amount = stream.skip(amount);
        } else {
            if (readCount > amount) {
                readCount -= (int) amount;
                readOffset += (int) amount;
            } else {
                amount = readCount + stream.skip(amount - readCount);
                readCount = 0;
                readOffset = readBuffer.length;
            }
        }

        position += amount;
        return amount;
    }

    public int readInt() throws IOException {
        primitiveRead(INTEGER_SIZE);
        return primitive[0] << 24 | primitive[1] << 16 | primitive[2] << 8 | primitive[3];
    }

    public short readShort() throws IOException {
        primitiveRead(SHORT_SIZE);
        return (short) (primitive[0] << 8 | primitive[1]);
    }

    public long readLong() throws IOException {
        primitiveRead(LONG_SIZE);
        long high = primitive[0] << 24 | primitive[1] << 16 | primitive[2] << 8 | primitive[3];
        long low = primitive[4] << 24 | primitive[5] << 16 | primitive[6] << 8 | primitive[7];
        return high << 32 | low;
    }

    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    public int read(byte[] buffer, int offset, int count) throws IOException {
        if (readCount < 0) {
            return -1;
        }
        int total = 0;

        if (count >= readBuffer.length) {
            if (readCount > 0) {
                System.arraycopy(readBuffer, readOffset, buffer, offset, readCount);
                readOffset += readCount;

                offset += readCount;
                count -= readCount;

                total = readCount;
                readCount = 0;
            }
            total += Math.max(stream.read(buffer, offset, count), 0);
        } else {
            while (count > 0 && !fillBuffer()) {
                int read = Math.min(readCount, count);
                System.arraycopy(readBuffer, readOffset, buffer, offset, read);

                readOffset += read;
                readCount -= read;

                offset += read;
                count -= read;

                total += read;
            }
        }

        position += total;
        return total;
    }

    public boolean available() {
        return readCount > 0 || stream.available() > 0;
    }

    public void rewind() throws IOException {
        stream.rewind();

        if ((position - viewSize) > 0) {
            viewSize = 0;// drop view
        } else {
            viewSize += position;
        }

        position = 0;
        readOffset = readBuffer.length;
    }

    public boolean canRewind() {
        return stream.canRewind();
    }

    /**
     * Wraps this instance of {@code DataReader} into {@code InputStream}
     * object. Note: Any read in the {@code DataReader} will not modify
     * (decrease) the view size
     *
     * @param size the size of the view
     * @return the view
     */
    public InputStream getView(int size) {
        if (view == null) {
            view = new InputStream() {
                @Override
                public int read() throws IOException {
                    if (viewSize < 1) {
                        return -1;
                    }
                    int res = DataReader.this.read();
                    if (res > 0) {
                        viewSize--;
                    }
                    return res;
                }

                @Override
                public int read(byte[] buffer) throws IOException {
                    return read(buffer, 0, buffer.length);
                }

                @Override
                public int read(byte[] buffer, int offset, int count) throws IOException {
                    if (viewSize < 1) {
                        return -1;
                    }

                    int res = DataReader.this.read(buffer, offset, Math.min(viewSize, count));
                    viewSize -= res;

                    return res;
                }

                @Override
                public long skip(long amount) throws IOException {
                    if (viewSize < 1) {
                        return 0;
                    }
                    int res = (int) DataReader.this.skipBytes(Math.min(amount, viewSize));
                    viewSize -= res;

                    return res;
                }

                @Override
                public int available() {
                    return viewSize;
                }

                @Override
                public void close() {
                    viewSize = 0;
                }

                @Override
                public boolean markSupported() {
                    return false;
                }

            };
        }
        viewSize = size;

        return view;
    }

    private final short[] primitive = new short[LONG_SIZE];

    private void primitiveRead(int amount) throws IOException {
        byte[] buffer = new byte[amount];
        int read = read(buffer, 0, amount);

        if (read != amount) {
            throw new EOFException("Truncated stream, missing " + String.valueOf(amount - read) + " bytes");
        }

        for (int i = 0; i < amount; i++) {
            primitive[i] = (short) (buffer[i] & 0xFF);// the "byte" data type in java is signed and is very annoying
        }
    }

    private final byte[] readBuffer = new byte[BUFFER_SIZE];
    private int readOffset;
    private int readCount;

    private boolean fillBuffer() throws IOException {
        if (readCount < 0) {
            return true;
        }
        if (readOffset >= readBuffer.length) {
            readCount = stream.read(readBuffer);
            if (readCount < 1) {
                readCount = -1;
                return true;
            }
            readOffset = 0;
        }

        return readCount < 1;
    }

}
