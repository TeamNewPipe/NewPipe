/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * InfoCache.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;

import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.Info;


public final class InfoCache {
    private static final boolean DEBUG = MainActivity.DEBUG;
    private final String TAG = getClass().getSimpleName();

    private static final InfoCache instance = new InfoCache();
    private static final int MAX_ITEMS_ON_CACHE = 60;
    /**
     * Trim the cache to this size
     */
    private static final int TRIM_CACHE_TO = 30;

    // TODO: Replace to one with timeout (like the one from guava)
    private static final LruCache<String, Info> lruCache = new LruCache<>(MAX_ITEMS_ON_CACHE);

    private InfoCache() {
        //no instance
    }

    public static InfoCache getInstance() {
        return instance;
    }

    public Info getFromKey(int serviceId, @NonNull String url) {
        if (DEBUG) Log.d(TAG, "getFromKey() called with: serviceId = [" + serviceId + "], url = [" + url + "]");
        synchronized (lruCache) {
            return lruCache.get(serviceId + url);
        }
    }

    public void putInfo(@NonNull Info info) {
        if (DEBUG) Log.d(TAG, "putInfo() called with: info = [" + info + "]");
        synchronized (lruCache) {
            lruCache.put(info.service_id + info.url, info);
        }
    }

    public void removeInfo(@NonNull Info info) {
        if (DEBUG) Log.d(TAG, "removeInfo() called with: info = [" + info + "]");
        synchronized (lruCache) {
            lruCache.remove(info.service_id + info.url);
        }
    }

    public void removeInfo(int serviceId, @NonNull String url) {
        if (DEBUG) Log.d(TAG, "removeInfo() called with: serviceId = [" + serviceId + "], url = [" + url + "]");
        synchronized (lruCache) {
            lruCache.remove(serviceId + url);
        }
    }

    public void clearCache() {
        if (DEBUG) Log.d(TAG, "clearCache() called");
        synchronized (lruCache) {
            lruCache.evictAll();
        }
    }

    public void trimCache() {
        if (DEBUG) Log.d(TAG, "trimCache() called");
        synchronized (lruCache) {
            lruCache.trimToSize(TRIM_CACHE_TO);
        }
    }

    public long getSize() {
        synchronized (lruCache) {
            return lruCache.size();
        }
    }

}
