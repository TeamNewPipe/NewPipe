package us.shandian.giga.get;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.schabi.newpipe.Downloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadMission extends Mission {
    private static final long serialVersionUID = 5L;// last bump: 30 june 2019

    static final int BUFFER_SIZE = 64 * 1024;
    static final int BLOCK_SIZE = 512 * 1024;

    @SuppressWarnings("SpellCheckingInspection")
    private static final String INSUFFICIENT_STORAGE = "ENOSPC";

    private static final String TAG = "DownloadMission";

    public static final int ERROR_NOTHING = -1;
    public static final int ERROR_PATH_CREATION = 1000;
    public static final int ERROR_FILE_CREATION = 1001;
    public static final int ERROR_UNKNOWN_EXCEPTION = 1002;
    public static final int ERROR_PERMISSION_DENIED = 1003;
    public static final int ERROR_SSL_EXCEPTION = 1004;
    public static final int ERROR_UNKNOWN_HOST = 1005;
    public static final int ERROR_CONNECT_HOST = 1006;
    public static final int ERROR_POSTPROCESSING = 1007;
    public static final int ERROR_POSTPROCESSING_STOPPED = 1008;
    public static final int ERROR_POSTPROCESSING_HOLD = 1009;
    public static final int ERROR_INSUFFICIENT_STORAGE = 1010;
    public static final int ERROR_PROGRESS_LOST = 1011;
    public static final int ERROR_TIMEOUT = 1012;
    public static final int ERROR_HTTP_NO_CONTENT = 204;
    public static final int ERROR_HTTP_UNSUPPORTED_RANGE = 206;

    /**
     * The urls of the file to download
     */
    public String[] urls;

    /**
     * Number of bytes downloaded
     */
    public long done;

    /**
     * Indicates a file generated dynamically on the web server
     */
    public boolean unknownLength;

    /**
     * offset in the file where the data should be written
     */
    public long[] offsets;

    /**
     * Indicates if the post-processing state:
     * 0: ready
     * 1: running
     * 2: completed
     * 3: hold
     */
    public volatile int psState;

    /**
     * the post-processing algorithm instance
     */
    public Postprocessing psAlgorithm;

    /**
     * The current resource to download, {@code urls[current]} and {@code offsets[current]}
     */
    public int current;

    /**
     * Metadata where the mission state is saved
     */
    public transient File metadata;

    /**
     * maximum attempts
     */
    public transient int maxRetry;

    /**
     * Approximated final length, this represent the sum of all resources sizes
     */
    public long nearLength;

    /**
     * Download blocks, the size is multiple of {@link DownloadMission#BLOCK_SIZE}.
     * Every entry (block) in this array holds an offset, used to resume the download.
     * An block offset can be -1 if the block was downloaded successfully.
     */
    int[] blocks;

    /**
     * Download/File resume offset in fallback mode (if applicable) {@link DownloadRunnableFallback}
     */
    long fallbackResumeOffset;

    /**
     * Maximum of download threads running, chosen by the user
     */
    public int threadCount = 3;

    private transient int finishCount;
    public transient boolean running;
    public boolean enqueued;

    public int errCode = ERROR_NOTHING;
    public Exception errObject = null;

    public transient boolean recovered;
    public transient Handler mHandler;
    private transient boolean mWritingToFile;
    private transient boolean[] blockAcquired;

    final Object LOCK = new Lock();

    private transient boolean deleted;

    public transient volatile Thread[] threads = new Thread[0];
    private transient Thread init = null;

    public DownloadMission(String[] urls, StoredFileHelper storage, char kind, Postprocessing psInstance) {
        if (urls == null) throw new NullPointerException("urls is null");
        if (urls.length < 1) throw new IllegalArgumentException("urls is empty");
        this.urls = urls;
        this.kind = kind;
        this.offsets = new long[urls.length];
        this.enqueued = true;
        this.maxRetry = 3;
        this.storage = storage;
        this.psAlgorithm = psInstance;

        if (DEBUG && psInstance == null && urls.length > 1) {
            Log.w(TAG, "mission created with multiple urls ¿missing post-processing algorithm?");
        }
    }

    /**
     * Acquire a block
     *
     * @return the block or {@code null} if no more blocks left
     */
    @Nullable
    Block acquireBlock() {
        synchronized (LOCK) {
            for (int i = 0; i < blockAcquired.length; i++) {
                if (!blockAcquired[i] && blocks[i] >= 0) {
                    Block block = new Block();
                    block.position = i;
                    block.done = blocks[i];

                    blockAcquired[i] = true;
                    return block;
                }
            }
        }

        return null;
    }

    /**
     * Release an block
     *
     * @param position the index of the block
     * @param done     amount of bytes downloaded
     */
    void releaseBlock(int position, int done) {
        synchronized (LOCK) {
            blockAcquired[position] = false;
            blocks[position] = done;
        }
    }

    /**
     * Open connection
     *
     * @param threadId   id of the calling thread, used only for debug
     * @param rangeStart range start
     * @param rangeEnd   range end
     * @return a {@link java.net.URLConnection URLConnection} linking to the URL.
     * @throws IOException if an I/O exception occurs.
     */
    HttpURLConnection openConnection(int threadId, long rangeStart, long rangeEnd) throws IOException {
        return openConnection(urls[current], threadId, rangeStart, rangeEnd);
    }

    HttpURLConnection openConnection(String url, int threadId, long rangeStart, long rangeEnd) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", Downloader.USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");

        // BUG workaround: switching between networks can freeze the download forever
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(10000);

        if (rangeStart >= 0) {
            String req = "bytes=" + rangeStart + "-";
            if (rangeEnd > 0) req += rangeEnd;

            conn.setRequestProperty("Range", req);

            if (DEBUG) {
                Log.d(TAG, threadId + ":" + conn.getRequestProperty("Range"));
            }
        }

        return conn;
    }

    /**
     * @param threadId id of the calling thread
     * @param conn     Opens and establish the communication
     * @throws IOException if an error occurred connecting to the server.
     * @throws HttpError   if the HTTP Status-Code is not satisfiable
     */
    void establishConnection(int threadId, HttpURLConnection conn) throws IOException, HttpError {
        conn.connect();
        int statusCode = conn.getResponseCode();

        if (DEBUG) {
            Log.d(TAG, threadId + ":Content-Length=" + conn.getContentLength() + " Code:" + statusCode);
        }

        switch (statusCode) {
            case 204:
            case 205:
            case 207:
                throw new HttpError(conn.getResponseCode());
            case 416:
                return;// let the download thread handle this error
            default:
                if (statusCode < 200 || statusCode > 299) {
                    throw new HttpError(statusCode);
                }
        }

    }


    private void notify(int what) {
        Message m = new Message();
        m.what = what;
        m.obj = this;

        mHandler.sendMessage(m);
    }

    synchronized void notifyProgress(long deltaLen) {
        if (!running) return;

        if (recovered) {
            recovered = false;
        }

        if (unknownLength) {
            length += deltaLen;// Update length before proceeding
        }

        done += deltaLen;

        if (done > length) {
            done = length;
        }

        if (done != length && !deleted && !mWritingToFile) {
            mWritingToFile = true;
            runAsync(-2, this::writeThisToFile);
        }

        notify(DownloadManagerService.MESSAGE_PROGRESS);
    }

    synchronized void notifyError(Exception err) {
        Log.e(TAG, "notifyError()", err);

        if (err instanceof FileNotFoundException) {
            notifyError(ERROR_FILE_CREATION, null);
        } else if (err instanceof SSLException) {
            notifyError(ERROR_SSL_EXCEPTION, null);
        } else if (err instanceof HttpError) {
            notifyError(((HttpError) err).statusCode, null);
        } else if (err instanceof ConnectException) {
            notifyError(ERROR_CONNECT_HOST, null);
        } else if (err instanceof UnknownHostException) {
            notifyError(ERROR_UNKNOWN_HOST, null);
        } else if (err instanceof SocketTimeoutException) {
            notifyError(ERROR_TIMEOUT, null);
        } else {
            notifyError(ERROR_UNKNOWN_EXCEPTION, err);
        }
    }

    public synchronized void notifyError(int code, Exception err) {
        Log.e(TAG, "notifyError() code = " + code, err);
        if (err instanceof IOException) {
            if (!storage.canWrite() || err.getMessage().contains("Permission denied")) {
                code = ERROR_PERMISSION_DENIED;
                err = null;
            } else if (err.getMessage().contains(INSUFFICIENT_STORAGE)) {
                code = ERROR_INSUFFICIENT_STORAGE;
                err = null;
            }
        }

        errCode = code;
        errObject = err;

        switch (code) {
            case ERROR_SSL_EXCEPTION:
            case ERROR_UNKNOWN_HOST:
            case ERROR_CONNECT_HOST:
            case ERROR_TIMEOUT:
                // do not change the queue flag for network errors, can be
                // recovered silently without the user interaction
                break;
            default:
                // also checks for server errors
                if (code < 500 || code > 599) enqueued = false;
        }

        notify(DownloadManagerService.MESSAGE_ERROR);

        if (running) {
            running = false;
            recovered = true;
            if (threads != null) selfPause();
        }
    }

    synchronized void notifyFinished() {
        if (errCode > ERROR_NOTHING) return;

        finishCount++;

        if (blocks.length < 1 || threads == null || finishCount == threads.length) {
            if (errCode != ERROR_NOTHING) return;

            if (DEBUG) {
                Log.d(TAG, "onFinish: " + (current + 1) + "/" + urls.length);
            }

            if ((current + 1) < urls.length) {
                // prepare next sub-mission
                long current_offset = offsets[current++];
                offsets[current] = current_offset + length;
                initializer();
                return;
            }

            current++;
            unknownLength = false;

            if (!doPostprocessing()) return;

            enqueued = false;
            running = false;
            deleteThisFromFile();

            notify(DownloadManagerService.MESSAGE_FINISHED);
        }
    }

    private void notifyPostProcessing(int state) {
        String action;
        switch (state) {
            case 1:
                action = "Running";
                break;
            case 2:
                action = "Completed";
                break;
            default:
                action = "Failed";
        }

        Log.d(TAG, action + " postprocessing on " + storage.getName());

        synchronized (LOCK) {
            // don't return without fully write the current state
            psState = state;
            Utility.writeToFile(metadata, DownloadMission.this);
        }
    }

    /**
     * Start downloading with multiple threads.
     */
    public void start() {
        if (running || isFinished()) return;

        // ensure that the previous state is completely paused.
        joinForThread(init);
        if (threads != null)
            for (Thread thread : threads) joinForThread(thread);

        running = true;
        errCode = ERROR_NOTHING;

        if (current >= urls.length) {
            threads = null;
            runAsync(1, this::notifyFinished);
            return;
        }

        if (blocks == null) {
            initializer();
            return;
        }

        init = null;
        finishCount = 0;
        blockAcquired = new boolean[blocks.length];

        if (blocks.length < 1) {
            if (unknownLength) {
                done = 0;
                length = 0;
            }

            threads = new Thread[]{runAsync(1, new DownloadRunnableFallback(this))};
        } else {
            int remainingBlocks = 0;
            for (int block : blocks) if (block >= 0) remainingBlocks++;

            if (remainingBlocks < 1) {
                runAsync(1, this::notifyFinished);
                return;
            }

            threads = new Thread[Math.min(threadCount, remainingBlocks)];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = runAsync(i + 1, new DownloadRunnable(this, i));
            }
        }
    }

    /**
     * Pause the mission
     */
    public void pause() {
        if (!running) return;

        if (isPsRunning()) {
            if (DEBUG) {
                Log.w(TAG, "pause during post-processing is not applicable.");
            }
            return;
        }

        running = false;
        recovered = true;

        if (init != null && init.isAlive()) {
            // NOTE: if start() method is running ¡will no have effect!
            init.interrupt();
            synchronized (LOCK) {
                resetState(false, true, ERROR_NOTHING);
            }
            return;
        }

        if (DEBUG && unknownLength) {
            Log.w(TAG, "pausing a download that can not be resumed (range requests not allowed by the server).");
        }

        // check if the calling thread (alias UI thread) is interrupted
        if (Thread.currentThread().isInterrupted()) {
            writeThisToFile();
            return;
        }

        // wait for all threads are suspended before save the state
        if (threads != null) runAsync(-1, this::selfPause);
    }

    private void selfPause() {
        try {
            for (Thread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    thread.join(5000);
                }
            }
        } catch (Exception e) {
            // nothing to do
        } finally {
            writeThisToFile();
        }
    }

    /**
     * Removes the downloaded file and the meta file
     */
    @Override
    public boolean delete() {
        deleted = true;
        if (psAlgorithm != null) psAlgorithm.cleanupTemporalDir();

        boolean res = deleteThisFromFile();

        if (!super.delete()) return false;
        return res;
    }


    /**
     * Resets the mission state
     *
     * @param rollback       {@code true} true to forget all progress, otherwise, {@code false}
     * @param persistChanges {@code true} to commit changes to the metadata file, otherwise, {@code false}
     */
    public void resetState(boolean rollback, boolean persistChanges, int errorCode) {
        done = 0;
        errCode = errorCode;
        errObject = null;
        unknownLength = false;
        threads = null;
        fallbackResumeOffset = 0;
        blocks = null;
        blockAcquired = null;

        if (rollback) current = 0;

        if (persistChanges)
            Utility.writeToFile(metadata, DownloadMission.this);
    }

    private void initializer() {
        init = runAsync(DownloadInitializer.mId, new DownloadInitializer(this));
    }

    /**
     * Write this {@link DownloadMission} to the meta file asynchronously
     * if no thread is already running.
     */
    private void writeThisToFile() {
        synchronized (LOCK) {
            if (deleted) return;
            Utility.writeToFile(metadata, DownloadMission.this);
        }
        mWritingToFile = false;
    }

    /**
     * Indicates if the download if fully finished
     *
     * @return true, otherwise, false
     */
    public boolean isFinished() {
        return current >= urls.length && (psAlgorithm == null || psState == 2);
    }

    /**
     * Indicates if the download file is corrupt due a failed post-processing
     *
     * @return {@code true} if this mission is unrecoverable
     */
    public boolean isPsFailed() {
        switch (errCode) {
            case ERROR_POSTPROCESSING:
            case ERROR_POSTPROCESSING_STOPPED:
                return psAlgorithm.worksOnSameFile;
        }

        return false;
    }

    /**
     * Indicates if a post-processing algorithm is running
     *
     * @return true, otherwise, false
     */
    public boolean isPsRunning() {
        return psAlgorithm != null && (psState == 1 || psState == 3);
    }

    /**
     * Indicated if the mission is ready
     *
     * @return true, otherwise, false
     */
    public boolean isInitialized() {
        return blocks != null; // DownloadMissionInitializer was executed
    }

    /**
     * Gets the approximated final length of the file
     *
     * @return the length in bytes
     */
    public long getLength() {
        long calculated;
        if (psState == 1 || psState == 3) {
            calculated = length;
        } else {
            calculated = offsets[current < offsets.length ? current : (offsets.length - 1)] + length;
        }

        calculated -= offsets[0];// don't count reserved space

        return calculated > nearLength ? calculated : nearLength;
    }

    /**
     * set this mission state on the queue
     *
     * @param queue true to add to the queue, otherwise, false
     */
    public void setEnqueued(boolean queue) {
        enqueued = queue;
        runAsync(-2, this::writeThisToFile);
    }

    /**
     * Attempts to continue a blocked post-processing
     *
     * @param recover {@code true} to retry, otherwise, {@code false} to cancel
     */
    public void psContinue(boolean recover) {
        psState = 1;
        errCode = recover ? ERROR_NOTHING : ERROR_POSTPROCESSING;
        threads[0].interrupt();
    }

    /**
     * Indicates whatever the backed storage is invalid
     *
     * @return {@code true}, if storage is invalid and cannot be used
     */
    public boolean hasInvalidStorage() {
        return errCode == ERROR_PROGRESS_LOST || storage == null || storage.isInvalid() || !storage.existsAsFile();
    }

    /**
     * Indicates whatever is possible to start the mission
     *
     * @return {@code true} is this mission its "healthy", otherwise, {@code false}
     */
    public boolean isCorrupt() {
        return (isPsFailed() || errCode == ERROR_POSTPROCESSING_HOLD) || isFinished() || hasInvalidStorage();
    }

    private boolean doPostprocessing() {
        if (psAlgorithm == null || psState == 2) return true;

        errObject = null;

        notifyPostProcessing(1);
        notifyProgress(0);

        if (DEBUG)
            Thread.currentThread().setName("[" + TAG + "]  ps = " +
                    psAlgorithm.getClass().getSimpleName() +
                    "  filename = " + storage.getName()
            );

        threads = new Thread[]{Thread.currentThread()};

        Exception exception = null;

        try {
            psAlgorithm.run(this);
        } catch (Exception err) {
            Log.e(TAG, "Post-processing failed. " + psAlgorithm.toString(), err);

            if (errCode == ERROR_NOTHING) errCode = ERROR_POSTPROCESSING;

            exception = err;
        } finally {
            notifyPostProcessing(errCode == ERROR_NOTHING ? 2 : 0);
        }

        if (errCode != ERROR_NOTHING) {
            if (exception == null) exception = errObject;
            notifyError(ERROR_POSTPROCESSING, exception);

            return false;
        }

        return true;
    }

    private boolean deleteThisFromFile() {
        synchronized (LOCK) {
            return metadata.delete();
        }
    }

    /**
     * run a new thread
     *
     * @param id  id of new thread (used for debugging only)
     * @param who the Runnable whose {@code run} method is invoked.
     */
    private void runAsync(int id, Runnable who) {
        runAsync(id, new Thread(who));
    }

    /**
     * run a new thread
     *
     * @param id  id of new thread (used for debugging only)
     * @param who the Thread whose {@code run} method is invoked when this thread is started
     * @return the passed thread
     */
    private Thread runAsync(int id, Thread who) {
        // known thread ids:
        //   -2:     state saving by  notifyProgress()  method
        //   -1:     wait for saving the state by  pause()  method
        //    0:     initializer
        //  >=1:     any download thread

        if (DEBUG) {
            who.setName(String.format("%s[%s] %s", TAG, id, storage.getName()));
        }

        who.start();

        return who;
    }

    private void joinForThread(Thread thread) {
        if (thread == null || !thread.isAlive()) return;
        if (thread == Thread.currentThread()) return;

        if (DEBUG) {
            Log.w(TAG, "a thread is !still alive!: " + thread.getName());
        }

        // still alive, this should not happen.
        // Possible reasons:
        //      slow device
        //      the user is spamming start/pause buttons
        //      start() method called quickly after pause()

        try {
            thread.join(10000);
        } catch (InterruptedException e) {
            Log.d(TAG, "timeout on join : " + thread.getName());
            throw new RuntimeException("A thread is still running:\n" + thread.getName());
        }
    }


    static class HttpError extends Exception {
        final int statusCode;

        HttpError(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public String getMessage() {
            return "HTTP " + statusCode;
        }
    }

    static class Block {
        int position;
        int done;
    }

    private static class Lock implements Serializable {
        // java.lang.Object cannot be used because is not serializable
    }
}
