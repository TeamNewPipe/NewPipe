package org.schabi.newpipe.local.subscription.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper

class SubscriptionImportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    // This is needed for API levels < 31 (Android S).
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(applicationContext.getString(R.string.import_ongoing), null, 0, 0)
    }

    override suspend fun doWork(): Result {
        val subscriptions =
            try {
                loadSubscriptionsFromInput(SubscriptionImportInput.fromData(inputData))
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error while loading subscriptions from path", e)
                }
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(applicationContext, R.string.subscriptions_import_unsuccessful, Toast.LENGTH_SHORT)
                        .show()
                }
                return Result.failure()
            }

        val mutex = Mutex()
        var index = 1
        val qty = subscriptions.size
        var title =
            applicationContext.resources.getQuantityString(R.plurals.load_subscriptions, qty, qty)

        val channelInfoList =
            try {
                withContext(Dispatchers.IO.limitedParallelism(PARALLEL_EXTRACTIONS)) {
                    subscriptions
                        .map {
                            async {
                                val channelInfo =
                                    ExtractorHelper.getChannelInfo(it.serviceId, it.url, true).await()
                                val channelTab =
                                    ExtractorHelper.getChannelTab(it.serviceId, channelInfo.tabs[0], true).await()

                                val currentIndex = mutex.withLock { index++ }
                                setForeground(createForegroundInfo(title, channelInfo.name, currentIndex, qty))

                                channelInfo to channelTab
                            }
                        }.awaitAll()
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error while loading subscription data", e)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, R.string.subscriptions_import_unsuccessful, Toast.LENGTH_SHORT)
                        .show()
                }
                return Result.failure()
            }

        title = applicationContext.resources.getQuantityString(R.plurals.import_subscriptions, qty, qty)
        setForeground(createForegroundInfo(title, null, 0, 0))
        index = 0

        val subscriptionManager = SubscriptionManager(applicationContext)
        for (chunk in channelInfoList.chunked(BUFFER_COUNT_BEFORE_INSERT)) {
            withContext(Dispatchers.IO) {
                subscriptionManager.upsertAll(chunk)
            }
            index += chunk.size
            setForeground(createForegroundInfo(title, null, index, qty))
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, R.string.import_complete_toast, Toast.LENGTH_SHORT)
                .show()
        }

        return Result.success()
    }

    private suspend fun loadSubscriptionsFromInput(input: SubscriptionImportInput): List<SubscriptionItem> {
        return withContext(Dispatchers.IO) {
            when (input) {
                is SubscriptionImportInput.ChannelUrlMode ->
                    NewPipe.getService(input.serviceId).subscriptionExtractor
                        .fromChannelUrl(input.url)
                        .map { SubscriptionItem(it.serviceId, it.url, it.name) }

                is SubscriptionImportInput.InputStreamMode ->
                    applicationContext.contentResolver.openInputStream(input.url.toUri())?.use {
                        val contentType =
                            MimeTypeMap.getFileExtensionFromUrl(input.url).ifEmpty { DEFAULT_MIME }
                        NewPipe.getService(input.serviceId).subscriptionExtractor
                            .fromInputStream(it, contentType)
                            .map { SubscriptionItem(it.serviceId, it.url, it.name) }
                    }

                is SubscriptionImportInput.PreviousExportMode ->
                    applicationContext.contentResolver.openInputStream(input.url.toUri())?.use {
                        ImportExportJsonHelper.readFrom(it)
                    }
            } ?: emptyList()
        }
    }

    private fun createForegroundInfo(
        title: String,
        text: String?,
        currentProgress: Int,
        maxProgress: Int
    ): ForegroundInfo {
        val notification =
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
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
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
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    companion object {
        // Log tag length is limited to 23 characters on API levels < 24.
        private const val TAG = "SubscriptionImport"

        private const val NOTIFICATION_ID = 4568
        private const val NOTIFICATION_CHANNEL_ID = "newpipe"
        private const val DEFAULT_MIME = "application/octet-stream"
        private const val PARALLEL_EXTRACTIONS = 8
        private const val BUFFER_COUNT_BEFORE_INSERT = 50

        const val WORK_NAME = "SubscriptionImportWorker"
    }
}

sealed class SubscriptionImportInput : Parcelable {
    @Parcelize
    data class ChannelUrlMode(val serviceId: Int, val url: String) : SubscriptionImportInput()

    @Parcelize
    data class InputStreamMode(val serviceId: Int, val url: String) : SubscriptionImportInput()

    @Parcelize
    data class PreviousExportMode(val url: String) : SubscriptionImportInput()

    fun toData(): Data {
        val (mode, serviceId, url) = when (this) {
            is ChannelUrlMode -> Triple(CHANNEL_URL_MODE, serviceId, url)
            is InputStreamMode -> Triple(INPUT_STREAM_MODE, serviceId, url)
            is PreviousExportMode -> Triple(PREVIOUS_EXPORT_MODE, null, url)
        }
        return workDataOf("mode" to mode, "service_id" to serviceId, "url" to url)
    }

    companion object {

        private const val CHANNEL_URL_MODE = 0
        private const val INPUT_STREAM_MODE = 1
        private const val PREVIOUS_EXPORT_MODE = 2

        fun fromData(data: Data): SubscriptionImportInput {
            val mode = data.getInt("mode", PREVIOUS_EXPORT_MODE)
            when (mode) {
                CHANNEL_URL_MODE -> {
                    val serviceId = data.getInt("service_id", -1)
                    if (serviceId == -1) {
                        throw IllegalArgumentException("No service id provided")
                    }
                    val url = data.getString("url")!!
                    return ChannelUrlMode(serviceId, url)
                }

                INPUT_STREAM_MODE -> {
                    val serviceId = data.getInt("service_id", -1)
                    if (serviceId == -1) {
                        throw IllegalArgumentException("No service id provided")
                    }
                    val url = data.getString("url")!!
                    return InputStreamMode(serviceId, url)
                }

                PREVIOUS_EXPORT_MODE -> {
                    val url = data.getString("url")!!
                    return PreviousExportMode(url)
                }

                else -> throw IllegalArgumentException("Unknown mode: $mode")
            }
        }
    }
}
