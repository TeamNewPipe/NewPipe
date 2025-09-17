package org.schabi.newpipe.download

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.schabi.newpipe.BuildConfig

class DownloadRevalidationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {
            DownloadMaintenance.revalidateAvailable(applicationContext)
            Result.success()
        } catch (throwable: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to revalidate downloads", throwable)
            }
            Result.retry()
        }
    }

    private companion object {
        private const val TAG = "DownloadRevalidation"
    }
}
