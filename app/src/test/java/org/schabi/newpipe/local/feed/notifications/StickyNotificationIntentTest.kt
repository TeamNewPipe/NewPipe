/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.KEY_LINK_TYPE
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.TvRemoteAction
import org.schabi.newpipe.util.TvRemoteKeyDispatcher
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StickyNotificationIntentTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val generation = "intent-session"
    private lateinit var database: AppDatabase
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private val dao get() = database.pendingStreamNotificationDAO()

    @Before
    fun setup() {
        StickyNotificationCoordinator.resetForTests()
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        preferences.edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, 0).edit().clear().commit()
        KidsMode.lock()
        ProfileManager.getActiveProfileId(context)
        preferences.edit()
            .putBoolean(context.getString(R.string.enable_streams_notifications), true)
            .putBoolean(StickyNotificationSettings.ENABLED_KEY, true)
            .putString(StickyNotificationSettings.GENERATION_KEY, generation)
            .commit()
        manager.createNotificationChannel(
            NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
    }

    @After
    fun teardown() {
        shadowOf(Looper.getMainLooper()).idle()
        StickyNotificationCoordinator.resetForTests()
        databaseField.set(null, previousDatabase)
        database.close()
        manager.cancelAll()
        preferences.edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, 0).edit().clear().commit()
        KidsMode.lock()
        RxJavaPlugins.reset()
    }

    @Test
    fun contentTapDirectlyStartsGuardedActivityAndOpensStreamWithoutAcknowledgingIt() {
        val item = entry()
        post(listOf(item))
        val child = notification(StickyNotificationAction.OPEN_ITEM)
        val pending = child.contentIntent
        assertTrue(shadowOf(pending).isActivity)
        val destination = requireNotNull(open(pending))
        assertEquals(MainActivity::class.java.name, destination.component?.className)
        assertEquals(item.streamUrl, destination.getStringExtra(KEY_URL))
        assertEquals(item.serviceId, destination.getIntExtra(KEY_SERVICE_ID, -1))
        assertEquals(StreamingService.LinkType.STREAM, destination.getSerializableExtra(KEY_LINK_TYPE))
        assertEquals(listOf(item), dao.pending(profile, generation))
        assertEquals(2, manager.activeNotifications.size)
        assertEquals(profile, ProfileManager.getActiveProfileId(context))
    }

    @Test
    fun channelAndOverflowTapsOpenTheirDestinationsWithoutClosingPendingAlerts() {
        val items = (0 until 25).map { entry(it, channel = it) }
        post(items)
        val channel = notification(StickyNotificationAction.OPEN_CHANNEL)
        val channelTarget = requireNotNull(StickyNotificationTarget.fromIntent(shadowOf(channel.contentIntent).savedIntent))
        val destination = requireNotNull(open(channel.contentIntent))
        assertEquals(channelTarget.url, destination.getStringExtra(KEY_URL))
        assertEquals(StreamingService.LinkType.CHANNEL, destination.getSerializableExtra(KEY_LINK_TYPE))
        val feed = requireNotNull(open(notification(StickyNotificationAction.OPEN_FEED).contentIntent))
        assertEquals(MainActivity::class.java.name, feed.component?.className)
        assertEquals(TvRemoteAction.FEED.id, feed.getStringExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION))
        assertEquals(items.size, dao.pending(profile, generation).size)
    }

    @Test
    fun childCloseAndManualSwipePersistAcknowledgementThroughTheRealReceiver() {
        val first = entry(1)
        val second = entry(2)
        post(listOf(first, second))
        val close = notification(StickyNotificationAction.OPEN_ITEM, first.streamUrl).actions.single().actionIntent
        assertTrue(shadowOf(close).isBroadcast)
        close.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(second), dao.pending(profile, generation))
        assertEquals(2, manager.activeNotifications.size)
        // Repeated system delivery is harmless and a duplicate fetch cannot reopen a closed item.
        close.send()
        shadowOf(Looper.getMainLooper()).idle()
        dao.insert(listOf(first))
        assertEquals(listOf(second), dao.pending(profile, generation))
        notification(StickyNotificationAction.OPEN_ITEM).deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dao.pending(profile, generation).isEmpty())
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun channelCloseAndSummarySwipeAreScopedToServiceAndChannel() {
        val first = entry(1)
        val second = entry(2)
        val otherService = first.copy(serviceId = 1)
        val otherChannel = entry(3, channel = 3)
        post(listOf(first, second, otherService, otherChannel))
        notification(StickyNotificationAction.OPEN_CHANNEL, first.channelUrl, serviceId = 0).actions.first().actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(otherService, otherChannel), dao.pending(profile, generation).toSet())
        notification(StickyNotificationAction.OPEN_CHANNEL, first.channelUrl, serviceId = 1).deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(otherChannel), dao.pending(profile, generation))
    }

    @Test
    fun overflowSwipeAcknowledgesTheAdvertisedTotalButPreservesOtherProfiles() {
        val other = requireNotNull(ProfileManager.createProfile(context, "Second"))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        val otherItem = entry(100).copy(profileId = other.id)
        val items = (0 until 45).map { entry(it, channel = it) }
        dao.insert(listOf(otherItem))
        post(items)
        assertTrue(manager.activeNotifications.size < items.size)
        val overflow = notification(StickyNotificationAction.OPEN_FEED)
        assertEquals(items.size, overflow.number)
        assertEquals(context.getString(R.string.sticky_notification_all_pending), overflow.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(overflow.deleteIntent, overflow.actions.single().actionIntent)
        assertEquals(overflow.deleteIntent, notification(StickyNotificationAction.OPEN_CHANNEL).actions.last().actionIntent)
        overflow.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dao.pending(profile, generation).isEmpty())
        assertEquals(listOf(otherItem), dao.pending(other.id, generation))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    @OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
    fun closeCompletesWhileArtworkIsHeldAndReleasedArtworkCannotResurrectTheAlert() {
        val item = entry().copy(thumbnailUrl = "https://example.com/thumbnail.jpg")
        post(listOf(item))
        val imageStarted = CountDownLatch(1)
        val imageFinished = CountDownLatch(1)
        val releaseImage = CompletableDeferred<Unit>()
        val imageLoader = ImageLoader.Builder(context)
            .mainCoroutineContext(Dispatchers.Unconfined)
            .coroutineContext(Dispatchers.Unconfined)
            .components {
                add(
                    Interceptor { chain ->
                        imageStarted.countDown()
                        try {
                            // The fixture controls response completion independently of the
                            // production image timeout, so this checks ordering rather than speed.
                            withContext(NonCancellable) { releaseImage.await() }
                            SuccessResult(Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).asImage(), chain.request)
                        } finally {
                            imageFinished.countDown()
                        }
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        val executor = Executors.newFixedThreadPool(2)
        val render = executor.submit {
            StickyNotificationCoordinator.reconcile(context, silent = false, alertChannels = setOf(item.serviceId to item.channelUrl))
        }
        try {
            assertTrue("The real Coil request must reach its controlled response", imageStarted.await(5, TimeUnit.SECONDS))
            val close = executor.submit {
                StickyNotificationCoordinator.closeItem(context, profile, generation, item.serviceId, item.streamUrl).blockingAwait()
            }
            close.get(5, TimeUnit.SECONDS)
            assertEquals("Close must complete before the held request finishes or times out", 1L, imageFinished.count)
            assertFalse(releaseImage.isCompleted)
            assertTrue(dao.pending(profile, generation).isEmpty())
            assertTrue(manager.activeNotifications.isEmpty())
        } finally {
            releaseImage.complete(Unit)
            try {
                render.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
                imageLoader.shutdown()
                SingletonImageLoader.reset()
                ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
            }
        }
        assertTrue(dao.pending(profile, generation).isEmpty())
        assertTrue("The late image response cannot repost the closed alert", manager.activeNotifications.isEmpty())
    }

    @Test
    fun switchingProfilesMakesCapturedOpenAndCloseActionsInert() {
        val item = entry()
        post(listOf(item))
        val child = notification(StickyNotificationAction.OPEN_ITEM)
        val summary = notification(StickyNotificationAction.OPEN_CHANNEL)
        val other = requireNotNull(ProfileManager.createProfile(context, "Second"))
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        assertNull(open(child.contentIntent))
        summary.actions.last().actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(item), dao.pending(profile, generation))
        assertEquals(other.id, ProfileManager.getActiveProfileId(context))
    }

    @Test
    fun staleGenerationCannotOpenOrCloseAnAlertWithTheSameStreamIdentity() {
        val item = entry()
        post(listOf(item))
        val oldChild = notification(StickyNotificationAction.OPEN_ITEM)
        val fresh = item.copy(generation = "later-session")
        preferences.edit().putString(StickyNotificationSettings.GENERATION_KEY, fresh.generation).commit()
        dao.insert(listOf(fresh))
        StickyNotificationRenderer.render(context, profile, listOf(fresh), silent = true)
        val newChild = notification(StickyNotificationAction.OPEN_ITEM)
        assertFalse(oldChild.contentIntent == newChild.contentIntent)
        assertFalse(oldChild.deleteIntent == newChild.deleteIntent)
        assertNull(open(oldChild.contentIntent))
        oldChild.deleteIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(fresh), dao.pending(profile, fresh.generation))
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun turningTheFeatureOffPreventsCapturedContentNavigation() {
        post(listOf(entry()))
        val content = notification(StickyNotificationAction.OPEN_ITEM).contentIntent
        preferences.edit().putBoolean(StickyNotificationSettings.ENABLED_KEY, false).commit()
        assertNull(open(content))
        assertEquals(profile, ProfileManager.getActiveProfileId(context))
    }

    @Test
    fun currentKidsProfileCanOpenItsOwnAlertsWithoutUnlockingParentControls() {
        context.getSharedPreferences(KidsMode.PREFERENCES, 0).edit().putStringSet("profiles", setOf(profile)).commit()
        assertTrue(KidsMode.requiresParentAccess(context))
        post(listOf(entry()))
        val destination = requireNotNull(open(notification(StickyNotificationAction.OPEN_ITEM).contentIntent))
        assertEquals(entry().streamUrl, destination.getStringExtra(KEY_URL))
        assertTrue(KidsMode.requiresParentAccess(context))
        assertEquals(profile, ProfileManager.getActiveProfileId(context))
        assertEquals(1, dao.pending(profile, generation).size)
    }

    @Test
    fun malformedNotificationDataAndUnavailableServicesDoNotNavigateOrAcknowledge() {
        post(listOf(entry()))
        val valid = shadowOf(notification(StickyNotificationAction.OPEN_ITEM).contentIntent).savedIntent
        val invalid = Intent(valid).apply { data = data!!.buildUpon().scheme("https").build() }
        assertNull(openActivity(invalid))
        val unsupported = StickyNotificationTarget(StickyNotificationAction.OPEN_ITEM, profile, generation, Int.MAX_VALUE, "https://example.com/item")
        assertNull(open(unsupported.contentIntent(context)))
        val close = Intent(shadowOf(notification(StickyNotificationAction.OPEN_ITEM).deleteIntent).savedIntent).apply {
            data = data!!.buildUpon().appendQueryParameter("profile", "different").build()
        }
        context.sendBroadcast(close)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, dao.pending(profile, generation).size)
    }

    private fun post(items: List<PendingStreamNotification>) {
        dao.insert(items)
        StickyNotificationRenderer.render(context, profile, dao.pending(profile, generation), silent = true)
    }

    private fun notification(action: StickyNotificationAction, url: String? = null, serviceId: Int? = null): Notification = manager.activeNotifications.first {
        val target = requireNotNull(StickyNotificationTarget.fromIntent(shadowOf(it.notification.contentIntent).savedIntent))
        target.action == action && (url == null || target.url == url) && (serviceId == null || target.serviceId == serviceId)
    }.notification

    private fun open(pending: PendingIntent): Intent? {
        pending.send()
        val intent = requireNotNull(shadowOf(context).nextStartedActivity)
        assertEquals(StickyNotificationActivity::class.java.name, intent.component?.className)
        return openActivity(intent)
    }

    private fun openActivity(intent: Intent): Intent? {
        val controller = Robolectric.buildActivity(StickyNotificationActivity::class.java, intent).create()
        return try {
            assertTrue(controller.get().isFinishing)
            shadowOf(controller.get()).nextStartedActivity
        } finally {
            controller.destroy()
        }
    }

    private fun entry(index: Int = 1, channel: Int = 1): PendingStreamNotification = PendingStreamNotification(
        profileId = profile,
        generation = generation,
        serviceId = 0,
        streamUrl = "https://example.com/watch/$index",
        channelUrl = "https://example.com/channel/$channel",
        channelName = "Channel $channel",
        streamTitle = "Title $index",
        thumbnailUrl = null,
        createdAt = 1_000L + index
    )
}
