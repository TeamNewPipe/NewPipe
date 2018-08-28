package us.shandian.giga.get;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadMission implements Serializable {
    private static final long serialVersionUID = 0L;

    private static final String TAG = DownloadMission.class.getSimpleName();

    public interface MissionListener {
        HashMap<MissionListener, Handler> handlerStore = new HashMap<>();

        void onProgressUpdate(DownloadMission downloadMission, long done, long total);

        void onFinish(DownloadMission downloadMission);

        void onError(DownloadMission downloadMission, int errCode);
    }

    public static final int ERROR_SERVER_UNSUPPORTED = 206;
    public static final int ERROR_UNKNOWN = 233;

    /**
     * The filename
     */
    public String name;

    /**
     * The url of the file to download
     */
    public String url;

    /**
     * The directory to store the download
     */
    public String location;

    /**
     * Number of blocks the size of {@link DownloadManager#BLOCK_SIZE}
     */
    public long blocks;

    /**
     * Number of bytes
     */
    public long length;

    /**
     * Number of bytes downloaded
     */
    public long done;
    public int threadCount = 3;
    public int finishCount;
    private final List<Long> threadPositions = new ArrayList<>();
    public final Map<Long, Boolean> blockState = new HashMap<>();
    public boolean running;
    public boolean finished;
    public boolean fallback;
    public int errCode = -1;
    public long timestamp;

    public transient boolean recovered;

    private transient ArrayList<WeakReference<MissionListener>> mListeners = new ArrayList<>();
    private transient boolean mWritingToFile;

    private static final int NO_IDENTIFIER = -1;

    public DownloadMission() {
    }

    public DownloadMission(String name, String url, String location) {
        if (name == null) throw new NullPointerException("name is null");
        if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
        if (url == null) throw new NullPointerException("url is null");
        if (url.isEmpty()) throw new IllegalArgumentException("url is empty");
        if (location == null) throw new NullPointerException("location is null");
        if (location.isEmpty()) throw new IllegalArgumentException("location is empty");
        this.url = url;
        this.name = name;
        this.location = location;
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
    public boolean isBlockPreserved(long block) {
        checkBlock(block);
        return blockState.containsKey(block) ? blockState.get(block) : false;
    }

    public void preserveBlock(long block) {
        checkBlock(block);
        synchronized (blockState) {
            blockState.put(block, true);
        }
    }

    /**
     * Set the download position of the file
     *
     * @param threadId the identifier of the thread
     * @param position the download position of the thread
     */
    public void setPosition(int threadId, long position) {
        threadPositions.set(threadId, position);
    }

    /**
     * Get the position of a thread
     *
     * @param threadId the identifier of the thread
     * @return the position for the thread
     */
    public long getPosition(int threadId) {
        return threadPositions.get(threadId);
    }

    public synchronized void notifyProgress(long deltaLen) {
        if (!running) return;

        if (recovered) {
            recovered = false;
        }

        done += deltaLen;

        if (done > length) {
            done = length;
        }

        if (done != length) {
            writeThisToFile();
        }

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            if (listener != null) {
                MissionListener.handlerStore.get(listener).post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onProgressUpdate(DownloadMission.this, done, length);
                    }
                });
            }
        }
    }

    /**
     * Called by a download thread when it finished.
     */
    public synchronized void notifyFinished() {
        if (errCode > 0) return;

        finishCount++;

        if (finishCount == threadCount) {
            onFinish();
        }
    }

    /**
     * Called when all parts are downloaded
     */
    private void onFinish() {
        if (errCode > 0) return;

        if (DEBUG) {
            Log.d(TAG, "onFinish");
        }

        running = false;
        finished = true;

        deleteThisFromFile();

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            if (listener != null) {
                MissionListener.handlerStore.get(listener).post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFinish(DownloadMission.this);
                    }
                });
            }
        }
    }

    public synchronized void notifyError(int err) {
        errCode = err;

        writeThisToFile();

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            MissionListener.handlerStore.get(listener).post(new Runnable() {
                @Override
                public void run() {
                    listener.onError(DownloadMission.this, errCode);
                }
            });
        }
    }

    public synchronized void addListener(MissionListener listener) {
        Handler handler = new Handler(Looper.getMainLooper());
        MissionListener.handlerStore.put(listener, handler);
        mListeners.add(new WeakReference<>(listener));
    }

    public synchronized void removeListener(MissionListener listener) {
        for (Iterator<WeakReference<MissionListener>> iterator = mListeners.iterator();
             iterator.hasNext(); ) {
            WeakReference<MissionListener> weakRef = iterator.next();
            if (listener != null && listener == weakRef.get()) {
                iterator.remove();
            }
        }
    }

    /**
     * Start downloading with multiple threads.
     */
    public void start() {
        if (!running && !finished) {
            running = true;

            if (!fallback) {
                for (int i = 0; i < threadCount; i++) {
                    if (threadPositions.size() <= i && !recovered) {
                        threadPositions.add((long) i);
                    }
                    new Thread(new DownloadRunnable(this, i)).start();
                }
            } else {
                // In fallback mode, resuming is not supported.
                threadCount = 1;
                done = 0;
                blocks = 0;
                new Thread(new DownloadRunnableFallback(this)).start();
            }
        }
    }

    public void pause() {
        if (running) {
            running = false;
            recovered = true;

            // TODO: Notify & Write state to info file
            // if (err)
        }
    }

    /**
     * Removes the file and the meta file
     */
    public void delete() {
        deleteThisFromFile();
        new File(location, name).delete();
    }

    /**
     * Write this {@link DownloadMission} to the meta file asynchronously
     * if no thread is already running.
     */
    public void writeThisToFile() {
        if (!mWritingToFile) {
            mWritingToFile = true;
            new Thread() {
                @Override
                public void run() {
                    doWriteThisToFile();
                    mWritingToFile = false;
                }
            }.start();
        }
    }

    /**
     * Write this {@link DownloadMission} to the meta file.
     */
    private void doWriteThisToFile() {
        synchronized (blockState) {
            Utility.writeToFile(getMetaFilename(), this);
        }
    }

    private void readObject(ObjectInputStream inputStream)
    throws java.io.IOException, ClassNotFoundException
    {
        inputStream.defaultReadObject();
        mListeners = new ArrayList<>();
    }

    private void deleteThisFromFile() {
        new File(getMetaFilename()).delete();
    }

    /**
     * Get the path of the meta file
     *
     * @return the path to the meta file
     */
    private String getMetaFilename() {
        return location + "/" + name + ".giga";
    }

    public File getDownloadedFile() {
        return new File(location, name);
    }

}
