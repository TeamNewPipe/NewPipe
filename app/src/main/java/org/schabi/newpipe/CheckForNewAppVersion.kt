@file:JvmName("CheckForNewAppVersion")

package org.schabi.newpipe

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction

private val DEBUG = MainActivity.DEBUG
private const val TAG = "CheckForNewAppVersion"
private const val GITHUB_APK_SHA1 = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15"
private const val NEWPIPE_API_URL = "https://newpipe.schabi.org/api/data.json"

private val APP: Application = App.app

private val isConnected: Boolean
    get() = APP.getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnected == true

/**
 * Method to get the apk's SHA1 key. See https://stackoverflow.com/questions/9293019/#22506133.
 *
 * @return String with the apk's SHA1 fingeprint in hexadecimal
 */
private val certificateSHA1Fingerprint: String?
    get() {
        val pm = APP.packageManager
        val packageName = APP.packageName
        val flags = PackageManager.GET_SIGNATURES
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            ErrorActivity.reportError(APP, e, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not find package info", R.string.app_ui_crash))
        }
        val signatures = packageInfo!!.signatures
        val cert = signatures[0].toByteArray()
        val input = cert.inputStream()
        var c: X509Certificate? = null
        try {
            val cf = CertificateFactory.getInstance("X509")
            c = cf.generateCertificate(input) as X509Certificate
        } catch (e: CertificateException) {
            ErrorActivity.reportError(APP, e, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Certificate error", R.string.app_ui_crash))
        }
        var hexString: String? = null
        try {
            val md = MessageDigest.getInstance("SHA1")
            val publicKey = md.digest(c!!.encoded)
            hexString = byte2HexFormatted(publicKey)
        } catch (e: NoSuchAlgorithmException) {
            ErrorActivity.reportError(APP, e, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash))
        } catch (e: CertificateEncodingException) {
            ErrorActivity.reportError(APP, e, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash))
        }
        return hexString
    }

val isGithubApk: Boolean
    get() = certificateSHA1Fingerprint == GITHUB_APK_SHA1

private fun byte2HexFormatted(arr: ByteArray): String {
    val str = StringBuilder(arr.size * 2)
    for (i in arr.indices) {
        var h = Integer.toHexString(arr[i].toInt())
        val l = h.length
        if (l == 1) {
            h = "0$h"
        }
        if (l > 2) {
            h = h.substring(l - 2, l)
        }
        str.append(h.toUpperCase(Locale.ROOT))
        if (i < arr.size - 1) {
            str.append(':')
        }
    }
    return str.toString()
}

/**
 * Method to compare the current and latest available app version.
 * If a newer version is available, we show the update notification.
 *
 * @param versionName Name of new version
 * @param apkLocationUrl Url with the new apk
 * @param versionCode Code of new version
 */
private fun compareAppVersionAndShowNotification(versionName: String, apkLocationUrl: String, versionCode: Int) {
    val notificationId = 2000
    if (BuildConfig.VERSION_CODE < versionCode) {
        // A pending intent to open the apk location url in the browser.
        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl.toUri())
        val pendingIntent = PendingIntent.getActivity(APP, 0, intent, 0)
        val notificationBuilder = NotificationCompat.Builder(APP, APP.getString(R.string.app_update_notification_channel_id))
                .setSmallIcon(R.drawable.ic_newpipe_update)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setContentTitle(APP.getString(R.string.app_update_notification_content_title))
                .setContentText(APP.getString(R.string.app_update_notification_content_text) + " " + versionName)
        val notificationManager = NotificationManagerCompat.from(APP)
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}

suspend fun checkNewVersion() = coroutineScope {
    val prefs = PreferenceManager.getDefaultSharedPreferences(APP)

    // Check if user has enabled/disabled update checking
    // and if the current apk is a github one or not.
    if (!prefs.getBoolean(APP.getString(R.string.update_app_key), true)) {
        cancel(CancellationException("Update checking is disabled"))
    }
    if (!isGithubApk) {
        cancel(CancellationException("APK is not from GitHub"))
    }

    val response = withContext(Dispatchers.IO) {
        if (!isActive || !isConnected) {
            return@withContext null
        }

        // Make a network request to get latest NewPipe data.
        try {
            return@withContext DownloaderImpl.getInstance()[NEWPIPE_API_URL].responseBody()
        } catch (e: IOException) {
            // connectivity problems, do not alarm user and fail silently
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(e))
            }
        } catch (e: ReCaptchaException) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(e))
            }
        }

        return@withContext null
    }

    // Parse the json from the response.
    if (response != null) {
        try {
            val githubStableObject = JsonParser.`object`().from(response)
                    .getObject("flavors").getObject("github").getObject("stable")
            val versionName = githubStableObject.getString("version")
            val versionCode = githubStableObject.getInt("version_code")
            val apkLocationUrl = githubStableObject.getString("apk")
            compareAppVersionAndShowNotification(versionName, apkLocationUrl, versionCode)
        } catch (e: JsonParserException) {
            // connectivity problems, do not alarm user and fail silently
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(e))
            }
        }
    }
}
