package org.schabi.newpipe

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.ReleaseVersionUtil.coerceUpdateCheckExpiry
import org.schabi.newpipe.util.ReleaseVersionUtil.isLastUpdateCheckExpired
import org.schabi.newpipe.util.ReleaseVersionUtil.isReleaseApk
import java.io.IOException

class NewVersionWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

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
        versionCode: Int
    ) {
        if (BuildConfig.VERSION_CODE >= versionCode) {
            return
        }
        val app = App.getApp()

        // A pending intent to open the apk location url in the browser.
        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl?.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(app, 0, intent, 0)
        val channelId = app.getString(R.string.app_update_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setContentTitle(app.getString(R.string.app_update_notification_content_title))
            .setContentText(
                app.getString(R.string.app_update_notification_content_text) +
                    " " + versionName
            )
        val notificationManager = NotificationManagerCompat.from(app)
        notificationManager.notify(2000, notificationBuilder.build())
    }

    @Throws(IOException::class, ReCaptchaException::class)
    private fun checkNewVersion() {
        // Check if the current apk is a github one or not.
        if (!isReleaseApk()) {
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        // Check if the last request has happened a certain time ago
        // to reduce the number of API requests.
        val expiry = prefs.getLong(applicationContext.getString(R.string.update_expiry_key), 0)
        if (!isLastUpdateCheckExpired(expiry)) {
            return
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
            val githubStableObject = JsonParser.`object`()
                .from(response.responseBody()).getObject("flavors")
                .getObject("github").getObject("stable")

            val versionName = githubStableObject.getString("version")
            val versionCode = githubStableObject.getInt("version_code")
            val apkLocationUrl = githubStableObject.getString("apk")
            compareAppVersionAndShowNotification(versionName, apkLocationUrl, versionCode)
        } catch (e: JsonParserException) {
            // Most likely something is wrong in data received from NEWPIPE_API_URL.
            // Do not alarm user and fail silently.
            if (DEBUG) {
                Log.w(TAG, "Could not get NewPipe API: invalid json", e)
            }
        }
    }

    override fun doWork(): Result {
        try {
            checkNewVersion()
        } catch (e: IOException) {
            Log.w(TAG, "Could not fetch NewPipe API: probably network problem", e)
            return Result.failure()
        } catch (e: ReCaptchaException) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e)
            return Result.failure()
        }
        return Result.success()
    }

    companion object {
        private val DEBUG = MainActivity.DEBUG
        private val TAG = NewVersionWorker::class.java.simpleName
        private const val NEWPIPE_API_URL = "https://newpipe.net/api/data.json"

        /**
         * Start a new worker which
         * checks if all conditions for performing a version check are met,
         * fetches the API endpoint [.NEWPIPE_API_URL] containing info
         * about the latest NewPipe version
         * and displays a notification about ana available update.
         * <br></br>
         * Following conditions need to be met, before data is request from the server:
         *
         *  *  The app is signed with the correct signing key (by TeamNewPipe / schabi).
         * If the signing key differs from the one used upstream, the update cannot be installed.
         *  * The user enabled searching for and notifying about updates in the settings.
         *  * The app did not recently check for updates.
         * We do not want to make unnecessary connections and DOS our servers.
         *
         */
        @JvmStatic
        fun enqueueNewVersionCheckingWork(context: Context) {
            val workRequest: WorkRequest =
                OneTimeWorkRequest.Builder(NewVersionWorker::class.java).build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
