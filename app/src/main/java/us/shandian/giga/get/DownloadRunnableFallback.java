package us.shandian.giga.get;

import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;

import us.shandian.giga.get.DownloadMission.HttpError;
import us.shandian.giga.util.Utility;

import static org.schabi.newpipe.BuildConfig.DEBUG;
import static us.shandian.giga.get.DownloadMission.ERROR_HTTP_FORBIDDEN;

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
            try {
                if (mIs != null) mIs.close();
            } finally {
                mConn.disconnect();
            }
        } catch (IOException e) {
            // nothing to do
        }

        if (mF != null) mF.close();
    }

    @Override
    public void run() {
        boolean done;
        long start = mMission.fallbackResumeOffset;

        if (DEBUG && !mMission.unknownLength && start > 0) {
            Log.i(TAG, "Resuming a single-thread download at " + start);
        }

        try {
            long rangeStart = (mMission.unknownLength || start < 1) ? -1 : start;

            int mId = 1;
            mConn = mMission.openConnection(false, rangeStart, -1);

            if (mRetryCount == 0 && rangeStart == -1) {
                // workaround: bypass android connection pool
                mConn.setRequestProperty("Range", "bytes=0-");
            }

            mMission.establishConnection(mId, mConn);

            // check if the download can be resumed
            if (mConn.getResponseCode() == 416 && start > 0) {
                mMission.notifyProgress(-start);
                start = 0;
                mRetryCount--;
                throw new DownloadMission.HttpError(416);
            }

            // secondary check for the file length
            if (!mMission.unknownLength)
                mMission.unknownLength = Utility.getContentLength(mConn) == -1;

            if (mMission.unknownLength || mConn.getResponseCode() == 200) {
                // restart amount of bytes downloaded
                mMission.done = mMission.offsets[mMission.current] - mMission.offsets[0];
            }

            mF = mMission.storage.getStream();
            mF.seek(mMission.offsets[mMission.current] + start);

            mIs = mConn.getInputStream();

            byte[] buf = new byte[DownloadMission.BUFFER_SIZE];
            int len = 0;

            while (mMission.running && (len = mIs.read(buf, 0, buf.length)) != -1) {
                mF.write(buf, 0, len);
                start += len;
                mMission.notifyProgress(len);
            }

            dispose();

            // if thread goes interrupted check if the last part is written. This avoid re-download the whole file
            done = len == -1;
        } catch (Exception e) {
            dispose();

            mMission.fallbackResumeOffset = start;

            if (!mMission.running || e instanceof ClosedByInterruptException) return;

            if (e instanceof HttpError && ((HttpError) e).statusCode == ERROR_HTTP_FORBIDDEN) {
                // for youtube streams. The url has expired, recover
                dispose();
                mMission.doRecover(ERROR_HTTP_FORBIDDEN);
                return;
            }

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

        if (done) {
            mMission.notifyFinished();
        } else {
            mMission.fallbackResumeOffset = start;
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
