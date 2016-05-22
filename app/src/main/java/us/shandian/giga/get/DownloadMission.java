package us.shandian.giga.get;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;

import us.shandian.giga.util.Utility;
import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadMission
{
	private static final String TAG = DownloadMission.class.getSimpleName();
	
	public static interface MissionListener {
		HashMap<MissionListener, Handler> handlerStore = new HashMap<>();
		
		public void onProgressUpdate(long done, long total);
		public void onFinish();
		public void onError(int errCode);
	}
	
	public static final int ERROR_SERVER_UNSUPPORTED = 206;
	public static final int ERROR_UNKNOWN = 233;
	
	public String name = "";
	public String url = "";
	public String location = "";
	public long blocks = 0;
	public long length = 0;
	public long done = 0;
	public int threadCount = 3;
	public int finishCount = 0;
	public ArrayList<Long> threadPositions = new ArrayList<Long>();
	public HashMap<Long, Boolean> blockState = new HashMap<Long, Boolean>();
	public boolean running = false;
	public boolean finished = false;
	public boolean fallback = false;
	public int errCode = -1;
	public long timestamp = 0;
	
	public transient boolean recovered = false;
	
	private transient ArrayList<WeakReference<MissionListener>> mListeners = new ArrayList<WeakReference<MissionListener>>();
	private transient boolean mWritingToFile = false;
	
	public boolean isBlockPreserved(long block) {
		return blockState.containsKey(block) ? blockState.get(block) : false;
	}
	
	public void preserveBlock(long block) {
		synchronized (blockState) {
			blockState.put(block, true);
		}
	}
	
	public void setPosition(int id, long position) {
		threadPositions.set(id, position);
	}
	
	public long getPosition(int id) {
		return threadPositions.get(id);
	}
	
	public synchronized void notifyProgress(long deltaLen) {
		if (!running) return;
		
		if (recovered) {
			recovered = false;
		}
		
		done += deltaLen;
		
		if (done > length) {
			done = length;
		}
		
		if (done != length) {
			writeThisToFile();
		}
		
		for (WeakReference<MissionListener> ref: mListeners) {
			final MissionListener listener = ref.get();
			if (listener != null) {
				MissionListener.handlerStore.get(listener).post(new Runnable() {
					@Override
					public void run() {
						listener.onProgressUpdate(done, length);
					}
				});
			}
		}
	}
	
	public synchronized void notifyFinished() {
		if (errCode > 0) return;
		
		finishCount++;
		
		if (finishCount == threadCount) {
			onFinish();
		}
	}
	
	private void onFinish() {
		if (errCode > 0) return;
		
		if (DEBUG) {
			Log.d(TAG, "onFinish");
		}
		
		running = false;
		finished = true;
		
		deleteThisFromFile();
		
		for (WeakReference<MissionListener> ref : mListeners) {
			final MissionListener listener = ref.get();
			if (listener != null) {
				MissionListener.handlerStore.get(listener).post(new Runnable() {
					@Override
					public void run() {
						listener.onFinish();
					}
				});
			}
		}
	}
	
	public synchronized void notifyError(int err) {
		errCode = err;
		
		writeThisToFile();
		
		for (WeakReference<MissionListener> ref : mListeners) {
			final MissionListener listener = ref.get();
			MissionListener.handlerStore.get(listener).post(new Runnable() {
				@Override
				public void run() {
					listener.onError(errCode);
				}
			});
		}
	}
	
	public synchronized void addListener(MissionListener listener) {
		Handler handler = new Handler(Looper.getMainLooper());
		MissionListener.handlerStore.put(listener, handler);
		mListeners.add(new WeakReference<MissionListener>(listener));
	}
	
	public synchronized void removeListener(MissionListener listener) {
		for (Iterator<WeakReference<MissionListener>> iterator = mListeners.iterator();
			 iterator.hasNext(); ) {
			WeakReference<MissionListener> weakRef = iterator.next();
			if (listener!=null && listener == weakRef.get())
			{
				iterator.remove();
			}
		}
	}
	
	public void start() {
		if (!running && !finished) {
			running = true;
			
			if (!fallback) {
				for (int i = 0; i < threadCount; i++) {
					if (threadPositions.size() <= i && !recovered) {
						threadPositions.add((long) i);
					}
					new Thread(new DownloadRunnable(this, i)).start();
				}
			} else {
				// In fallback mode, resuming is not supported.
				threadCount = 1;
				done = 0;
				blocks = 0;
				new Thread(new DownloadRunnableFallback(this)).start();
			}
		}
	}
	
	public void pause() {
		if (running) {
			running = false;
			recovered = true;
			
			// TODO: Notify & Write state to info file
			// if (err)
		}
	}
	
	public void delete() {
		deleteThisFromFile();
		new File(location + "/" + name).delete();
	}
	
	public void writeThisToFile() {
		if (!mWritingToFile) {
			mWritingToFile = true;
			new Thread() {
				@Override
				public void run() {
					doWriteThisToFile();
					mWritingToFile = false;
				}
			}.start();
		}
	}
	
	private void doWriteThisToFile() {
		synchronized (blockState) {
			Utility.writeToFile(location + "/" + name + ".giga", new Gson().toJson(this));
		}
	}
	
	private void deleteThisFromFile() {
		new File(location + "/" + name + ".giga").delete();
	}
}
