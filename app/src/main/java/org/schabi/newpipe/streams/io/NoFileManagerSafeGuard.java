package org.schabi.newpipe.streams.io;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;

/**
 * Helper for when no file-manager/activity was found.
 */
public final class NoFileManagerSafeGuard {
    private NoFileManagerSafeGuard() {
        // No impl
    }

    /**
     * Shows an alert dialog when no file-manager is found.
     * @param context Context
     */
    private static void showActivityNotFoundAlert(final Context context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Unable to open no file manager alert dialog: Context is null");
        }

        final String message;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ only allows SAF
            message = context.getString(R.string.no_appropriate_file_manager_message_android_10);
        } else {
            message = context.getString(
                    R.string.no_appropriate_file_manager_message,
                    context.getString(R.string.downloads_storage_use_saf_title));
        }


        new AlertDialog.Builder(context)
                .setTitle(R.string.no_app_to_open_intent)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * Launches the file manager safely.
     *
     * If no file manager is found (which is normally only the case when the user uninstalled
     * the default file manager or the OS lacks one) an alert dialog shows up, asking the user
     * to fix the situation.
     *
     * @param activityResultLauncher see {@link ActivityResultLauncher#launch(Object)}
     * @param input see {@link ActivityResultLauncher#launch(Object)}
     * @param tag Tag used for logging
     * @param context Context
     * @param <I> see {@link ActivityResultLauncher#launch(Object)}
     */
    public static <I> void launchSafe(
            final ActivityResultLauncher<I> activityResultLauncher,
            final I input,
            final String tag,
            final Context context
    ) {
        try {
            activityResultLauncher.launch(input);
        } catch (final ActivityNotFoundException aex) {
            Log.w(tag, "Unable to launch file/directory picker", aex);
            NoFileManagerSafeGuard.showActivityNotFoundAlert(context);
        }
    }
}
