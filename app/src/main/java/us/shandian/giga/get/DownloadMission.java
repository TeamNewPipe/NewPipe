package us.shandian.giga.get;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.schabi.newpipe.Downloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.SSLException;

import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadMission extends Mission {
    private static final long serialVersionUID = 4L;// last bump: 27 march 2019

    static final int BUFFER_SIZE = 64 * 1024;
    final static int BLOCK_SIZE = 512 * 1024;

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
     * Number of blocks the size of {@link DownloadMission#BLOCK_SIZE}
     */
    long blocks = -1;

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
     * The current resource to download, see {@code urls[current]} and {@code offsets[current]}
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

    public int threadCount = 3;
    boolean fallback;
    private int finishCount;
    public transient boolean running;
    public boolean enqueued;

    public int errCode = ERROR_NOTHING;

    public Exception errObject = null;
    public transient boolean recovered;
    public transient Handler mHandler;
    private transient boolean mWritingToFile;

    @SuppressWarnings("UseSparseArrays")// LongSparseArray is not serializable
    final HashMap<Long, Boolean> blockState = new HashMap<>();
    final List<Long> threadBlockPositions = new ArrayList<>();
    final List<Long> threadBytePositions = new ArrayList<>();

    private transient boolean deleted;
    int currentThreadCount;
    public transient volatile Thread[] threads = new Thread[0];
    private transient Thread init = null;

    protected DownloadMission() {

    }

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

    private void checkBlock(long block) {
        if (block < 0 || block >= blocks) {
            throw new IllegalArgumentException("illegal block identifier");
        }
    }

    /**
     * Check if a block is reserved
     *
     * @param block the block identifier
     * @return true if the block is reserved and false if otherwise
     */
    boolean isBlockPreserved(long block) {
        checkBlock(block);
        //noinspection ConstantConditions
        return blockState.containsKey(block) ? blockState.get(block) : false;
    }

    void preserveBlock(long block) {
        checkBlock(block);
        synchronized (blockState) {
            blockState.put(block, true);
        }
    }

    /**
     * Set the block of the file
     *
     * @param threadId the identifier of the thread
     * @param position the block of the thread
     */
    void setBlockPosition(int threadId, long position) {
        threadBlockPositions.set(threadId, position);
    }

    /**
     * Get the block of a file
     *
     * @param threadId the identifier of the thread
     * @return the block for the thread
     */
    long getBlockPosition(int threadId) {
        return threadBlockPositions.get(threadId);
    }

    /**
     * Save the position of the desired thread
     *
     * @param threadId the identifier of the thread
     * @param position the relative position in bytes or zero
     */
    void setThreadBytePosition(int threadId, long position) {
        threadBytePositions.set(threadId, position);
    }

    /**
     * Get position inside of the thread, where thread will be resumed
     *
     * @param threadId the identifier of the thread
     * @return the relative position in bytes or zero
     */
    long getThreadBytePosition(int threadId) {
        return threadBytePositions.get(threadId);
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
            } else if (err.getMessage().contains("ENOSPC")) {
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

        pause();

        notify(DownloadManagerService.MESSAGE_ERROR);
    }

    synchronized void notifyFinished() {
        if (errCode > ERROR_NOTHING) return;

        finishCount++;

        if (finishCount == currentThreadCount) {
            if (errCode != ERROR_NOTHING) return;

            if (DEBUG) {
                Log.d(TAG, "onFinish" + (current + 1) + "/" + urls.length);
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

        synchronized (blockState) {
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

        if (current >= urls.length && psAlgorithm != null) {
            runAsync(1, () -> {
                if (doPostprocessing()) {
                    running = false;
                    deleteThisFromFile();

                    notify(DownloadManagerService.MESSAGE_FINISHED);
                }
            });

            return;
        }

        if (blocks < 0) {
            initializer();
            return;
        }

        init = null;

        if (threads == null || threads.length < 1) {
            threads = new Thread[currentThreadCount];
        }

        if (fallback) {
            if (unknownLength) {
                done = 0;
                length = 0;
            }

            threads[0] = runAsync(1, new DownloadRunnableFallback(this));
        } else {
            for (int i = 0; i < currentThreadCount; i++) {
                threads[i] = runAsync(i + 1, new DownloadRunnable(this, i));
            }
        }
    }

    /**
     * Pause the mission
     */
    public synchronized void pause() {
        if (!running) return;

        if (isPsRunning()) {
            if (DEBUG) {
                Log.w(TAG, "pause during post-processing is not applicable.");
            }
            return;
        }

        running = false;
        recovered = true;

        if (init != null && Thread.currentThread() != init && init.isAlive()) {
            init.interrupt();
            synchronized (blockState) {
                resetState(false, true, ERROR_NOTHING);
            }
            return;
        }

        if (DEBUG && blocks == 0) {
            Log.w(TAG, "pausing a download that can not be resumed (range requests not allowed by the server).");
        }

        if (threads == null || Thread.currentThread().isInterrupted()) {
            writeThisToFile();
            return;
        }

        // wait for all threads are suspended before save the state
        runAsync(-1, () -> {
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
        });
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
        blocks = -1;
        errCode = errorCode;
        errObject = null;
        fallback = false;
        unknownLength = false;
        finishCount = 0;
        threadBlockPositions.clear();
        threadBytePositions.clear();
        blockState.clear();
        threads = new Thread[0];

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
        synchronized (blockState) {
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
        return blocks >= 0; // DownloadMissionInitializer was executed
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
        synchronized (blockState) {
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
        int statusCode;

        HttpError(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public String getMessage() {
            return "HTTP " + String.valueOf(statusCode);
        }
    }
}
