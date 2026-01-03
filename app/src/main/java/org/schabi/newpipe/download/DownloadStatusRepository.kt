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

enum class DownloadStage {
    Pending,
    Running,
    Finished
}

data class DownloadHandle(
    val serviceId: Int,
    val url: String,
    val streamUid: Long,
    val storageUri: Uri?,
    val timestamp: Long,
    val kind: Char
)

data class DownloadEntry(
    val handle: DownloadHandle,
    val displayName: String?,
    val qualityLabel: String?,
    val mimeType: String?,
    val fileUri: Uri?,
    val parentUri: Uri?,
    val fileAvailable: Boolean,
    val stage: DownloadStage
)

object DownloadStatusRepository {

    /**
     * Keeps a one-off binding to [DownloadManagerService] alive for as long as the caller stays
     * subscribed. We prime the channel with the latest persisted snapshot and then forward every
     * mission event emitted by the service-bound handler. Once the consumer cancels the flow we
     * make sure to unregister the handler and unbind the service to avoid leaking the connection.
     */
    fun observe(context: Context, serviceId: Int, url: String): Flow<List<DownloadEntry>> = callbackFlow {
        if (serviceId < 0 || url.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val appContext = context.applicationContext
        val intent = Intent(appContext, DownloadManagerService::class.java)
        appContext.startService(intent)
        // The download manager service only notifies listeners while a client is bound, so the flow
        // keeps a foreground-style binding alive for its entire lifetime. Holding on to
        // applicationContext avoids leaking short-lived UI contexts.
        var binder: DownloadManagerBinder? = null
        var registeredCallback: Handler.Callback? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val downloadBinder = service as? DownloadManagerBinder
                if (downloadBinder == null) {
                    trySend(emptyList())
                    appContext.unbindService(this)
                    close()
                    return
                }
                binder = downloadBinder
                // First delivery: snapshot persisted on disk so the UI paints immediately even
                // before the service emits new events.
                trySend(downloadBinder.getDownloadStatuses(serviceId, url, true).toDownloadEntries())

                val callback = Handler.Callback { message: Message ->
                    val mission = message.obj
                    if (mission.matches(serviceId, url)) {
                        // Each mission event carries opaque state, so we fetch a fresh snapshot to
                        // guarantee consistent entries while the download progresses or finishes.
                        val snapshots = downloadBinder.getDownloadStatuses(serviceId, url, false)
                        trySend(snapshots.toDownloadEntries())
                    }
                    false
                }
                registeredCallback = callback
                downloadBinder.addMissionEventListener(callback)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                registeredCallback?.let { callback -> binder?.removeMissionEventListener(callback) }
                binder = null
                trySend(emptyList())
            }
        }

        val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        awaitClose {
            // When the collector disappears we remove listeners and unbind immediately to avoid
            // holding the service forever; the service will rebind on the next subscription.
            registeredCallback?.let { callback -> binder?.removeMissionEventListener(callback) }
            runCatching { appContext.unbindService(connection) }
        }
    }

    suspend fun refresh(context: Context, serviceId: Int, url: String): List<DownloadEntry> {
        if (serviceId < 0 || url.isBlank()) return emptyList()
        return withBinder(context) { binder ->
            binder.getDownloadStatuses(serviceId, url, true).toDownloadEntries()
        }
    }

    suspend fun deleteFile(context: Context, handle: DownloadHandle): Boolean {
        if (handle.serviceId < 0 || handle.url.isBlank()) return false
        return withBinder(context) { binder ->
            binder.deleteFinishedMission(handle.serviceId, handle.url, handle.storageUri, handle.timestamp, true)
        }
    }

    suspend fun removeLink(context: Context, handle: DownloadHandle): Boolean {
        if (handle.serviceId < 0 || handle.url.isBlank()) return false
        return withBinder(context) { binder ->
            binder.deleteFinishedMission(handle.serviceId, handle.url, handle.storageUri, handle.timestamp, false)
        }
    }

    /**
     * Helper that briefly binds to [DownloadManagerService], executes [block] against its binder
     * and tears everything down in one place. All callers should use this to prevent scattering
     * ad-hoc bind/unbind logic across the codebase.
     */
    private suspend fun <T> withBinder(context: Context, block: (DownloadManagerBinder) -> T): T {
        val appContext = context.applicationContext
        val intent = Intent(appContext, DownloadManagerService::class.java)
        appContext.startService(intent)
        // The direct call path still needs the service running long enough to complete the
        // binder transaction, so we explicitly start it before establishing the short-lived bind.
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
    internal fun List<DownloadManager.DownloadStatusSnapshot>.toDownloadEntries(): List<DownloadEntry> {
        return buildList {
            for (snapshot in this@toDownloadEntries) {
                snapshot.toDownloadEntry()?.let { add(it) }
            }
        }
    }

    @VisibleForTesting
    @MainThread
    internal fun DownloadManager.DownloadStatusSnapshot.toDownloadEntry(): DownloadEntry? {
        val stage = when (state) {
            MissionState.Pending -> DownloadStage.Pending
            MissionState.PendingRunning -> DownloadStage.Running
            MissionState.Finished -> DownloadStage.Finished
            else -> return null
        }

        val (metadata, storage) = when (stage) {
            DownloadStage.Finished -> finishedMission?.let {
                MissionMetadata(
                    serviceId = it.serviceId,
                    url = it.source,
                    streamUid = it.streamUid,
                    timestamp = it.timestamp,
                    kind = it.kind,
                    qualityLabel = it.qualityLabel
                ) to it.storage
            }
            else -> pendingMission?.let {
                MissionMetadata(
                    serviceId = it.serviceId,
                    url = it.source,
                    streamUid = it.streamUid,
                    timestamp = it.timestamp,
                    kind = it.kind,
                    qualityLabel = it.qualityLabel
                ) to it.storage
            }
        } ?: return null

        val hasStorage = storage != null && !storage.isInvalid()
        val fileUri = storage?.getUri()
        val parentUri = storage?.getParentUri()

        val handle = DownloadHandle(
            serviceId = metadata.serviceId,
            url = metadata.url ?: "",
            streamUid = metadata.streamUid,
            storageUri = fileUri,
            timestamp = metadata.timestamp,
            kind = metadata.kind
        )

        val fileAvailable = when (stage) {
            DownloadStage.Finished -> hasStorage && fileExists
            DownloadStage.Pending, DownloadStage.Running -> false
        }

        return DownloadEntry(
            handle = handle,
            displayName = storage?.getName(),
            qualityLabel = metadata.qualityLabel,
            mimeType = if (hasStorage) storage.getType() else null,
            fileUri = fileUri,
            parentUri = parentUri,
            fileAvailable = fileAvailable,
            stage = stage
        )
    }

    private data class MissionMetadata(
        val serviceId: Int,
        val url: String?,
        val streamUid: Long,
        val timestamp: Long,
        val kind: Char,
        val qualityLabel: String?
    )
}
