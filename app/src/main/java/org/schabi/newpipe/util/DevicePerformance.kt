package org.schabi.newpipe.util

import android.app.ActivityManager
import android.content.Context

/** Centralizes automatic resource limits for memory-constrained devices. */
object DevicePerformance {
    private const val STANDARD_LOCAL_THUMBNAIL_CACHE_BYTES = 16 * 1024 * 1024
    private const val LOW_RAM_LOCAL_THUMBNAIL_CACHE_BYTES = 4 * 1024 * 1024

    @JvmStatic
    fun isLowRamDevice(context: Context): Boolean = context
        .getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    fun localThumbnailCacheBytes(context: Context): Int = localThumbnailCacheBytes(
        isLowRamDevice(context)
    )

    fun localThumbnailLoaderThreads(context: Context): Int = if (isLowRamDevice(context)) 1 else 2

    internal fun localThumbnailCacheBytes(lowRam: Boolean): Int = if (lowRam) {
        LOW_RAM_LOCAL_THUMBNAIL_CACHE_BYTES
    } else {
        STANDARD_LOCAL_THUMBNAIL_CACHE_BYTES
    }
}
