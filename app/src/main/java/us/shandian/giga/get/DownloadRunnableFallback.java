package us.shandian.giga.get;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;


import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

/**
 * Single-threaded fallback mode
 */
public class DownloadRunnableFallback extends Thread {
    private static final String TAG = "DownloadRunnableFallback";

    private final DownloadMission mMission;
    private final int mId = 1;

    private int mRetryCount = 0;
    private InputStream mIs;
    private RandomAccessFile mF;
    private HttpURLConnection mConn;

    DownloadRunnableFallback(@NonNull DownloadMission mission) {
        mMission = mission;
        mIs = null;
        mF = null;
        mConn = null;
    }

    private void dispose() {
        try {
            if (mIs != null) mIs.close();
        } catch (IOException e) {
            // nothing to do
        }

        try {
            if (mF != null) mF.close();
        } catch (IOException e) {
            // ¿ejected media storage? ¿file deleted? ¿storage ran out of space?
        }
    }

    @Override
    @SuppressLint("LongLogTag")
    public void run() {
        boolean done;

        long start = 0;

        if (!mMission.unknownLength) {
            start = mMission.getThreadBytePosition(0);
            if (DEBUG && start > 0) {
                Log.i(TAG, "Resuming a single-thread download at " + start);
            }
        }

        try {
            long rangeStart = (mMission.unknownLength || start < 1) ? -1 : start;

            mConn = mMission.openConnection(mId, rangeStart, -1);
            mMission.establishConnection(mId, mConn);

            // check if the download can be resumed
            if (mConn.getResponseCode() == 416 && start > 0) {
                start = 0;
                mRetryCount--;
                throw new DownloadMission.HttpError(416);
            }

            // secondary check for the file length
            if (!mMission.unknownLength)
                mMission.unknownLength = Utility.getContentLength(mConn) == -1;

            mF = new RandomAccessFile(mMission.getDownloadedFile(), "rw");
            mF.seek(mMission.offsets[mMission.current] + start);

            mIs = mConn.getInputStream();

            byte[] buf = new byte[64 * 1024];
            int len = 0;

            while (mMission.running && (len = mIs.read(buf, 0, buf.length)) != -1) {
                mF.write(buf, 0, len);
                start += len;
                mMission.notifyProgress(len);
            }

            // if thread goes interrupted check if the last part mIs written. This avoid re-download the whole file
            done = len == -1;
        } catch (Exception e) {
            dispose();

            // save position
            mMission.setThreadBytePosition(0, start);

            if (!mMission.running || e instanceof ClosedByInterruptException) return;

            if (mRetryCount++ >= mMission.maxRetry) {
                mMission.notifyError(e);
                return;
            }

            run();// try again
            return;
        }

        dispose();

        if (done) {
            mMission.notifyFinished();
        } else {
            mMission.setThreadBytePosition(0, start);
        }
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
