package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import org.schabi.newpipe.MainActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

public final class SerializedCache {
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final SerializedCache INSTANCE = new SerializedCache();
    private static final int MAX_ITEMS_ON_CACHE = 5;
    private static final LruCache<String, CacheData> LRU_CACHE =
            new LruCache<>(MAX_ITEMS_ON_CACHE);
    private static final String TAG = "SerializedCache";

    private SerializedCache() {
        //no instance
    }

    public static SerializedCache getInstance() {
        return INSTANCE;
    }

    @Nullable
    public <T> T take(@NonNull final String key, @NonNull final Class<T> type) {
        if (DEBUG) {
            Log.d(TAG, "take() called with: key = [" + key + "]");
        }
        synchronized (LRU_CACHE) {
            return LRU_CACHE.get(key) != null ? getItem(LRU_CACHE.remove(key), type) : null;
        }
    }

    @Nullable
    public <T> T get(@NonNull final String key, @NonNull final Class<T> type) {
        if (DEBUG) {
            Log.d(TAG, "get() called with: key = [" + key + "]");
        }
        synchronized (LRU_CACHE) {
            final CacheData data = LRU_CACHE.get(key);
            return data != null ? getItem(data, type) : null;
        }
    }

    @Nullable
    public <T extends Serializable> String put(@NonNull final T item,
                                               @NonNull final Class<T> type) {
        final String key = UUID.randomUUID().toString();
        return put(key, item, type) ? key : null;
    }

    public <T extends Serializable> boolean put(@NonNull final String key, @NonNull final T item,
                                                @NonNull final Class<T> type) {
        if (DEBUG) {
            Log.d(TAG, "put() called with: key = [" + key + "], item = [" + item + "]");
        }
        synchronized (LRU_CACHE) {
            try {
                LRU_CACHE.put(key, new CacheData<>(clone(item, type), type));
                return true;
            } catch (final Exception error) {
                Log.e(TAG, "Serialization failed for: ", error);
            }
        }
        return false;
    }

    public void clear() {
        if (DEBUG) {
            Log.d(TAG, "clear() called");
        }
        synchronized (LRU_CACHE) {
            LRU_CACHE.evictAll();
        }
    }

    public long size() {
        synchronized (LRU_CACHE) {
            return LRU_CACHE.size();
        }
    }

    @Nullable
    private <T> T getItem(@NonNull final CacheData data, @NonNull final Class<T> type) {
        return type.isAssignableFrom(data.type) ? type.cast(data.item) : null;
    }

    @NonNull
    private <T extends Serializable> T clone(@NonNull final T item,
                                             @NonNull final Class<T> type) throws Exception {
        final ByteArrayOutputStream bytesOutput = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(bytesOutput)) {
            objectOutput.writeObject(item);
            objectOutput.flush();
        }
        final Object clone = new ObjectInputStream(
                new ByteArrayInputStream(bytesOutput.toByteArray())).readObject();
        return type.cast(clone);
    }

    private static final class CacheData<T> {
        private final T item;
        private final Class<T> type;

        private CacheData(@NonNull final T item, @NonNull final Class<T> type) {
            this.item = item;
            this.type = type;
        }
    }
}
