package us.shandian.giga.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.player.helper.LockManager;

import java.io.File;
import java.util.ArrayList;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManager.NetworkState;

import static org.schabi.newpipe.BuildConfig.APPLICATION_ID;
import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerService extends Service {

    private static final String TAG = "DownloadManagerService";

    public static final int MESSAGE_RUNNING = 0;
    public static final int MESSAGE_PAUSED = 1;
    public static final int MESSAGE_FINISHED = 2;
    public static final int MESSAGE_PROGRESS = 3;
    public static final int MESSAGE_ERROR = 4;
    public static final int MESSAGE_DELETED = 5;

    private static final int FOREGROUND_NOTIFICATION_ID = 1000;
    private static final int DOWNLOADS_NOTIFICATION_ID = 1001;

    private static final String EXTRA_URLS = "DownloadManagerService.extra.urls";
    private static final String EXTRA_NAME = "DownloadManagerService.extra.name";
    private static final String EXTRA_LOCATION = "DownloadManagerService.extra.location";
    private static final String EXTRA_KIND = "DownloadManagerService.extra.kind";
    private static final String EXTRA_THREADS = "DownloadManagerService.extra.threads";
    private static final String EXTRA_POSTPROCESSING_NAME = "DownloadManagerService.extra.postprocessingName";
    private static final String EXTRA_POSTPROCESSING_ARGS = "DownloadManagerService.extra.postprocessingArgs";
    private static final String EXTRA_SOURCE = "DownloadManagerService.extra.source";
    private static final String EXTRA_NEAR_LENGTH = "DownloadManagerService.extra.nearLength";

    private static final String ACTION_RESET_DOWNLOAD_FINISHED = APPLICATION_ID + ".reset_download_finished";
    private static final String ACTION_OPEN_DOWNLOADS_FINISHED = APPLICATION_ID + ".open_downloads_finished";

    private DMBinder mBinder;
    private DownloadManager mManager;
    private Notification mNotification;
    private Handler mHandler;
    private boolean mForeground = false;
    private NotificationManager notificationManager = null;
    private boolean mDownloadNotificationEnable = true;

    private int downloadDoneCount = 0;
    private Builder downloadDoneNotification = null;
    private StringBuilder downloadDoneList = null;

    private final ArrayList<Handler> mEchoObservers = new ArrayList<>(1);

    private BroadcastReceiver mNetworkStateListener;

    private SharedPreferences mPrefs = null;
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefChangeListener = this::handlePreferenceChange;

    private boolean mLockAcquired = false;
    private LockManager mLock = null;

    private int downloadFailedNotificationID = DOWNLOADS_NOTIFICATION_ID + 1;
    private Builder downloadFailedNotification = null;
    private SparseArray<DownloadMission> mFailedDownloads = new SparseArray<>(5);

    private Bitmap icLauncher;
    private Bitmap icDownloadDone;
    private Bitmap icDownloadFailed;

    private PendingIntent mOpenDownloadList;

    /**
     * notify media scanner on downloaded media file ...
     *
     * @param file the downloaded file
     */
    private void notifyMediaScanner(File file) {
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        mBinder = new DMBinder();
        mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message msg) {
                DownloadManagerService.this.handleMessage(msg);
            }
        };

        mManager = new DownloadManager(this, mHandler);

        Intent openDownloadListIntent = new Intent(this, DownloadActivity.class)
                .setAction(Intent.ACTION_MAIN);

        mOpenDownloadList = PendingIntent.getActivity(this, 0,
                openDownloadListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        icLauncher = BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher);

        Builder builder = new Builder(this, getString(R.string.notification_channel_id))
                .setContentIntent(mOpenDownloadList)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(icLauncher)
                .setContentTitle(getString(R.string.msg_running))
                .setContentText(getString(R.string.msg_running_detail));

        mNotification = builder.build();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNetworkStateListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                    handleConnectivityChange(null);
                    return;
                }
                handleConnectivityChange(intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO));
            }
        };
        registerReceiver(mNetworkStateListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener);

        handlePreferenceChange(mPrefs, getString(R.string.downloads_cross_network));
        handlePreferenceChange(mPrefs, getString(R.string.downloads_maximum_retry));

        mLock = new LockManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            if (intent == null) {
                Log.d(TAG, "Restarting");
                return START_NOT_STICKY;
            }
            Log.d(TAG, "Starting");
        }
        Log.i(TAG, "Got intent: " + intent);
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(Intent.ACTION_RUN)) {
                String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
                String name = intent.getStringExtra(EXTRA_NAME);
                String location = intent.getStringExtra(EXTRA_LOCATION);
                int threads = intent.getIntExtra(EXTRA_THREADS, 1);
                char kind = intent.getCharExtra(EXTRA_KIND, '?');
                String psName = intent.getStringExtra(EXTRA_POSTPROCESSING_NAME);
                String[] psArgs = intent.getStringArrayExtra(EXTRA_POSTPROCESSING_ARGS);
                String source = intent.getStringExtra(EXTRA_SOURCE);
                long nearLength = intent.getLongExtra(EXTRA_NEAR_LENGTH, 0);

                mHandler.post(() -> mManager.startMission(urls, location, name, kind, threads, source, psName, psArgs, nearLength));

            } else if (downloadDoneNotification != null) {
                if (action.equals(ACTION_RESET_DOWNLOAD_FINISHED) || action.equals(ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    downloadDoneCount = 0;
                    downloadDoneList.setLength(0);
                }
                if (action.equals(ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    startActivity(new Intent(this, DownloadActivity.class)
                            .setAction(Intent.ACTION_MAIN)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    );
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DEBUG) {
            Log.d(TAG, "Destroying");
        }

        stopForeground(true);

        if (notificationManager != null && downloadDoneNotification != null) {
            downloadDoneNotification.setDeleteIntent(null);// prevent NewPipe running when is killed, cleared from recent, etc
            notificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification.build());
        }

        mManager.pauseAllMissions();

        manageLock(false);

        unregisterReceiver(mNetworkStateListener);
        mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);

        if (icDownloadDone != null) icDownloadDone.recycle();
        if (icDownloadFailed != null) icDownloadFailed.recycle();
        if (icLauncher != null) icLauncher.recycle();
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

    public void handleMessage(Message msg) {
        DownloadMission mission = (DownloadMission) msg.obj;

        switch (msg.what) {
            case MESSAGE_FINISHED:
                notifyMediaScanner(mission.getDownloadedFile());
                notifyFinishedDownload(mission.name);
                mManager.setFinished(mission);
                updateForegroundState(mManager.runAnotherMission());
                break;
            case MESSAGE_RUNNING:
            case MESSAGE_PROGRESS:
                updateForegroundState(true);
                break;
            case MESSAGE_ERROR:
                notifyFailedDownload(mission);
                updateForegroundState(mManager.runAnotherMission());
                break;
            case MESSAGE_PAUSED:
                updateForegroundState(mManager.getRunningMissionsCount() > 0);
                break;
        }

        if (msg.what != MESSAGE_ERROR)
            mFailedDownloads.delete(mFailedDownloads.indexOfValue(mission));

        synchronized (mEchoObservers) {
            for (Handler handler : mEchoObservers) {
                Message echo = new Message();
                echo.what = msg.what;
                echo.obj = msg.obj;

                handler.sendMessage(echo);
            }
        }
    }

    private void handleConnectivityChange(NetworkInfo info) {
        NetworkState status;

        if (info == null) {
            status = NetworkState.Unavailable;
            Log.i(TAG, "actual connectivity status is unavailable");
        } else if (!info.isAvailable() || !info.isConnected()) {
            status = NetworkState.Unavailable;
            Log.i(TAG, "actual connectivity status is not available and not connected");
        } else {
            int type = info.getType();
            if (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE_DUN) {
                status = NetworkState.MobileOperating;
            } else if (type == ConnectivityManager.TYPE_WIFI) {
                status = NetworkState.WifiOperating;
            } else if (type == ConnectivityManager.TYPE_WIMAX ||
                    type == ConnectivityManager.TYPE_ETHERNET ||
                    type == ConnectivityManager.TYPE_BLUETOOTH) {
                status = NetworkState.OtherOperating;
            } else {
                status = NetworkState.Unavailable;
            }
            Log.i(TAG, "actual connectivity status is " + status.name());
        }

        if (mManager == null) return;// avoid race-conditions while the service is starting
        mManager.handleConnectivityChange(status);
    }

    private void handlePreferenceChange(SharedPreferences prefs, String key) {
        if (key.equals(getString(R.string.downloads_maximum_retry))) {
            try {
                String value = prefs.getString(key, getString(R.string.downloads_maximum_retry_default));
                mManager.mPrefMaxRetry = Integer.parseInt(value);
            } catch (Exception e) {
                mManager.mPrefMaxRetry = 0;
            }
            mManager.updateMaximumAttempts();
        } else if (key.equals(getString(R.string.downloads_cross_network))) {
            mManager.mPrefCrossNetwork = prefs.getBoolean(key, false);
        }
    }

    public void updateForegroundState(boolean state) {
        if (state == mForeground) return;

        if (state) {
            startForeground(FOREGROUND_NOTIFICATION_ID, mNotification);
        } else {
            stopForeground(true);
        }

        manageLock(state);

        mForeground = state;
    }

    public static void startMission(Context context, String urls[], String location, String name, char kind,
                                    int threads, String source, String psName, String[] psArgs, long nearLength) {
        Intent intent = new Intent(context, DownloadManagerService.class);
        intent.setAction(Intent.ACTION_RUN);
        intent.putExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_LOCATION, location);
        intent.putExtra(EXTRA_KIND, kind);
        intent.putExtra(EXTRA_THREADS, threads);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_POSTPROCESSING_NAME, psName);
        intent.putExtra(EXTRA_POSTPROCESSING_ARGS, psArgs);
        intent.putExtra(EXTRA_NEAR_LENGTH, nearLength);
        context.startService(intent);
    }

    public static void checkForRunningMission(Context context, String location, String name, DMChecker check) {
        Intent intent = new Intent();
        intent.setClass(context, DownloadManagerService.class);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName cname, IBinder service) {
                try {
                    ((DMBinder) service).getDownloadManager().checkForRunningMission(location, name, check);
                } catch (Exception err) {
                    Log.w(TAG, "checkForRunningMission() callback is defective", err);
                }

                // TODO: find a efficient way to unbind the service. This destroy the service due idle, but is started again when the user start a download.
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }

    public void notifyFinishedDownload(String name) {
        if (!mDownloadNotificationEnable || notificationManager == null) {
            return;
        }

        if (downloadDoneNotification == null) {
            downloadDoneList = new StringBuilder(name.length());

            icDownloadDone = BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_download_done);
            downloadDoneNotification = new Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadDone)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setDeleteIntent(makePendingIntent(ACTION_RESET_DOWNLOAD_FINISHED))
                    .setContentIntent(makePendingIntent(ACTION_OPEN_DOWNLOADS_FINISHED));
        }

        if (downloadDoneCount < 1) {
            downloadDoneList.append(name);

            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                downloadDoneNotification.setContentTitle(getString(R.string.app_name));
            } else {
                downloadDoneNotification.setContentTitle(null);
            }

            downloadDoneNotification.setContentText(getString(R.string.download_finished));
            downloadDoneNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .setBigContentTitle(getString(R.string.download_finished))
                    .bigText(name)
            );
        } else {
            downloadDoneList.append('\n');
            downloadDoneList.append(name);

            downloadDoneNotification.setStyle(new NotificationCompat.BigTextStyle().bigText(downloadDoneList));
            downloadDoneNotification.setContentTitle(getString(R.string.download_finished_more, String.valueOf(downloadDoneCount + 1)));
            downloadDoneNotification.setContentText(downloadDoneList);
        }

        notificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification.build());
        downloadDoneCount++;
    }

    public void notifyFailedDownload(DownloadMission mission) {
        if (!mDownloadNotificationEnable || mFailedDownloads.indexOfValue(mission) >= 0) return;

        int id = downloadFailedNotificationID++;
        mFailedDownloads.put(id, mission);

        if (downloadFailedNotification == null) {
            icDownloadFailed = BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_warning);
            downloadFailedNotification = new Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadFailed)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentIntent(mOpenDownloadList);
        }

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            downloadFailedNotification.setContentTitle(getString(R.string.app_name));
            downloadFailedNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.download_failed).concat(": ").concat(mission.name)));
        } else {
            downloadFailedNotification.setContentTitle(getString(R.string.download_failed));
            downloadFailedNotification.setContentText(mission.name);
            downloadFailedNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(mission.name));
        }

        notificationManager.notify(id, downloadFailedNotification.build());
    }

    private PendingIntent makePendingIntent(String action) {
        Intent intent = new Intent(this, DownloadManagerService.class).setAction(action);
        return PendingIntent.getService(this, intent.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void manageObservers(Handler handler, boolean add) {
        synchronized (mEchoObservers) {
            if (add) {
                mEchoObservers.add(handler);
            } else {
                mEchoObservers.remove(handler);
            }
        }
    }

    private void manageLock(boolean acquire) {
        if (acquire == mLockAcquired) return;

        if (acquire)
            mLock.acquireWifiAndCpu();
        else
            mLock.releaseWifiAndCpu();

        mLockAcquired = acquire;
    }

    // Wrapper of DownloadManager
    public class DMBinder extends Binder {
        public DownloadManager getDownloadManager() {
            return mManager;
        }

        public void addMissionEventListener(Handler handler) {
            manageObservers(handler, true);
        }

        public void removeMissionEventListener(Handler handler) {
            manageObservers(handler, false);
        }

        public void clearDownloadNotifications() {
            if (notificationManager == null) return;
            if (downloadDoneNotification != null) {
                notificationManager.cancel(DOWNLOADS_NOTIFICATION_ID);
                downloadDoneList.setLength(0);
                downloadDoneCount = 0;
            }
            if (downloadFailedNotification != null) {
                for (; downloadFailedNotificationID > DOWNLOADS_NOTIFICATION_ID; downloadFailedNotificationID--) {
                    notificationManager.cancel(downloadFailedNotificationID);
                }
                mFailedDownloads.clear();
                downloadFailedNotificationID++;
            }
        }

        public void enableNotifications(boolean enable) {
            mDownloadNotificationEnable = enable;
        }

    }

    public interface DMChecker {
        void callback(boolean listed, boolean finished);
    }

}
