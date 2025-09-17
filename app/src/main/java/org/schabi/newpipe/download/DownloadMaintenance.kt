package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.download.DownloadedStreamStatus
import java.util.concurrent.TimeUnit

object DownloadMaintenance {
    private const val WORK_NAME = "download_revalidation"

    @JvmStatic
    fun revalidateAvailable(context: Context, limit: Int = 25) {
        val dao = NewPipeDatabase.getInstance(context).downloadedStreamsDao()
        val entries = dao.listByStatus(DownloadedStreamStatus.AVAILABLE, limit)
        if (entries.isEmpty()) return

        val now = System.currentTimeMillis()
        for (entry in entries) {
            val uriString = entry.fileUri
            if (uriString.isBlank()) {
                dao.updateStatus(entry.id, DownloadedStreamStatus.MISSING, now, entry.missingSince ?: now)
                continue
            }

            val available = DownloadAvailabilityChecker.isReadable(context, Uri.parse(uriString))
            if (available) {
                dao.updateStatus(entry.id, DownloadedStreamStatus.AVAILABLE, now, null)
            } else {
                dao.updateStatus(entry.id, DownloadedStreamStatus.MISSING, now, entry.missingSince ?: now)
            }
        }
    }

    @JvmStatic
    fun schedule(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<DownloadRevalidationWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}
