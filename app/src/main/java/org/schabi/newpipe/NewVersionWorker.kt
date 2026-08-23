package org.schabi.newpipe

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.ReleaseVersionUtil.coerceUpdateCheckExpiry
import org.schabi.newpipe.util.ReleaseVersionUtil.isLastUpdateCheckExpired
import java.io.IOException

class NewVersionWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private fun compareVersionName(buildVersionCodes: List<String>, versionCodes: List<String>): Boolean {
        return versionCodes[0].toInt() > buildVersionCodes[0].toInt() ||
            versionCodes[0].toInt() == buildVersionCodes[0].toInt() && versionCodes[1].toInt() > buildVersionCodes[1].toInt() ||
            versionCodes[0].toInt() == buildVersionCodes[0].toInt() && versionCodes[1].toInt() == buildVersionCodes[1].toInt() && versionCodes[2].toInt() > buildVersionCodes[2].toInt()
    }
    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     * @param versionCode    Code of new version
     */
    private fun compareAppVersionAndShowNotification(
        versionName: String,
        apkLocationUrl: String?,
        isManual: Boolean
    ) {
        val currentVersion = parseVersion(BuildConfig.VERSION_NAME)
        val newVersion = parseVersion(versionName)
        if (compareVersions(currentVersion, newVersion) >= 0) {
            if (isManual) {
                ContextCompat.getMainExecutor(applicationContext).execute {
                    Toast.makeText(
                        applicationContext, R.string.app_update_unavailable_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        // A pending intent to open the apk location url in the browser.
        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl?.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channelId = applicationContext.getString(R.string.app_update_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setContentTitle(applicationContext.getString(R.string.app_update_notification_content_title_new))
            .setContentText(
                applicationContext.getString(R.string.app_update_notification_content_text) +
                    " " + versionName
            )
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.notify(2000, notificationBuilder.build())
    }

    @Throws(IOException::class, ReCaptchaException::class)
    private fun checkNewVersion() {
        // Check if the current apk is a github one or not.
//        if (!isReleaseApk()) {
//            return
//        }

        if (!inputData.getBoolean(IS_MANUAL, false)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            // Check if the last request has happened a certain time ago
            // to reduce the number of API requests.
            val expiry = prefs.getLong(applicationContext.getString(R.string.update_expiry_key), 0)
            if (!isLastUpdateCheckExpired(expiry)) {
                return
            }
        }

        // Make a network request to get latest NewPipe data.
        val response = DownloaderImpl.getInstance().get(NEWPIPE_API_URL)
        handleResponse(response)
    }

    private fun handleResponse(response: Response) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        try {
            // Store a timestamp which needs to be exceeded,
            // before a new request to the API is made.
            val newExpiry = coerceUpdateCheckExpiry(response.getHeader("expires"))
            prefs.edit {
                putLong(applicationContext.getString(R.string.update_expiry_key), newExpiry)
            }
        } catch (e: Exception) {
            if (DEBUG) {
                Log.w(TAG, "Could not extract and save new expiry date", e)
            }
        }

        // Parse the json from the response.
        try {
            val githubReleases = JsonParser.`array`().from(response.responseBody())
            val includePreRelease = prefs.getBoolean(applicationContext.getString(R.string.show_prerelease_key), false)
            var selectedRelease: JsonObject? = null

            // 选择最新符合要求的版本（稳定版或包括预发布）
            for (i in 0 until githubReleases.size) {
                val release = githubReleases.getObject(i)
                if (!includePreRelease && release.getBoolean("prerelease")) continue
                if (selectedRelease == null || isNewerRelease(release, selectedRelease)) {
                    selectedRelease = release
                }
            }

            selectedRelease?.let { release ->
                val versionName = release.getString("name").removePrefix("v")
                val apkUrl = findCompatibleApkUrl(release, Build.SUPPORTED_ABIS)
                compareAppVersionAndShowNotification(versionName, apkUrl, inputData.getBoolean(IS_MANUAL, false))
            }
        } catch (e: JsonParserException) {
            if (DEBUG) Log.w(TAG, "JSON解析错误", e)
        }
    }

    private fun isNewerRelease(newRelease: JsonObject, currentRelease: JsonObject): Boolean {
        val newVersion = parseVersion(newRelease.getString("name").removePrefix("v"))
        val currentVersion = parseVersion(currentRelease.getString("name").removePrefix("v"))
        return compareVersions(newVersion, currentVersion) > 0
    }

    private fun findCompatibleApkUrl(release: JsonObject, abis: Array<String>): String? {
        val assets = release.getArray("assets")
        val apkAssets = (0 until assets.size)
            .map { assets.getObject(it) }
            .filter { it.getString("name").endsWith(".apk") }

        abis.forEach { abi ->
            val compatibleAsset = apkAssets.firstOrNull { asset ->
                getReleaseApkAbi(asset.getString("name")) == abi
            }
            if (compatibleAsset != null) {
                return compatibleAsset.getString("browser_download_url")
            }
        }

        return apkAssets.firstOrNull { it.getString("name").contains("universal") }
            ?.getString("browser_download_url")
    }



    override fun doWork(): Result {
        return try {
            checkNewVersion()
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Could not fetch NewPipe API: probably network problem", e)
            Result.failure()
        } catch (e: ReCaptchaException) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e)
            Result.failure()
        }
    }

    companion object {
        private val DEBUG = MainActivity.DEBUG
        private val TAG = NewVersionWorker::class.java.simpleName
        private val RELEASE_APK_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
        private const val NEWPIPE_API_URL = "https://api.github.com/repositories/490984887/releases"
        private const val IS_MANUAL = "isManual"

        private fun getReleaseApkAbi(name: String): String? {
            return RELEASE_APK_ABIS.firstOrNull { name.contains("-$it-") }
        }
        /**
         * Start a new worker which checks if all conditions for performing a version check are met,
         * fetches the API endpoint [.NEWPIPE_API_URL] containing info about the latest NewPipe
         * version and displays a notification about an available update if one is available.
         * <br></br>
         * Following conditions need to be met, before data is requested from the server:
         *
         *  *  The app is signed with the correct signing key (by TeamNewPipe / schabi).
         * If the signing key differs from the one used upstream, the update cannot be installed.
         *  * The user enabled searching for and notifying about updates in the settings.
         *  * The app did not recently check for updates.
         * We do not want to make unnecessary connections and DOS our servers.
         */
        @JvmStatic
        fun enqueueNewVersionCheckingWork(context: Context, isManual: Boolean) {
            val workRequest = OneTimeWorkRequestBuilder<NewVersionWorker>()
                .setInputData(workDataOf(IS_MANUAL to isManual))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val betaVersion: Int? // null表示正式版，数字表示beta版本号
)

private fun parseVersion(versionStr: String): Version {
    val normalized = versionStr.removePrefix("v")
    val parts = normalized.split("-beta", limit = 2)
    val mainPart = parts[0]

    val mainParts = mainPart.split(".").map {
        it.toIntOrNull() ?: throw IllegalArgumentException("Invalid version part: $it")
    }

    val (major, minor, patch) = mainParts

    // beta版本号处理
    val betaVersion = when {
        parts.size == 1 -> null  // 没有beta部分，是正式版
        parts[1].isEmpty() -> 0  // 是 "-beta" 结尾
        else -> parts[1].toIntOrNull() // 是 "-beta1" 这样的格式
    }

    return Version(major, minor, patch, betaVersion)
}

private fun compareVersions(v1: Version, v2: Version): Int {
    // 先比较主版本号
    val mainCompare = when {
        v1.major != v2.major -> v1.major.compareTo(v2.major)
        v1.minor != v2.minor -> v1.minor.compareTo(v2.minor)
        v1.patch != v2.patch -> v1.patch.compareTo(v2.patch)
        else -> 0
    }

    if (mainCompare != 0) return mainCompare

    // 主版本号相同，比较beta版本
    return when {
        v1.betaVersion == null && v2.betaVersion == null -> 0  // 都是正式版
        v1.betaVersion == null -> 1  // v1是正式版，比beta版大
        v2.betaVersion == null -> -1 // v2是正式版，比beta版大
        else -> v1.betaVersion.compareTo(v2.betaVersion) // 比较beta版本号
    }
}
