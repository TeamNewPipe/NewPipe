package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.DownloadMission.MissionListener;

/**
 * Created by erwin on 06.11.16.
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
