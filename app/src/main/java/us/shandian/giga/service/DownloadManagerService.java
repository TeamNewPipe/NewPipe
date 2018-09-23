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
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManager.NetworkState;

import static org.schabi.newpipe.BuildConfig.APPLICATION_ID;
import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerService extends Service {

    private static final String TAG = DownloadManagerService.class.getSimpleName();

    public static final int MESSAGE_RUNNING = 1;
    public static final int MESSAGE_PAUSED = 2;
    public static final int MESSAGE_FINISHED = 3;
    public static final int MESSAGE_PROGRESS = 4;
    public static final int MESSAGE_ERROR = 5;

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

    private static final String ACTION_RESET_DOWNLOAD_COUNT = APPLICATION_ID + ".reset_download_count";

    private DMBinder mBinder;
    private DownloadManager mManager;
    private Notification mNotification;
    private Handler mHandler;
    private int downloadDoneCount = 0;
    private Builder downloadDoneNotification = null;
    private StringBuilder downloadDoneList = null;
    NotificationManager notificationManager = null;
    private boolean mForeground = false;
    private final ArrayList<Handler> mEchoObservers = new ArrayList<>(1);

    private BroadcastReceiver mNetworkStateListener;

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

                mHandler.post(() -> mManager.startMission(urls, location, name, kind, threads, source, psName, psArgs));

            } else if (downloadDoneNotification != null && action.equals(ACTION_RESET_DOWNLOAD_COUNT)) {
                downloadDoneCount = 0;
                downloadDoneList.setLength(0);
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

        unregisterReceiver(mNetworkStateListener);

        mManager.pauseAllMissions();

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
        switch (msg.what) {
            case MESSAGE_FINISHED:
                DownloadMission mission = (DownloadMission) msg.obj;
                notifyMediaScanner(mission.getDownloadedFile());
                notifyFinishedDownload(mission.name);
                updateForegroundState(mManager.setFinished(mission));
                break;
            case MESSAGE_RUNNING:
            case MESSAGE_PROGRESS:
                updateForegroundState(true);
                break;
            case MESSAGE_PAUSED:
            case MESSAGE_ERROR:
                updateForegroundState(mManager.getRunningMissionsCount() > 0);
                break;
        }


        synchronized (mEchoObservers) {
            Iterator<Handler> iterator = mEchoObservers.iterator();
            while (iterator.hasNext()) {
                Handler handler = iterator.next();
                if (handler.getLooper().getThread().isAlive()) {
                    Message echo = new Message();
                    echo.what = msg.what;
                    echo.obj = msg.obj;
                    handler.sendMessage(echo);
                } else {
                    iterator.remove();// ¿missing call to removeMissionEventListener()?
                }
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

    public void updateForegroundState(boolean state) {
        if (state == mForeground) return;

        if (state) {
            startForeground(FOREGROUND_NOTIFICATION_ID, mNotification);
        } else {
            stopForeground(true);
        }

        mForeground = state;
    }

    public static void startMission(Context context, String urls[], String location, String name,
                                    char kind, int threads, String source, String postprocessingName,
                                    String[] postprocessingArgs) {
        Intent intent = new Intent(context, DownloadManagerService.class);
        intent.setAction(Intent.ACTION_RUN);
        intent.putExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_LOCATION, location);
        intent.putExtra(EXTRA_KIND, kind);
        intent.putExtra(EXTRA_THREADS, threads);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_POSTPROCESSING_NAME, postprocessingName);
        intent.putExtra(EXTRA_POSTPROCESSING_ARGS, postprocessingArgs);
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
        if (notificationManager == null) {
            return;
        }

        if (downloadDoneNotification == null) {
            downloadDoneList = new StringBuilder(name.length());

            Bitmap icon = BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_download_done);
            downloadDoneNotification = new Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icon)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setDeleteIntent(PendingIntent.getService(this, (int) System.currentTimeMillis(),
                            new Intent(this, DownloadManagerService.class)
                                    .setAction(ACTION_RESET_DOWNLOAD_COUNT)
                            , PendingIntent.FLAG_UPDATE_CURRENT))
                    .setContentIntent(mNotification.contentIntent);
        }

        if (downloadDoneCount < 1) {
            downloadDoneList.append(name);

            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                downloadDoneNotification.setContentTitle(getString(R.string.app_name));
                downloadDoneNotification.setContentText(getString(R.string.download_finished, name));
            } else {
                downloadDoneNotification.setContentTitle(getString(R.string.download_finished, name));
                downloadDoneNotification.setContentText(null);
            }
        } else {
            downloadDoneList.append(", ");
            downloadDoneList.append(name);

            downloadDoneNotification.setContentTitle(getString(R.string.download_finished_more, String.valueOf(downloadDoneCount + 1)));
            downloadDoneNotification.setContentText(downloadDoneList.toString());
        }

        notificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification.build());
        downloadDoneCount++;
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

        public void resetFinishedDownloadCount() {
            if (notificationManager == null || downloadDoneNotification == null) return;
            notificationManager.cancel(DOWNLOADS_NOTIFICATION_ID);
            downloadDoneList.setLength(0);
            downloadDoneCount = 0;
        }
    }

    public interface DMChecker {
        void callback(boolean listed, boolean finished);
    }

}
