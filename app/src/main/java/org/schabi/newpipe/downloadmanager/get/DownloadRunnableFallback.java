package org.schabi.newpipe.downloadmanager.get;

import java.io.BufferedInputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 */

// Single-threaded fallback mode
public class DownloadRunnableFallback implements Runnable {
    private final DownloadMission mMission;
    //private int mId;

    public DownloadRunnableFallback(DownloadMission mission) {
        if (mission == null) throw new NullPointerException("mission is null");
        //mId = id;
        mMission = mission;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(mMission.url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (conn.getResponseCode() != 200 && conn.getResponseCode() != 206) {
                notifyError(DownloadMission.ERROR_SERVER_UNSUPPORTED);
            } else {
                RandomAccessFile f = new RandomAccessFile(mMission.location + "/" + mMission.name, "rw");
                f.seek(0);
                BufferedInputStream ipt = new BufferedInputStream(conn.getInputStream());
                byte[] buf = new byte[512];
                int len = 0;

                while ((len = ipt.read(buf, 0, 512)) != -1 && mMission.running) {
                    f.write(buf, 0, len);
                    notifyProgress(len);

                    if (Thread.interrupted()) {
                        break;
                    }

                }

                f.close();
                ipt.close();
            }
        } catch (Exception e) {
            notifyError(DownloadMission.ERROR_UNKNOWN);
        }

        if (mMission.errCode == -1 && mMission.running) {
            notifyFinished();
        }
    }

    private void notifyProgress(final long len) {
        synchronized (mMission) {
            mMission.notifyProgress(len);
        }
    }

    private void notifyError(final int err) {
        synchronized (mMission) {
            mMission.notifyError(err);
            mMission.pause();
        }
    }

    private void notifyFinished() {
        synchronized (mMission) {
            mMission.notifyFinished();
        }
    }
}
