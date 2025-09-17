package org.schabi.newpipe.download

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Message
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.service.MissionState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DownloadStatus {
    data object None : DownloadStatus
    data class InProgress(val running: Boolean) : DownloadStatus
    data class Completed(val info: CompletedDownload) : DownloadStatus
}

data class CompletedDownload(
    val displayName: String?,
    val qualityLabel: String?,
    val mimeType: String?,
    val fileUri: Uri?,
    val parentUri: Uri?,
    val fileAvailable: Boolean
)

object DownloadStatusRepository {

    fun observe(context: Context, serviceId: Int, url: String): Flow<DownloadStatus> = callbackFlow {
        if (serviceId < 0 || url.isBlank()) {
            trySend(DownloadStatus.None)
            close()
            return@callbackFlow
        }

        val appContext = context.applicationContext
        val intent = Intent(appContext, DownloadManagerService::class.java)
        appContext.startService(intent)
        var binder: DownloadManagerBinder? = null
        var registeredCallback: Handler.Callback? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val downloadBinder = service as? DownloadManagerBinder
                if (downloadBinder == null) {
                    trySend(DownloadStatus.None)
                    appContext.unbindService(this)
                    close()
                    return
                }
                binder = downloadBinder
                trySend(downloadBinder.getDownloadStatus(serviceId, url, false).toDownloadStatus())

                val callback = Handler.Callback { message: Message ->
                    val mission = message.obj
                    if (mission.matches(serviceId, url)) {
                        val snapshot = downloadBinder.getDownloadStatus(serviceId, url, false)
                        trySend(snapshot.toDownloadStatus())
                    }
                    false
                }
                registeredCallback = callback
                downloadBinder.addMissionEventListener(callback)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                registeredCallback?.let { callback -> binder?.removeMissionEventListener(callback) }
                binder = null
                trySend(DownloadStatus.None)
            }
        }

        val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            trySend(DownloadStatus.None)
            close()
            return@callbackFlow
        }

        awaitClose {
            registeredCallback?.let { callback -> binder?.removeMissionEventListener(callback) }
            runCatching { appContext.unbindService(connection) }
        }
    }

    suspend fun refresh(context: Context, serviceId: Int, url: String): DownloadStatus {
        if (serviceId < 0 || url.isBlank()) return DownloadStatus.None
        return withBinder(context) { binder ->
            binder.getDownloadStatus(serviceId, url, true).toDownloadStatus()
        }
    }

    suspend fun deleteFile(context: Context, serviceId: Int, url: String): Boolean {
        if (serviceId < 0 || url.isBlank()) return false
        return withBinder(context) { binder ->
            binder.deleteFinishedMission(serviceId, url, true)
        }
    }

    suspend fun removeLink(context: Context, serviceId: Int, url: String): Boolean {
        if (serviceId < 0 || url.isBlank()) return false
        return withBinder(context) { binder ->
            binder.deleteFinishedMission(serviceId, url, false)
        }
    }

    private suspend fun <T> withBinder(context: Context, block: (DownloadManagerBinder) -> T): T {
        val appContext = context.applicationContext
        val intent = Intent(appContext, DownloadManagerService::class.java)
        appContext.startService(intent)
        return suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as? DownloadManagerBinder
                    if (binder == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Download service binder is null"))
                        }
                        appContext.unbindService(this)
                        return
                    }
                    try {
                        val result = block(binder)
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    } catch (throwable: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(throwable)
                        }
                    } finally {
                        appContext.unbindService(this)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Download service disconnected"))
                    }
                }
            }

            val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                continuation.resumeWithException(IllegalStateException("Unable to bind download service"))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                runCatching { appContext.unbindService(connection) }
            }
        }
    }

    private fun Any?.matches(serviceId: Int, url: String): Boolean {
        return when (this) {
            is DownloadMission -> this.serviceId == serviceId && url == this.source
            is FinishedMission -> this.serviceId == serviceId && url == this.source
            else -> false
        }
    }

    @VisibleForTesting
    @MainThread
    internal fun DownloadManager.DownloadStatusSnapshot?.toDownloadStatus(): DownloadStatus {
        if (this == null || state == MissionState.None) {
            return DownloadStatus.None
        }
        return when (state) {
            MissionState.Pending, MissionState.PendingRunning ->
                DownloadStatus.InProgress(state == MissionState.PendingRunning)
            MissionState.Finished -> {
                val mission = finishedMission
                if (mission == null) {
                    DownloadStatus.None
                } else {
                    val storage = mission.storage
                    val hasStorage = storage != null && !storage.isInvalid()
                    val info = CompletedDownload(
                        displayName = storage?.getName(),
                        qualityLabel = mission.qualityLabel,
                        mimeType = if (hasStorage) storage!!.getType() else null,
                        fileUri = if (hasStorage) storage!!.getUri() else null,
                        parentUri = if (hasStorage) storage!!.getParentUri() else null,
                        fileAvailable = fileExists && hasStorage
                    )
                    DownloadStatus.Completed(info)
                }
            }
            else -> DownloadStatus.None
        }
    }
}
