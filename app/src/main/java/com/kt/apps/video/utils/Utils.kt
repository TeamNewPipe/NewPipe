package com.kt.apps.video.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun isPipSettingAllowed(context: Context, packageName: String = context.packageName): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }
    } else {
        false
    }
}

fun requestPIPPermission(context: Context, packageName: String = context.packageName): Boolean {
    return runCatching {
        Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").let {
            it.data = Uri.fromParts("package", packageName, null)
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
        true
    }.getOrNull() ?: runCatching {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).let {
            it.data = Uri.fromParts("package", packageName, null)
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
        true
    }.getOrNull() ?: false
}
