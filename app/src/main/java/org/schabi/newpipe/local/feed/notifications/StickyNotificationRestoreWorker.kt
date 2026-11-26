package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Restores the saved projection silently, even while the device is offline. */
class StickyNotificationRestoreWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        StickyNotificationCoordinator.reconcile(applicationContext)
        Result.success()
    } catch (exception: Exception) {
        Log.w("StickyNotifications", "Could not restore pending stream alerts", exception)
        Result.retry()
    }
}
