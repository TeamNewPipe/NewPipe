package org.schabi.newpipe.util;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import org.schabi.newpipe.R;

public class PermissionChecker {

    // Call this method to check for notification permission and handle the result
    public static void checkNotificationPermission(Context context) {
        if (!isNotificationEnabled(context)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.enable_notification_title))
                    .setMessage(context.getString(R.string.enable_notification_text))
                    .setPositiveButton(context.getString(R.string.ok), (dialog, which) -> openNotificationSettings(context))
                    .setCancelable(true)
                    .show();
        }
    }

    // Check if the notification is enabled for this package
    private static boolean isNotificationEnabled(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return notificationManager.areNotificationsEnabled();
        } else {
            // For older versions, you might want to check specific settings using Settings.Secure
            return true; // Assuming it is enabled by default as you can't check on older versions
        }
    }

    // Intent to open the notification settings for this app
    private static void openNotificationSettings(Context context) {
        Intent intent = new Intent();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", context.getPackageName());
            intent.putExtra("app_uid", context.getApplicationInfo().uid);
        }
        context.startActivity(intent);
    }
}
