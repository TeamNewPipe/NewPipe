package org.schabi.newpipe.util;

import java.io.File;

import android.content.Context;
import androidx.annotation.NonNull;

public final class CacheDirUtils {

    private CacheDirUtils() {
        // no instance
    }

    public static String getExternalAppCacheDirPath(
                                @NonNull final Context context) {
        final File externalCacheDir = context.getExternalCacheDir();
        if (null != externalCacheDir) {
            // /storage/emulated/0/Android/data/<package_name>/cache/
            return externalCacheDir.getAbsolutePath();
        }

        return null;
    }

    public static String getInternalAppCacheDirPath(
                                @NonNull final Context context) {
        // always available, never be 'null'
        // /data/user/0/<package_name>/cache/
        return context.getCacheDir().getAbsolutePath();
    }

    /**
     * Returns the preferred cache directory path for the application.
     *
     * Prefers the external cache directory when available
     * (user-accessible, larger space),
     * falls back to the internal private cache directory otherwise
     * (always available, more secure).
     *
     * Typical paths:
     * - External: /storage/emulated/0/Android/data/<package_name>/cache/
     * - Internal: /data/user/0/<package_name>/cache/
     *             (or /data/data/<package_name>/cache/ on some devices)
     *
     * Note: The 'external' and 'internal' cache directories mentioned above
     * are Android terms. They are typically located on the device's
     * built-in storage and are not related to removable SD/TF cards.
     *
     * User "Clear Cache" in app settings deletes files in both locations.
     *
     * @param context used to get the available cache dir
     * @return absolute path string, never null
     */
    @NonNull
    public static String getPreferredAppCacheDirPath(
                                @NonNull final Context context) {

        final String externalCacheDirPath = getExternalAppCacheDirPath(context);
        if (null != externalCacheDirPath) {
            return externalCacheDirPath;
        }

        // Internal cache dir should always be available
        return getInternalAppCacheDirPath(context);
    }
}
