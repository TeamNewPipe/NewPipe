package org.schabi.newpipe.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * In-memory LRU cache for fully-loaded channel video lists, keyed by channel tab URL.
 * Caches up to {@code MAX_TOTAL_ITEMS} stream items across all channels, evicting the
 * least-recently-accessed channel when the limit is exceeded. Entries expire after 1 hour.
 */
public final class ChannelItemCache {

    private static final int MAX_TOTAL_ITEMS = 10_000;
    private static final long EXPIRY_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static final ChannelItemCache INSTANCE = new ChannelItemCache();

    private final LruCache<String, CacheEntry> cache =
            new LruCache<String, CacheEntry>(MAX_TOTAL_ITEMS) {
                @Override
                protected int sizeOf(final String key, final CacheEntry value) {
                    return value.items.size();
                }
            };

    private ChannelItemCache() {
    }

    /**
     * Returns the singleton instance.
     *
     * @return the shared {@link ChannelItemCache} instance
     */
    public static ChannelItemCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the cached item list for the given channel tab URL, or null if absent/expired.
     *
     * @param channelTabUrl the URL of the channel tab
     * @return a copy of the cached list, or null if absent or expired
     */
    @Nullable
    public List<StreamInfoItem> getItems(@NonNull final String channelTabUrl) {
        final CacheEntry entry = cache.get(channelTabUrl);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(channelTabUrl);
            return null;
        }
        return new ArrayList<>(entry.items);
    }

    /**
     * Stores or replaces the item list for the given channel tab URL.
     *
     * @param channelTabUrl the URL of the channel tab
     * @param items         the full list of stream items to cache
     */
    public void putItems(@NonNull final String channelTabUrl,
                         @NonNull final List<StreamInfoItem> items) {
        cache.put(channelTabUrl, new CacheEntry(new ArrayList<>(items)));
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        cache.evictAll();
    }

    private static final class CacheEntry {
        final List<StreamInfoItem> items;
        final long expiresAt;

        CacheEntry(@NonNull final List<StreamInfoItem> items) {
            this.items = items;
            this.expiresAt = System.currentTimeMillis() + EXPIRY_MILLIS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
