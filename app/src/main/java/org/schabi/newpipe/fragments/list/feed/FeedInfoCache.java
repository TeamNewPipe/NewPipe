package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-30.
 */

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.schabi.newpipe.fragments.list.feed.FeedInfo.INVALID_CONTENT_HASH;

/**
 * Handles caching the {@link FeedInfo} in the internal cache directory.
 */
final class FeedInfoCache {

    private static final String FEED_INFO_CACHE_FILENAME = "feed_info_cache.ser";
    private static final int MAX_FEED_CACHE_ITEMS = 500;

    private final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    private final File cacheFile;

    /**
     * Creates a cache for the {@link FeedInfo}.
     * @param context The current context used to get the internal cache directory
     */
    FeedInfoCache(Context context) {
        cacheFile = new File(context.getCacheDir(), FEED_INFO_CACHE_FILENAME);
    }

    /**
     * Stores the feed info in the cache.
     * @param feedInfo The geed info in question
     */
    void store(FeedInfo feedInfo) {
        if (feedInfo.getInfoItems().isEmpty()) {
            return;
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(cacheFile);
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream);

            outputStream.writeObject(getFeedWithLimitedSize(feedInfo));

            outputStream.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to cache the feed.", e);
        } finally {
            closeCacheFile(fileOutputStream);
        }
    }

    /**
     * @return The feed info from the cache or {@code null}.
     */
    @Nullable
    public FeedInfo read() {
        if (!cacheFile.exists()) {
            return null;
        }

        FileInputStream fileInputStream = null;
        ObjectInputStream inputStream = null;
        try {
            fileInputStream = new FileInputStream(cacheFile);
            inputStream = new ObjectInputStream(fileInputStream);

            return (FeedInfo) inputStream.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            Log.w(TAG, "Failed to read the feed from the cache file.", e);
            return null;
        } finally {
            closeCacheFile(inputStream);
            closeCacheFile(fileInputStream);
        }
    }

    private FeedInfo getFeedWithLimitedSize(FeedInfo feedInfo) {
        if (feedInfo.getInfoItems().size() > MAX_FEED_CACHE_ITEMS) {
            return new FeedInfo(feedInfo.getLastUpdated(),
                    new ArrayList<>(feedInfo.getInfoItems().subList(0, MAX_FEED_CACHE_ITEMS)),
                    INVALID_CONTENT_HASH);
        } else {
            return feedInfo;
        }
    }

    private void closeCacheFile(Closeable fileStream) {
        if (fileStream != null) {
            try {
                fileStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close the cache file of the feed.", e);
            }
        }
    }
}
