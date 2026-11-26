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
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35], application = Application::class)
@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
class StickyNotificationRendererTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val generation = "renderer-session"
    private lateinit var imageLoader: ImageLoader
    private var imageRequests = 0
    private var onImageRequest: () -> Unit = {}

    @Before
    fun setup() {
        StickyNotificationCoordinator.resetForTests()
        preferences.edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, 0).edit().clear().commit()
        KidsMode.lock()
        ProfileManager.getActiveProfileId(context)
        preferences.edit()
            .putBoolean(context.getString(R.string.enable_streams_notifications), true)
            .putString(StickyNotificationSettings.GENERATION_KEY, generation)
            .commit()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        imageLoader = ImageLoader.Builder(context)
            .mainCoroutineContext(Dispatchers.Unconfined)
            .coroutineContext(Dispatchers.Unconfined)
            .components {
                add(
                    Interceptor { chain ->
                        imageRequests++
                        onImageRequest()
                        SuccessResult(Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).asImage(), chain.request)
                    }
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @After
    fun teardown() {
        manager.cancelAll()
        imageLoader.shutdown()
        SingletonImageLoader.reset()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
        StickyNotificationCoordinator.resetForTests()
        preferences.edit().clear().commit()
        context.getSharedPreferences(KidsMode.PREFERENCES, 0).edit().clear().commit()
        KidsMode.lock()
    }

    @Test
    fun stickyProjectionIsOptInAndDoesNotCancelOtherNotificationNamespaces() {
        val unrelated = NotificationCompat.Builder(context, context.getString(R.string.streams_notification_channel_id))
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white).setContentTitle("Playback").build()
        manager.notify("playback", 7, unrelated)
        manager.notify("wizestream.sticky-other", 8, unrelated)
        StickyNotificationRenderer.render(context, profile, listOf(entry()), silent = true)
        assertEquals(setOf("playback", "wizestream.sticky-other"), manager.activeNotifications.map { it.tag }.toSet())
        enable()
        StickyNotificationRenderer.render(context, profile, listOf(entry()), silent = true)
        assertEquals(4, manager.activeNotifications.size)
        StickyNotificationRenderer.cancelAll(context)
        assertEquals(setOf("playback", "wizestream.sticky-other"), manager.activeNotifications.map { it.tag }.toSet())
    }

    @Test
    fun silentCardsArePersistentWithoutImitatingPlaybackAndUseExplicitImmutableIntents() {
        enable()
        StickyNotificationRenderer.render(context, profile, listOf(entry()), silent = true)
        val posted = manager.activeNotifications
        assertEquals(2, posted.size)
        assertEquals(0, imageRequests)
        posted.forEach {
            val notification = it.notification
            assertTrue(notification.flags and Notification.FLAG_NO_CLEAR != 0)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
            assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
            assertEquals(0, notification.flags and Notification.FLAG_FOREGROUND_SERVICE)
            assertNull(notification.sound)
            assertNull(notification.vibrate)
            assertEquals(0, notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
            assertNull(notification.extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE))
            assertEquals(StickyNotificationActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component?.className)
            assertEquals(StickyNotificationActionReceiver::class.java.name, shadowOf(notification.deleteIntent).savedIntent.component?.className)
            assertTrue(shadowOf(notification.contentIntent).flags and PendingIntent.FLAG_IMMUTABLE != 0)
            assertTrue(shadowOf(notification.deleteIntent).flags and PendingIntent.FLAG_IMMUTABLE != 0)
        }
        val child = posted.single { target(it).action == StickyNotificationAction.OPEN_ITEM }.notification
        assertEquals("Title 1", child.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Channel 1", child.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf(context.getString(R.string.sticky_notification_close)), child.actions.map { it.title.toString() })
        val summary = posted.single { target(it).action == StickyNotificationAction.OPEN_CHANNEL }.notification
        assertEquals(child.group, summary.group)
        assertEquals(1, summary.number)
        assertEquals(
            listOf(context.getString(R.string.sticky_notification_close_channel), context.getString(R.string.sticky_notification_close_all)),
            summary.actions.map { it.title.toString() }
        )
    }

    @Test
    fun serviceAndFullUrlIdentityAvoidHashCollisionsAndRepeatedRenderReplacesCards() {
        enable()
        val first = entry().copy(streamUrl = "https://example.com/Aa")
        val second = entry(2).copy(streamUrl = "https://example.com/BB")
        assertEquals(first.streamUrl.hashCode(), second.streamUrl.hashCode())
        val otherService = first.copy(serviceId = 1)
        StickyNotificationRenderer.render(context, profile, listOf(first, second, otherService), silent = true)
        val initial = manager.activeNotifications
        val children = initial.filter { target(it).action == StickyNotificationAction.OPEN_ITEM }
        assertEquals(3, children.size)
        assertEquals(3, children.map { it.notification.contentIntent }.toSet().size)
        assertEquals(2, initial.filter { target(it).action == StickyNotificationAction.OPEN_CHANNEL }.map { it.notification.group }.toSet().size)
        assertEquals(setOf(0, 1), children.map { target(it).serviceId }.toSet())
        StickyNotificationRenderer.render(context, profile, listOf(first.copy(streamTitle = "Updated"), second, otherService), silent = true)
        assertEquals(initial.map { it.tag }.toSet(), manager.activeNotifications.map { it.tag }.toSet())
        assertEquals(
            "Updated",
            manager.activeNotifications.single { target(it).serviceId == 0 && target(it).url == first.streamUrl }
                .notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
    }

    @Test
    fun boundedCardsCountEveryPendingStreamIncludingChannelsOutsideTheVisibleWindow() {
        enable()
        val entries = (0 until 25).flatMap { channel -> (0 until 3).map { stream -> entry(channel * 3 + stream, channel = channel) } }
        StickyNotificationRenderer.render(context, profile, entries, silent = true)
        val posted = manager.activeNotifications
        assertEquals(StickyNotificationRenderer.MAX_VISIBLE_STREAMS, posted.count { target(it).action == StickyNotificationAction.OPEN_ITEM })
        assertTrue(posted.size <= StickyNotificationRenderer.MAX_VISIBLE_STREAMS * 2 + 1)
        val summaries = posted.filter { target(it).action != StickyNotificationAction.OPEN_ITEM }
        val overflow = summaries.single { target(it).action == StickyNotificationAction.OPEN_FEED }
        assertEquals(entries.size, overflow.notification.number)
        assertEquals(context.getString(R.string.sticky_notification_all_pending), overflow.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(context.resources.getQuantityString(R.plurals.new_streams, 75, 75), overflow.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(
            overflow.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
                .contains(context.resources.getQuantityString(R.plurals.sticky_notification_streams_in_other_channels, 54, 54))
        )
        assertEquals(StickyNotificationAction.CLOSE_ALL, requireNotNull(StickyNotificationTarget.fromIntent(shadowOf(overflow.notification.deleteIntent).savedIntent)).action)
        summaries.filter { target(it).action == StickyNotificationAction.OPEN_CHANNEL }.forEach {
            assertEquals(3, it.notification.number)
            assertEquals(3, it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size)
        }
    }

    @Test
    fun allPendingWithinOneLargeChannelRemainsCountedWhenOnlySomeCardsAreVisible() {
        enable()
        StickyNotificationRenderer.render(context, profile, (0 until 50).map { entry(it) }, silent = true)
        val posted = manager.activeNotifications
        assertEquals(StickyNotificationRenderer.MAX_VISIBLE_STREAMS + 1, posted.size)
        val summary = posted.single { target(it).action == StickyNotificationAction.OPEN_CHANNEL }.notification
        assertEquals(50, summary.number)
        assertEquals(6, summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size)
    }

    @Test
    @Config(sdk = [35])
    fun onlyChannelsWithNewlyInsertedAlertsMaySoundAndLaterUploadsMayAlertAgain() {
        enable()
        val old = entry(1, channel = 1)
        val fresh = entry(2, channel = 2)
        StickyNotificationRenderer.render(context, profile, listOf(old), silent = true)
        renderNew(listOf(old, fresh), setOf(fresh.serviceId to fresh.channelUrl))
        val summaries = manager.activeNotifications.filter { target(it).action == StickyNotificationAction.OPEN_CHANNEL }
        val oldSummary = summaries.single { target(it).url == old.channelUrl }.notification
        val newSummary = summaries.single { target(it).url == fresh.channelUrl }.notification
        assertEquals(NotificationCompat.GROUP_ALERT_CHILDREN, oldSummary.groupAlertBehavior)
        assertTrue(oldSummary.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(NotificationCompat.GROUP_ALERT_SUMMARY, newSummary.groupAlertBehavior)
        assertEquals(0, newSummary.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals("Only the newly eligible channel requests artwork", 1, imageRequests)
        // The old channel can alert again when a later fetch actually inserts another stream.
        renderNew(listOf(old, fresh, entry(3, channel = 1)), setOf(old.serviceId to old.channelUrl))
        val updatedOld = manager.activeNotifications.single {
            target(it).action == StickyNotificationAction.OPEN_CHANNEL && target(it).url == old.channelUrl
        }.notification
        assertEquals(2, updatedOld.number)
        assertEquals(NotificationCompat.GROUP_ALERT_SUMMARY, updatedOld.groupAlertBehavior)
        assertEquals(0, updatedOld.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(3, imageRequests)
        // Merely restoring a disappeared OS card must not replay either channel's old alert.
        manager.cancelAll()
        StickyNotificationRenderer.render(context, profile, listOf(old, fresh), silent = false)
        assertEquals("An unchanged backlog does not trigger artwork requests", 3, imageRequests)
        assertTrue(
            manager.activeNotifications.filter { target(it).action == StickyNotificationAction.OPEN_CHANNEL }.all {
                it.notification.groupAlertBehavior == NotificationCompat.GROUP_ALERT_CHILDREN
            }
        )
    }

    @Test
    fun profileProjectionReplacesInactiveCardsAndCancelProfileUsesCapturedIdentity() {
        enable()
        StickyNotificationRenderer.render(context, profile, listOf(entry()), silent = true)
        val old = manager.activeNotifications.single { target(it).action == StickyNotificationAction.OPEN_ITEM }
        val other = requireNotNull(ProfileManager.createProfile(context, "Second"))
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        StickyNotificationRenderer.render(context, other.id, listOf(entry().copy(profileId = other.id)), silent = true)
        assertEquals(2, manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.all { target(it).profileId == other.id })
        val current = manager.activeNotifications.single { target(it).action == StickyNotificationAction.OPEN_ITEM }
        assertNotEquals(old.tag, current.tag)
        assertNotEquals(old.notification.contentIntent, current.notification.contentIntent)
        StickyNotificationRenderer.cancelProfile(context, profile)
        assertEquals(2, manager.activeNotifications.size)
        StickyNotificationRenderer.cancelProfile(context, other.id)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun obsoleteGenerationDismissedRecordsAndOtherProfilesAreNeverProjected() {
        enable()
        val other = requireNotNull(ProfileManager.createProfile(context, "Second"))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        StickyNotificationRenderer.render(
            context,
            profile,
            listOf(entry(), entry(2).copy(generation = "old"), entry(3).copy(dismissed = true), entry(4).copy(profileId = other.id)),
            silent = true
        )
        assertEquals(2, manager.activeNotifications.size)
        assertEquals(entry().streamUrl, target(manager.activeNotifications.single { target(it).action == StickyNotificationAction.OPEN_ITEM }).url)
        StickyNotificationRenderer.render(context, profile, emptyList(), silent = true)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun changingGenerationWhileArtworkLoadsPreventsPostingTheOldSession() {
        enable()
        onImageRequest = { preferences.edit().putString(StickyNotificationSettings.GENERATION_KEY, "new-session").commit() }
        renderNew(listOf(entry()), setOf(entry().serviceId to entry().channelUrl))
        assertEquals(1, imageRequests)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun changingProfileWhileArtworkLoadsPreventsPostingTheOldProfile() {
        enable()
        val other = requireNotNull(ProfileManager.createProfile(context, "Second"))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        onImageRequest = { assertTrue(ProfileManager.setActiveProfile(context, other.id)) }
        renderNew(listOf(entry()), setOf(entry().serviceId to entry().channelUrl))
        assertEquals(1, imageRequests)
        assertTrue(manager.activeNotifications.isEmpty())
        assertFalse(ProfileManager.getActiveProfileId(context) == profile)
    }

    private fun enable() {
        preferences.edit().putBoolean(StickyNotificationSettings.ENABLED_KEY, true).commit()
    }

    private fun renderNew(pending: List<PendingStreamNotification>, alertChannels: Set<Pair<Int, String>>) {
        val artwork = StickyNotificationRenderer.prepareArtwork(context, profile, pending, alertChannels)
        StickyNotificationRenderer.render(context, profile, pending, silent = false, alertChannels, artwork)
    }

    private fun entry(index: Int = 1, channel: Int = 1): PendingStreamNotification = PendingStreamNotification(
        profileId = profile,
        generation = generation,
        serviceId = 0,
        streamUrl = "https://example.com/watch/$index",
        channelUrl = "https://example.com/channel/$channel",
        channelName = "Channel $channel",
        streamTitle = "Title $index",
        thumbnailUrl = "https://example.com/thumbnail/$index.jpg",
        createdAt = 1_000L + index
    )

    private fun target(notification: StatusBarNotification): StickyNotificationTarget = requireNotNull(StickyNotificationTarget.fromIntent(shadowOf(notification.notification.contentIntent).savedIntent))
}
