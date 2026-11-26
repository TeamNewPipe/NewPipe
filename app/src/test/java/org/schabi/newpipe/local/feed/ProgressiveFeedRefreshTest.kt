package org.schabi.newpipe.local.feed

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.recommendations.LocalRecommendations
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProgressiveFeedRefreshTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val clock = TestScheduler()
    private lateinit var database: AppDatabase
    private lateinit var manager: FeedLoadManager
    private lateinit var feed: FeedDatabaseManager
    private val scope = FeedScope(0, SubscriptionEntity.YOUTUBE_MODE_REGULAR)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private val subscriptions = mutableListOf<Long>()
    private val stores = mutableListOf<ViewModelStore>()

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
        feed = FeedDatabaseManager(context, SubscriptionEntity.DEFAULT_PROFILE_ID)
        manager = FeedLoadManager(context, SubscriptionEntity.DEFAULT_PROFILE_ID)
        repeat(3) { index ->
            subscriptions += database.subscriptionDAO().insert(
                SubscriptionEntity(serviceId = 0, url = "https://example.com/channel/$index", name = "Channel $index")
            )
        }
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
    fun commitsFirstChannelBeforeCompletionAndKeepsSuccessAcrossFailuresAndDuplicates() {
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source).test()
        val first = update(0, "first", 2)
        source.onNext(Notification.createOnNext(first))
        result.assertNotComplete()
        assertEquals(listOf("first"), titles())
        assertEquals(listOf("first"), first.newStreams.map { it.name })

        source.onNext(Notification.createOnError(FeedLoadService.RequestException(subscriptions[1], "failed", java.io.IOException("offline"))))
        source.onNext(Notification.createOnNext(update(2, "newer", 1, listOf(java.io.IOException("partial tab")))))
        source.onNext(Notification.createOnNext(first))
        assertEquals(listOf("newer", "first"), titles())
        assertTrue(first.newStreams.isEmpty())
        assertEquals(2L, feed.notLoadedCount(scope = scope).blockingFirst())
        source.onComplete()
        result.assertComplete().assertNoErrors().assertValue { it.size == 4 }
        assertEquals(listOf("newer", "first"), titles())
        database.query("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun manualRefreshPersistsChannelsWithoutRetainingCompletedResults() {
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source, retainResults = false).test()

        source.onNext(Notification.createOnNext(update(0, "first", 2)))
        source.onNext(Notification.createOnNext(update(1, "second", 1)))
        assertEquals(listOf("second", "first"), titles())

        source.onComplete()
        result.assertComplete().assertNoErrors().assertValue(emptyList())
    }

    @Test
    fun unsubscribingDuringFetchDoesNotAbortOtherChannelCommits() {
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source).test()
        val deleted = update(0, "deleted", 0)
        database.subscriptionDAO().deleteSubscription(0, deleted.url)
        source.onNext(Notification.createOnNext(deleted))
        source.onNext(Notification.createOnNext(update(1, "kept", 1)))
        source.onComplete()
        result.assertComplete().assertNoErrors()
        assertTrue(deleted.newStreams.isEmpty())
        assertEquals(listOf("kept"), titles())
    }

    @Test
    fun observedFutureFilterUsesQueryTimeWhileFixedCutoffQueriesStaySupported() {
        database.runInTransaction {
            feed.upsertAll(subscriptions[0], update(0, "published", 1).streams)
            feed.upsertAll(subscriptions[1], update(1, "future", -1).streams)
        }
        val cutoff = OffsetDateTime.now().minusDays(2)
        val dao = database.feedDAO()
        assertTrue(dao.getStreams(SubscriptionEntity.DEFAULT_PROFILE_ID, -1, true, true, cutoff, 0, 1).blockingGet()!!.isEmpty())
        assertEquals(
            listOf("published"),
            dao.observeStreams(SubscriptionEntity.DEFAULT_PROFILE_ID, -1, true, true, cutoff, 0, 1, useCurrentTime = true)
                .blockingFirst().map { it.stream.title }
        )
    }

    @Test
    fun partialChannelKeepsCachedLiveStreamsAndDiscoveryTimes() {
        val cached = StreamInfoItem(0, "https://example.com/live", "live", StreamType.LIVE_STREAM).apply {
            uploaderName = "Channel 0"
            uploaderUrl = "https://example.com/channel/0"
        }
        database.runInTransaction { feed.upsertAll(subscriptions[0], listOf(cached)) }
        val membership = database.feedDAO().getMemberships(subscriptions[0]).single()
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source).test()
        source.onNext(Notification.createOnNext(update(0, "partial", 1, listOf(java.io.IOException("tab failed")))))
        assertEquals(setOf("live", "partial"), titles().toSet())
        assertEquals(membership, database.feedDAO().getMemberships(subscriptions[0]).first { it.streamId == membership.streamId })
        result.assertNotComplete()
        result.dispose()
    }

    @Test
    fun cancellationKeepsCommittedChannelsAndRejectsLaterResults() {
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source).test()
        source.onNext(Notification.createOnNext(update(0, "first", 1)))
        result.dispose()
        assertFalse(source.hasSubscribers())
        source.onNext(Notification.createOnNext(update(1, "too late", 0)))
        assertEquals(listOf("first"), titles())
    }

    @Test
    fun failedChannelTransactionRollsBackWithoutLosingEarlierCommit() {
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val result = manager.storeResults(source).test()
        source.onNext(Notification.createOnNext(update(0, "first", 1)))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_feed_update BEFORE INSERT ON feed_last_updated " +
                "BEGIN SELECT RAISE(ABORT, 'fixture'); END"
        )
        source.onNext(Notification.createOnNext(update(1, "rolled back", 0)))
        result.assertError { it.message.orEmpty().contains("fixture") }
        assertEquals(listOf("first"), titles())
        assertFalse(database.inTransaction())
    }

    @Test
    fun modelObservesPartialContentWhileRefreshingAndReusesItemsOnProgress() {
        feed.upsertAll(subscriptions[0], update(0, "cached", 3).streams)
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val refresh = manager.storeResults(source).test()
        FeedEventManager.postEvent(scope, FeedEventManager.Event.ProgressEvent(0, 3))
        val model = model()
        tick()
        assertEquals(listOf("cached"), state(model).items.map { it.streamWithState.stream.title })
        assertNotNull(state(model).progress)

        source.onNext(Notification.createOnNext(update(1, "new", 0)))
        tick()
        val items = state(model).items
        assertEquals(listOf("new", "cached"), items.map { it.streamWithState.stream.title })
        FeedEventManager.postEvent(scope, FeedEventManager.Event.ProgressEvent(2, 3, updateDescription = "Still fetching"))
        tick()
        assertSame(items, state(model).items)
        assertEquals("Still fetching", state(model).progress!!.updateDescription)

        // Disposing screen observation does not own the refresh. A newly created
        // model reads committed content and the replayed in-flight progress.
        stores.first().clear()
        source.onNext(Notification.createOnNext(update(2, "away", 1)))
        val returned = model()
        tick()
        assertEquals(listOf("new", "away", "cached"), state(returned).items.map { it.streamWithState.stream.title })
        assertNotNull(state(returned).progress)
        FeedEventManager.reset(scope)
        tick()
        assertEquals(null, state(returned).progress)
        refresh.dispose()
    }

    @Test
    fun modelKeepsHighlightBaselineAcrossProgressCompletionAndReactivation() {
        manager.storeResults(io.reactivex.rxjava3.core.Flowable.just(Notification.createOnNext(update(0, "cached", 3))))
            .test().assertComplete().assertNoErrors()
        FeedEventManager.reset(scope)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        val model = model(owner)
        tick()
        val baseline = state(model).oldestUpdate
        assertNotNull(baseline)
        FeedEventManager.postEvent(scope, FeedEventManager.Event.ProgressEvent(0, 3))
        tick()
        assertEquals(baseline, state(model).highlightAfter)
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val refresh = manager.storeResults(source).test()
        source.onNext(Notification.createOnNext(update(1, "first", 1)))
        tick()
        source.onNext(Notification.createOnNext(update(2, "last", 0)))
        tick()
        assertEquals(baseline, state(model).highlightAfter)
        source.onComplete()
        refresh.assertComplete().assertNoErrors()
        FeedEventManager.postEvent(scope, FeedEventManager.Event.SuccessResultEvent(emptyList()))
        tick()
        assertEquals(baseline, state(model).highlightAfter)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        tick()
        assertEquals(baseline, state(model).highlightAfter)
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test
    fun inactiveScreenStopsRenderingWhileRefreshKeepsCommittingAndReturnObservesLatestContent() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        val model = model(owner)
        val source = PublishProcessor.create<Notification<FeedUpdateInfo>>()
        val refresh = manager.storeResults(source).test()
        tick()
        val beforeNavigation = model.stateLiveData.value
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        source.onNext(Notification.createOnNext(update(0, "while away", 0)))
        tick()
        assertTrue(source.hasSubscribers())
        assertSame(beforeNavigation, model.stateLiveData.value)
        assertEquals(listOf("while away"), titles())
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        tick()
        assertEquals(listOf("while away"), state(model).items.map { it.streamWithState.stream.title })
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        refresh.dispose()
    }

    @Test
    fun switchingGroupsKeepsRefreshProgressAndObservesOnlySelectedMemberships() {
        feed.upsertAll(subscriptions[0], update(0, "first", 2).streams)
        feed.upsertAll(subscriptions[1], update(1, "second", 1).streams)
        val dao = database.feedGroupDAO()
        val group = dao.insert(org.schabi.newpipe.database.feed.model.FeedGroupEntity(0, "Rare channels", org.schabi.newpipe.local.subscription.FeedGroupIcon.ALL))
        dao.updateSubscriptionsForGroup(group, listOf(subscriptions[0]))
        FeedEventManager.postEvent(scope, FeedEventManager.Event.ProgressEvent(1, 3))
        val model = model()
        tick()
        assertEquals(2, state(model).items.size)
        model.selectGroup(group)
        tick()
        assertEquals(listOf("first"), state(model).items.map { it.streamWithState.stream.title })
        assertNotNull(state(model).progress)
        dao.updateSubscriptionsForGroup(group, listOf(subscriptions[1]))
        tick()
        assertEquals(listOf("second"), state(model).items.map { it.streamWithState.stream.title })
        model.selectGroup(-1)
        tick()
        assertEquals(2, state(model).items.size)
        assertNotNull(state(model).progress)
    }

    @Test
    fun groupSelectorTracksRenamesDeletionAndOnlyActiveProfileGroups() {
        val dao = database.feedGroupDAO()
        val group = org.schabi.newpipe.database.feed.model.FeedGroupEntity(0, "Rare channels", org.schabi.newpipe.local.subscription.FeedGroupIcon.ALL)
        val id = dao.insert(group)
        dao.insert(group.copy(uid = 0, name = "Other profile", profileId = "other"))
        var selected = id
        val actionBar = org.mockito.Mockito.mock(androidx.appcompat.app.ActionBar::class.java)
        var customView: android.view.View? = null
        org.mockito.Mockito.doAnswer {
            customView = it.getArgument(0)
            null
        }.`when`(actionBar).setCustomView(
            org.mockito.ArgumentMatchers.any(android.view.View::class.java),
            org.mockito.ArgumentMatchers.any(androidx.appcompat.app.ActionBar.LayoutParams::class.java)
        )
        org.mockito.Mockito.`when`(actionBar.customView).thenAnswer { customView }
        val themedContext = android.view.ContextThemeWrapper(context, R.style.LightTheme)
        val selector = FeedGroupSelector(themedContext, actionBar, { selected }) { newId, _ -> selected = newId }
        selector.attach()
        val button = requireNotNull(customView).findViewById<android.widget.TextView>(R.id.feed_group_toolbar_label)
        try {
            assertEquals("Rare channels", button.text.toString())
            dao.update(group.copy(uid = id, name = "Renamed"))
            assertEquals("Renamed", button.text.toString())
            FeedGroupSelector.updateSearchVisibility(actionBar, true)
            org.mockito.Mockito.verify(actionBar).setDisplayShowCustomEnabled(false)
            selector.attach(searching = true)
            org.mockito.Mockito.verify(actionBar, org.mockito.Mockito.times(2)).setDisplayShowCustomEnabled(false)
            FeedGroupSelector.updateSearchVisibility(actionBar, false)
            org.mockito.Mockito.verify(actionBar, org.mockito.Mockito.times(2)).setDisplayShowCustomEnabled(true)
            dao.delete(id)
            assertEquals(-1L, selected)
            assertEquals(android.view.View.GONE, button.visibility)
        } finally {
            selector.dispose()
        }
    }

    @Test
    fun mostViewedSelectionPersistsAlongsideLegacyDiscoveryAndTheRelevanceGate() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val modeKey = context.getString(R.string.feed_sort_mode_key)
        val discoveryKey = context.getString(R.string.feed_sort_by_discovery_key)
        val recommendationPreferences = context.getSharedPreferences(LocalRecommendations.PREFERENCES, android.content.Context.MODE_PRIVATE)
        val enabledKey = "${ProfileManager.getActiveProfileId(context)}:enabled"
        val previousEnabled = recommendationPreferences.all[enabledKey] as? Boolean
        try {
            preferences.edit().putBoolean(discoveryKey, true).commit()
            assertEquals(FeedSortMode.DISCOVERY, model().getSortMode())
            model().setSortMode(FeedSortMode.VIEWS_DESC)
            assertEquals("VIEWS_DESC", preferences.getString(modeKey, null))
            assertFalse(preferences.getBoolean(discoveryKey, true))
            assertEquals(FeedSortMode.VIEWS_DESC, model().getSortMode())

            preferences.edit().putString(modeKey, "REMOVED_MODE").putBoolean(discoveryKey, true).commit()
            assertEquals(FeedSortMode.DISCOVERY, model().getSortMode())
            preferences.edit().putBoolean(discoveryKey, false).commit()
            assertEquals(FeedSortMode.PUBLICATION, model().getSortMode())

            recommendationPreferences.edit().putBoolean(enabledKey, true).commit()
            model().setSortMode(FeedSortMode.RELEVANCE)
            assertEquals(FeedSortMode.RELEVANCE, model().getSortMode())
            recommendationPreferences.edit().putBoolean(enabledKey, false).commit()
            assertEquals(FeedSortMode.PUBLICATION, model().getSortMode())
            model().setSortMode(FeedSortMode.VIEWS_DESC)
            assertEquals(FeedSortMode.VIEWS_DESC, model().getSortMode())
        } finally {
            recommendationPreferences.edit().apply {
                previousEnabled?.let { putBoolean(enabledKey, it) } ?: remove(enabledKey)
            }.commit()
        }
    }

    @Test
    fun switchingToMostViewedResetsTheCachedWindowAndObservesUpdatedCounts() {
        val published = OffsetDateTime.now()
        val streams = (1L..3_001L).map { id ->
            StreamEntity(
                uid = id,
                serviceId = 0,
                url = "https://example.com/sort/$id",
                title = "Video $id",
                streamType = StreamType.VIDEO_STREAM,
                duration = 60,
                uploader = "Channel 0",
                viewCount = id,
                uploadDate = published.minusSeconds(id)
            )
        }
        database.runInTransaction {
            database.streamDAO().insertAll(streams)
            database.feedDAO().insertAll(streams.map { FeedEntity(it.uid, subscriptions[0]) })
        }
        val model = model()
        tick()
        repeat(10) {
            model.loadMoreCachedItems()
            tick()
        }
        assertTrue(model.showOlderCachedPage())
        tick()
        assertTrue(model.hasNewerCachedPage())
        assertEquals(listOf(3_001L), state(model).items.map { it.streamWithState.stream.uid })

        model.setSortMode(FeedSortMode.VIEWS_DESC)
        tick()
        assertFalse(model.hasNewerCachedPage())
        assertEquals(FeedPagination.INITIAL_LIMIT, state(model).items.size)
        assertEquals(3_001L, state(model).items.first().streamWithState.stream.uid)

        database.streamDAO().update(streams.first().copy(viewCount = 99_000))
        tick()
        assertEquals(1L, state(model).items.first().streamWithState.stream.uid)
        assertEquals(FeedSortMode.VIEWS_DESC, model().getSortMode())
    }

    private fun model(owner: LifecycleOwner? = null): FeedViewModel = FeedViewModel(context, initialShowPlayedItems = true, initialShowPartiallyPlayedItems = true, initialShowFutureItems = true).also {
        stores += ViewModelStore().apply { put("feed", it) }
        if (owner == null) {
            it.stateLiveData.observeForever(Observer { })
        } else {
            it.stateLiveData.observe(owner, Observer { })
        }
    }

    private fun state(model: FeedViewModel) = model.stateLiveData.value as FeedState.LoadedState

    private fun tick() {
        clock.advanceTimeBy(1, TimeUnit.SECONDS)
    }

    private fun titles() = feed.getStreams(-1, true, true, true, scope).blockingGet()!!.map { it.stream.title }

    private fun update(channel: Int, id: String, daysAgo: Long, errors: List<Throwable> = emptyList()): FeedUpdateInfo = FeedUpdateInfo(
        subscriptions[channel], 0, "", "Channel $channel", null, "https://example.com/channel/$channel", 0,
        SubscriptionEntity.YOUTUBE_MODE_REGULAR, null, null,
        listOf(
            StreamInfoItem(0, "https://example.com/$id", id, StreamType.VIDEO_STREAM).apply {
                uploadDate = DateWrapper(OffsetDateTime.now().minusDays(daysAgo))
                uploaderName = "Channel $channel"
                uploaderUrl = "https://example.com/channel/$channel"
            }
        ),
        errors
    )
}
