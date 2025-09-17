package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.util.Log
import org.schabi.newpipe.BuildConfig
import java.io.File

object DownloadAvailabilityChecker {
    private const val TAG = "DownloadAvailabilityChecker"

    fun isReadable(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme
        return when {
            scheme.equals("file", ignoreCase = true) ->
                File(uri.path ?: return false).canRead()
            scheme.equals("content", ignoreCase = true) ->
                probeContentUri(context, uri)
            else -> probeContentUri(context, uri)
        }
    }

    private fun probeContentUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (throwable: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to probe availability for $uri", throwable)
            }
            false
        }
    }
}
