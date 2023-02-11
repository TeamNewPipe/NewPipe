package org.schabi.newpipe

import android.content.Context
import android.content.Intent
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
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.PendingIntentCompat
import org.schabi.newpipe.util.ReleaseVersionUtil.coerceUpdateCheckExpiry
import org.schabi.newpipe.util.ReleaseVersionUtil.isLastUpdateCheckExpired
import org.schabi.newpipe.util.ReleaseVersionUtil.isReleaseApk
import org.schabi.newpipe.util.Version
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
     */
    private fun compareAppVersionAndShowNotification(
        versionName: String,
        apkLocationUrl: String?
    ) {
        val sourceVersion = Version.fromString(BuildConfig.VERSION_NAME)
        val targetVersion = Version.fromString(versionName)

        // abort if source version is the same or newer than target version
        if (sourceVersion >= targetVersion) {
            if (inputData.getBoolean(IS_MANUAL, false)) {
                // Show toast stating that the app is up-to-date if the update check was manual.
                ContextCompat.getMainExecutor(applicationContext).execute {
                    Toast.makeText(
                        applicationContext, R.string.app_update_unavailable_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl?.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntentCompat.getActivity(
            applicationContext, 0, intent, 0
        )
        val channelId = applicationContext.getString(R.string.app_update_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(
                applicationContext.getString(R.string.app_update_available_notification_title)
            )
            .setContentText(
                applicationContext.getString(
                    R.string.app_update_available_notification_text, versionName
                )
            )

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.notify(2000, notificationBuilder.build())
    }

    @Throws(IOException::class, ReCaptchaException::class)
    private fun checkNewVersion() {
        // Check if the current apk is a github one or not.
        if (!isReleaseApk()) {
            return
        }

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
        val response = DownloaderImpl.getInstance().get(API_URL)
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
            val jObj = JsonParser.`object`().from(response.responseBody())
            val versionName = jObj.getString("tag_name")
            val apkLocationUrl = jObj
                .getArray("assets")
                .getObject(0)
                .getString("browser_download_url")
            compareAppVersionAndShowNotification(versionName, apkLocationUrl)
        } catch (e: JsonParserException) {
            // Most likely something is wrong in data received from API_URL.
            // Do not alarm user and fail silently.
            if (DEBUG) {
                Log.w(TAG, "Could not get Github API: invalid json", e)
            }
        }
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
        private const val API_URL =
            "https://api.github.com/repos/polymorphicshade/NewPipe/releases/latest"
        private const val IS_MANUAL = "isManual"

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
