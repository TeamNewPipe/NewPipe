package com.kt.apps.video.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import org.schabi.newpipe.BuildConfig
import java.io.File

object AppUpdateUtils {
    @SuppressLint("SetWorldReadable", "WorldReadableFiles")
    fun installApkFile(context: Context, apkFile: File): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.apply {
                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", apkFile)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = uri
                }
            } else {
                // Before N, a MODE_WORLD_READABLE file could be passed via the ACTION_INSTALL_PACKAGE
                // Intent. Since N, MODE_WORLD_READABLE files are forbidden, and a FileProvider is recommended.
                val tempFilename = "Tmp.apk"
                val buffer = ByteArray(8 * 1024)
                val fileMode = Context.MODE_WORLD_READABLE
                runCatching {
                    context.openFileOutput(tempFilename, fileMode).use { fileOutputStream ->
                        apkFile.inputStream().use { inputStream ->
                            var n: Int
                            while (inputStream.read(buffer).also { n = it } >= 0) {
                                fileOutputStream.write(buffer, 0, n)
                            }
                        }
                    }
                }.onFailure {
                    Log.i("AppUpdate", "Failed to write temporary APK file", it)
                }

                val uri = Uri.fromFile(context.getFileStreamPath(tempFilename))
                intent.apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }.onFailure {
            Log.e("AppUpdate", "Install apk fail: ", it)
        }.isSuccess
    }
}
