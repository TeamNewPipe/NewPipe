package us.shandian.giga.io;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author kapodamy
 */
public class FileStream extends SharpStream {

    public RandomAccessFile source;

    public FileStream(@NonNull File target) throws FileNotFoundException {
        this.source = new RandomAccessFile(target, "rw");
    }

    public FileStream(@NonNull String path) throws FileNotFoundException {
        this.source = new RandomAccessFile(path, "rw");
    }

    @Override
    public int read() throws IOException {
        return source.read();
    }

    @Override
    public int read(byte b[]) throws IOException {
        return source.read(b);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return source.read(b, off, len);
    }

    @Override
    public long skip(long pos) throws IOException {
        return source.skipBytes((int) pos);
    }

    @Override
    public long available() {
        try {
            return source.length() - source.getFilePointer();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public void close() {
        if (source == null) return;
        try {
            source.close();
        } catch (IOException err) {
            // nothing to do
        }
        source = null;
    }

    @Override
    public boolean isClosed() {
        return source == null;
    }

    @Override
    public void rewind() throws IOException {
        source.seek(0);
    }

    @Override
    public boolean canRewind() {
        return true;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public boolean canSetLength() {
        return true;
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
    public void setLength(long length) throws IOException {
        source.setLength(length);
    }

    @Override
    public void seek(long offset) throws IOException {
        source.seek(offset);
    }

    @Override
    public long length() throws IOException {
        return source.length();
    }
}
