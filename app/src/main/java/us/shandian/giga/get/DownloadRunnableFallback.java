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
public class DownloadRunnableFallback implements Runnable {
    private static final String TAG = "DownloadRunnableFallback";

    private final DownloadMission mMission;
    private int retryCount = 0;

    private InputStream is;
    private RandomAccessFile f;

    DownloadRunnableFallback(@NonNull DownloadMission mission) {
        mMission = mission;
        is = null;
        f = null;
    }

    private void dispose() {
        try {
            if (is != null) is.close();
        } catch (IOException e) {
            // nothing to do
        }

        try {
            if (f != null) f.close();
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
            HttpURLConnection conn = mMission.openConnection(1, rangeStart, -1);

            // secondary check for the file length
            if (!mMission.unknownLength)
                mMission.unknownLength = Utility.getContentLength(conn) == -1;

            f = new RandomAccessFile(mMission.getDownloadedFile(), "rw");
            f.seek(mMission.offsets[mMission.current] + start);

            is = conn.getInputStream();

            byte[] buf = new byte[64 * 1024];
            int len = 0;

            while (mMission.running && (len = is.read(buf, 0, buf.length)) != -1) {
                f.write(buf, 0, len);
                start += len;
                mMission.notifyProgress(len);
            }

            // if thread goes interrupted check if the last part is written. This avoid re-download the whole file
            done = len == -1;
        } catch (Exception e) {
            dispose();

            // save position
            mMission.setThreadBytePosition(0, start);

            if (e instanceof ClosedByInterruptException) return;

            if (retryCount++ >= mMission.maxRetry) {
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
}
