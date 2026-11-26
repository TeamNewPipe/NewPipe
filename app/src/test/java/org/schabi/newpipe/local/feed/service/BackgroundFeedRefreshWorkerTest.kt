package org.schabi.newpipe.local.feed.service

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackgroundFeedRefreshWorkerTest {
    @Test
    fun `silent refresh leaves active alerts for notification worker but refreshes all without alerts`() {
        val subscriptions = listOf(
            SubscriptionEntity(uid = 1, notificationMode = NotificationMode.DISABLED),
            SubscriptionEntity(uid = 2, notificationMode = NotificationMode.ENABLED),
            SubscriptionEntity(uid = 3, notificationMode = NotificationMode.KEYWORDS_ONLY)
        )
        assertEquals(setOf(1L), BackgroundFeedRefreshWorker.subscriptionsToRefresh(subscriptions, true))
        assertEquals(setOf(1L, 2L, 3L), BackgroundFeedRefreshWorker.subscriptionsToRefresh(subscriptions, false))
    }

    @Test
    fun mutedSubscriptionsAreExcludedWithAndWithoutActiveAlerts() {
        val subscriptions = listOf(
            SubscriptionEntity(uid = 1, notificationMode = NotificationMode.DISABLED),
            SubscriptionEntity(uid = 2, notificationMode = NotificationMode.ENABLED),
            SubscriptionEntity(uid = 3, notificationMode = NotificationMode.DISABLED, feedMuted = true),
            SubscriptionEntity(uid = 4, notificationMode = NotificationMode.ENABLED, feedMuted = true),
            SubscriptionEntity(uid = 5, notificationMode = NotificationMode.KEYWORDS_ONLY, feedMuted = true)
        )
        assertEquals(setOf(1L), BackgroundFeedRefreshWorker.subscriptionsToRefresh(subscriptions, true))
        assertEquals(setOf(1L, 2L), BackgroundFeedRefreshWorker.subscriptionsToRefresh(subscriptions, false))
    }

    @Test
    fun `schedule uses independent network and interval settings with safe defaults`() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        val defaults = BackgroundFeedRefreshWorker.request(context).workSpec
        assertEquals(TimeUnit.HOURS.toMillis(1), defaults.intervalDuration)
        assertEquals(NetworkType.UNMETERED, defaults.constraints.requiredNetworkType)
        assertTrue(defaults.constraints.requiresBatteryNotLow())
        preferences.edit()
            .putBoolean(context.getString(R.string.background_feed_wifi_only_key), false)
            .putString(context.getString(R.string.background_feed_interval_key), "180")
            .commit()
        val changed = BackgroundFeedRefreshWorker.request(context).workSpec
        assertEquals(TimeUnit.HOURS.toMillis(3), changed.intervalDuration)
        assertEquals(NetworkType.CONNECTED, changed.constraints.requiredNetworkType)
        assertEquals(60L, BackgroundFeedRefreshWorker.intervalMinutes("bad backup value"))
        assertEquals(60L, BackgroundFeedRefreshWorker.intervalMinutes("1"))
    }
}
