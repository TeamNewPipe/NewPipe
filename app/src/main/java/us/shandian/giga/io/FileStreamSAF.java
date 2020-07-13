package us.shandian.giga.io;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileStreamSAF extends SharpStream {

    private final FileInputStream in;
    private final FileOutputStream out;
    private final FileChannel channel;
    private final ParcelFileDescriptor file;

    private boolean disposed;

    public FileStreamSAF(@NonNull ContentResolver contentResolver, Uri fileUri) throws IOException {
        // Notes:
        // the file must exists first
        // ¡read-write mode must allow seek!
        // It is not guaranteed to work with files in the cloud (virtual files), tested in local storage devices

        file = contentResolver.openFileDescriptor(fileUri, "rw");

        if (file == null) {
            throw new IOException("Cannot get the ParcelFileDescriptor for " + fileUri.toString());
        }

        in = new FileInputStream(file.getFileDescriptor());
        out = new FileOutputStream(file.getFileDescriptor());
        channel = out.getChannel();// or use in.getChannel()
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return in.read(buffer);
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        return in.read(buffer, offset, count);
    }

    @Override
    public long skip(long amount) throws IOException {
        return in.skip(amount);// ¿or use channel.position(channel.position() + amount)?
    }

    @Override
    public long available() {
        try {
            return in.available();
        } catch (IOException e) {
            return 0;// ¡but not -1!
        }
    }

    @Override
    public void rewind() throws IOException {
        seek(0);
    }

    @Override
    public void close() {
        try {
            disposed = true;

            file.close();
            in.close();
            out.close();
            channel.close();
        } catch (IOException e) {
            Log.e("FileStreamSAF", "close() error", e);
        }
    }

    @Override
    public boolean isClosed() {
        return disposed;
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

    public boolean canSetLength() {
        return true;
    }

    public boolean canSeek() {
        return true;
    }

    @Override
    public void write(byte value) throws IOException {
        out.write(value);
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        out.write(buffer);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        out.write(buffer, offset, count);
    }

    public void setLength(long length) throws IOException {
        channel.truncate(length);
    }

    public void seek(long offset) throws IOException {
        channel.position(offset);
    }

    @Override
    public long length() throws IOException {
        return channel.size();
    }
}
