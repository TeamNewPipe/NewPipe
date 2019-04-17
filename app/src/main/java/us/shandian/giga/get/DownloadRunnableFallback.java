package us.shandian.giga.get;

import android.support.annotation.NonNull;
import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;

/**
 * Single-threaded fallback mode
 */
public class DownloadRunnableFallback extends Thread {
    private static final String TAG = "DownloadRunnableFallbac";

    private final DownloadMission mMission;

    private int mRetryCount = 0;
    private InputStream mIs;
    private SharpStream mF;
    private HttpURLConnection mConn;

    DownloadRunnableFallback(@NonNull DownloadMission mission) {
        mMission = mission;
    }

    private void dispose() {
        try {
            if (mIs != null) mIs.close();
        } catch (IOException e) {
            // nothing to do
        }

        if (mF != null) mF.close();
    }

    @Override
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

            int mId = 1;
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

            mF = mMission.storage.getStream();
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

            if (DEBUG) {
                Log.e(TAG, "got exception, retrying...", e);
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
