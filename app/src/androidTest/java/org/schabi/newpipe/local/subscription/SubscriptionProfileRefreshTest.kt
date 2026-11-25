package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xwray.groupie.Group
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.ServiceHelper

@RunWith(AndroidJUnit4::class)
class SubscriptionProfileRefreshTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val viewModelStore = ViewModelStore()
    private lateinit var database: AppDatabase
    private lateinit var studyProfileId: String
    private lateinit var previousProfileId: String
    private var previousService: String? = null
    private var previousGroupListViewMode: Boolean? = null

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        previousProfileId = ProfileManager.getActiveProfileId(context)
        previousService = preferences.getString(context.getString(R.string.current_service_key), null)
        previousGroupListViewMode = SubscriptionGroupViewPreferences.getListViewMode(context)
        preferences.edit().remove(SubscriptionGroupViewPreferences.KEY).commit()
        studyProfileId = requireNotNull(
            ProfileManager.createProfile(context, "Study ${UUID.randomUUID().toString().take(8)}")
        ).id
        ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID)
        ServiceHelper.setSelectedServiceId(context, 0)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { viewModelStore.clear() }
        ProfileManager.deleteProfile(context, studyProfileId)
        ProfileManager.setActiveProfile(context, previousProfileId)
        val editor = preferences.edit()
            .putString(context.getString(R.string.current_service_key), previousService)
            .remove(SubscriptionGroupViewPreferences.KEY)
        previousGroupListViewMode?.let { editor.putBoolean(SubscriptionGroupViewPreferences.KEY, it) }
        editor.commit()
        database.close()
    }

    @Test
    fun recreationRebindsRetainedFragmentViewModelInBothDirections() {
        val defaultChannel = addChannel(ProfileManager.DEFAULT_PROFILE_ID, "Default channel")
        val defaultGroup = addGroup(ProfileManager.DEFAULT_PROFILE_ID, "Default group")
        val studyChannel = addChannel(studyProfileId, "Study channel")
        val studyGroup = addGroup(studyProfileId, "Study group")

        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var retainedViewModel: SubscriptionViewModel
            scenario.onActivity { activity ->
                val fragment = SubscriptionFragment().apply { useAsFrontPage(true) }
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment, FRAGMENT_TAG)
                    .commitNow()
                retainedViewModel = ViewModelProvider(fragment)[SubscriptionViewModel::class.java]
            }
            awaitRows(retainedViewModel, listOf(defaultChannel), listOf(defaultGroup))

            assertTrue(ProfileManager.setActiveProfile(context, studyProfileId))
            scenario.recreate()
            assertRetainedViewModel(scenario, retainedViewModel)
            awaitRows(retainedViewModel, listOf(studyChannel), listOf(studyGroup))

            // Subscribing while Study is selected must update Study's live query.
            val newStudyChannel = addChannel(studyProfileId, "Another study channel")
            awaitRows(retainedViewModel, listOf(studyChannel, newStudyChannel), listOf(studyGroup))

            assertTrue(ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
            scenario.recreate()
            assertRetainedViewModel(scenario, retainedViewModel)
            awaitRows(retainedViewModel, listOf(defaultChannel), listOf(defaultGroup))
        }
    }

    @Test
    fun switchingToEmptyProfileClearsCachedRowsAndKeepsFiltersAndLiveUpdates() {
        val defaultChannel = addChannel(ProfileManager.DEFAULT_PROFILE_ID, "Matching channel")
        val defaultGroup = addGroup(ProfileManager.DEFAULT_PROFILE_ID, "Matching group")
        lateinit var viewModel: SubscriptionViewModel
        instrumentation.runOnMainSync {
            viewModel = SubscriptionViewModel(context)
            viewModelStore.put("subscriptions", viewModel)
            viewModel.setFilterQuery("matching")
            viewModel.setListViewMode(false)
        }
        awaitRows(viewModel, listOf(defaultChannel), listOf(defaultGroup))

        instrumentation.runOnMainSync {
            viewModel.setProfile(studyProfileId)
            assertEquals(SubscriptionState.LoadedState(emptyList()), viewModel.stateLiveData.value)
            assertEquals(Pair(emptyList<Group>(), false), viewModel.feedGroupsLiveData.value)
        }

        // Invalidate both tables for the previous profile as well as the newly selected one.
        val newDefaultChannel = addChannel(ProfileManager.DEFAULT_PROFILE_ID, "Matching new default")
        val newDefaultGroup = addGroup(ProfileManager.DEFAULT_PROFILE_ID, "Matching new default")
        val studyChannel = addChannel(studyProfileId, "Matching study channel")
        val studyGroup = addGroup(studyProfileId, "Matching study group")
        addChannel(studyProfileId, "Unrelated channel")
        val otherService = addChannel(studyProfileId, "Matching other service", serviceId = 1)
        val musicChannel = addChannel(studyProfileId, "Matching music channel", youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        addGroup(studyProfileId, "Unrelated group")
        awaitRows(viewModel, listOf(studyChannel, otherService, musicChannel), listOf(studyGroup))
        instrumentation.runOnMainSync {
            val groups = requireNotNull(viewModel.feedGroupsLiveData.value)
            assertEquals(false, groups.second)
            assertTrue(groups.first.single() is FeedGroupCardGridItem)
            viewModel.setProfile(ProfileManager.DEFAULT_PROFILE_ID)
        }
        awaitRows(
            viewModel,
            listOf(defaultChannel, newDefaultChannel),
            listOf(defaultGroup, newDefaultGroup)
        )
    }

    @Test
    fun subscriptionLibraryKeepsAllServicesWhenBrowsingServiceChanges() {
        val profile = ProfileManager.DEFAULT_PROFILE_ID
        val youtube = addChannel(profile, "YouTube")
        val peertube = addChannel(profile, "PeerTube", serviceId = 3)
        val music = addChannel(profile, "Music", youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC)
        addChannel(studyProfileId, "Other profile", serviceId = 3)
        lateinit var viewModel: SubscriptionViewModel
        instrumentation.runOnMainSync {
            viewModel = SubscriptionViewModel(context)
            viewModelStore.put("subscriptions", viewModel)
        }
        val expected = listOf(youtube, peertube, music)
        awaitRows(viewModel, expected, emptyList())
        instrumentation.runOnMainSync {
            ServiceHelper.setSelectedServiceId(context, 3)
            viewModel.setFilterQuery("PeerTube")
        }
        awaitRows(viewModel, listOf(peertube), emptyList())
        instrumentation.runOnMainSync { viewModel.setFilterQuery("") }
        awaitRows(viewModel, expected, emptyList())
    }

    private fun assertRetainedViewModel(
        scenario: ActivityScenario<AboutActivity>,
        expected: SubscriptionViewModel
    ) {
        scenario.onActivity { activity ->
            val fragment = requireNotNull(activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG))
            assertSame(expected, ViewModelProvider(fragment)[SubscriptionViewModel::class.java])
        }
    }

    private fun addChannel(
        profileId: String,
        name: String,
        serviceId: Int = 0,
        youtubeModeMask: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR
    ): Long = database.subscriptionDAO().insert(
        SubscriptionEntity(
            serviceId = serviceId,
            url = "https://example.com/channel/${UUID.randomUUID()}",
            name = name,
            profileId = profileId,
            youtubeModeMask = youtubeModeMask
        )
    )

    private fun addGroup(profileId: String, name: String): Long = database.feedGroupDAO().insert(
        FeedGroupEntity(uid = 0, name = name, icon = FeedGroupIcon.EDUCATION, profileId = profileId)
    )

    private fun awaitRows(viewModel: SubscriptionViewModel, channels: List<Long>, groups: List<Long>) {
        awaitValue(viewModel.stateLiveData) { state ->
            state is SubscriptionState.LoadedState && itemIds(state.subscriptions) == channels.sorted()
        }
        awaitValue(viewModel.feedGroupsLiveData) { itemIds(it.first) == groups.sorted() }
    }

    private fun itemIds(groups: List<Group>): List<Long> = groups.map { it.getItem(0).id }.sorted()

    private fun <T> awaitValue(liveData: LiveData<T>, predicate: (T) -> Boolean) {
        val received = CountDownLatch(1)
        val observer = Observer<T> { value ->
            if (predicate(value)) received.countDown()
        }
        try {
            instrumentation.runOnMainSync { liveData.observeForever(observer) }
            assertTrue("Expected profile rows; last value: ${liveData.value}", received.await(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { liveData.removeObserver(observer) }
        }
    }

    companion object {
        private const val FRAGMENT_TAG = "profile-subscriptions"
    }
}
