package us.shandian.giga.service;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.util.DiffUtil;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.get.sqlite.DownloadDataSource;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManager {
    private static final String TAG = DownloadManager.class.getSimpleName();

    enum NetworkState {Unavailable, WifiOperating, MobileOperating, OtherOperating}

    public final static int SPECIAL_NOTHING = 0;
    public final static int SPECIAL_PENDING = 1;
    public final static int SPECIAL_FINISHED = 2;

    private final DownloadDataSource mDownloadDataSource;

    private final ArrayList<DownloadMission> mMissionsPending = new ArrayList<>();
    private final ArrayList<FinishedMission> mMissionsFinished;

    private final Handler mHandler;
    private final File mPendingMissionsDir;

    private NetworkState mLastNetworkStatus = NetworkState.Unavailable;

    int mPrefMaxRetry;
    boolean mPrefCrossNetwork;

    /**
     * Create a new instance
     *
     * @param context Context for the data source for finished downloads
     * @param handler Thread required for Messaging
     */
    DownloadManager(@NonNull Context context, Handler handler) {
        if (DEBUG) {
            Log.d(TAG, "new DownloadManager instance. 0x" + Integer.toHexString(this.hashCode()));
        }

        mDownloadDataSource = new DownloadDataSource(context);
        mHandler = handler;
        mMissionsFinished = loadFinishedMissions();
        mPendingMissionsDir = getPendingDir(context);

        if (!Utility.mkdir(mPendingMissionsDir, false)) {
            throw new RuntimeException("failed to create pending_downloads in data directory");
        }

        loadPendingMissions();
    }

    private static File getPendingDir(@NonNull Context context) {
        //File dir = new File(ContextCompat.getDataDir(context), "pending_downloads");
        File dir = context.getExternalFilesDir("pending_downloads");

        if (dir == null) {
            // One of the following paths are not accessible ¿unmounted internal memory?
            //        /storage/emulated/0/Android/data/org.schabi.newpipe[.debug]/pending_downloads
            //        /sdcard/Android/data/org.schabi.newpipe[.debug]/pending_downloads
            Log.w(TAG, "path to pending downloads are not accessible");
        }

        return dir;
    }

    /**
     * Loads finished missions from the data source
     */
    private ArrayList<FinishedMission> loadFinishedMissions() {
        ArrayList<FinishedMission> finishedMissions = mDownloadDataSource.loadFinishedMissions();

        // missions always is stored by creation order, simply reverse the list
        ArrayList<FinishedMission> result = new ArrayList<>(finishedMissions.size());
        for (int i = finishedMissions.size() - 1; i >= 0; i--) {
            FinishedMission mission = finishedMissions.get(i);
            File file = mission.getDownloadedFile();

            if (!file.isFile()) {
                if (DEBUG) {
                    Log.d(TAG, "downloaded file removed: " + file.getAbsolutePath());
                }
                mDownloadDataSource.deleteMission(mission);
                continue;
            }

            result.add(mission);
        }

        return result;
    }

    private void loadPendingMissions() {
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

        for (File sub : subs) {
            if (sub.isFile()) {
                DownloadMission mis = Utility.readFromFile(sub);

                if (mis == null) {
                    //noinspection ResultOfMethodCallIgnored
                    sub.delete();
                } else {
                    if (mis.isFinished()) {
                        //noinspection ResultOfMethodCallIgnored
                        sub.delete();
                        continue;
                    }

                    File dl = mis.getDownloadedFile();
                    boolean exists = dl.exists();

                    if (mis.isPsRunning()) {
                        if (mis.postprocessingThis) {
                            // Incomplete post-processing results in a corrupted download file
                            // because the selected algorithm works on the same file to save space.
                            if (exists && dl.isFile() && !dl.delete())
                                Log.w(TAG, "Unable to delete incomplete download file: " + sub.getPath());

                            exists = true;
                        }

                        mis.postprocessingState = 0;
                        mis.errCode = DownloadMission.ERROR_POSTPROCESSING;
                        mis.errObject = new RuntimeException("stopped unexpectedly");
                    } else if (exists && !dl.isFile()) {
                        // probably a folder, this should never happens
                        if (!sub.delete()) {
                            Log.w(TAG, "Unable to delete serialized file: " + sub.getPath());
                        }
                        continue;
                    }

                    if (!exists) {
                        // downloaded file deleted, reset mission state
                        DownloadMission m = new DownloadMission(mis.urls, mis.name, mis.location, mis.kind, mis.postprocessingName, mis.postprocessingArgs);
                        m.timestamp = mis.timestamp;
                        m.threadCount = mis.threadCount;
                        m.source = mis.source;
                        m.maxRetry = mis.maxRetry;
                        m.nearLength = mis.nearLength;
                        mis = m;
                    }

                    mis.running = false;
                    mis.recovered = exists;
                    mis.metadata = sub;
                    mis.mHandler = mHandler;

                    mMissionsPending.add(mis);
                }
            }
        }

        if (mMissionsPending.size() > 1) {
            Collections.sort(mMissionsPending, (mission1, mission2) -> Long.compare(mission1.timestamp, mission2.timestamp));
        }
    }

    /**
     * Start a new download mission
     *
     * @param urls     the list of urls to download
     * @param location the location
     * @param name     the name of the file to create
     * @param kind     type of file (a: audio  v: video  s: subtitle ?: file-extension defined)
     * @param threads  the number of threads maximal used to download chunks of the file.
     * @param psName   the name of the required post-processing algorithm, or {@code null} to ignore.
     * @param source   source url of the resource
     * @param psArgs   the arguments for the post-processing algorithm.
     */
    void startMission(String[] urls, String location, String name, char kind, int threads,
                      String source, String psName, String[] psArgs, long nearLength) {
        synchronized (this) {
            // check for existing pending download
            DownloadMission pendingMission = getPendingMission(location, name);
            if (pendingMission != null) {
                // generate unique filename (?)
                try {
                    name = generateUniqueName(location, name);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to generate unique name", e);
                    name = System.currentTimeMillis() + name;
                    Log.i(TAG, "Using " + name);
                }
            } else {
                // check for existing finished download
                int index = getFinishedMissionIndex(location, name);
                if (index >= 0) mDownloadDataSource.deleteMission(mMissionsFinished.remove(index));
            }

            DownloadMission mission = new DownloadMission(urls, name, location, kind, psName, psArgs);
            mission.timestamp = System.currentTimeMillis();
            mission.threadCount = threads;
            mission.source = source;
            mission.mHandler = mHandler;
            mission.maxRetry = mPrefMaxRetry;
            mission.nearLength = nearLength;

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

            mMissionsPending.add(mission);

            // Before starting, save the state in case the internet connection is not available
            Utility.writeToFile(mission.metadata, mission);

            if (canDownloadInCurrentNetwork() && (getRunningMissionsCount() < 1)) {
                mission.start();
                mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
            }
        }
    }


    public void resumeMission(DownloadMission mission) {
        if (!mission.running) {
            mission.start();
            mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_RUNNING);
        }
    }

    public void pauseMission(DownloadMission mission) {
        if (mission.running) {
            mission.pause();
            mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_PAUSED);
        }
    }

    public void deleteMission(Mission mission) {
        synchronized (this) {
            if (mission instanceof DownloadMission) {
                mMissionsPending.remove(mission);
            } else if (mission instanceof FinishedMission) {
                mMissionsFinished.remove(mission);
                mDownloadDataSource.deleteMission(mission);
            }

            mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_DELETED);
            mission.delete();
        }
    }


    /**
     * Get a pending mission by its location and name
     *
     * @param location the location
     * @param name     the name
     * @return the mission or null if no such mission exists
     */
    @Nullable
    private DownloadMission getPendingMission(String location, String name) {
        for (DownloadMission mission : mMissionsPending) {
            if (location.equalsIgnoreCase(mission.location) && name.equalsIgnoreCase(mission.name)) {
                return mission;
            }
        }
        return null;
    }

    /**
     * Get a finished mission by its location and name
     *
     * @param location the location
     * @param name     the name
     * @return the mission index or -1 if no such mission exists
     */
    private int getFinishedMissionIndex(String location, String name) {
        for (int i = 0; i < mMissionsFinished.size(); i++) {
            FinishedMission mission = mMissionsFinished.get(i);
            if (location.equalsIgnoreCase(mission.location) && name.equalsIgnoreCase(mission.name)) {
                return i;
            }
        }

        return -1;
    }

    public Mission getAnyMission(String location, String name) {
        synchronized (this) {
            Mission mission = getPendingMission(location, name);
            if (mission != null) return mission;

            int idx = getFinishedMissionIndex(location, name);
            if (idx >= 0) return mMissionsFinished.get(idx);
        }

        return null;
    }

    int getRunningMissionsCount() {
        int count = 0;
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running && !mission.isFinished() && !mission.isPsFailed())
                    count++;
            }
        }

        return count;
    }

    void pauseAllMissions() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) mission.pause();
        }
    }


    /**
     * Splits the filename into name and extension
     * <p>
     * Dots are ignored if they appear: not at all, at the beginning of the file,
     * at the end of the file
     *
     * @param name the name to split
     * @return a string array with a length of 2 containing the name and the extension
     */
    private static String[] splitName(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0 || (dotIndex == name.length() - 1)) {
            return new String[]{name, ""};
        } else {
            return new String[]{name.substring(0, dotIndex), name.substring(dotIndex + 1)};
        }
    }

    /**
     * Generates a unique file name.
     * <p>
     * e.g. "myName (1).txt" if the name "myName.txt" exists.
     *
     * @param location the location (to check for existing files)
     * @param name     the name of the file
     * @return the unique file name
     * @throws IllegalArgumentException if the location is not a directory
     * @throws SecurityException        if the location is not readable
     */
    private static String generateUniqueName(String location, String name) {
        if (location == null) throw new NullPointerException("location is null");
        if (name == null) throw new NullPointerException("name is null");
        File destination = new File(location);
        if (!destination.isDirectory()) {
            throw new IllegalArgumentException("location is not a directory: " + location);
        }
        final String[] nameParts = splitName(name);
        String[] existingName = destination.list((dir, name1) -> name1.startsWith(nameParts[0]));
        Arrays.sort(existingName);
        String newName;
        int downloadIndex = 0;
        do {
            newName = nameParts[0] + " (" + downloadIndex + ")." + nameParts[1];
            ++downloadIndex;
            if (downloadIndex == 1000) {  // Probably an error on our side
                throw new RuntimeException("Too many existing files");
            }
        } while (Arrays.binarySearch(existingName, newName) >= 0);
        return newName;
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
            mDownloadDataSource.addMission(mission);
        }
    }

    /**
     * runs another mission in queue if possible
     *
     * @return true if exits pending missions running or a mission was started, otherwise, false
     */
    boolean runAnotherMission() {
        synchronized (this) {
            if (mMissionsPending.size() < 1) return false;

            int i = getRunningMissionsCount();
            if (i > 0) return true;

            if (!canDownloadInCurrentNetwork()) return false;

            for (DownloadMission mission : mMissionsPending) {
                if (!mission.running && mission.errCode == DownloadMission.ERROR_NOTHING && mission.enqueued) {
                    resumeMission(mission);
                    return true;
                }
            }

            return false;
        }
    }

    public MissionIterator getIterator() {
        return new MissionIterator();
    }

    /**
     * Forget all finished downloads, but, doesn't delete any file
     */
    public void forgetFinishedDownloads() {
        synchronized (this) {
            for (FinishedMission mission : mMissionsFinished) {
                mDownloadDataSource.deleteMission(mission);
            }
            mMissionsFinished.clear();
        }
    }

    private boolean canDownloadInCurrentNetwork() {
        if (mLastNetworkStatus == NetworkState.Unavailable) return false;
        return !(mPrefCrossNetwork && mLastNetworkStatus == NetworkState.MobileOperating);
    }

    void handleConnectivityChange(NetworkState currentStatus) {
        if (currentStatus == mLastNetworkStatus) return;

        mLastNetworkStatus = currentStatus;

        if (currentStatus == NetworkState.Unavailable) {
            return;
        } else if (currentStatus != NetworkState.MobileOperating || !mPrefCrossNetwork) {
            return;
        }

        boolean flag = false;
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) {
                if (mission.running && !mission.isFinished() && !mission.isPsRunning()) {
                    flag = true;
                    mission.pause();
                }
            }
        }

        if (flag) mHandler.sendEmptyMessage(DownloadManagerService.MESSAGE_PAUSED);
    }

    void updateMaximumAttempts() {
        synchronized (this) {
            for (DownloadMission mission : mMissionsPending) mission.maxRetry = mPrefMaxRetry;
        }
    }

    /**
     * Fast check for pending downloads. If exists, the user will be notified
     * TODO: call this method in somewhere
     *
     * @param context the application context
     */
    public static void notifyUserPendingDownloads(Context context) {
        int pending = getPendingDir(context).list().length;
        if (pending < 1) return;

        Toast.makeText(context, context.getString(
                R.string.msg_pending_downloads,
                String.valueOf(pending)
        ), Toast.LENGTH_LONG).show();
    }

    void checkForRunningMission(String location, String name, DownloadManagerService.DMChecker check) {
        boolean listed;
        boolean finished = false;

        synchronized (this) {
            DownloadMission mission = getPendingMission(location, name);
            if (mission != null) {
                listed = true;
            } else {
                listed = getFinishedMissionIndex(location, name) >= 0;
                finished = listed;
            }
        }

        check.callback(listed, finished);
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

        public MissionItem getItemUnsafe(int position) {
            synchronized (DownloadManager.this) {
                int count = mMissionsPending.size();
                int count2 = mMissionsFinished.size();

                if (count > 0) {
                    position--;
                    if (position == -1)
                        return new MissionItem(SPECIAL_PENDING);
                    else if (position < count)
                        return new MissionItem(SPECIAL_NOTHING, mMissionsPending.get(position));
                    else if (position == count && count2 > 0)
                        return new MissionItem(SPECIAL_FINISHED);
                    else
                        position -= count;
                } else {
                    if (count2 > 0 && position == 0) {
                        return new MissionItem(SPECIAL_FINISHED);
                    }
                }

                position--;

                if (count2 < 1) {
                    throw new RuntimeException(
                            String.format("Out of range. pending_count=%s  finished_count=%s  position=%s", count, count2, position)
                    );
                }

                return new MissionItem(SPECIAL_NOTHING, mMissionsFinished.get(position));
            }
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
            return areItemsTheSame(oldItemPosition, newItemPosition);
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
