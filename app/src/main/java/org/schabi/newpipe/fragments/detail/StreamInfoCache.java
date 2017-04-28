package org.schabi.newpipe.fragments.detail;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.Iterator;
import java.util.LinkedHashMap;


@SuppressWarnings("WeakerAccess")
public class StreamInfoCache {
    private static String TAG = "StreamInfoCache@";
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final StreamInfoCache instance = new StreamInfoCache();
    private static final int MAX_ITEMS_ON_CACHE = 20;

    private final LinkedHashMap<String, StreamInfo> myCache = new LinkedHashMap<>();

    private StreamInfoCache() {
        TAG += "" + Integer.toHexString(hashCode());
    }

    public static StreamInfoCache getInstance() {
        if (DEBUG) Log.d(TAG, "getInstance() called");
        return instance;
    }

    public boolean hasKey(@NonNull String url) {
        if (DEBUG) Log.d(TAG, "hasKey() called with: url = [" + url + "]");
        return !TextUtils.isEmpty(url) && myCache.containsKey(url) && myCache.get(url) != null;
    }

    public StreamInfo getFromKey(@NonNull String url) {
        if (DEBUG) Log.d(TAG, "getFromKey() called with: url = [" + url + "]");
        return myCache.get(url);
    }

    public void putInfo(@NonNull StreamInfo info) {
        if (DEBUG) Log.d(TAG, "putInfo() called with: info = [" + info + "]");
        putInfo(info.webpage_url, info);
    }

    public void putInfo(@NonNull String url, @NonNull StreamInfo info) {
        if (DEBUG) Log.d(TAG, "putInfo() called with: url = [" + url + "], info = [" + info + "]");
        myCache.put(url, info);
    }

    public void removeInfo(@NonNull StreamInfo info) {
        if (DEBUG) Log.d(TAG, "removeInfo() called with: info = [" + info + "]");
        myCache.remove(info.webpage_url);
    }

    public void removeInfo(@NonNull String url) {
        if (DEBUG) Log.d(TAG, "removeInfo() called with: url = [" + url + "]");
        myCache.remove(url);
    }

    @SuppressWarnings("unused")
    public void clearCache() {
        if (DEBUG) Log.d(TAG, "clearCache() called");
        myCache.clear();
    }

    public void removeOldEntries() {
        if (DEBUG) Log.d(TAG, "removeOldEntries() called , size = " + getSize());
        if (getSize() > MAX_ITEMS_ON_CACHE) {
            Iterator<String> iterator = myCache.keySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
                if (DEBUG) Log.d(TAG, "getSize() = " + getSize());
                if (getSize() <= MAX_ITEMS_ON_CACHE) break;
            }
        }
    }

    public int getSize() {
        return myCache.size();
    }

}
