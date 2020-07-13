package us.shandian.giga.service;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.get.sqlite.FinishedMissionStore;
import us.shandian.giga.io.StoredDirectoryHelper;
import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManager {
    private static final String TAG = DownloadManager.class.getSimpleName();

    enum NetworkState {Unavailable, Operating, MeteredOperating}

    public final static int SPECIAL_NOTHING = 0;
    public final static int SPECIAL_PENDING = 1;
    public final static int SPECIAL_FINISHED = 2;

    public static final String TAG_AUDIO = "audio";
    public static final String TAG_VIDEO = "video";
    private static final String DOWNLOADS_METADATA_FOLDER = "pending_downloads";

    private final FinishedMissionStore mFinishedMissionStore;

    private final ArrayList<DownloadMission> mMissionsPending = new ArrayList<>();
    private final ArrayList<FinishedMission> mMissionsFinished;

    private final Handler mHandler;
    private final File mPendingMissionsDir;

    private NetworkState mLastNetworkStatus = NetworkState.Unavailable;

    int mPrefMaxRetry;
    boolean mPrefMeteredDownloads;
    boolean mPrefQueueLimit;
    private boolean mSelfMissionsControl;

    StoredDirectoryHelper mMainStorageAudio;
    StoredDirectoryHelper mMainStorageVideo;

    /**
     * Create a new instance
     *
     * @param context Context for the data source for finished downloads
     * @param handler Thread required for Messaging
     */
    DownloadManager(@NonNull Context context, Handler handler, StoredDirectoryHelper storageVideo, StoredDirectoryHelper storageAudio) {
        if (DEBUG) {
            Log.d(TAG, "new DownloadManager instance. 0x" + Integer.toHexString(this.hashCode()));
        }

        mFinishedMissionStore = new FinishedMissionStore(context);
        mHandler = handler;
        mMainStorageAudio = storageAudio;
        mMainStorageVideo = storageVideo;
        mMissionsFinished = loadFinishedMissions();
        mPendingMissionsDir = getPendingDir(context);

        loadPendingMissions(context);
    }

    private static File getPendingDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir(DOWNLOADS_METADATA_FOLDER);
        if (testDir(dir)) return dir;

        dir = new File(context.getFilesDir(), DOWNLOADS_METADATA_FOLDER);
        if (testDir(dir)) return dir;

        throw new RuntimeException("path to pending downloads are not accessible");
    }

    private static boolean testDir(@Nullable File dir) {
        if (dir == null) return false;

        try {
            if (!Utility.mkdir(dir, false)) {
                Log.e(TAG, "testDir() cannot create the directory in path: " + dir.getAbsolutePath());
                return false;
            }

            File tmp = new File(dir, ".tmp");
            if (!tmp.createNewFile()) return false;
            return tmp.delete();// if the file was created, SHOULD BE deleted too
        } catch (Exception e) {
            Log.e(TAG, "testDir() failed: " + dir.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Loads finished missions from the data source
     */
    private ArrayList<FinishedMission> loadFinishedMissions() {
        ArrayList<FinishedMission> finishedMissions = mFinishedMissionStore.loadFinishedMissions();

        // check if the files exists, otherwise, forget the download
        for (int i = finishedMissions.size() - 1; i >= 0; i--) {
            FinishedMission mission = finishedMissions.get(i);

            if (!mission.storage.existsAsFile()) {
                if (DEBUG) Log.d(TAG, "downloaded file removed: " + mission.storage.getName());

                mFinishedMissionStore.deleteMission(mission);
                finishedMissions.remove(i);
            }
        }

        return finishedMissions;
    }

    private void loadPendingMissions(Context ctx) {
        File[] subs = mPendingMissionsDir.listFiles();

        if (subs == null) {
            Log.e(TAG, "listFiles() returned null");
            return;
        }
        if (subs.length < 1) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Loading pending downloads from directory: " + mPendingMissionsDir.getAbsolutePath());
        }

        File tempDir = pickAvailableTemporalDir(ctx);
        Log.i(TAG, "using '" + tempDir + "' as temporal directory");

        for (File sub : subs) {
            if (!sub.isFile()) continue;
            if (sub.getName().equals(".tmp")) continue;

            DownloadMission mis = Utility.readFromFile(sub);
            if (mis == null || mis.isFinished()) {
                //noinspection ResultOfMethodCallIgnored
                sub.delete();
                continue;
            }

            mis.threads = new Thread[0];

            boolean exists;
            try {
                mis.storage = StoredFileHelper.deserialize(mis.storage, ctx);
                exists = !mis.storage.isInvalid() && mis.storage.existsAsFile();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to load the file source of " + mis.storage.toString(), ex);
                mis.storage.invalidate();
                exists = false;
            }

            if (mis.isPsRunning()) {
                if (mis.psAlgorithm.worksOnSameFile) {
                    // Incomplete post-processing results in a corrupted download file
                    // because the selected algorithm works on the same file to save space.
                    // the file will be deleted if the storage API
                    // is Java IO (avoid showing the "Save as..." dialog)
                    if (exists && mis.storage.isDirect() && !mis.storage.delete())
                        Log.w(TAG, "Unable to delete incomplete download file: " + sub.getPath());
                }

                mis.psState = 0;
                mis.errCode = DownloadMission.ERROR_POSTPROCESSING_STOPPED;
            } else if (!exists) {
                tryRecover(mis);

                // the progress is lost, reset mission state
                if (mis.isInitialized())
                    mis.resetState(true, true, DownloadMission.ERROR_PROGRESS_LOST);
            }

            if (mis.psAlgorithm != null) {
                mis.psAlgorithm.cleanupTemporalDir();
                mis.psAlgorithm.setTemporalDir(tempDir);
            }

            mis.metadata = sub;
            mis.maxRetry = mPrefMaxRetry;
            mis.mHandler = mHandler;

            mMissionsPending.add(mis);
        }

        if (mMissionsPending.size() > 1)
            Collections.sort(mMissionsPending, (mission1, mission2) -> Long.compare(mission1.timestamp, mission2.timestamp));
    }

    /**
     * Start a new download mission
     *
     * @param mission the new download mission to add and run (if possible)
     */
    void startMission(DownloadMission mission) {
        synchronized (this) {
            mission.timestamp = System.currentTimeMillis();
            mission.mHandler = mHandler;
            mission.maxRetry = mPrefMaxRetry;

            // create metadata file
            while (true) {
                mission.metadata = new File(mPendingMissionsDir, String.valueOf(mission.timestamp));
                if (!mission.metadata.isFile() && !mission.metadata.exists()) {
                    try {
                        if (!mission.metadata.createNewFile())
                            throw new RuntimeException("Cant create download metadata file");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
                mission.timestamp = System.currentTimeMillis();
            }

            mSelfMissionsControl = true;
            mMissionsPending.add(mission);

            // Before continue, save the metadata in case the internet connection is not available
            Utility.writeToFile(mission.metadata, mission);

            if (mission.storage == null) {
                // noting to do here
                mission.errCode = DownloadMission.ERROR_FILE_CREATION;
                if (mission.errObject != null)
                    mission.errObject = new IOException("DownloadMission.storage == NULL");
                return;
            }

            boolean start = !mPrefQueueLimit || getRunningMissionsCount() < 1;

            if (canDownloadInCurrentNetwork() && start) {
                mission.start();
            }
        }
    }


    public void resumeMission(DownloadMission mission) {
        if (!mission.running) {
            mission.start();
        }
    }

    public void pauseMission(DownloadMission mission) {
        if (mission.running) {
            mission.setEnqueued(false);
            mission.pause();
        }
    }

    public void deleteMission(Mission mission) {
        synchronized (this) {
            if (mission instanceof DownloadMission) {
                mMissionsPending.remove(mission);
            } else if (mission instanceof FinishedMission) {
                mMissionsFinished.remove(mission);
                mFinishedMissionStore.deleteMission(mission);
            }

            mission.delete();
        }
    }

    public void forgetMission(StoredFileHelper storage) {
        synchronized (this) {
            Mission mission = getAnyMission(storage);
            if (mission == null) return;

            if (mission instanceof DownloadMission) {
                mMissionsPending.remove(mission);
            } else if (mission instanceof FinishedMission) {
                mMissionsFinished.remove(mission);
                mFinishedMissionStore.deleteMission(mission);
            }

            mission.storage = null;
            mission.delete();
        }
    }

    public void tryRecover(DownloadMission mission) {
        StoredDirectoryHelper mainStorage = getMainStorage(mission.storage.getTag());

        if (!mission.storage.isInvalid() && mission.storage.create()) return;

        // using javaIO cannot recreate the file
        // using SAF in older devices (no tree available)
        //
        // force the user to pick again the save path
        mission.storage.invalidate();

        if (mainStorage == null) return;

        // if the user has changed the save path before this download, the original save path will be lost
        StoredFileHelper newStorage = mainStorage.createFile(mission.storage.getName(), mission.storage.getType());

        if (newStorage != null) mission.storage = newStorage;
    }


    /**
     * Get a pending mission by its path
     *
     * @param storage where the file possible is stored
     * @return the mission or null if no such mission exists
     */
    @Nullable
    private DownloadMission getPendingMission(StoredFileHelper storage) {
        for (DownloadMission mission : mMissionsPending) {
            if (mission.storage.equals(storage)) {
                return mission;
            }
        }
        return null;
    }

    /**
     * Get a finished mission by its path
     *
     * @param storage where the file possible is stored
     * @return the mission index or -1 if no such mission exists
     */
    private int getFinishedMissionIndex(StoredFileHelper storage) {
        for (int i = 0; i < mMissionsFinished.size(); i++) {
            if (mMissionsFinished.get(i).storage.equals(storage)) {
                return i;
            }
        }

        return -1;
    }

    private Mission getAnyMission(StoredFileHelper storage) {
        synchronized (this) {
            Mission mission = getPendingMission(storage);
            if (mission != null) return mission;

            int idx = getFinishedMissionIndex(storage);
            if (idx >= 0) return mMissionsFinished.get(idx);
        }

        return null;
    }

    int getRunningMissionsCount() {
        int count = 0;
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running && !mission.isPsFailed() && !mission.isFinished())
                    count++;
            }
        }

        return count;
    }

    public void pauseAllMissions(boolean force) {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (!mission.running || mission.isPsRunning() || mission.isFinished()) continue;

                if (force) {
                    // avoid waiting for threads
                    mission.init = null;
                    mission.threads = new Thread[0];
                }

                mission.pause();
            }
        }
    }

    public void startAllMissions() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running || mission.isCorrupt()) continue;

                mission.start();
            }
        }
    }

    /**
     * Set a pending download as finished
     *
     * @param mission the desired mission
     */
    void setFinished(DownloadMission mission) {
        synchronized (this) {
            mMissionsPending.remove(mission);
            mMissionsFinished.add(0, new FinishedMission(mission));
            mFinishedMissionStore.addFinishedMission(mission);
        }
    }

    /**
     * runs one or multiple missions in from queue if possible
     *
     * @return true if one or multiple missions are running, otherwise, false
     */
    boolean runMissions() {
        synchronized (this) {
            if (mMissionsPending.size() < 1) return false;
            if (!canDownloadInCurrentNetwork()) return false;

            if (mPrefQueueLimit) {
                for (DownloadMission mission : mMissionsPending)
                    if (!mission.isFinished() && mission.running) return true;
            }

            boolean flag = false;
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running || !mission.enqueued || mission.isFinished())
                    continue;

                resumeMission(mission);
                if (mission.errCode != DownloadMission.ERROR_NOTHING) continue;

                if (mPrefQueueLimit) return true;
                flag = true;
            }

            return flag;
        }
    }

    public MissionIterator getIterator() {
        mSelfMissionsControl = true;
        return new MissionIterator();
    }

    /**
     * Forget all finished downloads, but, doesn't delete any file
     */
    public void forgetFinishedDownloads() {
        synchronized (this) {
            for (FinishedMission mission : mMissionsFinished) {
                mFinishedMissionStore.deleteMission(mission);
            }
            mMissionsFinished.clear();
        }
    }

    private boolean canDownloadInCurrentNetwork() {
        if (mLastNetworkStatus == NetworkState.Unavailable) return false;
        return !(mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating);
    }

    void handleConnectivityState(NetworkState currentStatus, boolean updateOnly) {
        if (currentStatus == mLastNetworkStatus) return;

        mLastNetworkStatus = currentStatus;
        if (currentStatus == NetworkState.Unavailable) return;

        if (!mSelfMissionsControl || updateOnly) {
            return;// don't touch anything without the user interaction
        }

        boolean isMetered = mPrefMeteredDownloads && mLastNetworkStatus == NetworkState.MeteredOperating;

        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.isCorrupt() || mission.isPsRunning()) continue;

                if (mission.running && isMetered) {
                    mission.pause();
                } else if (!mission.running && !isMetered && mission.enqueued) {
                    mission.start();
                    if (mPrefQueueLimit) break;
                }
            }
        }
    }

    void updateMaximumAttempts() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) mission.maxRetry = mPrefMaxRetry;
        }
    }

    public MissionState checkForExistingMission(StoredFileHelper storage) {
        synchronized (this) {
            DownloadMission pending = getPendingMission(storage);

            if (pending == null) {
                if (getFinishedMissionIndex(storage) >= 0) return MissionState.Finished;
            } else {
                if (pending.isFinished()) {
                    return MissionState.Finished;// this never should happen (race-condition)
                } else {
                    return pending.running ? MissionState.PendingRunning : MissionState.Pending;
                }
            }
        }

        return MissionState.None;
    }

    private static boolean isDirectoryAvailable(File directory) {
        return directory != null && directory.canWrite() && directory.exists();
    }

    static File pickAvailableTemporalDir(@NonNull Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        if (isDirectoryAvailable(dir)) return dir;

        dir = ctx.getFilesDir();
        if (isDirectoryAvailable(dir)) return dir;

        // this never should happen
        dir = ctx.getDir("muxing_tmp", Context.MODE_PRIVATE);
        if (isDirectoryAvailable(dir)) return dir;

        // fallback to cache dir
        dir = ctx.getCacheDir();
        if (isDirectoryAvailable(dir)) return dir;

        throw new RuntimeException("Not temporal directories are available");
    }

    @Nullable
    private StoredDirectoryHelper getMainStorage(@NonNull String tag) {
        if (tag.equals(TAG_AUDIO)) return mMainStorageAudio;
        if (tag.equals(TAG_VIDEO)) return mMainStorageVideo;

        Log.w(TAG, "Unknown download category, not [audio video]: " + tag);

        return null;// this never should happen
    }

    public class MissionIterator extends DiffUtil.Callback {
        final Object FINISHED = new Object();
        final Object PENDING = new Object();

        ArrayList<Object> snapshot;
        ArrayList<Object> current;
        ArrayList<Mission> hidden;

        boolean hasFinished = false;

        private MissionIterator() {
            hidden = new ArrayList<>(2);
            current = null;
            snapshot = getSpecialItems();
        }

        private ArrayList<Object> getSpecialItems() {
            synchronized (DownloadManager.this) {
                ArrayList<Mission> pending = new ArrayList<>(mMissionsPending);
                ArrayList<Mission> finished = new ArrayList<>(mMissionsFinished);
                ArrayList<Mission> remove = new ArrayList<>(hidden);

                // hide missions (if required)
                Iterator<Mission> iterator = remove.iterator();
                while (iterator.hasNext()) {
                    Mission mission = iterator.next();
                    if (pending.remove(mission) || finished.remove(mission)) iterator.remove();
                }

                int fakeTotal = pending.size();
                if (fakeTotal > 0) fakeTotal++;

                fakeTotal += finished.size();
                if (finished.size() > 0) fakeTotal++;

                ArrayList<Object> list = new ArrayList<>(fakeTotal);
                if (pending.size() > 0) {
                    list.add(PENDING);
                    list.addAll(pending);
                }
                if (finished.size() > 0) {
                    list.add(FINISHED);
                    list.addAll(finished);
                }

                hasFinished = finished.size() > 0;

                return list;
            }
        }

        public MissionItem getItem(int position) {
            Object object = snapshot.get(position);

            if (object == PENDING) return new MissionItem(SPECIAL_PENDING);
            if (object == FINISHED) return new MissionItem(SPECIAL_FINISHED);

            return new MissionItem(SPECIAL_NOTHING, (Mission) object);
        }

        public int getSpecialAtItem(int position) {
            Object object = snapshot.get(position);

            if (object == PENDING) return SPECIAL_PENDING;
            if (object == FINISHED) return SPECIAL_FINISHED;

            return SPECIAL_NOTHING;
        }


        public void start() {
            current = getSpecialItems();
        }

        public void end() {
            snapshot = current;
            current = null;
        }

        public void hide(Mission mission) {
            hidden.add(mission);
        }

        public void unHide(Mission mission) {
            hidden.remove(mission);
        }

        public boolean hasFinishedMissions() {
            return hasFinished;
        }

        /**
         * Check if exists missions running and paused. Corrupted and hidden missions are not counted
         *
         * @return two-dimensional array contains the current missions state.
         * 1° entry: true if has at least one mission running
         * 2° entry: true if has at least one mission paused
         */
        public boolean[] hasValidPendingMissions() {
            boolean running = false;
            boolean paused = false;

            synchronized (DownloadManager.this) {
                for (DownloadMission mission : mMissionsPending) {
                    if (hidden.contains(mission) || mission.isCorrupt())
                        continue;

                    if (mission.running)
                        running = true;
                    else
                        paused = true;
                }
            }

            return new boolean[]{running, paused};
        }


        @Override
        public int getOldListSize() {
            return snapshot.size();
        }

        @Override
        public int getNewListSize() {
            return current.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return snapshot.get(oldItemPosition) == current.get(newItemPosition);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Object x = snapshot.get(oldItemPosition);
            Object y = current.get(newItemPosition);

            if (x instanceof Mission && y instanceof Mission) {
                return ((Mission) x).storage.equals(((Mission) y).storage);
            }

            return false;
        }
    }

    public class MissionItem {
        public int special;
        public Mission mission;

        MissionItem(int s, Mission m) {
            special = s;
            mission = m;
        }

        MissionItem(int s) {
            this(s, null);
        }
    }

}
