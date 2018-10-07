package us.shandian.giga.get;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.schabi.newpipe.download.ExtSDDownloadFailedActivity;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerImpl implements DownloadManager {
    private static final String TAG = DownloadManagerImpl.class.getSimpleName();
    private final DownloadDataSource mDownloadDataSource;

    private final ArrayList<DownloadMission> mMissions = new ArrayList<>();
    @NonNull
    private final Context context;

    /**
     * Create a new instance
     *
     * @param searchLocations    the directories to search for unfinished downloads
     * @param downloadDataSource the data source for finished downloads
     */
    public DownloadManagerImpl(Collection<String> searchLocations, DownloadDataSource downloadDataSource) {
        mDownloadDataSource = downloadDataSource;
        this.context = null;
        loadMissions(searchLocations);
    }

    public DownloadManagerImpl(Collection<String> searchLocations, DownloadDataSource downloadDataSource, Context context) {
        mDownloadDataSource = downloadDataSource;
        this.context = context;
        loadMissions(searchLocations);
    }

    @Override
    public int startMission(String url, String location, String name, boolean isAudio, int threads) {
        DownloadMission existingMission = getMissionByLocation(location, name);
        if (existingMission != null) {
            // Already downloaded or downloading
            if (existingMission.finished) {
                // Overwrite mission
                deleteMission(mMissions.indexOf(existingMission));
            } else {
                // Rename file (?)
                try {
                    name = generateUniqueName(location, name);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to generate unique name", e);
                    name = System.currentTimeMillis() + name;
                    Log.i(TAG, "Using " + name);
                }
            }
        }

        DownloadMission mission = new DownloadMission(name, url, location);
        mission.timestamp = System.currentTimeMillis();
        mission.threadCount = threads;
        mission.addListener(new MissionListener(mission));
        new Initializer(mission).start();
        return insertMission(mission);
    }

    @Override
    public void resumeMission(int i) {
        DownloadMission d = getMission(i);
        if (!d.running && d.errCode == -1) {
            d.start();
        }
    }

    @Override
    public void pauseMission(int i) {
        DownloadMission d = getMission(i);
        if (d.running) {
            d.pause();
        }
    }

    @Override
    public void deleteMission(int i) {
        DownloadMission mission = getMission(i);
        if (mission.finished) {
            mDownloadDataSource.deleteMission(mission);
        }
        mission.delete();
        mMissions.remove(i);
    }

    private void loadMissions(Iterable<String> searchLocations) {
        mMissions.clear();
        loadFinishedMissions();
        for (String location : searchLocations) {
            loadMissions(location);
        }

    }

    /**
     * Sort a list of mission by its timestamp. Oldest first
     * @param missions the missions to sort
     */
    static void sortByTimestamp(List<DownloadMission> missions) {
        Collections.sort(missions, new Comparator<DownloadMission>() {
            @Override
            public int compare(DownloadMission o1, DownloadMission o2) {
                return Long.compare(o1.timestamp, o2.timestamp);
            }
        });
    }

    /**
     * Loads finished missions from the data source
     */
    private void loadFinishedMissions() {
        List<DownloadMission> finishedMissions = mDownloadDataSource.loadMissions();
        if (finishedMissions == null) {
            finishedMissions = new ArrayList<>();
        }
        // Ensure its sorted
        sortByTimestamp(finishedMissions);

        mMissions.ensureCapacity(mMissions.size() + finishedMissions.size());
        for (DownloadMission mission : finishedMissions) {
            File downloadedFile = mission.getDownloadedFile();
            if (!downloadedFile.isFile()) {
                if (DEBUG) {
                    Log.d(TAG, "downloaded file removed: " + downloadedFile.getAbsolutePath());
                }
                mDownloadDataSource.deleteMission(mission);
            } else {
                mission.length = downloadedFile.length();
                mission.finished = true;
                mission.running = false;
                mMissions.add(mission);
            }
        }
    }

    private void loadMissions(String location) {

        File f = new File(location);

        if (f.exists() && f.isDirectory()) {
            File[] subs = f.listFiles();

            if (subs == null) {
                Log.e(TAG, "listFiles() returned null");
                return;
            }

            for (File sub : subs) {
                if (sub.isFile() && sub.getName().endsWith(".giga")) {
                    DownloadMission mis = Utility.readFromFile(sub.getAbsolutePath());
                    if (mis != null) {
                        if (mis.finished) {
                            if (!sub.delete()) {
                                Log.w(TAG, "Unable to delete .giga file: " + sub.getPath());
                            }
                            continue;
                        }

                        mis.running = false;
                        mis.recovered = true;
                        insertMission(mis);
                    }
                }
            }
        }
    }

    @Override
    public DownloadMission getMission(int i) {
        return mMissions.get(i);
    }

    @Override
    public int getCount() {
        return mMissions.size();
    }

    private int insertMission(DownloadMission mission) {
        int i = -1;

        DownloadMission m = null;

        if (mMissions.size() > 0) {
            do {
                m = mMissions.get(++i);
            } while (m.timestamp > mission.timestamp && i < mMissions.size() - 1);

            //if (i > 0) i--;
        } else {
            i = 0;
        }

        mMissions.add(i, mission);

        return i;
    }

    /**
     * Get a mission by its location and name
     *
     * @param location the location
     * @param name     the name
     * @return the mission or null if no such mission exists
     */
    private
    @Nullable
    DownloadMission getMissionByLocation(String location, String name) {
        for (DownloadMission mission : mMissions) {
            if (location.equals(mission.location) && name.equals(mission.name)) {
                return mission;
            }
        }
        return null;
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
     * e.g. "myname (1).txt" if the name "myname.txt" exists.
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
        String[] existingName = destination.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(nameParts[0]);
            }
        });
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

    private class Initializer extends Thread {
        private final DownloadMission mission;
        private final Handler handler;

        public Initializer(DownloadMission mission) {
            this.mission = mission;
            this.handler = new Handler();
        }

        @Override
        public void run() {
            try {
                URL url = new URL(mission.url);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                mission.length = conn.getContentLength();

                if (mission.length <= 0) {
                    mission.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED;
                    //mission.notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED);
                    return;
                }

                // Open again
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Range", "bytes=" + (mission.length - 10) + "-" + mission.length);

                if (conn.getResponseCode() != 206) {
                    // Fallback to single thread if no partial content support
                    mission.fallback = true;

                    if (DEBUG) {
                        Log.d(TAG, "falling back");
                    }
                }

                if (DEBUG) {
                    Log.d(TAG, "response = " + conn.getResponseCode());
                }

                mission.blocks = mission.length / BLOCK_SIZE;

                if (mission.threadCount > mission.blocks) {
                    mission.threadCount = (int) mission.blocks;
                }

                if (mission.threadCount <= 0) {
                    mission.threadCount = 1;
                }

                if (mission.blocks * BLOCK_SIZE < mission.length) {
                    mission.blocks++;
                }


                new File(mission.location).mkdirs();
                new File(mission.location + "/" + mission.name).createNewFile();
                RandomAccessFile af = new RandomAccessFile(mission.location + "/" + mission.name, "rw");
                af.setLength(mission.length);
                af.close();

                mission.start();
            } catch (IOException ie) {
                if(context == null) throw new RuntimeException(ie);

                if(ie.getMessage().contains("Permission denied")) {
                    handler.post(() ->
                        context.startActivity(new Intent(context, ExtSDDownloadFailedActivity.class)));
                } else throw new RuntimeException(ie);
            } catch (Exception e) {
                // TODO Notify
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Waits for mission to finish to add it to the {@link #mDownloadDataSource}
     */
    private class MissionListener implements DownloadMission.MissionListener {
        private final DownloadMission mMission;

        private MissionListener(DownloadMission mission) {
            if (mission == null) throw new NullPointerException("mission is null");
            // Could the mission be passed in onFinish()?
            mMission = mission;
        }

        @Override
        public void onProgressUpdate(DownloadMission downloadMission, long done, long total) {
        }

        @Override
        public void onFinish(DownloadMission downloadMission) {
            mDownloadDataSource.addMission(mMission);
        }

        @Override
        public void onError(DownloadMission downloadMission, int errCode) {
        }
    }
}
