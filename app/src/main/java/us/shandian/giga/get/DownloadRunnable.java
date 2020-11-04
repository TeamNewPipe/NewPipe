package us.shandian.giga.get;

import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import us.shandian.giga.get.DownloadMission.Block;
import us.shandian.giga.get.DownloadMission.HttpError;

import static org.schabi.newpipe.BuildConfig.DEBUG;
import static us.shandian.giga.get.DownloadMission.ERROR_HTTP_FORBIDDEN;


/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
public class DownloadRunnable extends Thread {
    private static final String TAG = "DownloadRunnable";

    private final DownloadMission mMission;
    private final int mId;

    private HttpURLConnection mConn;

    DownloadRunnable(DownloadMission mission, int id) {
        if (mission == null) throw new NullPointerException("mission is null");
        mMission = mission;
        mId = id;
    }

    private void releaseBlock(Block block, long remain) {
        // set the block offset to -1 if it is completed
        mMission.releaseBlock(block.position, remain < 0 ? -1 : block.done);
    }

    @Override
    public void run() {
        boolean retry = false;
        Block block = null;
        int retryCount = 0;
        SharpStream f;

        try {
            f = mMission.storage.getStream();
        } catch (IOException e) {
            mMission.notifyError(e);// this never should happen
            return;
        }

        while (mMission.running && mMission.errCode == DownloadMission.ERROR_NOTHING) {
            if (!retry) {
                block = mMission.acquireBlock();
            }

            if (block == null) {
                if (DEBUG) Log.d(TAG, mId + ":no more blocks left, exiting");
                break;
            }

            if (DEBUG) {
                if (retry)
                    Log.d(TAG, mId + ":retry block at position=" + block.position + " from the start");
                else
                    Log.d(TAG, mId + ":acquired block at position=" + block.position + " done=" + block.done);
            }

            long start = (long)block.position * DownloadMission.BLOCK_SIZE;
            long end = start + DownloadMission.BLOCK_SIZE - 1;

            start += block.done;

            if (end >= mMission.length) {
                end = mMission.length - 1;
            }

            try {
                mConn = mMission.openConnection(false, start, end);
                mMission.establishConnection(mId, mConn);

                // check if the download can be resumed
                if (mConn.getResponseCode() == 416) {
                    if (block.done > 0) {
                        // try again from the start (of the block)
                        mMission.notifyProgress(-block.done);
                        block.done = 0;
                        retry = true;
                        mConn.disconnect();
                        continue;
                    }

                    throw new DownloadMission.HttpError(416);
                }

                retry = false;

                // The server may be ignoring the range request
                if (mConn.getResponseCode() != 206) {
                    if (DEBUG) {
                        Log.e(TAG, mId + ":Unsupported " + mConn.getResponseCode());
                    }
                    mMission.notifyError(new DownloadMission.HttpError(mConn.getResponseCode()));
                    break;
                }

                f.seek(mMission.offsets[mMission.current] + start);

                try (InputStream is = mConn.getInputStream()) {
                    byte[] buf = new byte[DownloadMission.BUFFER_SIZE];
                    int len;

                    // use always start <= end
                    // fixes a deadlock because in some videos, youtube is sending one byte alone
                    while (start <= end && mMission.running && (len = is.read(buf, 0, buf.length)) != -1) {
                        f.write(buf, 0, len);
                        start += len;
                        block.done += len;
                        mMission.notifyProgress(len);
                    }
                }

                if (DEBUG && mMission.running) {
                    Log.d(TAG, mId + ":position " + block.position + " stopped " + start + "/" + end);
                }
            } catch (Exception e) {
                if (!mMission.running || e instanceof ClosedByInterruptException) break;

                if (e instanceof HttpError && ((HttpError) e).statusCode == ERROR_HTTP_FORBIDDEN) {
                    // for youtube streams. The url has expired, recover
                    f.close();

                    if (mId == 1) {
                        // only the first thread will execute the recovery procedure
                        mMission.doRecover(ERROR_HTTP_FORBIDDEN);
                    }
                    return;
                }

                if (retryCount++ >= mMission.maxRetry) {
                    mMission.notifyError(e);
                    break;
                }

                retry = true;
            } finally {
                if (!retry) releaseBlock(block, end - start);
            }
        }

        f.close();

        if (DEBUG) {
            Log.d(TAG, "thread " + mId + " exited from main download loop");
        }

        if (mMission.errCode == DownloadMission.ERROR_NOTHING && mMission.running) {
            if (DEBUG) {
                Log.d(TAG, "no error has happened, notifying");
            }
            mMission.notifyFinished();
        }

        if (DEBUG && !mMission.running) {
            Log.d(TAG, "The mission has been paused. Passing.");
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();

        try {
            if (mConn != null) mConn.disconnect();
        } catch (Exception e) {
            // nothing to do
        }
    }

}
