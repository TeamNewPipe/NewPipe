package org.schabi.newpipe.local.feed

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_ALL_ID
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_UNGROUPED_ID
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MutedFeedRefreshTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val now = OffsetDateTime.parse("2026-11-26T12:00:00Z")
    private val all = FeedScope(FeedScope.ALL_SERVICES, 0)
    private val regular = FeedScope(0, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
    private lateinit var database: AppDatabase
    private lateinit var feed: FeedDatabaseManager
    private val dao get() = database.feedDAO()
    private val subscriptions get() = database.subscriptionDAO()
    private val groups get() = database.feedGroupDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(context, 0)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        feed = FeedDatabaseManager(context, profile)
    }

    @After
    fun tearDown() {
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun allCandidateQueriesRespectMuteAlongsideProfilesGroupsServicesModesAndAlerts() {
        val youtube = channel("Regular")
        val music = channel("Music", modes = 2)
        val sound = channel("Sound", service = 1)
        val muted = channel("Muted", muted = true, modes = 3)
        channel("Muted sound", muted = true, service = 1)
        val foreign = channel("Foreign", profileId = "other")
        val group = group(listOf(youtube.uid, music.uid, muted.uid))
        val expected = setOf(youtube.uid, music.uid, sound.uid)

        assertEquals(expected, dao.getAllOutdated(profile, now).blockingFirst().ids())
        assertEquals(expected, dao.getAllOutdatedForScope(profile, -1, 0, now).blockingFirst().ids())
        assertEquals(setOf(youtube.uid), dao.getAllOutdatedForScope(profile, 0, 1, now).blockingFirst().ids())
        assertEquals(setOf(music.uid), dao.getAllOutdatedForScope(profile, 0, 2, now).blockingFirst().ids())
        assertEquals(setOf(sound.uid), dao.getAllOutdatedForScope(profile, 1, 1, now).blockingFirst().ids())
        assertEquals(setOf(youtube.uid, music.uid), dao.getAllOutdatedForGroup(profile, group, now).blockingFirst().ids())
        assertEquals(setOf(music.uid), dao.getAllOutdatedForGroupAndScope(profile, group, 0, 2, now).blockingFirst().ids())
        assertEquals(setOf(sound.uid), dao.getAllOutdatedForGroup(profile, GROUP_UNGROUPED_ID, now).blockingFirst().ids())
        assertEquals(setOf(sound.uid), dao.getAllOutdatedForGroupAndScope(profile, GROUP_UNGROUPED_ID, -1, 0, now).blockingFirst().ids())
        assertEquals(expected, dao.getOutdatedWithNotificationModes(profile, now, listOf(NotificationMode.ENABLED)).blockingFirst().ids())
        assertEquals(setOf(foreign.uid), dao.getAllOutdated("other", now).blockingFirst().ids())

        // Unmuting re-enables every saved mode without changing the group or alert preference.
        subscriptions.setFeedMutedForProfile(profile, listOf(muted.uid), false)
        assertEquals(setOf(youtube.uid, muted.uid), dao.getAllOutdatedForGroupAndScope(profile, group, 0, 1, now).blockingFirst().ids())
        assertEquals(setOf(music.uid, muted.uid), dao.getAllOutdatedForGroupAndScope(profile, group, 0, 2, now).blockingFirst().ids())
        assertEquals(NotificationMode.ENABLED, subscriptions.getSubscriptionForProfile(profile, muted.uid)!!.notificationMode)
    }

    @Test
    fun refreshCountersIgnoreMutedChannelsWhileCachedItemsAndAllExclusionsStayIndependent() {
        val old = channel("Old")
        val unloaded = channel("Unloaded")
        val mutedOld = channel("Muted old", muted = true)
        val mutedUnloaded = channel("Muted unloaded", muted = true)
        val ids = listOf(old.uid, unloaded.uid, mutedOld.uid, mutedUnloaded.uid)
        val group = group(ids)
        dao.setLastUpdatedForSubscription(FeedLastUpdatedEntity(old.uid, 1, now.minusDays(2)))
        dao.setLastUpdatedForSubscription(FeedLastUpdatedEntity(mutedOld.uid, 1, now.minusDays(10)))
        val stream = StreamEntity(
            serviceId = 0,
            url = "https://example.com/cached",
            title = "Cached",
            streamType = StreamType.VIDEO_STREAM,
            duration = 60,
            uploader = requireNotNull(mutedOld.name),
            uploaderUrl = mutedOld.url,
            uploadDate = now
        )
        stream.uid = database.streamDAO().insert(stream)
        dao.insertAll(listOf(FeedEntity(stream.uid, mutedOld.uid)))

        for (scope in listOf(all, regular)) {
            for (groupId in listOf(GROUP_ALL_ID, group)) {
                assertEquals(1L, feed.notLoadedCount(groupId, scope).blockingFirst())
                assertEquals(listOf(now.minusDays(2)), feed.oldestSubscriptionUpdate(groupId, scope).blockingFirst())
                assertEquals(listOf(stream.uid), feed.getStreams(groupId, true, true, true, scope).blockingGet()!!.map { it.stream.uid })
            }
        }
        FeedAllExclusions.toggle(context, profile, 0, mutedOld.url!!)
        assertTrue(feed.getStreams(GROUP_ALL_ID, true, true, true, regular).blockingGet()!!.isEmpty())
        assertEquals(1, feed.getStreams(group, true, true, true, regular).blockingGet()!!.size)
        assertEquals(setOf(old.uid, unloaded.uid), dao.getAllOutdated(profile, now).blockingFirst().ids())

        groups.updateSubscriptionsForGroupForProfile(profile, group, emptyList())
        assertEquals(1L, feed.notLoadedCount(GROUP_UNGROUPED_ID, regular).blockingFirst())
        assertEquals(listOf(now.minusDays(2)), feed.oldestSubscriptionUpdate(GROUP_UNGROUPED_ID, regular).blockingFirst())
        assertEquals(1, feed.getStreams(GROUP_UNGROUPED_ID, true, true, true, regular).blockingGet()!!.size)

        subscriptions.setFeedMutedForProfile(profile, listOf(old.uid, unloaded.uid), true)
        assertEquals(0L, feed.notLoadedCount(scope = regular).blockingFirst())
        assertEquals(listOf<OffsetDateTime?>(null), feed.oldestSubscriptionUpdate(GROUP_ALL_ID, regular).blockingFirst())
        assertEquals(1, dao.getMemberships(mutedOld.uid).size)
        assertEquals(4, subscriptions.getAllDirectForProfile(profile).size)
    }

    @Test
    fun forcedAggregateAndNotificationRefreshesCannotOverrideMuteOrAnExplicitIdSubset() {
        val grouped = channel("Grouped", muted = true, modes = 3)
        val ungrouped = channel("Ungrouped", muted = true)
        val group = group(listOf(grouped.uid))
        mockStatic(ExtractorHelper::class.java).use { extractor ->
            for (scope in listOf(null, all, regular, FeedScope(0, 2))) {
                for (groupId in listOf(GROUP_ALL_ID, group, GROUP_UNGROUPED_ID, FeedLoadManager.GROUP_NOTIFICATION_ENABLED)) {
                    FeedLoadManager(context, profile).startLoading(
                        groupId = groupId,
                        scope = scope,
                        ignoreOutdatedThreshold = true,
                        onlySubscriptions = setOf(grouped.uid, ungrouped.uid)
                    ).test().assertComplete().assertNoErrors().assertValue(emptyList())
                }
            }
            extractor.verifyNoInteractions()
        }
        // Muting never writes a fake successful fetch timestamp.
        database.query("SELECT COUNT(*) FROM feed_last_updated", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertTrue(dao.getAllOutdated(profile, now).blockingFirst().isEmpty())
    }

    private fun channel(name: String, muted: Boolean = false, modes: Int = 1, service: Int = 0, profileId: String = profile): SubscriptionEntity = SubscriptionEntity(
        serviceId = service,
        url = "https://example.com/$name",
        name = name,
        youtubeModeMask = modes,
        notificationMode = NotificationMode.ENABLED,
        feedMuted = muted,
        profileId = profileId
    ).apply { uid = subscriptions.insert(this) }

    private fun group(ids: List<Long>): Long = groups.insert(FeedGroupEntity(0, "Group", FeedGroupIcon.ALL, profileId = profile)).also {
        groups.updateSubscriptionsForGroupForProfile(profile, it, ids)
    }

    private fun List<SubscriptionEntity>.ids() = map { it.uid }.toSet()
}
