package us.shandian.giga.io;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

public class CircularFileWriter extends SharpStream {

    private static final int QUEUE_BUFFER_SIZE = 8 * 1024;// 8 KiB
    private static final int COPY_BUFFER_SIZE = 128 * 1024; // 128 KiB
    private static final int NOTIFY_BYTES_INTERVAL = 64 * 1024;// 64 KiB
    private static final int THRESHOLD_AUX_LENGTH = 15 * 1024 * 1024;// 15 MiB

    private final OffsetChecker callback;

    public ProgressReport onProgress;
    public WriteErrorHandle onWriteError;

    private long reportPosition;
    private long maxLengthKnown = -1;

    private BufferedFile out;
    private BufferedFile aux;

    public CircularFileWriter(SharpStream target, File temp, OffsetChecker checker) throws IOException {
        Objects.requireNonNull(checker);

        if (!temp.exists()) {
            if (!temp.createNewFile()) {
                throw new IOException("Cannot create a temporal file");
            }
        }

        aux = new BufferedFile(temp);
        out = new BufferedFile(target);

        callback = checker;

        reportPosition = NOTIFY_BYTES_INTERVAL;
    }

    private void flushAuxiliar(long amount) throws IOException {
        if (aux.length < 1) {
            return;
        }

        out.flush();
        aux.flush();

        boolean underflow = aux.offset < aux.length || out.offset < out.length;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        aux.target.seek(0);
        out.target.seek(out.length);

        long length = amount;
        while (length > 0) {
            int read = (int) Math.min(length, Integer.MAX_VALUE);
            read = aux.target.read(buffer, 0, Math.min(read, buffer.length));

            if (read < 1) {
                amount -= length;
                break;
            }

            out.writeProof(buffer, read);
            length -= read;
        }

        if (underflow) {
            if (out.offset >= out.length) {
                // calculate the aux underflow pointer
                if (aux.offset < amount) {
                    out.offset += aux.offset;
                    aux.offset = 0;
                    out.target.seek(out.offset);
                } else {
                    aux.offset -= amount;
                    out.offset = out.length + amount;
                }
            } else {
                aux.offset = 0;
            }
        } else {
            out.offset += amount;
            aux.offset -= amount;
        }

        out.length += amount;

        if (out.length > maxLengthKnown) {
            maxLengthKnown = out.length;
        }

        if (amount < aux.length) {
            // move the excess data to the beginning of the file
            long readOffset = amount;
            long writeOffset = 0;

            aux.length -= amount;
            length = aux.length;
            while (length > 0) {
                int read = (int) Math.min(length, Integer.MAX_VALUE);
                read = aux.target.read(buffer, 0, Math.min(read, buffer.length));

                aux.target.seek(writeOffset);
                aux.writeProof(buffer, read);

                writeOffset += read;
                readOffset += read;
                length -= read;

                aux.target.seek(readOffset);
            }

            aux.target.setLength(aux.length);
            return;
        }

        if (aux.length > THRESHOLD_AUX_LENGTH) {
            aux.target.setLength(THRESHOLD_AUX_LENGTH);// or setLength(0);
        }

        aux.reset();
    }

    /**
     * Flush any buffer and close the output file. Use this method if the
     * operation is successful
     *
     * @return the final length of the file
     * @throws IOException if an I/O error occurs
     */
    public long finalizeFile() throws IOException {
        flushAuxiliar(aux.length);

        out.flush();

        // change file length (if required)
        long length = Math.max(maxLengthKnown, out.length);
        if (length != out.target.length()) {
            out.target.setLength(length);
        }

        close();

        return length;
    }

    /**
     * Close the file without flushing any buffer
     */
    @Override
    public void close() {
        if (out != null) {
            out.close();
            out = null;
        }
        if (aux != null) {
            aux.close();
            aux = null;
        }
    }

    @Override
    public void write(byte b) throws IOException {
        write(new byte[]{b}, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        long available;
        long offsetOut = out.getOffset();
        long offsetAux = aux.getOffset();
        long end = callback.check();

        if (end == -1) {
            available = Integer.MAX_VALUE;
        } else if (end < offsetOut) {
            throw new IOException("The reported offset is invalid: " + end + "<" + offsetOut);
        } else {
            available = end - offsetOut;
        }

        boolean usingAux = aux.length > 0 && offsetOut >= out.length;
        boolean underflow = offsetAux < aux.length || offsetOut < out.length;

        if (usingAux) {
            // before continue calculate the final length of aux
            long length = offsetAux + len;
            if (underflow) {
                if (aux.length > length) {
                    length = aux.length;// the length is not changed
                }
            } else {
                length = aux.length + len;
            }

            aux.write(b, off, len);

            if (length >= THRESHOLD_AUX_LENGTH && length <= available) {
                flushAuxiliar(available);
            }
        } else {
            if (underflow) {
                available = out.length - offsetOut;
            }

            int length = Math.min(len, (int) Math.min(Integer.MAX_VALUE, available));
            out.write(b, off, length);

            len -= length;
            off += length;

            if (len > 0) {
                aux.write(b, off, len);
            }
        }

        if (onProgress != null) {
            long absoluteOffset = out.getOffset() + aux.getOffset();
            if (absoluteOffset > reportPosition) {
                reportPosition = absoluteOffset + NOTIFY_BYTES_INTERVAL;
                onProgress.report(absoluteOffset);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        aux.flush();
        out.flush();

        long total = out.length + aux.length;
        if (total > maxLengthKnown) {
            maxLengthKnown = total;// save the current file length in case the method {@code rewind()} is called
        }
    }

    @Override
    public long skip(long amount) throws IOException {
        seek(out.getOffset() + aux.getOffset() + amount);
        return amount;
    }

    @Override
    public void rewind() throws IOException {
        if (onProgress != null) {
            onProgress.report(0);// rollback the whole progress
        }

        seek(0);

        reportPosition = NOTIFY_BYTES_INTERVAL;
    }

    @Override
    public void seek(long offset) throws IOException {
        long total = out.length + aux.length;

        if (offset == total) {
            // do not ignore the seek offset if a underflow exists
            long relativeOffset = out.getOffset() + aux.getOffset();
            if (relativeOffset == total) {
                return;
            }
        }

        // flush everything, avoid any underflow
        flush();

        if (offset < 0 || offset > total) {
            throw new IOException("desired offset is outside of range=0-" + total + " offset=" + offset);
        }

        if (offset > out.length) {
            out.seek(out.length);
            aux.seek(offset - out.length);
        } else {
            out.seek(offset);
            aux.seek(0);
        }
    }

    @Override
    public boolean isClosed() {
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

    @Override
    public boolean canSeek() {
        return true;
    }

    // <editor-fold defaultstate="collapsed" desc="stub read methods">
    @Override
    public boolean canRead() {
        return false;
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public int read(byte[] buffer
    ) {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public int read(byte[] buffer, int offset, int count
    ) {
        throw new UnsupportedOperationException("write-only");
    }

    @Override
    public long available() {
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

    public interface WriteErrorHandle {

        /**
         * Attempts to handle a I/O exception
         *
         * @param err the cause
         * @return {@code true} to retry and continue, otherwise, {@code false}
         * and throw the exception
         */
        boolean handle(Exception err);
    }

    class BufferedFile {

        final SharpStream target;

        private long offset;
        long length;

        private byte[] queue = new byte[QUEUE_BUFFER_SIZE];
        private int queueSize;

        BufferedFile(File file) throws FileNotFoundException {
            this.target = new FileStream(file);
        }

        BufferedFile(SharpStream target) {
            this.target = target;
        }

        long getOffset() {
            return offset + queueSize;// absolute offset in the file
        }

        void close() {
            queue = null;
            target.close();
        }

        void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                // if the queue is full, the method available() will flush the queue
                int read = Math.min(available(), len);

                // enqueue incoming buffer
                System.arraycopy(b, off, queue, queueSize, read);
                queueSize += read;

                len -= read;
                off += read;
            }

            long total = offset + queueSize;
            if (total > length) {
                length = total;// save length
            }
        }

        void flush() throws IOException {
            writeProof(queue, queueSize);
            offset += queueSize;
            queueSize = 0;
        }

        protected void rewind() throws IOException {
            offset = 0;
            target.seek(0);
        }

        int available() throws IOException {
            if (queueSize >= queue.length) {
                flush();
                return queue.length;
            }

            return queue.length - queueSize;
        }

        void reset() throws IOException {
            offset = 0;
            length = 0;
            target.seek(0);
        }

        void seek(long absoluteOffset) throws IOException {
            if (absoluteOffset == offset) {
                return;// nothing to do
            }
            offset = absoluteOffset;
            target.seek(absoluteOffset);
        }

        void writeProof(byte[] buffer, int length) throws IOException {
            if (onWriteError == null) {
                target.write(buffer, 0, length);
                return;
            }

            while (true) {
                try {
                    target.write(buffer, 0, length);
                    return;
                } catch (Exception e) {
                    if (!onWriteError.handle(e)) {
                        throw e;// give up
                    }
                }
            }
        }

        @NonNull
        @Override
        public String toString() {
            String absLength;

            try {
                absLength = Long.toString(target.length());
            } catch (IOException e) {
                absLength = "[" + e.getLocalizedMessage() + "]";
            }

            return String.format(
                    "offset=%s  length=%s  queue=%s  absLength=%s",
                    offset, length, queueSize, absLength
            );
        }
    }
}
