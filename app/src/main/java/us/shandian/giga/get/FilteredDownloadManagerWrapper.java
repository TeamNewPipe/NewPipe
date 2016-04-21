package us.shandian.giga.get;

import android.content.Context;

import java.util.Map.Entry;
import java.util.HashMap;

public class FilteredDownloadManagerWrapper implements DownloadManager
{

	private boolean mDownloaded = false; // T=Filter downloaded files; F=Filter downloading files
	private DownloadManager mManager;
	private HashMap<Integer, Integer> mElementsMap = new HashMap<Integer, Integer>();
	
	public FilteredDownloadManagerWrapper(DownloadManager manager, boolean filterDownloaded) {
		mManager = manager;
		mDownloaded = filterDownloaded;
		refreshMap();
	}
	
	private void refreshMap() {
		mElementsMap.clear();
		
		int size = 0;
		for (int i = 0; i < mManager.getCount(); i++) {
			if (mManager.getMission(i).finished == mDownloaded) {
				mElementsMap.put(size++, i);
			}
		}
	}
	
	private int toRealPosition(int pos) {
		if (mElementsMap.containsKey(pos)) {
			return mElementsMap.get(pos);
		} else {
			return -1;
		}
	}
	
	private int toFakePosition(int pos) {
		for (Entry<Integer, Integer> entry : mElementsMap.entrySet()) {
			if (entry.getValue() == pos) {
				return entry.getKey();
			}
		}
		
		return -1;
	}

	@Override
	public int startMission(String url, String name, int threads) {
		int ret = mManager.startMission(url, name, threads);
		refreshMap();
		return toFakePosition(ret);
	}

	@Override
	public void resumeMission(int id) {
		mManager.resumeMission(toRealPosition(id));
	}

	@Override
	public void pauseMission(int id) {
		mManager.pauseMission(toRealPosition(id));
	}

	@Override
	public void deleteMission(int id) {
		mManager.deleteMission(toRealPosition(id));
	}

	@Override
	public DownloadMission getMission(int id) {
		return mManager.getMission(toRealPosition(id));
	}
	

	@Override
	public int getCount() {
		return mElementsMap.size();
	}

	@Override
	public String getLocation() {
		return mManager.getLocation();
	}
	
}
