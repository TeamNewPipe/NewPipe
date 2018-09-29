package us.shandian.giga.get;

import android.util.Log;

import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.schabi.newpipe.BuildConfig.DEBUG;

/**
 * Runnable to download blocks of a file until the file is completely downloaded,
 * an error occurs or the process is stopped.
 */
public class DownloadRunnable implements Runnable {
    private static final String TAG = DownloadRunnable.class.getSimpleName();

    private final DownloadMission mMission;
    private final int mId;

    public DownloadRunnable(DownloadMission mission, int id) {
        if (mission == null) throw new NullPointerException("mission is null");
        mMission = mission;
        mId = id;
    }

    @Override
    public void run() {
        boolean retry = mMission.recovered;
        long position = mMission.getPosition(mId);

        if (DEBUG) {
            Log.d(TAG, mId + ":default pos " + position);
            Log.d(TAG, mId + ":recovered: " + mMission.recovered);
        }

        while (mMission.errCode == -1 && mMission.running && position < mMission.blocks) {

            if (Thread.currentThread().isInterrupted()) {
                mMission.pause();
                return;
            }

            if (DEBUG && retry) {
                Log.d(TAG, mId + ":retry is true. Resuming at " + position);
            }

            // Wait for an unblocked position
            while (!retry && position < mMission.blocks && mMission.isBlockPreserved(position)) {

                if (DEBUG) {
                    Log.d(TAG, mId + ":position " + position + " preserved, passing");
                }

                position++;
            }

            retry = false;

            if (position >= mMission.blocks) {
                break;
            }

            if (DEBUG) {
                Log.d(TAG, mId + ":preserving position " + position);
            }

            mMission.preserveBlock(position);
            mMission.setPosition(mId, position);

            long start = position * DownloadManager.BLOCK_SIZE;
            long end = start + DownloadManager.BLOCK_SIZE - 1;

            if (end >= mMission.length) {
                end = mMission.length - 1;
            }

            HttpURLConnection conn = null;

            int total = 0;

            try {
                URL url = new URL(mMission.url);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Range", "bytes=" + start + "-" + end);

                if (DEBUG) {
                    Log.d(TAG, mId + ":" + conn.getRequestProperty("Range"));
                    Log.d(TAG, mId + ":Content-Length=" + conn.getContentLength() + " Code:" + conn.getResponseCode());
                }

                // A server may be ignoring the range request
                if (conn.getResponseCode() != 206) {
                    mMission.errCode = DownloadMission.ERROR_SERVER_UNSUPPORTED;
                    notifyError();

                    if (DEBUG) {
                        Log.e(TAG, mId + ":Unsupported " + conn.getResponseCode());
                    }

                    break;
                }

                RandomAccessFile f = new RandomAccessFile(mMission.location + "/" + mMission.name, "rw");
                f.seek(start);
                java.io.InputStream ipt = conn.getInputStream();
                byte[] buf = new byte[64*1024];

                while (start < end && mMission.running) {
                    int len = ipt.read(buf, 0, buf.length);

                    if (len == -1) {
                        break;
                    } else {
                        start += len;
                        total += len;
                        f.write(buf, 0, len);
                        notifyProgress(len);
                    }
                }

                if (DEBUG && mMission.running) {
                    Log.d(TAG, mId + ":position " + position + " finished, total length " + total);
                }

                f.close();
                ipt.close();

                // TODO We should save progress for each thread
            } catch (Exception e) {
                // TODO Retry count limit & notify error
                retry = true;

                notifyProgress(-total);

                if (DEBUG) {
                    Log.d(TAG, mId + ":position " + position + " retrying", e);
                }
            }
        }

        if (DEBUG) {
            Log.d(TAG, "thread " + mId + " exited main loop");
        }

        if (mMission.errCode == -1 && mMission.running) {
            if (DEBUG) {
                Log.d(TAG, "no error has happened, notifying");
            }
            notifyFinished();
        }

        if (DEBUG && !mMission.running) {
            Log.d(TAG, "The mission has been paused. Passing.");
        }
    }

    private void notifyProgress(final long len) {
        synchronized (mMission) {
            mMission.notifyProgress(len);
        }
    }

    private void notifyError() {
        synchronized (mMission) {
            mMission.notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED);
            mMission.pause();
        }
    }

    private void notifyFinished() {
        synchronized (mMission) {
            mMission.notifyFinished();
        }
    }
}
