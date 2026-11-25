/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ThemeHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionGroupViewModeTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val viewModelStore = ViewModelStore()
    private val clock = TestScheduler()
    private lateinit var database: AppDatabase
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxJavaPlugins.setComputationSchedulerHandler { clock }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        preferences.edit().clear().commit()
        RuntimeEnvironment.setQualifiers("normal-port")
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        addGroup(ProfileManager.DEFAULT_PROFILE_ID)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun gridChoiceSurvivesFreshViewModelWithListVideoLayout() {
        setVideoLayout("list")
        val first = newViewModel()
        assertMode(true, first)

        first.setListViewMode(false)

        assertMode(false, first)
        assertMode(false, newViewModel())
    }

    @Test
    fun listChoiceSurvivesFreshViewModelWithGridVideoLayout() {
        setVideoLayout("grid")
        val first = newViewModel()
        assertMode(false, first)

        first.setListViewMode(true)

        assertMode(true, first)
        assertMode(true, newViewModel())
    }

    @Test
    fun mostRecentToggleReplacesTheSavedChoice() {
        var viewModel = newViewModel()
        for (listMode in listOf(false, true, false, true)) {
            viewModel.setListViewMode(listMode)
            assertEquals(listMode, preferences.getBoolean(SubscriptionGroupViewPreferences.KEY, !listMode))
            viewModel = newViewModel()
            assertMode(listMode, viewModel)
        }
    }

    @Test
    fun missingPreferenceUsesThemeDefaultsWithoutSavingAnExplicitChoice() {
        for ((videoLayout, listMode) in listOf("list" to true, "grid" to false, "card" to false)) {
            setVideoLayout(videoLayout)
            assertMode(listMode, newViewModel())
            assertFalse(preferences.contains(SubscriptionGroupViewPreferences.KEY))
        }

        setVideoLayout("auto")
        RuntimeEnvironment.setQualifiers("normal-port")
        assertMode(false, newViewModel())
        RuntimeEnvironment.setQualifiers("normal-land")
        assertMode(true, newViewModel())
        assertFalse(preferences.contains(SubscriptionGroupViewPreferences.KEY))
    }

    @Test
    fun explicitChoiceSurvivesVideoLayoutChangesAndRotationWithoutChangingVideoPreferences() {
        val portraitKey = context.getString(R.string.list_view_mode_portrait_key)
        val landscapeKey = context.getString(R.string.list_view_mode_landscape_key)
        val layoutKey = context.getString(R.string.list_view_mode_key)
        val videoKeys = setOf(layoutKey, portraitKey, landscapeKey)

        for (listMode in listOf(false, true)) {
            preferences.edit().putString(layoutKey, "card")
                .putString(portraitKey, "grid").putString(landscapeKey, "list").commit()
            val originalVideoPreferences = preferences.all.filterKeys { it in videoKeys }
            newViewModel().setListViewMode(listMode)
            assertEquals(originalVideoPreferences, preferences.all.filterKeys { it in videoKeys })

            RuntimeEnvironment.setQualifiers("normal-port")
            assertEquals(ItemViewMode.GRID, ThemeHelper.getItemViewMode(context))
            assertMode(listMode, newViewModel())
            RuntimeEnvironment.setQualifiers("normal-land")
            assertEquals(ItemViewMode.LIST, ThemeHelper.getItemViewMode(context))
            assertMode(listMode, newViewModel())

            preferences.edit().putString(portraitKey, "list").putString(landscapeKey, "card").commit()
            RuntimeEnvironment.setQualifiers("normal-port")
            assertEquals(ItemViewMode.LIST, ThemeHelper.getItemViewMode(context))
            assertMode(listMode, newViewModel())
            RuntimeEnvironment.setQualifiers("normal-land")
            assertEquals(ItemViewMode.CARD, ThemeHelper.getItemViewMode(context))
            assertMode(listMode, newViewModel())
        }
    }

    @Test
    fun choiceIsGlobalAcrossProfileSwitchesAndFreshViewModels() {
        val studyProfile = requireNotNull(ProfileManager.createProfile(context, "Study"))
        addGroup(studyProfile.id)
        assertTrue(ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
        val first = newViewModel()
        first.setListViewMode(false)

        assertTrue(ProfileManager.setActiveProfile(context, studyProfile.id))
        first.setProfile(studyProfile.id)
        assertMode(false, first)
        val study = newViewModel()
        assertMode(false, study)

        study.setListViewMode(true)
        assertTrue(ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
        study.setProfile(ProfileManager.DEFAULT_PROFILE_ID)
        assertMode(true, study)
        assertMode(true, newViewModel())
    }

    private fun newViewModel(): SubscriptionViewModel {
        viewModelStore.clear()
        return SubscriptionViewModel(context).also { viewModelStore.put("subscriptions", it) }
    }

    private fun assertMode(listMode: Boolean, viewModel: SubscriptionViewModel) {
        clock.advanceTimeBy(1, TimeUnit.SECONDS)
        assertEquals(listMode, viewModel.getListViewMode())
        val groups = requireNotNull(viewModel.feedGroupsLiveData.value)
        assertEquals(listMode, groups.second)
        assertTrue(
            if (listMode) groups.first.single() is FeedGroupCardItem else groups.first.single() is FeedGroupCardGridItem
        )
    }

    private fun setVideoLayout(mode: String) {
        preferences.edit().putString(context.getString(R.string.list_view_mode_key), mode)
            .remove(context.getString(R.string.list_view_mode_portrait_key))
            .remove(context.getString(R.string.list_view_mode_landscape_key)).commit()
    }

    private fun addGroup(profileId: String) {
        database.feedGroupDAO().insert(
            FeedGroupEntity(uid = 0, name = "Group", icon = FeedGroupIcon.EDUCATION, profileId = profileId)
        )
    }
}
