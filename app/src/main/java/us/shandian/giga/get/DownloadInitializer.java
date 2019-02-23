package us.shandian.giga.get;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadInitializer extends Thread {
    private final static String TAG = "DownloadInitializer";
    final static int mId = 0;

    private DownloadMission mMission;
    private HttpURLConnection mConn;

    DownloadInitializer(@NonNull DownloadMission mission) {
        mMission = mission;
        mConn = null;
    }

    @Override
    public void run() {
        if (mMission.current > 0) mMission.resetState();

        int retryCount = 0;
        while (true) {
            try {
                mMission.currentThreadCount = mMission.threadCount;

                mConn = mMission.openConnection(mId, -1, -1);
                mMission.establishConnection(mId, mConn);

                if (!mMission.running || Thread.interrupted()) return;

                mMission.length = Utility.getContentLength(mConn);


                if (mMission.length == 0) {
                    mMission.notifyError(DownloadMission.ERROR_HTTP_NO_CONTENT, null);
                    return;
                }

                // check for dynamic generated content
                if (mMission.length == -1 && mConn.getResponseCode() == 200) {
                    mMission.blocks = 0;
                    mMission.length = 0;
                    mMission.fallback = true;
                    mMission.unknownLength = true;
                    mMission.currentThreadCount = 1;

                    if (DEBUG) {
                        Log.d(TAG, "falling back (unknown length)");
                    }
                } else {
                    // Open again
                    mConn = mMission.openConnection(mId, mMission.length - 10, mMission.length);
                    mMission.establishConnection(mId, mConn);

                    if (!mMission.running || Thread.interrupted()) return;

                    synchronized (mMission.blockState) {
                        if (mConn.getResponseCode() == 206) {
                            if (mMission.currentThreadCount > 1) {
                                mMission.blocks = mMission.length / DownloadMission.BLOCK_SIZE;

                                if (mMission.currentThreadCount > mMission.blocks) {
                                    mMission.currentThreadCount = (int) mMission.blocks;
                                }
                                if (mMission.currentThreadCount <= 0) {
                                    mMission.currentThreadCount = 1;
                                }
                                if (mMission.blocks * DownloadMission.BLOCK_SIZE < mMission.length) {
                                    mMission.blocks++;
                                }
                            } else {
                                // if one thread is solicited don't calculate blocks, is useless
                                mMission.blocks = 1;
                                mMission.fallback = true;
                                mMission.unknownLength = false;
                            }

                            if (DEBUG) {
                                Log.d(TAG, "http response code = " + mConn.getResponseCode());
                            }
                        } else {
                            // Fallback to single thread
                            mMission.blocks = 0;
                            mMission.fallback = true;
                            mMission.unknownLength = false;
                            mMission.currentThreadCount = 1;

                            if (DEBUG) {
                                Log.d(TAG, "falling back due http response code = " + mConn.getResponseCode());
                            }
                        }

                        for (long i = 0; i < mMission.currentThreadCount; i++) {
                            mMission.threadBlockPositions.add(i);
                            mMission.threadBytePositions.add(0L);
                        }
                    }

                    if (!mMission.running || Thread.interrupted()) return;
                }

                File file;
                if (mMission.current == 0) {
                    file = new File(mMission.location);
                    if (!Utility.mkdir(file, true)) {
                        mMission.notifyError(DownloadMission.ERROR_PATH_CREATION, null);
                        return;
                    }

                    file = new File(file, mMission.name);

                    // if the name is used by another process, delete it
                    if (file.exists() && !file.isFile() && !file.delete()) {
                        mMission.notifyError(DownloadMission.ERROR_FILE_CREATION, null);
                        return;
                    }

                    if (!file.exists() && !file.createNewFile()) {
                        mMission.notifyError(DownloadMission.ERROR_FILE_CREATION, null);
                        return;
                    }
                } else {
                    file = new File(mMission.location, mMission.name);
                }

                RandomAccessFile af = new RandomAccessFile(file, "rw");
                af.setLength(mMission.offsets[mMission.current] + mMission.length);
                af.seek(mMission.offsets[mMission.current]);
                af.close();

                if (!mMission.running || Thread.interrupted()) return;

                mMission.running = false;
                break;
            } catch (InterruptedIOException | ClosedByInterruptException e) {
                return;
            } catch (Exception e) {
                if (!mMission.running) return;

                if (e instanceof IOException && e.getMessage().contains("Permission denied")) {
                    mMission.notifyError(DownloadMission.ERROR_PERMISSION_DENIED, e);
                    return;
                }

                if (retryCount++ > mMission.maxRetry) {
                    Log.e(TAG, "initializer failed", e);
                    mMission.notifyError(e);
                    return;
                }

                Log.e(TAG, "initializer failed, retrying", e);
            }
        }

        // hide marquee in the progress bar
        mMission.done++;

        mMission.start();
    }

    @Override
    public void interrupt() {
        super.interrupt();

        if (mConn != null) {
            try {
                mConn.disconnect();
            } catch (Exception e) {
                // nothing to do
            }
        }
    }
}
