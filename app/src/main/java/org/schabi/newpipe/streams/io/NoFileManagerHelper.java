package org.schabi.newpipe.streams.io;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;

/**
 * Helper for when no file-manager/activity was found.
 */
public final class NoFileManagerHelper {
    private NoFileManagerHelper() {
        // No impl
    }

    /**
     * Shows an alert dialog when no file-manager is found.
     * @param context Context
     */
    public static void showActivityNotFoundAlert(final Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.no_app_to_open_intent)
                .setMessage(
                        context.getString(
                                R.string.no_appropriate_file_manager_message,
                                context.getString(R.string.downloads_storage_use_saf_title)))
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
