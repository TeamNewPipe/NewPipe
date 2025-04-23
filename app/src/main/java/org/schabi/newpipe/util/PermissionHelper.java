package org.schabi.newpipe.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.NewPipeSettings;

import java.util.function.Consumer;

public final class PermissionHelper {
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


    /**
     * Check that we have the notification permission or ask the user for permission.
     *
     * @param activity main activity
     * @param userChoice a callback that gets called with the user choice
     */
    public static void checkPostNotificationsPermissionOnStartup(
            final ComponentActivity activity,
            final Consumer<Boolean> userChoice) {

        // On Android before TIRAMISU, notifications are always allowed to be sent
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            userChoice.accept(true);
            return;
        }

        // if we have the permission already, continue
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            userChoice.accept(true);
            return;
        }

        // if we already asked the user, don’t ask again
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final String wasAskedKey = activity.getString(
                R.string.user_was_asked_notification_permission_on_startup_key);
        if (prefs.getBoolean(wasAskedKey, false)) {
            userChoice.accept(false);
            return;
        }

        // else let’s ask the user for permission
        @SuppressLint("ApplySharedPref")
        final ActivityResultCallback<Boolean> cb = isGranted -> {
            // first make sure that we only ever ask the user once on startup
            final SharedPreferences prefs2 =
                    PreferenceManager.getDefaultSharedPreferences(activity);
            // commit setting before doing anything else
            prefs2.edit().putBoolean(wasAskedKey, true).commit();

            // forward the user choice
            userChoice.accept(isGranted);
        };

        final ActivityResultLauncher<String> notificationRequestReference =
                activity.registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        cb
                );

        notificationRequestReference.launch(Manifest.permission.POST_NOTIFICATIONS);
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
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean checkSystemAlertWindowPermission(final Context context) {
        if (!Settings.canDrawOverlays(context)) {
            final Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(i);
            } catch (final ActivityNotFoundException ignored) {
            }
            return false;
        } else {
            return true;
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSystemAlertWindowPermission(context)) {
            return true;
        } else {
            Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
