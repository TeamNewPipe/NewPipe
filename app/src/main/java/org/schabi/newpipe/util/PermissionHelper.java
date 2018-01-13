package org.schabi.newpipe.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import org.schabi.newpipe.R;

public class PermissionHelper {
    public static final int PERMISSION_WRITE_STORAGE = 778;
    public static final int PERMISSION_READ_STORAGE = 777;
    public static final int PERMISSION_SYSTEM_ALERT_WINDOW = 779;


    public static boolean checkStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if(!checkReadStoragePermissions(activity)) return false;
        }
        return checkWriteStoragePermissions(activity);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public static boolean checkReadStoragePermissions(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_READ_STORAGE);

            return false;
        }
        return true;
    }


    public static boolean checkWriteStoragePermissions(Activity activity) {
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
                    PERMISSION_WRITE_STORAGE);

            // PERMISSION_WRITE_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            /*}*/
            return false;
        }
        return true;
    }


    /**
     * In order to be able to draw over other apps, the permission android.permission.SYSTEM_ALERT_WINDOW have to be granted.
     * <p>
     * On < API 23 (MarshMallow) the permission was granted when the user installed the application (via AndroidManifest),
     * on > 23, however, it have to start a activity asking the user if he agree.
     * <p>
     * This method just return if canDraw over other apps, if it doesn't, try to get the permission,
     * it does not get the result of the startActivityForResult, if the user accept, the next time that he tries to open
     * it will return true.
     *
     * @param activity context to startActivityForResult
     * @return returns {@link Settings#canDrawOverlays(Context)}
     **/
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean checkSystemAlertWindowPermission(Activity activity) {
        if (!Settings.canDrawOverlays(activity)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(i, PERMISSION_SYSTEM_ALERT_WINDOW);
            return false;
        }else return true;
    }

    public static boolean isPopupEnabled(Activity activity) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                PermissionHelper.checkSystemAlertWindowPermission(activity);
    }

    public static void showPopupEnablementToast(Context context) {
        Toast toast = Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG);
        TextView messageView = toast.getView().findViewById(android.R.id.message);
        if (messageView != null) messageView.setGravity(Gravity.CENTER);
        toast.show();
    }
}
