package org.schabi.newpipe.learning

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase

class LearningRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var database: AppDatabase
    private lateinit var study: String
    private lateinit var other: String
    private lateinit var originalProfile: String
    private var originalMaster: Boolean? = null
    private var originalTabs: String? = null

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        originalProfile = ProfileManager.getActiveProfileId(context)
        originalMaster = prefs.all[context.getString(R.string.learning_mode_key)] as? Boolean
        originalTabs = prefs.getString(context.getString(R.string.saved_tabs_key), null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        study = ProfileManager.createProfile(context, "Study ${UUID.randomUUID().toString().take(8)}")!!.id
        other = ProfileManager.createProfile(context, "Other ${UUID.randomUUID().toString().take(8)}")!!.id
        assertFalse(LearningMode.isProfileIncluded(context, study))
        prefs.edit().putBoolean(context.getString(R.string.learning_mode_key), true).commit()
        LearningMode.setProfileIncluded(context, study, true)
        LearningReminders.setEnabled(context, study, true)
        LearningReminders.setEnabled(context, other, true)
        // Drive time explicitly below; no content exists while automatic work is being canceled.
        WorkManager.getInstance(context).cancelUniqueWork("learning_daily_reminders").result.get(5, TimeUnit.SECONDS)
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
    }

    @After
    fun cleanup() {
        listOf(study, other).forEach { id ->
            LearningReminders.cancelNotification(context, id)
            ProfileManager.deleteProfile(context, id)
            prefs.edit().apply {
                prefs.all.keys.filter { it.contains(id) && it.startsWith("learning_") }.forEach { remove(it) }
            }.commit()
        }
        ProfileManager.setActiveProfile(context, originalProfile)
        prefs.edit().apply {
            val masterKey = context.getString(R.string.learning_mode_key)
            originalMaster?.let { putBoolean(masterKey, it) } ?: remove(masterKey)
            putString(context.getString(R.string.saved_tabs_key), originalTabs)
        }.commit()
        LearningReminders.initialize(context)
        database.close()
    }

    @Test
    fun remindersRespectProfilesAndStillArriveAfterStudyingToday() {
        val stream = addPlaylist(study)
        assertTrue(database.learningContentDAO().hasLearningPlaylists(study))
        assertFalse(database.learningContentDAO().hasLearningPlaylists(other))
        addPlaylist(other)
        val now = LocalDateTime.of(2026, 9, 30, 12, 0)
        database.learningSessionDAO().upsert(
            LearningSessionEntity("already-studied", stream, 0, 60_000, 60_000, now.toLocalDate().toString(), false, true, study)
        )
        LearningReminders.sendDueReminders(context, now)
        awaitCondition { notificationCount(study) == 1 }
        assertEquals(0, notificationCount(other))
        LearningReminders.cancelNotification(context, study)
        awaitCondition { notificationCount(study) == 0 }
        LearningReminders.sendDueReminders(context, now.plusHours(1))
        assertEquals(0, notificationCount(study))
        LearningReminders.sendDueReminders(context, now.plusDays(1))
        awaitCondition { notificationCount(study) == 1 }
        LearningMode.setProfileIncluded(context, study, false)
        awaitCondition { notificationCount(study) == 0 }
        LearningReminders.sendDueReminders(context, now.plusDays(2))
        assertEquals(0, notificationCount(study))
    }

    @Test
    fun disabledAndDeletedProfilesCannotBeSelectedByStaleNotifications() {
        ProfileManager.setActiveProfile(context, study)
        val excludedIntent = LearningReminders.intent(context, other)
        assertFalse(LearningReminders.selectProfile(context, excludedIntent))
        assertFalse(excludedIntent.hasExtra(LearningReminders.EXTRA_PROFILE_ID))
        assertEquals(study, ProfileManager.getActiveProfileId(context))
        val deletedIntent = LearningReminders.intent(context, other)
        ProfileManager.deleteProfile(context, other)
        assertFalse(LearningReminders.selectProfile(context, deletedIntent))
        assertEquals(study, ProfileManager.getActiveProfileId(context))
    }

    @Test(timeout = 45_000)
    fun notificationRoutesColdAndRunningActivityToTheRightProfileWithoutRecordingAStreak() {
        ActivityScenario.launch<MainActivity>(LearningReminders.intent(context, study)).use { scenario ->
            try {
                awaitDashboard(scenario, study)
                LearningMode.setProfileIncluded(context, other, true)
                val destination = LearningReminders.intent(context, other)
                assertFalse(destination.filterEquals(LearningReminders.intent(context, study)))
                // ActivityScenario tracks lifecycle events by intent data. Keep its launch
                // identity while changing the profile extra used by production routing.
                destination.data = LearningReminders.intent(context, study).data
                onActivity(scenario) { it.startActivity(destination) }
                awaitDashboard(scenario, other)
                assertTrue(database.learningDashboardDAO().observeDailyStudyActivity(study).blockingFirst().isEmpty())
                assertTrue(database.learningDashboardDAO().observeDailyStudyActivity(other).blockingFirst().isEmpty())
            } finally {
                onActivity(scenario) { it.finish() }
            }
        }
    }

    private fun addPlaylist(profile: String): Long {
        val streamId = database.streamDAO().insert(StreamEntity(serviceId = 0, url = "https://example.com/$profile", title = "Lesson", streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Teacher"))
        val playlistId = database.playlistDAO().insert(PlaylistEntity(name = "Lessons", isThumbnailPermanent = false, thumbnailStreamId = streamId, displayIndex = 0, profileId = profile))
        database.playlistStreamDAO().insert(PlaylistStreamEntity(playlistId, streamId, 0))
        database.learningContentDAO().upsertSource(LearningContentSourceEntity("local:$playlistId", LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST, localPlaylistId = playlistId))
        return streamId
    }

    private fun notificationCount(profile: String): Int {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            try {
                return notifications.activeNotifications.count { it.tag?.endsWith(profile) == true }
            } catch (error: NullPointerException) {
                val origin = error.stackTrace.firstOrNull()
                if (
                    Build.VERSION.SDK_INT != Build.VERSION_CODES.M ||
                    origin?.className != NotificationManager::class.java.name ||
                    origin?.methodName != "getActiveNotifications"
                ) {
                    throw error
                }
                // Android 6 reads the notification count before locking its list. Concurrent
                // cancellation can fail the Binder reply and cause this framework NPE. Require
                // a successful snapshot; an unreadable notification list must never mean zero.
                if (SystemClock.uptimeMillis() >= deadline) {
                    throw AssertionError("Android 6 could not return an active notification snapshot", error)
                }
                SystemClock.sleep(50)
            }
        }
    }

    private fun awaitDashboard(scenario: ActivityScenario<MainActivity>, profile: String) {
        awaitCondition {
            var ready = false
            onActivity(scenario) { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                ready = ProfileManager.getActiveProfileId(context) == profile &&
                    activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is LearningDashboardFragment &&
                    !activity.intent.hasExtra(LearningReminders.EXTRA_PROFILE_ID)
            }
            ready
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            if (condition()) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Condition did not become true")
    }

    private fun onActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity { action(it) }
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("Activity callback did not complete", completed.await(10, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
}
