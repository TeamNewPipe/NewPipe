package us.shandian.giga.get;

import android.support.annotation.NonNull;
import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadInitializer extends Thread {
    private final static String TAG = "DownloadInitializer";
    final static int mId = 0;
    private final static int RESERVE_SPACE_DEFAULT = 5 * 1024 * 1024;// 5 MiB
    private final static int RESERVE_SPACE_MAXIMUM = 150 * 1024 * 1024;// 150 MiB

    private DownloadMission mMission;
    private HttpURLConnection mConn;

    DownloadInitializer(@NonNull DownloadMission mission) {
        mMission = mission;
        mConn = null;
    }

    @Override
    public void run() {
        if (mMission.current > 0) mMission.resetState(false, true, DownloadMission.ERROR_NOTHING);

        int retryCount = 0;
        while (true) {
            try {
                mMission.currentThreadCount = mMission.threadCount;

                if (mMission.blocks < 0 && mMission.current == 0) {
                    // calculate the whole size of the mission
                    long finalLength = 0;
                    long lowestSize = Long.MAX_VALUE;

                    for (int i = 0; i < mMission.urls.length && mMission.running; i++) {
                        mConn = mMission.openConnection(mMission.urls[i], mId, -1, -1);
                        mMission.establishConnection(mId, mConn);

                        if (Thread.interrupted()) return;
                        long length = Utility.getContentLength(mConn);

                        if (i == 0) mMission.length = length;
                        if (length > 0) finalLength += length;
                        if (length < lowestSize) lowestSize = length;
                    }

                    mMission.nearLength = finalLength;

                    // reserve space at the start of the file
                    if (mMission.psAlgorithm != null && mMission.psAlgorithm.reserveSpace) {
                        if (lowestSize < 1) {
                            // the length is unknown use the default size
                            mMission.offsets[0] = RESERVE_SPACE_DEFAULT;
                        } else {
                            // use the smallest resource size to download, otherwise, use the maximum
                            mMission.offsets[0] = lowestSize < RESERVE_SPACE_MAXIMUM ? lowestSize : RESERVE_SPACE_MAXIMUM;
                        }
                    }
                } else {
                    // ask for the current resource length
                    mConn = mMission.openConnection(mId, -1, -1);
                    mMission.establishConnection(mId, mConn);

                    if (!mMission.running || Thread.interrupted()) return;

                    mMission.length = Utility.getContentLength(mConn);
                }

                if (mMission.length == 0 || mConn.getResponseCode() == 204) {
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

                SharpStream fs = mMission.storage.getStream();
                fs.setLength(mMission.offsets[mMission.current] + mMission.length);
                fs.seek(mMission.offsets[mMission.current]);
                fs.close();

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
