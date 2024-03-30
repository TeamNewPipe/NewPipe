package org.schabi.newpipe.util

import android.util.Log
import androidx.collection.LruCache
import org.schabi.newpipe.MainActivity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID

class SerializedCache private constructor() {
    fun <T> take(key: String, type: Class<T>): T? {
        if (DEBUG) {
            Log.d(TAG, "take() called with: key = [" + key + "]")
        }
        synchronized(LRU_CACHE, { return if (LRU_CACHE.get(key) != null) getItem(LRU_CACHE.remove(key)!!, type) else null })
    }

    operator fun <T> get(key: String, type: Class<T>): T? {
        if (DEBUG) {
            Log.d(TAG, "get() called with: key = [" + key + "]")
        }
        synchronized(LRU_CACHE, {
            val data: CacheData<*>? = LRU_CACHE.get(key)
            return if (data != null) getItem(data, type) else null
        })
    }

    fun <T : Serializable?> put(item: T,
                                type: Class<T>): String? {
        val key: String = UUID.randomUUID().toString()
        return if (put(key, item, type)) key else null
    }

    fun <T : Serializable?> put(key: String, item: T,
                                type: Class<T>): Boolean {
        if (DEBUG) {
            Log.d(TAG, "put() called with: key = [" + key + "], item = [" + item + "]")
        }
        synchronized(LRU_CACHE, {
            try {
                LRU_CACHE.put(key, CacheData(clone(item, type), type))
                return true
            } catch (error: Exception) {
                Log.e(TAG, "Serialization failed for: ", error)
            }
        })
        return false
    }

    fun clear() {
        if (DEBUG) {
            Log.d(TAG, "clear() called")
        }
        synchronized(LRU_CACHE, { LRU_CACHE.evictAll() })
    }

    fun size(): Long {
        synchronized(LRU_CACHE, { return LRU_CACHE.size().toLong() })
    }

    private fun <T> getItem(data: CacheData<*>, type: Class<T>): T? {
        return if (type.isAssignableFrom(data.type)) type.cast(data.item) else null
    }

    @Throws(Exception::class)
    private fun <T : Serializable?> clone(item: T,
                                          type: Class<T>): T {
        val bytesOutput: ByteArrayOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(bytesOutput).use({ objectOutput ->
            objectOutput.writeObject(item)
            objectOutput.flush()
        })
        val clone: Any = ObjectInputStream(
                ByteArrayInputStream(bytesOutput.toByteArray())).readObject()
        return type.cast(clone)
    }

    private class CacheData<T>(val item: T, val type: Class<T>)
    companion object {
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG
        val instance: SerializedCache = SerializedCache()
        private val MAX_ITEMS_ON_CACHE: Int = 5
        private val LRU_CACHE: LruCache<String, CacheData<*>> = LruCache(MAX_ITEMS_ON_CACHE)
        private val TAG: String = "SerializedCache"
    }
}
