package org.schabi.newpipe.local.subscription.workers

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Pair
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.NO_SERVICE_ID

class SubscriptionImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    // This is needed for API levels < 31 (Android S).
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(R.string.import_ongoing)
        return createForegroundInfo(createNotification(title, null, 0, 0))
    }

    override suspend fun doWork(): Result {
        val mode = inputData.getInt(KEY_MODE, CHANNEL_URL_MODE)
        val extractor = NewPipe.getService(inputData.getInt(KEY_SERVICE_ID, NO_SERVICE_ID))
            .subscriptionExtractor
        val value = inputData.getString(KEY_VALUE) ?: ""

        val subscriptions = withContext(Dispatchers.IO) {
            if (mode == CHANNEL_URL_MODE) {
                extractor
                    .fromChannelUrl(value)
                    .map { SubscriptionItem(it.serviceId, it.url, it.name) }
            } else {
                applicationContext.contentResolver.openInputStream(value.toUri())?.use {
                    if (mode == INPUT_STREAM_MODE) {
                        val contentType = MimeTypeMap.getFileExtensionFromUrl(value).ifEmpty { DEFAULT_MIME }
                        extractor
                            .fromInputStream(it, contentType)
                            .map { SubscriptionItem(it.serviceId, it.url, it.name) }
                    } else {
                        ImportExportJsonHelper.readFrom(it)
                    }
                } ?: emptyList()
            }
        }

        val mutex = Mutex()
        var index = 1
        val qty = subscriptions.size
        var title =
            applicationContext.resources.getQuantityString(R.plurals.load_subscriptions, qty, qty)

        val channelInfoList = withContext(Dispatchers.IO.limitedParallelism(PARALLEL_EXTRACTIONS)) {
            subscriptions
                .map {
                    async {
                        val channelInfo =
                            ExtractorHelper.getChannelInfo(it.serviceId, it.url, true).await()
                        val channelTab =
                            ExtractorHelper.getChannelTab(it.serviceId, channelInfo.tabs[0], true).await()

                        val currentIndex = mutex.withLock { index++ }
                        val notification = createNotification(title, channelInfo.name, currentIndex, qty)
                        setForeground(createForegroundInfo(notification))

                        Pair(channelInfo, listOf(channelTab))
                    }
                }.awaitAll()
        }

        title = applicationContext.resources.getQuantityString(R.plurals.import_subscriptions, qty, qty)
        setForeground(createForegroundInfo(createNotification(title, null, 0, 0)))
        index = 0

        val subscriptionManager = SubscriptionManager(applicationContext)
        for (chunk in channelInfoList.chunked(BUFFER_COUNT_BEFORE_INSERT)) {
            withContext(Dispatchers.IO) {
                subscriptionManager.upsertAll(chunk)
            }
            index += chunk.size
            setForeground(createForegroundInfo(createNotification(title, null, index, qty)))
        }

        withContext(Dispatchers.Main) {
            Toast
                .makeText(applicationContext, R.string.import_complete_toast, Toast.LENGTH_SHORT)
                .show()
        }

        return Result.success()
    }

    private fun createNotification(
        title: String,
        text: String?,
        currentProgress: Int,
        maxProgress: Int,
    ): Notification =
        NotificationCompat
            .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setOngoing(true)
            .setProgress(maxProgress, currentProgress, currentProgress == 0)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(title)
            .setContentText(text)
            .addAction(
                R.drawable.ic_close,
                applicationContext.getString(R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            ).apply {
                if (currentProgress > 0 && maxProgress > 0) {
                    val progressText = "$currentProgress/$maxProgress"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setSubText(progressText)
                    } else {
                        setContentInfo(progressText)
                    }
                }
            }.build()

    private fun createForegroundInfo(notification: Notification): ForegroundInfo {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    companion object {
        private const val NOTIFICATION_ID = 4568
        private const val NOTIFICATION_CHANNEL_ID = "newpipe"
        private const val DEFAULT_MIME = "application/octet-stream"
        private const val PARALLEL_EXTRACTIONS = 8
        private const val BUFFER_COUNT_BEFORE_INSERT = 50

        const val WORK_NAME = "SubscriptionImportWorker"
        const val CHANNEL_URL_MODE = 0
        const val INPUT_STREAM_MODE = 1
        const val PREVIOUS_EXPORT_MODE = 2
        const val KEY_MODE = "key_mode"
        const val KEY_VALUE = "key_value"
    }
}
