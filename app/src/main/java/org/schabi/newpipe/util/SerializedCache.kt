package org.schabi.newpipe.util

import android.util.Log
import androidx.collection.LruCache
import org.schabi.newpipe.DebugConstants
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID
import okio.Buffer

object SerializedCache {
    private const val DEBUG = DebugConstants.DEBUG
    private const val MAX_ITEMS_ON_CACHE = 5
    private val LRU_CACHE = LruCache<String, CacheData<*>>(MAX_ITEMS_ON_CACHE)
    private const val TAG = "SerializedCache"

    @JvmStatic
    fun getInstance(): SerializedCache = this

    @JvmStatic
    fun <T : Any> take(key: String, type: Class<T>): T? {
        if (DEBUG) {
            Log.d(TAG, "take() called with: key = [$key]")
        }
        synchronized(LRU_CACHE) {
            return LRU_CACHE.remove(key)?.let { getItem(it, type) }
        }
    }

    inline fun <reified T : Any> take(key: String): T? = take(key, T::class.java)

    @JvmStatic
    fun <T : Any> get(key: String, type: Class<T>): T? {
        if (DEBUG) {
            Log.d(TAG, "get() called with: key = [$key]")
        }
        synchronized(LRU_CACHE) {
            return LRU_CACHE[key]?.let { getItem(it, type) }
        }
    }

    inline fun <reified T : Any> get(key: String): T? = get(key, T::class.java)

    @JvmStatic
    fun <T : Serializable> put(item: T, type: Class<T>): String? {
        val key = UUID.randomUUID().toString()
        return if (put(key, item, type)) key else null
    }

    inline fun <reified T : Serializable> put(item: T): String? = put(item, T::class.java)

    @JvmStatic
    fun <T : Serializable> put(key: String, item: T, type: Class<T>): Boolean {
        if (DEBUG) {
            Log.d(TAG, "put() called with: key = [$key], item = [$item]")
        }
        synchronized(LRU_CACHE) {
            return try {
                LRU_CACHE.put(key, CacheData(clone(item, type), type))
                true
            } catch (error: Exception) {
                Log.e(TAG, "Serialization failed for: ", error)
                false
            }
        }
    }

    inline fun <reified T : Serializable> put(key: String, item: T): Boolean =
        put(key, item, T::class.java)

    @JvmStatic
    fun clear() {
        if (DEBUG) {
            Log.d(TAG, "clear() called")
        }
        synchronized(LRU_CACHE) {
            LRU_CACHE.evictAll()
        }
    }

    @JvmStatic
    fun size(): Int {
        synchronized(LRU_CACHE) {
            return LRU_CACHE.size()
        }
    }

    private fun <T : Any> getItem(data: CacheData<*>, type: Class<T>): T? {
        return if (type.isAssignableFrom(data.type)) type.cast(data.item) else null
    }

    @Throws(Exception::class)
    private fun <T : Serializable> clone(item: T, type: Class<T>): T {
        val buffer = Buffer()
        ObjectOutputStream(buffer.outputStream()).use { it.writeObject(item) }
        val clone = ObjectInputStream(buffer.inputStream()).use { it.readObject() }
        return type.cast(clone)!!
    }

    private class CacheData<T>(val item: T, val type: Class<T>)
}
