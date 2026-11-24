package org.schabi.newpipe.local.subscription.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.SavedServicePolicy

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
                Log.e(TAG, "Error while loading subscriptions from path", e)
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(applicationContext, R.string.subscriptions_import_unsuccessful, Toast.LENGTH_SHORT)
                        .show()
                }
                return Result.failure()
            }

        val totalCount = subscriptions.size
        val importingTitle =
            applicationContext.resources.getQuantityString(
                R.plurals.import_subscriptions,
                totalCount,
                totalCount
            )
        val supportedServiceIds = ServiceList.all().mapTo(mutableSetOf()) { it.serviceId }
        val entities = prepareSubscriptionEntities(subscriptions, supportedServiceIds)
        setForeground(
            createForegroundInfo(
                importingTitle,
                null,
                totalCount - entities.size,
                totalCount
            )
        )

        val profileId = inputData.getString(PROFILE_ID_KEY)
            ?: ProfileManager.getActiveProfileId(applicationContext)
        val subscriptionManager = SubscriptionManager(applicationContext, profileId)
        var insertedCount = 0
        var processedCount = totalCount - entities.size
        try {
            for (chunk in entities.chunked(BUFFER_COUNT_BEFORE_INSERT)) {
                withContext(Dispatchers.IO) {
                    insertedCount += subscriptionManager.insertImportedSubscriptions(chunk)
                }
                processedCount += chunk.size
                setForeground(
                    createForegroundInfo(importingTitle, null, processedCount, totalCount)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error while storing imported subscriptions", e)
            withContext(Dispatchers.Main) {
                Toast
                    .makeText(applicationContext, R.string.subscriptions_import_unsuccessful, Toast.LENGTH_SHORT)
                    .show()
            }
            return Result.failure()
        }

        val skippedCount = totalCount - insertedCount
        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    applicationContext,
                    applicationContext.getString(
                        R.string.subscriptions_import_complete_summary,
                        insertedCount,
                        skippedCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
        }

        return Result.success(
            workDataOf(
                IMPORTED_COUNT_KEY to insertedCount,
                SKIPPED_COUNT_KEY to skippedCount
            )
        )
    }

    private suspend fun loadSubscriptionsFromInput(input: SubscriptionImportInput): List<SubscriptionItem> {
        return withContext(Dispatchers.IO) {
            when (input) {
                is SubscriptionImportInput.ChannelUrlMode ->
                    NewPipe.getService(input.serviceId).subscriptionExtractor
                        .fromChannelUrl(input.url)
                        .map { SubscriptionItem(it.serviceId, it.url, it.name) }

                is SubscriptionImportInput.InputStreamMode ->
                    StoredFileHelper(applicationContext, input.url.toUri(), DEFAULT_MIME).let { fileHelper ->
                        SharpInputStream(fileHelper.getStream()).use { stream ->
                            val contentType = getInputStreamContentType(fileHelper)

                            NewPipe.getService(input.serviceId).subscriptionExtractor
                                .fromInputStream(stream, contentType)
                                .map { SubscriptionItem(it.serviceId, it.url, it.name) }
                        }
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
                .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
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
        private const val BUFFER_COUNT_BEFORE_INSERT = 50

        const val WORK_NAME = "SubscriptionImportWorker"
        const val IMPORTED_COUNT_KEY = "imported_count"
        const val SKIPPED_COUNT_KEY = "skipped_count"
        const val PROFILE_ID_KEY = "profile_id"

        internal fun getInputStreamContentType(fileHelper: StoredFileHelper): String {
            val contentType = fileHelper.getType()
            if (!contentType.isNullOrEmpty() && contentType != DEFAULT_MIME) {
                return contentType
            }

            val fileName = fileHelper.getName() ?: return DEFAULT_MIME
            val pointIndex = fileName.lastIndexOf('.')
            return when {
                pointIndex == -1 || pointIndex >= fileName.length - 1 -> DEFAULT_MIME
                else -> fileName.substring(pointIndex + 1)
            }
        }

        internal fun prepareSubscriptionEntities(
            subscriptions: List<SubscriptionItem>,
            supportedServiceIds: Set<Int>
        ): List<SubscriptionEntity> = subscriptions.mapNotNull { subscription ->
            val url = subscription.url.trim()
            if (!SavedServicePolicy.canPreserve(subscription.serviceId, supportedServiceIds) || url.isEmpty()) {
                null
            } else {
                SubscriptionEntity(
                    serviceId = subscription.serviceId,
                    url = url,
                    name = subscription.name.trim().ifEmpty { url }
                )
            }
        }
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
            when (val mode = data.getInt("mode", PREVIOUS_EXPORT_MODE)) {
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
