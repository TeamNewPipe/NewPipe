package org.schabi.newpipe.util

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings

object PermissionHelper {
    val POST_NOTIFICATIONS_REQUEST_CODE: Int = 779
    val DOWNLOAD_DIALOG_REQUEST_CODE: Int = 778
    val DOWNLOADS_REQUEST_CODE: Int = 777
    fun checkStoragePermissions(activity: Activity?, requestCode: Int): Boolean {
        if (NewPipeSettings.useStorageAccessFramework(activity)) {
            return true // Storage permissions are not needed for SAF
        }
        if (!checkReadStoragePermissions(activity, requestCode)) {
            return false
        }
        return checkWriteStoragePermissions(activity, requestCode)
    }

    fun checkReadStoragePermissions(activity: Activity?,
                                    requestCode: Int): Boolean {
        if ((ContextCompat.checkSelfPermission((activity)!!, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions((activity), arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestCode)
            return false
        }
        return true
    }

    fun checkWriteStoragePermissions(activity: Activity?,
                                     requestCode: Int): Boolean {
        // Here, thisActivity is the current activity
        if ((ContextCompat.checkSelfPermission((activity)!!,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)) {

            // Should we show an explanation?
            /*if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {*/

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions((activity), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode)

            // PERMISSION_WRITE_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
            /*}*/return false
        }
        return true
    }

    fun checkPostNotificationsPermission(activity: Activity?,
                                         requestCode: Int): Boolean {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission((activity)!!,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions((activity), arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
            return false
        }
        return true
    }

    /**
     * In order to be able to draw over other apps,
     * the permission android.permission.SYSTEM_ALERT_WINDOW have to be granted.
     *
     *
     * On < API 23 (MarshMallow) the permission was granted
     * when the user installed the application (via AndroidManifest),
     * on > 23, however, it have to start a activity asking the user if he agrees.
     *
     *
     *
     * This method just return if the app has permission to draw over other apps,
     * and if it doesn't, it will try to get the permission.
     *
     *
     * @param context [Context]
     * @return [Settings.canDrawOverlays]
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun checkSystemAlertWindowPermission(context: Context?): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            val i: Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context!!.getPackageName()))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(i)
            } catch (ignored: ActivityNotFoundException) {
            }
            return false
        } else {
            return true
        }
    }

    /**
     * Determines whether the popup is enabled, and if it is not, starts the system activity to
     * request the permission with [.checkSystemAlertWindowPermission] and shows a
     * toast to the user explaining why the permission is needed.
     *
     * @param context the Android context
     * @return whether the popup is enabled
     */
    fun isPopupEnabledElseAsk(context: Context?): Boolean {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || checkSystemAlertWindowPermission(context))) {
            return true
        } else {
            Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG).show()
            return false
        }
    }
}
