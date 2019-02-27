package us.shandian.giga.postprocessing.io;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class CircularFile extends SharpStream {

    private final static int AUX_BUFFER_SIZE = 1024 * 1024;// 1 MiB
    private final static int AUX_BUFFER_SIZE2 = 512 * 1024;// 512 KiB
    private final static int NOTIFY_BYTES_INTERVAL = 64 * 1024;// 64 KiB
    private final static int QUEUE_BUFFER_SIZE = 8 * 1024;// 8 KiB
    private final static boolean IMMEDIATE_AUX_BUFFER_FLUSH = false;

    private RandomAccessFile out;
    private long position;
    private long maxLengthKnown = -1;

    private ArrayList<ManagedBuffer> auxiliaryBuffers;
    private OffsetChecker callback;
    private ManagedBuffer queue;
    private long startOffset;
    private ProgressReport onProgress;
    private long reportPosition;

    public CircularFile(File file, long offset, ProgressReport progressReport, OffsetChecker checker) throws IOException {
        if (checker == null) {
            throw new NullPointerException("checker is null");
        }

        try {
            queue = new ManagedBuffer(QUEUE_BUFFER_SIZE);
            out = new RandomAccessFile(file, "rw");
            out.seek(offset);
            position = offset;
        } catch (IOException err) {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                // nothing to do
            }
            throw err;
        }

        auxiliaryBuffers = new ArrayList<>(15);
        callback = checker;
        startOffset = offset;
        reportPosition = offset;
        onProgress = progressReport;

    }

    /**
     * Close the file without flushing any buffer
     */
    @Override
    public void dispose() {
        try {
            auxiliaryBuffers = null;
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (IOException err) {
            // nothing to do
        }
    }

    /**
     * Flush any buffer and close the output file. Use this method if the
     * operation is successful
     *
     * @return the final length of the file
     * @throws IOException if an I/O error occurs
     */
    public long finalizeFile() throws IOException {
        flushEverything();

        if (maxLengthKnown > -1) {
            position = maxLengthKnown;
        }
        if (position < out.length()) {
            out.setLength(position);
        }

        dispose();

        return position;
    }

    @Override
    public void write(byte b) throws IOException {
        write(new byte[]{b}, 0, 1);
    }

    @Override
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        long end = callback.check();
        long available;

        if (end == -1) {
            available = Long.MAX_VALUE;
        } else {
            if (end < startOffset) {
                throw new IOException("The reported offset is invalid. reported offset is " + String.valueOf(end));
            }
            available = end - position;
        }

        // Check if possible flush one or more auxiliary buffer
        if (auxiliaryBuffers.size() > 0) {
            ManagedBuffer aux = auxiliaryBuffers.get(0);

            // check if there is enough space to flush it completely
            while (available >= (aux.size + queue.size)) {
                available -= aux.size;
                writeQueue(aux.buffer, 0, aux.size);
                aux.dereference();
                auxiliaryBuffers.remove(0);

                if (auxiliaryBuffers.size() < 1) {
                    aux = null;
                    break;
                }
                aux = auxiliaryBuffers.get(0);
            }

            if (IMMEDIATE_AUX_BUFFER_FLUSH) {
                // try partial flush to avoid allocate another auxiliary buffer
                if (aux != null && aux.available() < len && available > queue.size) {
                    int size = Math.min(aux.size, (int) available - queue.size);

                    writeQueue(aux.buffer, 0, size);
                    aux.dereference(size);

                    available -= size;
                }
            }
        }

        if (auxiliaryBuffers.size() < 1 && available > (len + queue.size)) {
            writeQueue(b, off, len);
        } else {
            int i = auxiliaryBuffers.size() - 1;
            while (len > 0) {
                if (i < 0) {
                    // allocate a new auxiliary buffer
                    auxiliaryBuffers.add(new ManagedBuffer(AUX_BUFFER_SIZE));
                    i++;
                }

                ManagedBuffer aux = auxiliaryBuffers.get(i);
                available = aux.available();

                if (available < 1) {
                    // secondary auxiliary buffer
                    available = len;
                    aux = new ManagedBuffer(Math.max(len, AUX_BUFFER_SIZE2));
                    auxiliaryBuffers.add(aux);
                    i++;
                } else {
                    available = Math.min(len, available);
                }

                aux.write(b, off, (int) available);

                len -= available;
                if (len > 0) off += available;
            }
        }
    }

    private void writeOutside(byte buffer[], int offset, int length) throws IOException {
        out.write(buffer, offset, length);
        position += length;

        if (onProgress != null && position > reportPosition) {
            reportPosition = position + NOTIFY_BYTES_INTERVAL;
            onProgress.report(position);
        }
    }

    private void writeQueue(byte[] buffer, int offset, int length) throws IOException {
        while (length > 0) {
            if (queue.available() < length) {
                flushQueue();

                if (length >= queue.buffer.length) {
                    writeOutside(buffer, offset, length);
                    return;
                }
            }

            int size = Math.min(queue.available(), length);
            queue.write(buffer, offset, size);

            offset += size;
            length -= size;
        }

        if (queue.size >= queue.buffer.length) {
            flushQueue();
        }
    }

    private void flushQueue() throws IOException {
        writeOutside(queue.buffer, 0, queue.size);
        queue.size = 0;
    }

    private void flushEverything() throws IOException {
        flushQueue();

        if (auxiliaryBuffers.size() > 0) {
            for (ManagedBuffer aux : auxiliaryBuffers) {
                writeOutside(aux.buffer, 0, aux.size);
                aux.dereference();
            }
            auxiliaryBuffers.clear();
        }
    }

    /**
     * Flush any buffer directly to the file. Warning: use this method ONLY if
     * all read dependencies are disposed
     *
     * @throws IOException if the dependencies are not disposed
     */
    @Override
    public void flush() throws IOException {
        if (callback.check() != -1) {
            throw new IOException("All read dependencies of this file must be disposed first");
        }
        flushEverything();

        // Save the current file length in case the method {@code rewind()} is called
        if (position > maxLengthKnown) {
            maxLengthKnown = position;
        }
    }

    @Override
    public void rewind() throws IOException {
        flush();
        out.seek(startOffset);

        if (onProgress != null) {
            onProgress.report(-position);
        }

        position = startOffset;
        reportPosition = startOffset;

    }

    @Override
    public long skip(long amount) throws IOException {
        flush();
        position += amount;

        out.seek(position);

        return amount;
    }

    @Override
    public boolean isDisposed() {
        return out == null;
    }

    @Override
    public boolean canRewind() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    //<editor-fold defaultState="collapsed" desc="stub read methods">
    @Override
    public boolean canRead() {
        return false;
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public int read(byte[] buffer) {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public int read(byte[] buffer, int offset, int count) {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public int available() {
        throw new UnsupportedOperationException("write-only");
    }
//</editor-fold>

    public interface OffsetChecker {

        /**
         * Checks the amount of available space ahead
         *
         * @return absolute offset in the file where no more data SHOULD NOT be
         * written. If the value is -1 the whole file will be used
         */
        long check();
    }

    public interface ProgressReport {

        void report(long progress);
    }

    class ManagedBuffer {

        byte[] buffer;
        int size;

        ManagedBuffer(int length) {
            buffer = new byte[length];
        }

        void dereference() {
            buffer = null;
            size = 0;
        }

        void dereference(int amount) {
            if (amount > size) {
                throw new IndexOutOfBoundsException("Invalid dereference amount (" + amount + ">=" + size + ")");
            }
            size -= amount;
            System.arraycopy(buffer, amount, buffer, 0, size);
        }

        protected int available() {
            return buffer.length - size;
        }

        private void write(byte[] b, int off, int len) {
            System.arraycopy(b, off, buffer, size, len);
            size += len;
        }

        @Override
        public String toString() {
            return "holding: " + String.valueOf(size) + " length: " + String.valueOf(buffer.length) + " available: " + String.valueOf(available());
        }

    }
}
