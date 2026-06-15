/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.workers

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.runCatching
import kotlin.to
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper

class SubscriptionImportWorker(
    private val context: Context,
    private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    private lateinit var subscriptions: List<SubscriptionItem>

    private val subscriptionManager = SubscriptionManager(context)
    private val mutex = Mutex()
    private val size: Int
        get() = subscriptions.size

    // This is needed for API levels < 31 (Android S).
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(title = context.getString(R.string.import_ongoing))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        subscriptions = try {
            loadSubscriptionsFromInput(SubscriptionImportInput.fromData(inputData))
        } catch (exception: Exception) {
            Log.e(TAG, "Error while loading subscriptions from path", exception)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.subscriptions_import_unsuccessful, LENGTH_SHORT)
                    .show()
            }
            return Result.failure()
        }

        val title = context.resources.getQuantityString(R.plurals.import_subscriptions, size, size)
        setForeground(createForegroundInfo(title = title))

        try {
            var index = 1
            subscriptions.asFlow()
                .flatMapMerge(concurrency = PARALLEL_EXTRACTIONS) { subscription ->
                    flow {
                        runCatching {
                            ExtractorHelper.getChannelInfo(
                                subscription.serviceId,
                                subscription.url,
                                true
                            ).await()
                        }.mapCatching { channelInfo ->
                            val channelTab = when {
                                channelInfo.tabs.isNotEmpty() -> {
                                    ExtractorHelper.getChannelTab(
                                        subscription.serviceId,
                                        channelInfo.tabs[0],
                                        true
                                    ).await()
                                }

                                else -> {
                                    Log.i(TAG, "No channel tabs available for: ${channelInfo.url}")
                                    null
                                }
                            }
                            Pair(channelInfo, channelTab)
                        }.also {
                            mutex.withLock { index++ }
                        }.onFailure { exception ->
                            Log.e(TAG, "Failed to fetch subscription", exception)
                        }.onSuccess { (channelInfo, channelTab) ->
                            setForeground(createForegroundInfo(title, channelInfo.name, index))
                            emit(channelInfo to channelTab)
                        }
                    }
                }
                .chunked(BUFFER_COUNT_BEFORE_INSERT)
                .collect { chunk ->
                    subscriptionManager.upsertAll(chunk)
                }
        } catch (exception: Exception) {
            Log.e(TAG, "Error while processing subscription data", exception)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.subscriptions_import_unsuccessful, LENGTH_SHORT)
                    .show()
            }
            return Result.failure()
        }

        setForeground(createForegroundInfo(title = title, progress = size))
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, R.string.import_complete_toast, LENGTH_SHORT)
                .show()
        }

        return Result.success()
    }

    private suspend fun loadSubscriptionsFromInput(
        input: SubscriptionImportInput
    ): List<SubscriptionItem> {
        return withContext(Dispatchers.IO) {
            when (input) {
                is SubscriptionImportInput.ChannelUrlMode ->
                    NewPipe.getService(input.serviceId).subscriptionExtractor
                        .fromChannelUrl(input.url)
                        .map { SubscriptionItem(it.serviceId, it.url, it.name) }

                is SubscriptionImportInput.InputStreamMode ->
                    context.contentResolver.openInputStream(input.url.toUri())?.use { stream ->
                        val contentType =
                            MimeTypeMap.getFileExtensionFromUrl(input.url).ifEmpty { DEFAULT_MIME }
                        NewPipe.getService(input.serviceId).subscriptionExtractor
                            .fromInputStream(stream, contentType)
                            .map { SubscriptionItem(it.serviceId, it.url, it.name) }
                    }

                is SubscriptionImportInput.PreviousExportMode ->
                    context.contentResolver.openInputStream(input.url.toUri())?.use {
                        ImportExportJsonHelper.readFrom(it)
                    }
            } ?: emptyList()
        }
    }

    private fun createForegroundInfo(
        title: String,
        text: String? = null,
        progress: Int = 0
    ): ForegroundInfo {
        val maxProgress = subscriptions.size
        val notification = NotificationCompat
            .Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setOngoing(true)
            .setProgress(maxProgress, progress, progress == 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(title)
            .setContentText(text)
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id)
            ).apply {
                if (progress > 0 && maxProgress > 0) {
                    val progressText = "$progress/$maxProgress"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setSubText(progressText)
                    } else {
                        setContentInfo(progressText)
                    }
                }
            }.build()
        val serviceType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }

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
