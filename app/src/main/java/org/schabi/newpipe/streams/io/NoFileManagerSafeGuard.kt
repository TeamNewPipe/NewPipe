package org.schabi.newpipe.streams.io

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R

/**
 * Helper for when no file-manager/activity was found.
 */
object NoFileManagerSafeGuard {
    /**
     * Shows an alert dialog when no file-manager is found.
     * @param context Context
     */
    private fun showActivityNotFoundAlert(context: Context?) {
        if (context == null) {
            throw IllegalArgumentException(
                    "Unable to open no file manager alert dialog: Context is null")
        }
        val message: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ only allows SAF
            message = context.getString(R.string.no_appropriate_file_manager_message_android_10)
        } else {
            message = context.getString(
                    R.string.no_appropriate_file_manager_message,
                    context.getString(R.string.downloads_storage_use_saf_title))
        }
        AlertDialog.Builder(context)
                .setTitle(R.string.no_app_to_open_intent)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
    }

    /**
     * Launches the file manager safely.
     *
     * If no file manager is found (which is normally only the case when the user uninstalled
     * the default file manager or the OS lacks one) an alert dialog shows up, asking the user
     * to fix the situation.
     *
     * @param activityResultLauncher see [ActivityResultLauncher.launch]
     * @param input see [ActivityResultLauncher.launch]
     * @param tag Tag used for logging
     * @param context Context
     * @param <I> see [ActivityResultLauncher.launch]
    </I> */
    fun <I> launchSafe(
            activityResultLauncher: ActivityResultLauncher<I>,
            input: I,
            tag: String?,
            context: Context?
    ) {
        try {
            activityResultLauncher.launch(input)
        } catch (aex: ActivityNotFoundException) {
            Log.w(tag, "Unable to launch file/directory picker", aex)
            showActivityNotFoundAlert(context)
        }
    }
}
