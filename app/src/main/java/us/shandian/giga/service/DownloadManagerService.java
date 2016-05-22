package us.shandian.giga.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import org.schabi.newpipe.NewPipeSettings;
import org.schabi.newpipe.R;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadManagerImpl;
import us.shandian.giga.get.DownloadMission;
import org.schabi.newpipe.download.MainActivity;
import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerService extends Service implements DownloadMission.MissionListener
{
	
	private static final String TAG = DownloadManagerService.class.getSimpleName();
	
	private DMBinder mBinder;
	private DownloadManager mManager;
	private Notification mNotification;
	private Handler mHandler;
	private long mLastTimeStamp = System.currentTimeMillis();

	@Override
	public void onCreate() {
		super.onCreate();
		
		if (DEBUG) {
			Log.d(TAG, "onCreate");
		}
		
		mBinder = new DMBinder();
		if (mManager == null) {
			String path = NewPipeSettings.getVideoDownloadPath(this);
			mManager = new DownloadManagerImpl(this, path);
			if (DEBUG) {
				Log.d(TAG, "mManager == null");
				Log.d(TAG, "Download directory: " + path);
			}
		}
		
		Intent i = new Intent();
		i.setAction(Intent.ACTION_MAIN);
		i.setClass(this, MainActivity.class);

		Drawable icon = this.getResources().getDrawable(R.mipmap.ic_launcher);

		Builder builder = new Builder(this)
				.setContentIntent(PendingIntent.getActivity(this, 0, i, 0))
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setLargeIcon(((BitmapDrawable) icon).getBitmap())
				.setContentTitle(getString(R.string.msg_running))
				.setContentText(getString(R.string.msg_running_detail));

		PendingIntent pendingIntent =
				PendingIntent.getActivity(
						this,
						0,
						new Intent(this, MainActivity.class)
								.setAction(MainActivity.INTENT_LIST),
						PendingIntent.FLAG_UPDATE_CURRENT
				);

		builder.setContentIntent(pendingIntent);

		mNotification = builder.build();

		HandlerThread thread = new HandlerThread("ServiceMessenger");
		thread.start();
			
		mHandler = new Handler(thread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == 0) {
					int runningCount = 0;
					
					for (int i = 0; i < mManager.getCount(); i++) {
						if (mManager.getMission(i).running) {
							runningCount++;
						}
					}
					
					updateState(runningCount);
				}
			}
		};
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (DEBUG) {
			Log.d(TAG, "Starting");
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
		return mBinder;
	}
	

	@Override
	public void onProgressUpdate(long done, long total) {

		long now = System.currentTimeMillis();

		long delta = now - mLastTimeStamp;

		if (delta > 2000) {
			postUpdateMessage();
			mLastTimeStamp = now;
		}
	}

	@Override
	public void onFinish() {
		postUpdateMessage();
	}

	@Override
	public void onError(int errCode) {
		postUpdateMessage();
	}
	
	private void postUpdateMessage() {
		mHandler.sendEmptyMessage(0);
	}
	
	private void updateState(int runningCount) {
		if (runningCount == 0) {
			stopForeground(true);
		} else {
			startForeground(1000, mNotification);
		}
	}
	
	
	// Wrapper of DownloadManager
	public class DMBinder extends Binder {
		public DownloadManager getDownloadManager() {
			return mManager;
		}
		
		public void onMissionAdded(DownloadMission mission) {
			mission.addListener(DownloadManagerService.this);
			postUpdateMessage();
		}
		
		public void onMissionRemoved(DownloadMission mission) {
			mission.removeListener(DownloadManagerService.this);
			postUpdateMessage();
		}
		
	}

}
