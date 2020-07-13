package us.shandian.giga.get;

import android.os.Build;
import android.os.Handler;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.DownloaderImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;

import javax.net.ssl.SSLException;

import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadMission extends Mission {
    private static final long serialVersionUID = 6L;// last bump: 07 october 2019

    static final int BUFFER_SIZE = 64 * 1024;
    static final int BLOCK_SIZE = 512 * 1024;

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
    public static final int ERROR_RESOURCE_GONE = 1013;
    public static final int ERROR_HTTP_NO_CONTENT = 204;
    static final int ERROR_HTTP_FORBIDDEN = 403;

    /**
     * The urls of the file to download
     */
    public String[] urls;

    /**
     * Number of bytes downloaded and written
     */
    public volatile long done;

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
    volatile long fallbackResumeOffset;

    /**
     * Maximum of download threads running, chosen by the user
     */
    public int threadCount = 3;

    /**
     * information required to recover a download
     */
    public MissionRecoveryInfo[] recoveryInfo;

    private transient int finishCount;
    public transient volatile boolean running;
    public boolean enqueued;

    public int errCode = ERROR_NOTHING;
    public Exception errObject = null;

    public transient Handler mHandler;
    private transient boolean[] blockAcquired;

    private transient long writingToFileNext;
    private transient volatile boolean writingToFile;

    final Object LOCK = new Lock();

    @NonNull
    public transient Thread[] threads = new Thread[0];
    public transient Thread init = null;

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
     * Opens a connection
     *
     * @param headRequest {@code true} for use {@code HEAD} request method, otherwise, {@code GET} is used
     * @param rangeStart  range start
     * @param rangeEnd    range end
     * @return a {@link java.net.URLConnection URLConnection} linking to the URL.
     * @throws IOException if an I/O exception occurs.
     */
    HttpURLConnection openConnection(boolean headRequest, long rangeStart, long rangeEnd) throws IOException {
        return openConnection(urls[current], headRequest, rangeStart, rangeEnd);
    }

    HttpURLConnection openConnection(String url, boolean headRequest, long rangeStart, long rangeEnd) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", DownloaderImpl.USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "*");

        if (headRequest) conn.setRequestMethod("HEAD");

        // BUG workaround: switching between networks can freeze the download forever
        conn.setConnectTimeout(30000);

        if (rangeStart >= 0) {
            String req = "bytes=" + rangeStart + "-";
            if (rangeEnd > 0) req += rangeEnd;

            conn.setRequestProperty("Range", req);
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
        int statusCode = conn.getResponseCode();

        if (DEBUG) {
            Log.d(TAG, threadId + ":[request]  Range=" + conn.getRequestProperty("Range"));
            Log.d(TAG, threadId + ":[response] Code=" + statusCode);
            Log.d(TAG, threadId + ":[response] Content-Length=" + conn.getContentLength());
            Log.d(TAG, threadId + ":[response] Content-Range=" + conn.getHeaderField("Content-Range"));
        }


        switch (statusCode) {
            case 204:
            case 205:
            case 207:
                throw new HttpError(statusCode);
            case 416:
                return;// let the download thread handle this error
            default:
                if (statusCode < 200 || statusCode > 299) {
                    throw new HttpError(statusCode);
                }
        }

    }


    private void notify(int what) {
        mHandler.obtainMessage(what, this).sendToTarget();
    }

    synchronized void notifyProgress(long deltaLen) {
        if (unknownLength) {
            length += deltaLen;// Update length before proceeding
        }

        done += deltaLen;

        if (metadata == null) return;

        if (!writingToFile && (done > writingToFileNext || deltaLen < 0)) {
            writingToFile = true;
            writingToFileNext = done + BLOCK_SIZE;
            writeThisToFileAsync();
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (err != null && err.getCause() instanceof ErrnoException) {
                int errno = ((ErrnoException) err.getCause()).errno;
                if (errno == OsConstants.ENOSPC) {
                    code = ERROR_INSUFFICIENT_STORAGE;
                    err = null;
                } else if (errno == OsConstants.EACCES) {
                    code = ERROR_PERMISSION_DENIED;
                    err = null;
                }
            }
        }

        if (err instanceof IOException) {
            if (err.getMessage().contains("Permission denied")) {
                code = ERROR_PERMISSION_DENIED;
                err = null;
            } else if (err.getMessage().contains("ENOSPC")) {
                code = ERROR_INSUFFICIENT_STORAGE;
                err = null;
            } else if (!storage.canWrite()) {
                code = ERROR_FILE_CREATION;
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

        if (running) pauseThreads();
    }

    synchronized void notifyFinished() {
        if (current < urls.length) {
            if (++finishCount < threads.length) return;

            if (DEBUG) {
                Log.d(TAG, "onFinish: downloaded " + (current + 1) + "/" + urls.length);
            }

            current++;
            if (current < urls.length) {
                // prepare next sub-mission
                offsets[current] = offsets[current - 1] + length;
                initializer();
                return;
            }
        }

        if (psAlgorithm != null && psState == 0) {
            threads = new Thread[]{
                    runAsync(1, this::doPostprocessing)
            };
            return;
        }


        // this mission is fully finished

        unknownLength = false;
        enqueued = false;
        running = false;

        deleteThisFromFile();
        notify(DownloadManagerService.MESSAGE_FINISHED);
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

        if (state == 2) {
            psState = state;
            return;
        }

        synchronized (LOCK) {
            // don't return without fully write the current state
            psState = state;
            writeThisToFile();
        }
    }


    /**
     * Start downloading with multiple threads.
     */
    public void start() {
        if (running || isFinished() || urls.length < 1) return;

        // ensure that the previous state is completely paused.
        joinForThreads(10000);

        running = true;
        errCode = ERROR_NOTHING;

        if (hasInvalidStorage()) {
            notifyError(ERROR_FILE_CREATION, null);
            return;
        }

        if (current >= urls.length) {
            notifyFinished();
            return;
        }

        notify(DownloadManagerService.MESSAGE_RUNNING);

        if (urls[current] == null) {
            doRecover(ERROR_RESOURCE_GONE);
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
            threads = new Thread[]{runAsync(1, new DownloadRunnableFallback(this))};
        } else {
            int remainingBlocks = 0;
            for (int block : blocks) if (block >= 0) remainingBlocks++;

            if (remainingBlocks < 1) {
                notifyFinished();
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
        notify(DownloadManagerService.MESSAGE_PAUSED);

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

        init = null;
        pauseThreads();
    }

    private void pauseThreads() {
        running = false;
        joinForThreads(-1);
        writeThisToFile();
    }

    /**
     * Removes the downloaded file and the meta file
     */
    @Override
    public boolean delete() {
        if (psAlgorithm != null) psAlgorithm.cleanupTemporalDir();

        notify(DownloadManagerService.MESSAGE_DELETED);

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
        length = 0;
        errCode = errorCode;
        errObject = null;
        unknownLength = false;
        threads = new Thread[0];
        fallbackResumeOffset = 0;
        blocks = null;
        blockAcquired = null;

        if (rollback) current = 0;
        if (persistChanges) writeThisToFile();
    }

    private void initializer() {
        init = runAsync(DownloadInitializer.mId, new DownloadInitializer(this));
    }

    private void writeThisToFileAsync() {
        runAsync(-2, this::writeThisToFile);
    }

    /**
     * Write this {@link DownloadMission} to the meta file asynchronously
     * if no thread is already running.
     */
    void writeThisToFile() {
        synchronized (LOCK) {
            if (metadata == null) return;
            Utility.writeToFile(metadata, this);
            writingToFile = false;
        }
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
            return length;
        }

        calculated = offsets[current < offsets.length ? current : (offsets.length - 1)] + length;
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
        writeThisToFileAsync();
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
        if (urls.length < 1) return false;
        return (isPsFailed() || errCode == ERROR_POSTPROCESSING_HOLD) || isFinished();
    }

    /**
     * Indicates if mission urls has expired and there an attempt to renovate them
     *
     * @return {@code true} if the mission is running a recovery procedure, otherwise, {@code false}
     */
    public boolean isRecovering() {
        return threads.length > 0 && threads[0] instanceof DownloadMissionRecover && threads[0].isAlive();
    }

    private void doPostprocessing() {
        errCode = ERROR_NOTHING;
        errObject = null;
        Thread thread = Thread.currentThread();

        notifyPostProcessing(1);

        if (DEBUG) {
            thread.setName("[" + TAG + "]  ps = " + psAlgorithm + "  filename = " + storage.getName());
        }

        Exception exception = null;

        try {
            psAlgorithm.run(this);
        } catch (Exception err) {
            Log.e(TAG, "Post-processing failed. " + psAlgorithm.toString(), err);

            if (err instanceof InterruptedIOException || err instanceof ClosedByInterruptException || thread.isInterrupted()) {
                notifyError(DownloadMission.ERROR_POSTPROCESSING_STOPPED, null);
                return;
            }

            if (errCode == ERROR_NOTHING) errCode = ERROR_POSTPROCESSING;

            exception = err;
        } finally {
            notifyPostProcessing(errCode == ERROR_NOTHING ? 2 : 0);
        }

        if (errCode != ERROR_NOTHING) {
            if (exception == null) exception = errObject;
            notifyError(ERROR_POSTPROCESSING, exception);
            return;
        }

        notifyFinished();
    }

    /**
     * Attempts to recover the download
     *
     * @param errorCode error code which trigger the recovery procedure
     */
    void doRecover(int errorCode) {
        Log.i(TAG, "Attempting to recover the mission: " + storage.getName());

        if (recoveryInfo == null) {
            notifyError(errorCode, null);
            urls = new String[0];// mark this mission as dead
            return;
        }

        joinForThreads(0);

        threads = new Thread[]{
                runAsync(DownloadMissionRecover.mID, new DownloadMissionRecover(this, errorCode))
        };
    }

    private boolean deleteThisFromFile() {
        synchronized (LOCK) {
            boolean res = metadata.delete();
            metadata = null;
            return res;
        }
    }

    /**
     * run a new thread
     *
     * @param id  id of new thread (used for debugging only)
     * @param who the Runnable whose {@code run} method is invoked.
     */
    private Thread runAsync(int id, Runnable who) {
        return runAsync(id, new Thread(who));
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

    /**
     * Waits at most {@code millis} milliseconds for the thread to die
     *
     * @param millis the time to wait in milliseconds
     */
    private void joinForThreads(int millis) {
        final Thread currentThread = Thread.currentThread();

        if (init != null && init != currentThread && init.isAlive()) {
            init.interrupt();

            if (millis > 0) {
                try {
                    init.join(millis);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Initializer thread is still running", e);
                    return;
                }
            }
        }

        // if a thread is still alive, possible reasons:
        //      slow device
        //      the user is spamming start/pause buttons
        //      start() method called quickly after pause()

        for (Thread thread : threads) {
            if (!thread.isAlive() || thread == Thread.currentThread()) continue;
            thread.interrupt();
        }

        try {
            for (Thread thread : threads) {
                if (!thread.isAlive()) continue;
                if (DEBUG) {
                    Log.w(TAG, "thread alive: " + thread.getName());
                }
                if (millis > 0) thread.join(millis);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("A download thread is still running", e);
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

    public static class Block {
        public int position;
        public int done;
    }

    private static class Lock implements Serializable {
        // java.lang.Object cannot be used because is not serializable
    }
}
