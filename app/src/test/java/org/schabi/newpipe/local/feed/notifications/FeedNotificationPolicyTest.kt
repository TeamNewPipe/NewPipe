package org.schabi.newpipe.local.feed.notifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FeedNotificationPolicyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private lateinit var database: AppDatabase
    private lateinit var policy: FeedNotificationPolicy
    private lateinit var subscription: SubscriptionEntity
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        subscription = SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Channel", notificationMode = NotificationMode.ENABLED)
            .apply { uid = database.subscriptionDAO().insert(this) }
        policy = FeedNotificationPolicy(context, profile)
        notifications.createNotificationChannel(
            NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
        )
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        databaseField.set(null, previousDatabase)
        database.close()
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.MEDIUM)
    }

    @Test
    fun muteAfterPersistenceSuppressesAnAlreadyCollectedUpdateWithoutChangingAlertSettings() {
        val update = update()
        assertTrue(policy.prepare(update))
        mute(true)
        assertFalse(policy.prepare(update))
        assertFalse(policy.canNotify(update.uid))
        assertEquals(NotificationMode.ENABLED, database.subscriptionDAO().getSubscriptionForProfile(profile, subscription.uid)!!.notificationMode)
        mute(false)
        assertTrue(policy.prepare(update))
        NotificationHelper(context) { _, _, _ -> null }.displayNewStreamsNotifications(update) { policy.canNotify(update.uid) }
        assertEquals(2, shadowOf(notifications).allNotifications.size)
    }

    @Test
    fun mutingDuringAvatarOrThumbnailLoadingStopsChildrenAndSummaryBeforeTheyArePosted() {
        for (muteOnLoad in listOf(1, 2)) {
            mute(false)
            val update = update()
            assertTrue(policy.prepare(update))
            var loads = 0
            val helper = NotificationHelper(context) { _, _, _ ->
                loads++
                if (loads == muteOnLoad) mute(true)
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            }
            helper.displayNewStreamsNotifications(update) { policy.canNotify(update.uid) }
            assertEquals(muteOnLoad, loads)
            assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        }
    }

    @Test
    fun currentKeywordsUnsubscribeAndProfileChangesOverrideTheFetchSnapshot() {
        val update = update()
        database.subscriptionDAO().setNotificationSettingsForProfile(profile, subscription.uid, NotificationMode.KEYWORDS_ONLY, "unrelated")
        assertFalse(policy.prepare(update))
        database.subscriptionDAO().setNotificationSettingsForProfile(profile, subscription.uid, NotificationMode.ENABLED, "saved")
        assertTrue(policy.prepare(update()))
        val other = requireNotNull(ProfileManager.createProfile(context, "Other"))
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        assertFalse(policy.canNotify(update.uid))
        assertFalse(policy.prepare(update()))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        database.subscriptionDAO().delete(subscription)
        assertFalse(policy.canNotify(update.uid))
        assertFalse(policy.prepare(update()))
    }

    private fun mute(value: Boolean) {
        database.subscriptionDAO().setFeedMutedForProfile(profile, listOf(subscription.uid), value)
    }

    private fun update(): FeedUpdateInfo {
        val item = StreamInfoItem(0, "https://example.com/video", "New video", StreamType.VIDEO_STREAM).apply {
            thumbnailUrl = "https://example.com/thumbnail.jpg"
            uploaderName = "Channel"
        }
        return FeedUpdateInfo(
            subscription.uid, NotificationMode.ENABLED, "", "Channel", "https://example.com/avatar.jpg",
            subscription.url!!, 0, 1, null, null, listOf(item), emptyList()
        ).apply { newStreams = streams }
    }
}
