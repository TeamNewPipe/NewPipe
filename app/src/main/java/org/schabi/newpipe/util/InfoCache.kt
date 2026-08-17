/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * InfoCache.kt is part of NewPipe
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

package org.schabi.newpipe.util

import android.util.Log
import androidx.collection.LruCache
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.extractor.Info

object InfoCache {
    private val TAG = InfoCache::class.java.simpleName
    private val DEBUG = DebugConstants.DEBUG

    private const val MAX_ITEMS_ON_CACHE = 200

    /**
     * Trim the cache to this size.
     */
    private const val TRIM_CACHE_TO = 100

    private val LRU_CACHE = LruCache<String, CacheData>(MAX_ITEMS_ON_CACHE)

    /**
     * Identifies the type of [Info] to put into the cache.
     */
    enum class Type {
        STREAM,
        CHANNEL,
        CHANNEL_TAB,
        COMMENTS,
        PLAYLIST,
        KIOSK,
        SEARCH,
    }

    private fun keyOf(
        serviceId: Int,
        url: String,
        cacheType: Type
    ): String {
        return "$serviceId:${cacheType.ordinal}:$url"
    }

    private fun removeStaleCache() {
        for ((key, data) in LRU_CACHE.snapshot()) {
            if (data != null && data.isExpired) {
                LRU_CACHE.remove(key)
            }
        }
    }

    private fun getInfo(key: String): Info? {
        val data = LRU_CACHE.get(key) ?: return null

        if (data.isExpired) {
            LRU_CACHE.remove(key)
            return null
        }

        return data.info
    }

    fun getFromKey(
        serviceId: Int,
        url: String,
        cacheType: Type
    ): Info? {
        if (DEBUG) {
            Log.d(TAG, "getFromKey() called with: serviceId = [$serviceId], url = [$url]")
        }
        synchronized(LRU_CACHE) {
            return getInfo(keyOf(serviceId, url, cacheType))
        }
    }

    fun putInfo(
        serviceId: Int,
        url: String,
        info: Info,
        cacheType: Type
    ) {
        if (DEBUG) {
            Log.d(TAG, "putInfo() called with: info = [$info]")
        }

        val expirationMillis = ServiceHelper.getCacheExpirationMillis(info.serviceId)
        synchronized(LRU_CACHE) {
            val data = CacheData(info, expirationMillis)
            LRU_CACHE.put(keyOf(serviceId, url, cacheType), data)
        }
    }

    fun removeInfo(
        serviceId: Int,
        url: String,
        cacheType: Type
    ) {
        if (DEBUG) {
            Log.d(TAG, "removeInfo() called with: serviceId = [$serviceId], url = [$url]")
        }
        synchronized(LRU_CACHE) {
            LRU_CACHE.remove(keyOf(serviceId, url, cacheType))
        }
    }

    fun clearCache() {
        if (DEBUG) {
            Log.d(TAG, "clearCache() called")
        }
        synchronized(LRU_CACHE) {
            LRU_CACHE.evictAll()
        }
    }

    fun trimCache() {
        if (DEBUG) {
            Log.d(TAG, "trimCache() called")
        }
        synchronized(LRU_CACHE) {
            removeStaleCache()
            LRU_CACHE.trimToSize(TRIM_CACHE_TO)
        }
    }

    val size: Int
        get() = synchronized(LRU_CACHE) {
            LRU_CACHE.size()
        }

    private class CacheData(val info: Info, timeoutMillis: Long) {
        private val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

        val isExpired: Boolean
            get() = System.currentTimeMillis() > expireTimestamp
    }
}
