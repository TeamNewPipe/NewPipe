package us.shandian.giga.get;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;


import static org.schabi.newpipe.BuildConfig.DEBUG;

// Single-threaded fallback mode
public class DownloadRunnableFallback implements Runnable {
    private static final String TAG = "DownloadRunnableFallbac";

    private final DownloadMission mMission;
    private int retryCount = 0;

    private BufferedInputStream ipt;
    private RandomAccessFile f;

    DownloadRunnableFallback(@NonNull DownloadMission mission) {
        mMission = mission;
        ipt = null;
        f = null;
    }

    private void dispose() {
        try {
            if (ipt != null) ipt.close();
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
    public void run() {
        boolean done;

        int start = 0;

        if (!mMission.unknownLength) {
            start = mMission.getBlockBytePosition(0);
            if (DEBUG && start > 0) {
                Log.i(TAG, "Resuming a single-thread download at " + start);
            }
        }

        try {
            int rangeStart = (mMission.unknownLength || start < 1) ? -1 : start;
            HttpURLConnection conn = mMission.openConnection(1, rangeStart, -1);

            // secondary check for the file length
            if (!mMission.unknownLength) mMission.unknownLength = conn.getContentLength() == -1;

            f = new RandomAccessFile(mMission.getDownloadedFile(), "rw");
            f.seek(mMission.offsets[mMission.current] + start);

            ipt = new BufferedInputStream(conn.getInputStream());

            byte[] buf = new byte[DownloadMission.BUFFER_SIZE];
            int len = 0;

            while (mMission.running && (len = ipt.read(buf, 0, buf.length)) != -1) {
                f.write(buf, 0, len);
                start += len;

                mMission.notifyProgress(len);

                if (Thread.interrupted()) break;
            }

            // if thread goes interrupted check if the last part is written. This avoid re-download the whole file
            done = len == -1;
        } catch (Exception e) {
            dispose();

            // save position
            mMission.setThreadBytePosition(0, start);

            if (e instanceof ClosedByInterruptException) return;

            if (retryCount++ > mMission.maxRetry) {
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
