package org.schabi.newpipe.local.feed.notifications

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.reset
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.CrashRecoveryManager
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileDeletionManager
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SuppressLint("RestrictedApi") // Keep scheduler assertions local; the real coordinator/Room still run.
class StickyNotificationPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private lateinit var database: AppDatabase
    private lateinit var subscription: SubscriptionEntity
    private lateinit var workManager: WorkManagerImpl
    private var previousDatabase: Any? = null
    private var previousWorkManager: WorkManagerImpl? = null
    private val pauseRestoreQuery = AtomicBoolean()
    private val restoreThread = AtomicReference<Thread?>()
    private val restoreReachedDatabase = CountDownLatch(1)
    private val releaseRestore = CountDownLatch(1)
    private val profileId = ProfileManager.DEFAULT_PROFILE_ID
    private val generation = "first-session"

    @Before
    fun setUp() {
        StickyNotificationCoordinator.resetForTests()
        preferences.edit().clear().commit()
        CrashRecoveryManager.keepCurrentPreferences(context)
        ProfileManager.getActiveProfileId(context)
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        previousWorkManager = WorkManagerImpl.getInstance()
        workManager = mock(WorkManagerImpl::class.java)
        WorkManagerImpl.setDelegate(workManager)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .setQueryCallback(
                { sql, _ ->
                    if (Thread.currentThread() === restoreThread.get() && sql.startsWith("BEGIN") &&
                        pauseRestoreQuery.compareAndSet(true, false)
                    ) {
                        restoreReachedDatabase.countDown()
                        check(releaseRestore.await(5, TimeUnit.SECONDS)) { "Restore race fixture was not released" }
                    }
                },
                { it.run() }
            )
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        subscription = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel",
            name = "Channel",
            notificationMode = NotificationMode.ENABLED
        )
        subscription.uid = database.subscriptionDAO().insert(subscription)
        preferences.edit().putBoolean(context.getString(R.string.enable_streams_notifications), true)
            .putBoolean(StickyNotificationSettings.ENABLED_KEY, true)
            .putString(StickyNotificationSettings.GENERATION_KEY, generation).commit()
        notifications.createNotificationChannel(
            NotificationChannel(context.getString(R.string.streams_notification_channel_id), "Streams", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    @After
    fun tearDown() {
        releaseRestore.countDown()
        StickyNotificationCoordinator.resetForTests()
        databaseField.set(null, previousDatabase)
        database.close()
        notifications.cancelAll()
        WorkManagerImpl.setDelegate(previousWorkManager)
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun committedAlertsSurviveFeedPruningAndClosedIdentitiesCannotReopenAfterRefetch() {
        val update = update("Retained video")
        store(manager(), update)
        assertEquals(listOf("Retained video"), pending().map { it.streamTitle })
        FeedDatabaseManager(context, profileId).clear()
        assertFalse(database.streamDAO().exists(0, update.streams.single().url))
        assertEquals(1, pending().size)
        store(manager(), update)
        assertEquals(1, pending().size)

        StickyNotificationCoordinator.closeItem(context, profileId, generation, 0, update.streams.single().url).blockingAwait()
        FeedDatabaseManager(context, profileId).clear()
        store(manager(), update)
        assertTrue(pending().isEmpty())
        database.query("SELECT dismissed, stream_title, channel_name, thumbnail_url FROM pending_stream_notifications", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun feedTransactionFailureRollsBackItsAlertWithoutLosingEarlierChannels() {
        store(manager(), update("Earlier"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_feed_update BEFORE INSERT ON feed_last_updated " +
                "BEGIN SELECT RAISE(ABORT, 'fixture rollback'); END"
        )
        val failed = update("Rejected")
        manager().storeResults(Flowable.just(Notification.createOnNext(failed))).test()
            .assertError { it.message.orEmpty().contains("fixture rollback") }
        assertEquals(listOf("Earlier"), pending().map { it.streamTitle })
        assertFalse(database.streamDAO().exists(0, failed.streams.single().url))
    }

    @Test
    fun onlySuccessfullyCommittedNewInsertionsBecomeAudibleChannelCandidates() {
        val session = requireNotNull(StickyNotificationSettings.capture(context, profileId))
        val firstCapture = StickyNotificationCapture(context, session)
        val first = update("Newly committed")
        store(FeedLoadManager(context, profileId, firstCapture::persist), first)
        assertEquals(setOf(0 to requireNotNull(subscription.url)), firstCapture.newlyInsertedChannels())

        val repeatedCapture = StickyNotificationCapture(context, session)
        store(FeedLoadManager(context, profileId, repeatedCapture::persist), first)
        assertTrue(repeatedCapture.newlyInsertedChannels().isEmpty())

        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_feed_update BEFORE INSERT ON feed_last_updated " +
                "BEGIN SELECT RAISE(ABORT, 'fixture rollback'); END"
        )
        val failedCapture = StickyNotificationCapture(context, session)
        FeedLoadManager(context, profileId, failedCapture::persist)
            .storeResults(Flowable.just(Notification.createOnNext(update("Never committed")))).test()
            .assertError { it.message.orEmpty().contains("fixture rollback") }
        assertTrue(failedCapture.newlyInsertedChannels().isEmpty())
        assertEquals(listOf("Newly committed"), pending().map { it.streamTitle })
    }

    @Test
    fun ordinaryRefreshAndEnablingDoNotReconstructMissedAlerts() {
        val cached = update("Already cached")
        store(FeedLoadManager(context, profileId), cached)
        assertTrue(pending().isEmpty())
        store(manager(), cached)
        assertTrue(pending().isEmpty())
        store(manager(), update("Actually new"))
        assertEquals(listOf("Actually new"), pending().map { it.streamTitle })
    }

    @Test
    fun captureUsesCurrentNotificationModeAndKeywordsInsideTheFeedTransaction() {
        val inFlight = manager()
        subscription.notificationMode = NotificationMode.KEYWORDS_ONLY
        subscription.notificationKeywords = "selected"
        database.subscriptionDAO().update(subscription)
        store(inFlight, update("Unmatched", "Selected topic"))
        assertEquals(listOf("Selected topic"), pending().map { it.streamTitle })
        subscription.notificationMode = NotificationMode.DISABLED
        database.subscriptionDAO().update(subscription)
        store(manager(), update("Selected but now disabled"))
        assertEquals(1, pending().size)
    }

    @Test
    fun quickDisableEnableInvalidatesOldRequestsAndIntentsBeforeCleanupRuns() {
        val inFlight = manager()
        store(manager(), update("Old pending"))
        StickyNotificationSettings.setEnabled(context, false)
        StickyNotificationSettings.setEnabled(context, true)
        val nextGeneration = StickyNotificationSettings.generation(context)
        assertNotEquals(generation, nextGeneration)
        assertFalse(StickyNotificationCoordinator.canOpen(context, profileId, generation))
        store(inFlight, update("Late old result"))
        store(manager(), update("New session"))
        StickyNotificationCoordinator.reconcile(context)
        assertTrue(pending().isEmpty())
        assertEquals(listOf("New session"), pending(nextGeneration).map { it.streamTitle })
        StickyNotificationCoordinator.closeAll(context, profileId, generation).blockingAwait()
        assertEquals(1, pending(nextGeneration).size)
    }

    @Test
    fun disablingMasterAlsoDiscardsRetainedAlertsAndDoesNotReviveThemWhenEnabledAgain() {
        store(manager(), update("Old"))
        StickyNotificationSettings.setMasterEnabled(context, false)
        StickyNotificationCoordinator.reconcile(context)
        assertTrue(database.pendingStreamNotificationDAO().generations().isEmpty())
        StickyNotificationSettings.setMasterEnabled(context, true)
        StickyNotificationCoordinator.reconcile(context)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertTrue(pending(StickyNotificationSettings.generation(context)).isEmpty())
    }

    @Test
    fun staleProfileCloseIsRejectedAndMatchingUrlsRemainIsolatedAcrossServicesAndProfiles() {
        val other = requireNotNull(ProfileManager.createProfile(context, "Other"))
        assertTrue(ProfileManager.setActiveProfile(context, profileId))
        val first = entry("same-url")
        val differentService = first.copy(serviceId = 1)
        val differentProfile = first.copy(profileId = other.id)
        database.pendingStreamNotificationDAO().insert(listOf(first, differentService, differentProfile))
        StickyNotificationCoordinator.closeChannel(context, profileId, generation, 0, first.channelUrl).blockingAwait()
        assertEquals(listOf(1), pending().map { it.serviceId })
        assertEquals(1, database.pendingStreamNotificationDAO().pending(other.id, generation).size)
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        StickyNotificationCoordinator.closeAll(context, profileId, generation).blockingAwait()
        assertEquals(1, pending().size)
        StickyNotificationCoordinator.closeAll(context, other.id, generation).blockingAwait()
        StickyNotificationCoordinator.closeAll(context, other.id, generation).blockingAwait()
        assertTrue(database.pendingStreamNotificationDAO().pending(other.id, generation).isEmpty())
        assertEquals(1, pending().size)
    }

    @Test
    fun deletingProfilePurgesItsSnapshotsWithoutChangingOtherProfiles() {
        val other = requireNotNull(ProfileManager.createProfile(context, "To delete"))
        assertTrue(ProfileManager.setActiveProfile(context, profileId))
        database.pendingStreamNotificationDAO().insert(listOf(entry("kept"), entry("removed").copy(profileId = other.id)))
        ProfileDeletionManager.deleteProfile(context, other.id).blockingAwait()
        assertTrue(database.pendingStreamNotificationDAO().pending(other.id, generation).isEmpty())
        assertEquals(listOf("kept"), pending().map { it.streamTitle })
    }

    @Test
    fun restorationIsSilentIdempotentAndBlockedChannelKeepsThePendingRecord() {
        database.pendingStreamNotificationDAO().insert(listOf(entry("offline")))
        val worker = StickyNotificationRestoreWorker(context, mock(WorkerParameters::class.java))
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
        val originalCount = shadowOf(notifications).allNotifications.size
        assertTrue(originalCount > 0)
        assertTrue(shadowOf(notifications).allNotifications.all { it.sound == null })
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
        assertEquals(originalCount, shadowOf(notifications).allNotifications.size)
        val blockedChannel = requireNotNull(notifications.getNotificationChannel(context.getString(R.string.streams_notification_channel_id)))
        blockedChannel.importance = NotificationManager.IMPORTANCE_NONE
        notifications.createNotificationChannel(blockedChannel)
        assertFalse(NotificationHelper.areNotificationsEnabledOnDevice(context))
        StickyNotificationCoordinator.reconcile(context)
        assertEquals(1, pending().size)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun bootAndPackageReplacementScheduleAnOfflineRestoreOnly() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            reset(workManager)
            StickyNotificationBootReceiver().onReceive(context, Intent(action))
            // Read the real invocation: Java Mockito matchers return null, which cannot cross
            // WorkManager's Kotlin non-null request boundary during verification.
            val invocation = mockingDetails(workManager).invocations.single { it.method.name == "enqueueUniqueWork" }
            assertEquals(StickyNotificationCoordinator.RESTORE_WORK, invocation.arguments[0])
            assertEquals(ExistingWorkPolicy.REPLACE, invocation.arguments[1])
            val request = invocation.arguments[2] as OneTimeWorkRequest
            assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
            assertEquals(StickyNotificationRestoreWorker::class.java.name, request.workSpec.workerClassName)
            assertTrue(pending().isEmpty())
        }
    }

    @Test
    fun safeModeDoesNotMistakeTemporaryRecoveryDefaultsForOptingOut() {
        database.pendingStreamNotificationDAO().insert(listOf(entry("retained")))
        assertTrue(CrashRecoveryManager.requestSafeMode(context))
        assertTrue(CrashRecoveryManager.prepareStartup(context))
        StickyNotificationCoordinator.reconcile(context)
        assertEquals(listOf("retained"), pending().map { it.streamTitle })
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertTrue(CrashRecoveryManager.keepCurrentPreferences(context))
    }

    @Test
    fun cleanupWaitingForRoomCannotDeleteAnAlertFromANewlyEnabledGeneration() {
        database.pendingStreamNotificationDAO().insert(listOf(entry("old")))
        val failure = AtomicReference<Throwable?>()
        pauseRestoreQuery.set(true)
        val restore = Thread(
            {
                runCatching { StickyNotificationCoordinator.reconcile(context) }
                    .exceptionOrNull()?.let(failure::set)
            },
            "sticky-restore-race"
        ).apply {
            restoreThread.set(this)
            start()
        }
        try {
            assertTrue("Restore must reach Room before the off/on race", restoreReachedDatabase.await(5, TimeUnit.SECONDS))
            StickyNotificationSettings.setEnabled(context, false)
            StickyNotificationSettings.setEnabled(context, true)
            val newGeneration = StickyNotificationSettings.generation(context)
            database.pendingStreamNotificationDAO().insert(listOf(entry("new").copy(generation = newGeneration)))
            releaseRestore.countDown()
            restore.join(5_000)
            assertFalse("Restore must finish without a lock inversion", restore.isAlive)
            failure.get()?.let { throw AssertionError("Restore failed", it) }
            assertEquals(listOf("new"), pending(newGeneration).map { it.streamTitle })
            assertTrue(pending().isEmpty())
        } finally {
            releaseRestore.countDown()
            restore.join(5_000)
        }
    }

    private fun manager(): FeedLoadManager {
        val session = StickyNotificationSettings.capture(context, profileId)
        assertNotNull(session)
        return FeedLoadManager(context, profileId, StickyNotificationCapture(context, requireNotNull(session))::persist)
    }

    private fun pending(epoch: String = generation) = database.pendingStreamNotificationDAO().pending(profileId, epoch)

    private fun entry(title: String) = PendingStreamNotification(
        profileId, generation, 0, "https://example.com/$title", "https://example.com/channel", "Channel", title, null, 123L
    )

    private fun store(manager: FeedLoadManager, update: FeedUpdateInfo) {
        manager.storeResults(Flowable.just(Notification.createOnNext(update))).test().assertComplete().assertNoErrors()
    }

    private fun update(vararg titles: String) = FeedUpdateInfo(
        subscription.uid,
        NotificationMode.ENABLED,
        "",
        "Channel",
        null,
        requireNotNull(subscription.url),
        0,
        SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        null,
        null,
        titles.map { title ->
            StreamInfoItem(0, "https://example.com/${title.replace(' ', '_')}", title, StreamType.VIDEO_STREAM).apply {
                uploadDate = DateWrapper(OffsetDateTime.now().minusHours(1))
                uploaderName = "Channel"
                uploaderUrl = subscription.url
            }
        },
        emptyList()
    )
}
