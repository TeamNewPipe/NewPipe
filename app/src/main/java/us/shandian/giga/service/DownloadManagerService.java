package us.shandian.giga.service;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.settings.NewPipeSettings;

import java.util.ArrayList;

import us.shandian.giga.get.DownloadDataSource;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadManagerImpl;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.sqlite.SQLiteDownloadDataSource;

import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerService extends Service {

    private static final String TAG = DownloadManagerService.class.getSimpleName();

    /**
     * Message code of update messages stored as {@link Message#what}.
     */
    private static final int UPDATE_MESSAGE = 0;
    private static final int NOTIFICATION_ID = 1000;
    private static final String EXTRA_NAME = "DownloadManagerService.extra.name";
    private static final String EXTRA_LOCATION = "DownloadManagerService.extra.location";
    private static final String EXTRA_IS_AUDIO = "DownloadManagerService.extra.is_audio";
    private static final String EXTRA_THREADS = "DownloadManagerService.extra.threads";


    private DMBinder mBinder;
    private DownloadManager mManager;
    private Notification mNotification;
    private Handler mHandler;
    private long mLastTimeStamp = System.currentTimeMillis();
    private DownloadDataSource mDataSource;


    private final MissionListener missionListener = new MissionListener();


    private void notifyMediaScanner(DownloadMission mission) {
        Uri uri = Uri.parse("file://" + mission.location + "/" + mission.name);
        // notify media scanner on downloaded media file ...
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        mBinder = new DMBinder();
        if (mDataSource == null) {
            mDataSource = new SQLiteDownloadDataSource(this);
        }
        if (mManager == null) {
            ArrayList<String> paths = new ArrayList<>(2);
            paths.add(NewPipeSettings.getVideoDownloadPath(this));
            paths.add(NewPipeSettings.getAudioDownloadPath(this));
            mManager = new DownloadManagerImpl(paths, mDataSource, this);
            if (DEBUG) {
                Log.d(TAG, "mManager == null");
                Log.d(TAG, "Download directory: " + paths);
            }
        }

        Intent openDownloadListIntent = new Intent(this, DownloadActivity.class)
                .setAction(Intent.ACTION_MAIN);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                openDownloadListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap iconBitmap = BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher);

        Builder builder = new Builder(this, getString(R.string.notification_channel_id))
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(iconBitmap)
                .setContentTitle(getString(R.string.msg_running))
                .setContentText(getString(R.string.msg_running_detail));

        mNotification = builder.build();

        HandlerThread thread = new HandlerThread("ServiceMessenger");
        thread.start();

        mHandler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_MESSAGE: {
                        int runningCount = 0;

                        for (int i = 0; i < mManager.getCount(); i++) {
                            if (mManager.getMission(i).running) {
                                runningCount++;
                            }
                        }
                        updateState(runningCount);
                        break;
                    }
                }
            }
        };

    }

    private void startMissionAsync(final String url, final String location, final String name,
                                   final boolean isAudio, final int threads) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int missionId = mManager.startMission(url, location, name, isAudio, threads);
                mBinder.onMissionAdded(mManager.getMission(missionId));
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, "Starting");
        }
        Log.i(TAG, "Got intent: " + intent);
        String action = intent.getAction();
        if (action != null && action.equals(Intent.ACTION_RUN)) {
            String name = intent.getStringExtra(EXTRA_NAME);
            String location = intent.getStringExtra(EXTRA_LOCATION);
            int threads = intent.getIntExtra(EXTRA_THREADS, 1);
            boolean isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false);
            String url = intent.getDataString();
            startMissionAsync(url, location, name, isAudio, threads);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DEBUG) {
            Log.d(TAG, "Destroying");
        }

        for (int i = 0; i < mManager.getCount(); i++) {
            mManager.pauseMission(i);
        }

        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        int permissionCheck;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission denied (read)", Toast.LENGTH_SHORT).show();
            }
        }

        permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission denied (write)", Toast.LENGTH_SHORT).show();
        }

        return mBinder;
    }

    private void postUpdateMessage() {
        mHandler.sendEmptyMessage(UPDATE_MESSAGE);
    }

    private void updateState(int runningCount) {
        if (runningCount == 0) {
            stopForeground(true);
        } else {
            startForeground(NOTIFICATION_ID, mNotification);
        }
    }

    public static void startMission(Context context, String url, String location, String name, boolean isAudio, int threads) {
        Intent intent = new Intent(context, DownloadManagerService.class);
        intent.setAction(Intent.ACTION_RUN);
        intent.setData(Uri.parse(url));
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_LOCATION, location);
        intent.putExtra(EXTRA_IS_AUDIO, isAudio);
        intent.putExtra(EXTRA_THREADS, threads);
        context.startService(intent);
    }


    private class MissionListener implements DownloadMission.MissionListener {
        @Override
        public void onProgressUpdate(DownloadMission downloadMission, long done, long total) {
            long now = System.currentTimeMillis();
            long delta = now - mLastTimeStamp;
            if (delta > 2000) {
                postUpdateMessage();
                mLastTimeStamp = now;
            }
        }

        @Override
        public void onFinish(DownloadMission downloadMission) {
            postUpdateMessage();
            notifyMediaScanner(downloadMission);
        }

        @Override
        public void onError(DownloadMission downloadMission, int errCode) {
            postUpdateMessage();
        }
    }


    // Wrapper of DownloadManager
    public class DMBinder extends Binder {
        public DownloadManager getDownloadManager() {
            return mManager;
        }

        public void onMissionAdded(DownloadMission mission) {
            mission.addListener(missionListener);
            postUpdateMessage();
        }

        public void onMissionRemoved(DownloadMission mission) {
            mission.removeListener(missionListener);
            postUpdateMessage();
        }
    }
}
