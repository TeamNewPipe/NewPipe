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
import android.text.Html
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings

object PermissionHelper {
    const val POST_NOTIFICATIONS_REQUEST_CODE = 779
    const val DOWNLOAD_DIALOG_REQUEST_CODE = 778
    const val DOWNLOADS_REQUEST_CODE = 777

    @JvmStatic
    fun checkStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (NewPipeSettings.useStorageAccessFramework(activity)) {
            return true // Storage permissions are not needed for SAF
        }

        if (!checkReadStoragePermissions(activity, requestCode)) {
            return false
        }
        return checkWriteStoragePermissions(activity, requestCode)
    }

    @JvmStatic
    fun checkReadStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                requestCode
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun checkWriteStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun checkPostNotificationsPermission(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!App.instance.notificationsRequested) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode
                )
                App.instance.setNotificationsRequested()
                return false
            }
        }
        return true
    }

    /**
     * In order to be able to draw over other apps,
     * the permission android.permission.SYSTEM_ALERT_WINDOW have to be granted.
     *
     * On < API 23 (MarshMallow) the permission was granted
     * when the user installed the application (via AndroidManifest),
     * on > 23, however, it have to start a activity asking the user if he agrees.
     *
     * This method just return if the app has permission to draw over other apps,
     * and if it doesn't, it will try to get the permission.
     *
     * @param context [Context]
     * @return [Settings.canDrawOverlays]
     **/
    @JvmStatic
    fun checkSystemAlertWindowPermission(context: Context): Boolean {
        return if (!Settings.canDrawOverlays(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val i = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(i)
                } catch (ignored: ActivityNotFoundException) {
                }
                false
            } else {
                val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
                val title = context.getString(R.string.permission_display_over_apps)
                val permissionName = context.getString(R.string.permission_display_over_apps_permission_name)
                val appNameItalic = "<i>$appName</i>"
                val permissionNameItalic = "<i>$permissionName</i>"
                val message = context.getString(
                    R.string.permission_display_over_apps_message,
                    appNameItalic,
                    permissionNameItalic
                )
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT))
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (ignored: ActivityNotFoundException) {
                        }
                    }
                    .setCancelable(true)
                    .show()
                false
            }
        } else {
            true
        }
    }

    /**
     * Determines whether the popup is enabled, and if it is not, starts the system activity to
     * request the permission with [checkSystemAlertWindowPermission] and shows a
     * toast to the user explaining why the permission is needed.
     *
     * @param context the Android context
     * @return whether the popup is enabled
     */
    @JvmStatic
    fun isPopupEnabledElseAsk(context: Context): Boolean {
        return if (checkSystemAlertWindowPermission(context)) {
            true
        } else {
            Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG).show()
            false
        }
    }
}
