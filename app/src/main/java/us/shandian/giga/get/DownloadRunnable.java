package us.shandian.giga.get;

import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import static org.schabi.newpipe.BuildConfig.DEBUG;

/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
public class DownloadRunnable extends Thread {
    private static final String TAG = DownloadRunnable.class.getSimpleName();

    private final DownloadMission mMission;
    private final int mId;

    private HttpURLConnection mConn;

    DownloadRunnable(DownloadMission mission, int id) {
        if (mission == null) throw new NullPointerException("mission is null");
        mMission = mission;
        mId = id;
    }

    @Override
    public void run() {
        boolean retry = mMission.recovered;
        long blockPosition = mMission.getBlockPosition(mId);
        int retryCount = 0;

        if (DEBUG) {
            Log.d(TAG, mId + ":default pos " + blockPosition);
            Log.d(TAG, mId + ":recovered: " + mMission.recovered);
        }

        SharpStream f;
        InputStream is = null;

        try {
            f = mMission.storage.getStream();
        } catch (IOException e) {
            mMission.notifyError(e);// this never should happen
            return;
        }

        while (mMission.running && mMission.errCode == DownloadMission.ERROR_NOTHING && blockPosition < mMission.blocks) {

            if (DEBUG && retry) {
                Log.d(TAG, mId + ":retry is true. Resuming at " + blockPosition);
            }

            // Wait for an unblocked position
            while (!retry && blockPosition < mMission.blocks && mMission.isBlockPreserved(blockPosition)) {

                if (DEBUG) {
                    Log.d(TAG, mId + ":position " + blockPosition + " preserved, passing");
                }

                blockPosition++;
            }

            retry = false;

            if (blockPosition >= mMission.blocks) {
                break;
            }

            if (DEBUG) {
                Log.d(TAG, mId + ":preserving position " + blockPosition);
            }

            mMission.preserveBlock(blockPosition);
            mMission.setBlockPosition(mId, blockPosition);

            long start = blockPosition * DownloadMission.BLOCK_SIZE;
            long end = start + DownloadMission.BLOCK_SIZE - 1;
            long offset = mMission.getThreadBytePosition(mId);

            start += offset;

            if (end >= mMission.length) {
                end = mMission.length - 1;
            }

            long total = 0;

            try {
                mConn = mMission.openConnection(mId, start, end);
                mMission.establishConnection(mId, mConn);

                // check if the download can be resumed
                if (mConn.getResponseCode() == 416 && offset > 0) {
                    retryCount--;
                    throw new DownloadMission.HttpError(416);
                }

                // The server may be ignoring the range request
                if (mConn.getResponseCode() != 206) {
                    mMission.notifyError(new DownloadMission.HttpError(mConn.getResponseCode()));

                    if (DEBUG) {
                        Log.e(TAG, mId + ":Unsupported " + mConn.getResponseCode());
                    }

                    break;
                }

                f.seek(mMission.offsets[mMission.current] + start);

                is = mConn.getInputStream();

                byte[] buf = new byte[DownloadMission.BUFFER_SIZE];
                int len;

                while (start < end && mMission.running && (len = is.read(buf, 0, buf.length)) != -1) {
                    f.write(buf, 0, len);
                    start += len;
                    total += len;
                    mMission.notifyProgress(len);
                }

                if (DEBUG && mMission.running) {
                    Log.d(TAG, mId + ":position " + blockPosition + " finished, " + total + " bytes downloaded");
                }

                if (mMission.running)
                    mMission.setThreadBytePosition(mId, 0L);// clear byte position for next block
                else
                    mMission.setThreadBytePosition(mId, total);// download paused, save progress for this block

            } catch (Exception e) {
                if (DEBUG) {
                    Log.d(TAG, mId + ": position=" + blockPosition + " total=" + total + " stopped due exception", e);
                }

                mMission.setThreadBytePosition(mId, total);

                if (!mMission.running || e instanceof ClosedByInterruptException) break;

                if (retryCount++ >= mMission.maxRetry) {
                    mMission.notifyError(e);
                    break;
                }

                retry = true;
            }
        }

        try {
            if (is != null) is.close();
        } catch (Exception err) {
            // nothing to do
        }

        try {
            f.close();
        } catch (Exception err) {
            // ¿ejected media storage?  ¿file deleted?  ¿storage ran out of space?
        }

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
