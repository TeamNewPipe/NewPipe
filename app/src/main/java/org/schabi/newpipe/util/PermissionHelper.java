package org.schabi.newpipe.util;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

public final class PermissionHelper {
    public static final int DOWNLOAD_DIALOG_REQUEST_CODE = 778;
    public static final int DOWNLOADS_REQUEST_CODE = 777;

    private PermissionHelper() { }

    public static boolean checkStoragePermissions(final Activity activity, final int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (!checkReadStoragePermissions(activity, requestCode)) {
                return false;
            }
        }
        return checkWriteStoragePermissions(activity, requestCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
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
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);

            // PERMISSION_WRITE_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            /*}*/
            return false;
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
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean checkSystemAlertWindowPermission(final Context context) {
        if (!Settings.canDrawOverlays(context)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return false;
        } else {
            return true;
        }
    }

    public static boolean isPopupEnabled(final Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || PermissionHelper.checkSystemAlertWindowPermission(context);
    }

    public static void showPopupEnablementToast(final Context context) {
        Toast toast = Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG);
        TextView messageView = toast.getView().findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setGravity(Gravity.CENTER);
        }
        toast.show();
    }
}
