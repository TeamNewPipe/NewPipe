package org.schabi.newpipe.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.schabi.newpipe.MainActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

public class SerializedCache {
    private static final boolean DEBUG = MainActivity.DEBUG;
    private final String TAG = getClass().getSimpleName();

    private static final SerializedCache instance = new SerializedCache();
    private static final int MAX_ITEMS_ON_CACHE = 5;

    private static final LruCache<String, CacheData> lruCache =
            new LruCache<>(MAX_ITEMS_ON_CACHE);

    private SerializedCache() {
        //no instance
    }

    public static SerializedCache getInstance() {
        return instance;
    }

    @Nullable
    public <T> T take(@NonNull final String key, @NonNull final Class<T> type) {
        if (DEBUG) Log.d(TAG, "take() called with: key = [" + key + "]");
        synchronized (lruCache) {
            return lruCache.get(key) != null ? getItem(lruCache.remove(key), type) : null;
        }
    }

    @Nullable
    public <T> T get(@NonNull final String key, @NonNull final Class<T> type) {
        if (DEBUG) Log.d(TAG, "get() called with: key = [" + key + "]");
        synchronized (lruCache) {
            final CacheData data = lruCache.get(key);
            return data != null ? getItem(data, type) : null;
        }
    }

    @Nullable
    public <T extends Serializable> String put(@NonNull T item, @NonNull final Class<T> type) {
        final String key = UUID.randomUUID().toString();
        return put(key, item, type) ? key : null;
    }

    public <T extends Serializable> boolean put(@NonNull final String key, @NonNull T item,
                                                @NonNull final Class<T> type) {
        if (DEBUG) Log.d(TAG, "put() called with: key = [" + key + "], item = [" + item + "]");
        synchronized (lruCache) {
            try {
                lruCache.put(key, new CacheData<>(clone(item, type), type));
                return true;
            } catch (final Exception error) {
                Log.e(TAG, "Serialization failed for: ", error);
            }
        }
        return false;
    }

    public void clear() {
        if (DEBUG) Log.d(TAG, "clear() called");
        synchronized (lruCache) {
            lruCache.evictAll();
        }
    }

    public long size() {
        synchronized (lruCache) {
            return lruCache.size();
        }
    }

    @Nullable
    private <T> T getItem(@NonNull final CacheData data, @NonNull final Class<T> type) {
        return type.isAssignableFrom(data.type) ? type.cast(data.item) : null;
    }

    @NonNull
    private <T extends Serializable> T clone(@NonNull T item,
                                             @NonNull final Class<T> type) throws Exception {
        final ByteArrayOutputStream bytesOutput = new ByteArrayOutputStream();
        try (final ObjectOutputStream objectOutput = new ObjectOutputStream(bytesOutput)) {
            objectOutput.writeObject(item);
            objectOutput.flush();
        }
        final Object clone = new ObjectInputStream(
                new ByteArrayInputStream(bytesOutput.toByteArray())).readObject();
        return type.cast(clone);
    }

    final private static class CacheData<T> {
        private final T item;
        private final Class<T> type;

        private CacheData(@NonNull final T item, @NonNull Class<T> type) {
            this.item = item;
            this.type = type;
        }
    }
}
