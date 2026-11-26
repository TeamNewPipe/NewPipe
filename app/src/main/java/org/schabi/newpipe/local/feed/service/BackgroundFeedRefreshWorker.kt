package org.schabi.newpipe.local.feed.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.profiles.ProfileManager

/** Periodic feed caching that does not depend on new-stream notifications. */
class BackgroundFeedRefreshWorker(context: Context, parameters: WorkerParameters) : RxWorker(context, parameters) {
    override fun createWork(): Single<Result> = Single.defer {
        if (!isEnabled(applicationContext)) {
            return@defer Single.just(Result.success())
        }
        Flowable.fromIterable(ProfileManager.getProfiles(applicationContext))
            .concatMapSingle { profile ->
                if (!isEnabled(applicationContext) || ProfileManager.getProfile(applicationContext, profile.id) == null) {
                    Single.just(false)
                } else {
                    val loader = FeedLoadManager(applicationContext, profile.id)
                    val alertsActive = profile.id == ProfileManager.getActiveProfileId(applicationContext) &&
                        NotificationHelper.areNewStreamsNotificationsEnabled(applicationContext) &&
                        NotificationHelper.areNotificationsEnabledOnDevice(applicationContext)
                    SubscriptionManager(applicationContext, profile.id).subscriptions().firstOrError()
                        .flatMap { subscriptions ->
                            loader.startLoading(onlySubscriptions = subscriptionsToRefresh(subscriptions, alertsActive))
                        }
                        .doOnDispose(loader::cancel)
                        .map { results -> results.any { it.isOnError || it.value?.errors?.isNotEmpty() == true } }
                }
            }
            .toList()
            .map { failures ->
                when {
                    failures.none { it } -> Result.success()
                    runAttemptCount < 2 -> Result.retry()
                    else -> Result.failure()
                }
            }
    }.onErrorReturn { if (runAttemptCount < 2) Result.retry() else Result.failure() }

    companion object {
        internal const val WORK_NAME = "background-feed-refresh"
        private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

        private fun isEnabled(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.background_feed_enabled_key), false)

        fun initialize(context: Context) {
            val appContext = context.applicationContext
            val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
            val keys = setOf(
                appContext.getString(R.string.background_feed_enabled_key),
                appContext.getString(R.string.background_feed_interval_key),
                appContext.getString(R.string.background_feed_wifi_only_key)
            )
            preferenceListener?.let(preferences::unregisterOnSharedPreferenceChangeListener)
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in keys) schedule(appContext)
            }.also(preferences::registerOnSharedPreferenceChangeListener)
            schedule(appContext)
        }

        // Let the notification worker discover new videos for channels with active alerts.
        // Otherwise a silent refresh would mark those videos as old before alerts can be sent.
        internal fun subscriptionsToRefresh(subscriptions: List<SubscriptionEntity>, alertsActive: Boolean): Set<Long> = subscriptions.filter {
            !it.feedMuted &&
                (!alertsActive || it.notificationMode !in setOf(NotificationMode.ENABLED, NotificationMode.KEYWORDS_ONLY))
        }.mapTo(mutableSetOf()) { it.uid }

        internal fun intervalMinutes(value: String?): Long = value?.toLongOrNull()
            ?.takeIf { it in setOf(15L, 30L, 60L, 180L, 360L, 720L) } ?: 60L

        internal fun request(context: Context): androidx.work.PeriodicWorkRequest {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val interval = intervalMinutes(
                preferences.getString(
                    context.getString(R.string.background_feed_interval_key),
                    "60"
                )
            )
            val wifiOnly = preferences.getBoolean(context.getString(R.string.background_feed_wifi_only_key), true)
            return PeriodicWorkRequestBuilder<BackgroundFeedRefreshWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        }

        private fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (isEnabled(context)) {
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request(context))
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
