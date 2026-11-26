/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_ALL_ID
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_UNGROUPED_ID
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.GroupChannelsViewModel
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UngroupedFeedTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase
    private lateinit var feed: FeedDatabaseManager
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val scope = FeedScope(0, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
    private val allScope = FeedScope(FeedScope.ALL_SERVICES, 3)
    private val now = OffsetDateTime.parse("2026-01-02T12:00:00Z")
    private val clock = TestScheduler()
    private val stores = mutableListOf<ViewModelStore>()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private val groups get() = database.feedGroupDAO()
    private val dao get() = database.feedDAO()

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { clock }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(context, 0)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        feed = FeedDatabaseManager(context, profile)
        FeedEventManager.reset(scope)
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        FeedEventManager.reset(scope)
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun membershipsAcrossMultipleGroupsDetermineUngroupedWithoutCreatingAVirtualRow() {
        val first = channel("First")
        val second = channel("Second")
        val firstStream = stream(first, "First")
        val secondStream = stream(second, "Second")
        val one = group("One")
        val two = group("Two")
        assertNotEquals(GROUP_ALL_ID, GROUP_UNGROUPED_ID)
        assertNotEquals(FeedLoadManager.GROUP_NOTIFICATION_ENABLED, GROUP_UNGROUPED_ID)
        assertEquals(setOf(first.uid, second.uid), ungroupedIds())

        groups.updateSubscriptionsForGroupForProfile(profile, one, listOf(first.uid))
        groups.updateSubscriptionsForGroupForProfile(profile, two, listOf(first.uid))
        assertEquals(listOf(secondStream.uid), streamIds())
        assertEquals(setOf(second.uid), ungroupedIds())
        assertEquals(listOf(firstStream.uid), streamIds(groupId = one))
        groups.deleteForProfile(profile, one)
        assertEquals(setOf(second.uid), ungroupedIds())
        groups.updateSubscriptionsForGroupForProfile(profile, two, emptyList())
        assertEquals(setOf(first.uid, second.uid), ungroupedIds())
        assertEquals(setOf(firstStream.uid, secondStream.uid), streamIds().toSet())
        assertEquals(listOf(two), feed.groups().blockingFirst().map { it.uid })
        assertEquals(0, groups.getSubscriptionIdsForDirect(GROUP_UNGROUPED_ID).size)
        assertNull(groups.getGroupForProfile(profile, GROUP_UNGROUPED_ID).blockingGet())
    }

    @Test
    fun profileAndServiceScopesStayIsolatedAndGroupingAppliesAcrossYoutubeModes() {
        val bothModes = channel("Both", modes = 3)
        val regular = stream(bothModes, "Regular", mode = 1)
        val music = stream(bothModes, "Music", mode = 2)
        val soundcloud = channel("SoundCloud", service = 1)
        val sound = stream(soundcloud, "Sound")
        val elsewhere = channel("Other profile", profileId = "other")
        val privateStream = stream(elsewhere, "Private")
        val foreignGroup = group("Foreign", "other")
        groups.updateSubscriptionsForGroupForProfile("other", foreignGroup, listOf(elsewhere.uid))

        assertEquals(listOf(regular.uid), streamIds())
        assertEquals(listOf(music.uid), streamIds(selectedScope = FeedScope(0, 2)))
        assertEquals(setOf(regular.uid, music.uid, sound.uid), streamIds(selectedScope = allScope).toSet())
        assertFalse(privateStream.uid in streamIds(selectedScope = allScope))
        assertEquals(setOf(bothModes.uid, soundcloud.uid), ungroupedIds())

        val localGroup = group("Local")
        groups.updateSubscriptionsForGroupForProfile(profile, localGroup, listOf(bothModes.uid))
        assertTrue(streamIds().isEmpty())
        assertTrue(streamIds(selectedScope = FeedScope(0, 2)).isEmpty())
        assertEquals(listOf(sound.uid), streamIds(selectedScope = allScope))
        assertTrue(FeedDatabaseManager(context, "other").subscriptionIdsForGroup(GROUP_UNGROUPED_ID).blockingFirst().isEmpty())
    }

    @Test
    fun refreshCandidatesCountsAndOldestUpdateUseTheSameUngroupedMembership() {
        val unloaded = channel("Unloaded")
        val old = channel("Old")
        val fresh = channel("Fresh")
        val music = channel("Music", modes = 2)
        val grouped = channel("Grouped")
        channel("Foreign", profileId = "other")
        val realGroup = group("Real")
        groups.updateSubscriptionsForGroupForProfile(profile, realGroup, listOf(grouped.uid))
        dao.setLastUpdatedForSubscription(FeedLastUpdatedEntity(old.uid, 1, now.minusDays(2)))
        dao.setLastUpdatedForSubscription(FeedLastUpdatedEntity(fresh.uid, 1, now))
        dao.setLastUpdatedForSubscription(FeedLastUpdatedEntity(grouped.uid, 1, now.minusDays(10)))

        assertEquals(1L, feed.notLoadedCount(GROUP_UNGROUPED_ID, scope).blockingFirst())
        assertEquals(listOf(now.minusDays(2)), feed.oldestSubscriptionUpdate(GROUP_UNGROUPED_ID, scope).blockingFirst())
        assertEquals(
            setOf(unloaded.uid, old.uid),
            feed.outdatedSubscriptionsForGroupAndScope(GROUP_UNGROUPED_ID, scope, now.minusDays(1)).blockingFirst().map { it.uid }.toSet()
        )
        assertEquals(2L, feed.notLoadedCount(GROUP_UNGROUPED_ID, allScope).blockingFirst())
        assertEquals(
            setOf(unloaded.uid, old.uid, music.uid),
            feed.outdatedSubscriptionsForGroup(GROUP_UNGROUPED_ID, now.minusDays(1)).blockingFirst().map { it.uid }.toSet()
        )
        assertEquals(
            setOf(unloaded.uid, old.uid, music.uid),
            feed.outdatedSubscriptionsForGroupAndScope(GROUP_UNGROUPED_ID, allScope, now.minusDays(1)).blockingFirst().map { it.uid }.toSet()
        )
        groups.updateSubscriptionsForGroupForProfile(profile, realGroup, listOf(grouped.uid, unloaded.uid, old.uid))
        assertEquals(0L, feed.notLoadedCount(GROUP_UNGROUPED_ID, scope).blockingFirst())
        assertEquals(listOf(now), feed.oldestSubscriptionUpdate(GROUP_UNGROUPED_ID, scope).blockingFirst())
        assertTrue(feed.outdatedSubscriptionsForGroupAndScope(GROUP_UNGROUPED_ID, scope, now.minusDays(1)).blockingFirst().isEmpty())
    }

    @Test
    fun ungroupedFilteringPrecedesPaginationAndPreservesSortAndAllFeedExclusionSemantics() {
        val ungrouped = channel("Ungrouped")
        val grouped = channel("Grouped")
        val realGroup = group("Real")
        groups.updateSubscriptionsForGroupForProfile(profile, realGroup, listOf(grouped.uid))
        val visible = database.runInTransaction<List<StreamEntity>> {
            val result = (1..625).map { index ->
                stream(ungrouped, "Visible $index", views = index.toLong(), published = now.minusSeconds(index.toLong()))
            }
            repeat(30) { stream(grouped, "Grouped $it", views = 10000, published = now) }
            result
        }
        FeedAllExclusions.toggle(context, profile, ungrouped.serviceId, requireNotNull(ungrouped.url))
        val expected = visible.reversed().map { it.uid }
        val first = streamIds(views = true)
        val second = streamIds(views = true, offset = 500)
        assertEquals(expected.take(500), first)
        assertEquals(expected.drop(500), second)
        assertTrue(first.intersect(second.toSet()).isEmpty())
        assertEquals(visible.take(500).map { it.uid }, streamIds())
        assertEquals(expected.take(500), streamIds(discovery = true))
        assertEquals(expected.take(500), feed.getStreams(GROUP_UNGROUPED_ID, true, true, true, scope, sortByViews = true).blockingGet()!!.map { it.stream.uid })
        assertTrue(feed.getStreams(GROUP_ALL_ID, true, true, true, scope).blockingGet()!!.all { it.stream.title.startsWith("Grouped") })
        assertEquals(30, streamIds(groupId = realGroup).size)
    }

    @Test
    fun playedPartialAndFutureFiltersStillApplyToUngroupedStreams() {
        val channel = channel("Filters")
        val normal = stream(channel, "Normal")
        val played = stream(channel, "Played")
        val partial = stream(channel, "Partial")
        val future = stream(channel, "Future", published = now.plusDays(1))
        database.streamHistoryDAO().insert(StreamHistoryEntity(played.uid, now, 1, profile))
        database.streamHistoryDAO().insert(StreamHistoryEntity(partial.uid, now, 1, profile))
        database.streamStateDAO().upsert(StreamStateEntity(played.uid, 100_000, profile))
        database.streamStateDAO().upsert(StreamStateEntity(partial.uid, 50_000, profile))
        val filtered = dao.getStreams(profile, GROUP_UNGROUPED_ID, false, false, now, 0, 1).blockingGet()!!.map { it.stream.uid }
        assertEquals(listOf(normal.uid), filtered)
        assertEquals(setOf(normal.uid, played.uid, partial.uid, future.uid), streamIds().toSet())
    }

    @Test
    fun switchingToUngroupedResetsTheCachedWindowAndObservesMembershipChanges() {
        val channel = channel("Paging")
        database.runInTransaction { repeat(3001) { stream(channel, "Video $it", views = it.toLong()) } }
        val model = FeedViewModel(context, initialShowPlayedItems = true, initialShowPartiallyPlayedItems = true, initialShowFutureItems = true)
        stores += ViewModelStore().also { it.put("feed", model) }
        model.stateLiveData.observeForever { }
        tick()
        repeat(10) {
            model.loadMoreCachedItems()
            tick()
        }
        assertTrue(model.showOlderCachedPage())
        tick()
        assertTrue(model.hasNewerCachedPage())
        assertEquals(1, state(model).items.size)
        model.selectGroup(GROUP_UNGROUPED_ID)
        tick()
        assertFalse(model.hasNewerCachedPage())
        assertEquals(500, state(model).items.size)

        val realGroup = group("Added by sync")
        groups.updateSubscriptionsForGroupForProfile(profile, realGroup, listOf(channel.uid))
        tick()
        assertTrue(state(model).items.isEmpty())
        groups.deleteForProfile(profile, realGroup)
        tick()
        assertEquals(500, state(model).items.size)
        assertTrue(feed.groups().blockingFirst().isEmpty())
    }

    @Test
    fun channelListObservesMembershipChangesAndRemainsProfileScoped() {
        val first = channel("First")
        val second = channel("Second", service = 1)
        channel("Other profile", profileId = "other")
        val model = GroupChannelsViewModel(context)
        stores += ViewModelStore().also { it.put("channels", model) }
        model.state.observeForever { }
        model.load(GROUP_UNGROUPED_ID)
        tick()
        assertEquals(listOf(first.uid, second.uid), model.state.value!!.channels.map { it.uid })
        val realGroup = group("Real")
        groups.updateSubscriptionsForGroupForProfile(profile, realGroup, listOf(first.uid))
        tick()
        assertEquals(listOf(second.uid), model.state.value!!.channels.map { it.uid })
        database.subscriptionDAO().delete(second)
        tick()
        assertTrue(model.state.value!!.channels.isEmpty())
        groups.deleteForProfile(profile, realGroup)
        tick()
        assertEquals(listOf(first.uid), model.state.value!!.channels.map { it.uid })
    }

    @Test
    fun groupPickerAppendsUngroupedAndKeepsItSelectedWhenRealGroupsDisappear() {
        val realGroup = group("Real")
        group("Other profile", "other")
        val actionBar = mock(ActionBar::class.java)
        var toolbar: View? = null
        doAnswer {
            toolbar = it.getArgument(0)
            null
        }.`when`(actionBar).setCustomView(any(View::class.java), any(ActionBar.LayoutParams::class.java))
        var selected = GROUP_ALL_ID
        val host = Robolectric.buildActivity(FragmentActivity::class.java)
        host.get().setTheme(R.style.LightTheme)
        host.setup()
        val selector = FeedGroupSelector(host.get(), actionBar, { selected }) { id, _ -> selected = id }
        try {
            selector.attach()
            requireNotNull(toolbar).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val picker = ShadowDialog.getLatestDialog() as AlertDialog
            val labels = (0 until picker.listView.adapter.count).map { picker.listView.adapter.getItem(it).toString() }
            assertEquals(listOf(context.getString(R.string.feed_group_filter_all), "Real", context.getString(R.string.feed_group_ungrouped)), labels)
            picker.listView.performItemClick(null, 2, 2)
            assertEquals(GROUP_UNGROUPED_ID, selected)
            groups.deleteForProfile(profile, realGroup)
            tick()
            assertEquals(GROUP_UNGROUPED_ID, selected)
            assertEquals(context.getString(R.string.feed_group_ungrouped), toolbar!!.findViewById<TextView>(R.id.feed_group_toolbar_label).text.toString())
            assertTrue(toolbar!!.isEnabled)
            toolbar!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val emptyGroups = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(2, emptyGroups.listView.adapter.count)
            assertTrue(emptyGroups.listView.isItemChecked(1))
        } finally {
            selector.dispose()
            host.pause().stop().destroy()
        }
    }

    private fun channel(name: String, profileId: String = profile, service: Int = 0, modes: Int = 1): SubscriptionEntity = SubscriptionEntity(serviceId = service, url = "https://example.com/$name", name = name, profileId = profileId, youtubeModeMask = modes)
        .apply { uid = database.subscriptionDAO().insert(this) }

    private fun group(name: String, profileId: String = profile): Long = groups.insert(
        FeedGroupEntity(0, name, FeedGroupIcon.ALL, profileId = profileId)
    )

    private fun stream(
        channel: SubscriptionEntity,
        title: String,
        mode: Int = 1,
        views: Long = 0,
        published: OffsetDateTime = now.minusDays(1)
    ): StreamEntity = StreamEntity(
        serviceId = channel.serviceId,
        url = "https://example.com/video/$title",
        title = title,
        streamType = StreamType.VIDEO_STREAM,
        duration = 100,
        uploader = requireNotNull(channel.name),
        viewCount = views,
        uploadDate = published
    ).apply {
        uid = database.streamDAO().insert(this)
        dao.insertAll(listOf(FeedEntity(uid, channel.uid, mode, uid)))
    }

    private fun ungroupedIds(): Set<Long> = feed.subscriptionIdsForGroup(GROUP_UNGROUPED_ID).blockingFirst().toSet()

    private fun streamIds(
        groupId: Long = GROUP_UNGROUPED_ID,
        selectedScope: FeedScope = scope,
        views: Boolean = false,
        discovery: Boolean = false,
        offset: Int = 0
    ): List<Long> = dao.getStreams(
        profile,
        groupId,
        true,
        true,
        null,
        selectedScope.serviceId,
        selectedScope.youtubeModeMask,
        sortByDiscovery = discovery,
        limit = 500,
        offset = offset,
        sortByViews = views
    ).blockingGet()!!.map { it.stream.uid }

    private fun tick() {
        clock.advanceTimeBy(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun state(model: FeedViewModel) = model.stateLiveData.value as FeedState.LoadedState
}
