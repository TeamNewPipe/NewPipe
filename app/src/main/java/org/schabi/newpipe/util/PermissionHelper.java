package org.schabi.newpipe.util;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.NewPipeSettings;

public final class PermissionHelper {
    private static final String TAG = PermissionHelper.class.getSimpleName();
    public static final int POST_NOTIFICATIONS_REQUEST_CODE = 779;
    public static final int DOWNLOAD_DIALOG_REQUEST_CODE = 778;
    public static final int DOWNLOADS_REQUEST_CODE = 777;

    private PermissionHelper() { }

    public static boolean checkStoragePermissions(final Activity activity, final int requestCode) {
        if (NewPipeSettings.useStorageAccessFramework(activity)) {
            return true; // Storage permissions are not needed for SAF
        }

        if (!checkReadStoragePermissions(activity, requestCode)) {
            return false;
        }
        return checkWriteStoragePermissions(activity, requestCode);
    }

    public static boolean checkReadStoragePermissions(final Activity activity,
                                                      final int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);

            return false;
        }
        return true;
    }


    public static boolean checkWriteStoragePermissions(final Activity activity,
                                                       final int requestCode) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            /*if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {*/

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);

            // PERMISSION_WRITE_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            /*}*/
            return false;
        }
        return true;
    }

    public static boolean checkPostNotificationsPermission(final Activity activity,
                                                           final int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            if (!App.getInstance().getNotificationsRequested()) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
                App.getInstance().setNotificationsRequested();
                return false;
            }
        }
        return true;
    }

    /**
     * In order to be able to draw over other apps,
     * the permission android.permission.SYSTEM_ALERT_WINDOW have to be granted.
     * <p>
     * On < API 23 (MarshMallow) the permission was granted
     * when the user installed the application (via AndroidManifest),
     * on > 23, however, it have to start a activity asking the user if he agrees.
     * </p>
     * <p>
     * This method just return if the app has permission to draw over other apps,
     * and if it doesn't, it will try to get the permission.
     * </p>
     *
     * @param context {@link Context}
     * @return {@link Settings#canDrawOverlays(Context)}
     **/
    public static boolean checkSystemAlertWindowPermission(final Context context) {
        if (!Settings.canDrawOverlays(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                final Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(i);
                } catch (final ActivityNotFoundException ignored) {
                }
                return false;
            // from Android R the ACTION_MANAGE_OVERLAY_PERMISSION will only point to the menu,
            // so let’s add a dialog that points the user to the right setting.
            } else {
                // TODO: Remove the activity workaround once the AlertDialogs are migrated
                //       to compose.
                final Activity activity = findActivity(context);
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    Log.w(TAG, "Cannot get activity. Skipping explanatory dialog.");
                    openSystemAlertPermissionWindow(context);
                    return false;
                }
                final String appName = context.getApplicationInfo()
                        .loadLabel(context.getPackageManager()).toString();
                final String title = context.getString(R.string.permission_display_over_apps);
                final String permissionName =
                        context.getString(R.string.permission_display_over_apps_permission_name);
                final String appNameItalic = "<i>" + appName + "</i>";
                final String permissionNameItalic = "<i>" + permissionName + "</i>";
                final String message =
                        context.getString(R.string.permission_display_over_apps_message,
                                appNameItalic,
                                permissionNameItalic
                        );
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT))
                        .setPositiveButton("OK", (dialog, which) -> {
                            openSystemAlertPermissionWindow(activity);
                        })
                        .setCancelable(true)
                        .show();
                return false;
            }

        } else {
            return true;
        }
    }

    /**
     * Opens the system alert permission window to let the user grant the permission on Android R
     * and newer.
     * @param context the context to use
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static void openSystemAlertPermissionWindow(final Context context) {
        // we don’t need the package name here, since it won’t do anything on >R
        final Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (final ActivityNotFoundException ignored) {
            Log.d(TAG, "Could not open system alert permission window", ignored);
        }
    }

    @Nullable
    private static Activity findActivity(final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper wrappedContext) {
            if (wrappedContext instanceof Activity activity) {
                return activity;
            }
            final Context base = wrappedContext.getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return null;
    }

    /**
     * Determines whether the popup is enabled, and if it is not, starts the system activity to
     * request the permission with {@link #checkSystemAlertWindowPermission(Context)} and shows a
     * toast to the user explaining why the permission is needed.
     *
     * @param context the Android context
     * @return whether the popup is enabled
     */
    public static boolean isPopupEnabledElseAsk(final Context context) {
        if (checkSystemAlertWindowPermission(context)) {
            return true;
        } else {
            Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
