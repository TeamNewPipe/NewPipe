package us.shandian.giga.get;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;

import static org.schabi.newpipe.BuildConfig.DEBUG;

/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
public class DownloadRunnable implements Runnable {
    private static final String TAG = DownloadRunnable.class.getSimpleName();

    private final DownloadMission mMission;
    private final int mId;

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

        BufferedInputStream ipt = null;
        RandomAccessFile f;

        try {
            f = new RandomAccessFile(mMission.getDownloadedFile(), "rw");
        } catch (FileNotFoundException e) {
            mMission.notifyError(e);// this never should happen
            return;
        }

        while (mMission.errCode == DownloadMission.ERROR_NOTHING && mMission.running && blockPosition < mMission.blocks) {

            if (Thread.currentThread().isInterrupted()) {
                mMission.pause();
                return;
            }

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

            long start = (blockPosition * DownloadMission.BLOCK_SIZE) + mMission.getBlockBytePosition(mId);
            long end = start + DownloadMission.BLOCK_SIZE - 1;

            if (end >= mMission.length) {
                end = mMission.length - 1;
            }

            int total = 0;

            try {
                HttpURLConnection conn = mMission.openConnection(mId, start, end);

                // The server may be ignoring the range request
                if (conn.getResponseCode() != 206) {
                    mMission.notifyError(new DownloadMission.HttpError(conn.getResponseCode()));

                    if (DEBUG) {
                        Log.e(TAG, mId + ":Unsupported " + conn.getResponseCode());
                    }

                    break;
                }

                f.seek(mMission.offsets[mMission.current] + start);

                ipt = new BufferedInputStream(conn.getInputStream());
                byte[] buf = new byte[DownloadMission.BUFFER_SIZE];
                int len;

                while (start < end && mMission.running && (len = ipt.read(buf, 0, buf.length)) != -1) {
                    f.write(buf, 0, len);
                    start += len;
                    total += len;
                    mMission.notifyProgress(len);
                }

                if (DEBUG && mMission.running) {
                    Log.d(TAG, mId + ":position " + blockPosition + " finished, total length " + total);
                }

                // if the download is paused, save progress for this thread
                if (!mMission.running) {
                    mMission.setThreadBytePosition(mId, total);
                    break;
                }
            } catch (Exception e) {
                mMission.setThreadBytePosition(mId, total);

                if (e instanceof ClosedByInterruptException) break;

                if (retryCount++ > mMission.maxRetry) {
                    mMission.notifyError(e);
                    break;
                }

                if (DEBUG) {
                    Log.d(TAG, mId + ":position " + blockPosition + " retrying due exception", e);
                }
            }
        }

        try {
            f.close();
        } catch (Exception err) {
            // ¿ejected media storage?  ¿file deleted?  ¿storage ran out of space?
        }

        try {
            if (ipt != null) ipt.close();
        } catch (Exception err) {
            // nothing to do
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
}
