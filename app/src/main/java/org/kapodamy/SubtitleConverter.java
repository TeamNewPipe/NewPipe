package org.kapodamy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import junit.framework.Assert;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadManagerImpl;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.DownloadMission.MissionListener;
import us.shandian.giga.service.DownloadManagerService;

public class SubtitleConverter implements MissionListener, ServiceConnection {
    private Context context;
    private DownloadManager manager;
    private boolean added;

    private String location;
    private String name;
    private SubtitlesStream info;

    private SubtitleConverter(Context context, SubtitlesStream subtitles, String location, String name) {
        this.context = context;
        this.info = subtitles;
        this.location = location;
        this.name = name;
        this.added = false;

        Intent intent = new Intent();
        intent.setClass(context, DownloadManagerService.class);
        context.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    /**
     * Manual dispose
     */
    private void Dispose() {
        if (context == null) {
            return;
        }
        manager.removeListener(this);
        context.unbindService(this);

        location = null;
        name = null;
        info = null;
        context = null;
    }

    public boolean isDisposed() {
        return context == null;
    }


    // ----------------  internal used methods  ---------------- //

    private boolean checkMission(DownloadMission mission) {
        return info.url.equals(mission.url) && location.equals(mission.location) && name.equals(mission.name);
    }

    @Override
    public void onProgressUpdate(DownloadMission downloadMission, long done, long total) {
    }

    @Override
    public void onFinish(DownloadMission downloadMission) {
        if (context == null || !added || !checkMission(downloadMission)) {
            return;
        }

        boolean result = SubtitleUtils.Dump(
                context.getCacheDir(),
                info,
                downloadMission.location.concat(File.separator).concat(downloadMission.name),
                true);

        if (result) {
            downloadMission.removeListener(this);
            Dispose();
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast.makeText(context, context.getString(R.string.caption_parse_error, downloadMission.name), Toast.LENGTH_LONG).show();
        });

        for (int i = manager.getCount() - 1; i >= 0; i++) {
            if (manager.getMission(i) != downloadMission) {
                continue;
            }
            manager.deleteMission(i);// SubtitleConverter.Dispose() method should be called by "onDeleted" callback
            break;
        }
    }

    @Override
    public void onDeleted(DownloadMission downloadMission) {
        if (context == null) {
            return;
        }
        if (added && checkMission(downloadMission)) {
            downloadMission.removeListener(this);
            Dispose();
        }
    }

    @Override
    public void onError(DownloadMission downloadMission, int errCode) {
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
        manager = ((DownloadManagerService.DMBinder) binder).getDownloadManager();
        manager.addListener(this);
        added = true;

        // now start the download
        manager.startMission(info.url, location, name, 's', 1);
        //DownloadManagerService.startMission(activity, info.url, location, name, 's', 1);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (!added) {
            Log.w("", "SubtitleConverter: service disconnected without download the subtitle");
        }
        Dispose();
    }

    public static void startMission(Context context, SubtitlesStream stream, String location, String fileName) {
        new WeakReference<>(new SubtitleConverter(context, stream, location, fileName));
    }
}