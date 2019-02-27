package us.shandian.giga.postprocessing.io;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * @author kapodamy
 */
public class FileStream extends SharpStream {

    public enum Mode {
        Read,
        ReadWrite
    }

    public RandomAccessFile source;
    private final Mode mode;

    public FileStream(String path, Mode mode) throws IOException {
        String flags;

        if (mode == Mode.Read) {
            flags = "r";
        } else {
            flags = "rw";
        }

        this.mode = mode;
        source = new RandomAccessFile(path, flags);
    }

    @Override
    public int read() throws IOException {
        return source.read();
    }

    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return source.read(b, off, len);
    }

    @Override
    public long skip(long pos) throws IOException {
        FileChannel fc = source.getChannel();
        fc.position(fc.position() + pos);
        return pos;
    }

    @Override
    public int available() {
        try {
            return (int) (source.length() - source.getFilePointer());
        } catch (IOException ex) {
            return 0;
        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Override
    public void dispose() {
        try {
            source.close();
        } catch (IOException err) {

        } finally {
            source = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return source == null;
    }

    @Override
    public void rewind() throws IOException {
        source.getChannel().position(0);
    }

    @Override
    public boolean canRewind() {
        return true;
    }

    @Override
    public boolean canRead() {
        return mode == Mode.Read || mode == Mode.ReadWrite;
    }

    @Override
    public boolean canWrite() {
        return mode == Mode.ReadWrite;
    }

    @Override
    public void write(byte value) throws IOException {
        source.write(value);
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        source.write(buffer);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        source.write(buffer, offset, count);
    }

    @Override
    public void flush() {
    }

    @Override
    public void setLength(long length) throws IOException {
        source.setLength(length);
    }
}
