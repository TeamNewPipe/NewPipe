package org.schabi.newpipe.local.subscription.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.withContext
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R

class SubscriptionExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    // This is needed for API levels < 31 (Android S).
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(applicationContext.getString(R.string.export_ongoing))
    }

    override suspend fun doWork(): Result {
        return try {
            val uri = inputData.getString(EXPORT_PATH)!!.toUri()
            val table = NewPipeDatabase.getInstance(applicationContext).subscriptionDAO()
            val subscriptions =
                table.getAll()
                    .awaitFirst()
                    .map { SubscriptionItem(it.serviceId, it.url ?: "", it.name ?: "") }

            val qty = subscriptions.size
            val title = applicationContext.resources.getQuantityString(R.plurals.export_subscriptions, qty, qty)
            setForeground(createForegroundInfo(title))

            withContext(Dispatchers.IO) {
                // Truncate file if it already exists
                applicationContext.contentResolver.openOutputStream(uri, "wt")?.use {
                    ImportExportJsonHelper.writeTo(subscriptions, it)
                }
            }

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Exported $qty subscriptions")
            }

            withContext(Dispatchers.Main) {
                Toast
                    .makeText(applicationContext, R.string.export_complete_toast, Toast.LENGTH_SHORT)
                    .show()
            }

            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error while exporting subscriptions", e)
            }

            withContext(Dispatchers.Main) {
                Toast
                    .makeText(applicationContext, R.string.subscriptions_export_unsuccessful, Toast.LENGTH_SHORT)
                    .show()
            }

            return Result.failure()
        }
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setOngoing(true)
                .setProgress(-1, -1, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentTitle(title)
                .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    companion object {
        private const val TAG = "SubscriptionExportWork"
        private const val NOTIFICATION_ID = 4567
        private const val NOTIFICATION_CHANNEL_ID = "newpipe"
        private const val WORK_NAME = "exportSubscriptions"
        private const val EXPORT_PATH = "exportPath"

        fun schedule(
            context: Context,
            uri: Uri,
        ) {
            val data = workDataOf(EXPORT_PATH to uri.toString())
            val workRequest =
                OneTimeWorkRequestBuilder<SubscriptionExportWorker>()
                    .setInputData(data)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        }
    }
}
