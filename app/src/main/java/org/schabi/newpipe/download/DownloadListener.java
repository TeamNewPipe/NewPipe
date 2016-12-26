package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.DownloadMission.MissionListener;

/**
 * Created by erwin on 06.11.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadListener.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

class DownloadListener implements MissionListener
{
    DownloadMission mMission;
    Context mContext;

    public DownloadListener(Context context, DownloadMission mission)
    {
        super();
        mMission = mission;
        mContext = context;
    }

    @Override
    public void onProgressUpdate(long done, long total)
    {
        // do nothing special ...
    }

    @Override
    public void onFinish()
    {
        // notify media scanner on downloaded media file ...
        mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                          Uri.parse( "file://" + mMission.location
                                                     + "/" + mMission.name)));
    }

    @Override
    public void onError(int errCode)
    {
        // do nothing special ...
    }
}
